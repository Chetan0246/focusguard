package dev.chetan.focusguard.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.chetan.focusguard.MainActivity
import dev.chetan.focusguard.R
import dev.chetan.focusguard.core.DayContext
import dev.chetan.focusguard.core.NudgeDecision
import dev.chetan.focusguard.core.NudgePolicy
import dev.chetan.focusguard.core.PlanJson
import dev.chetan.focusguard.core.RuleClassifier
import dev.chetan.focusguard.data.DemoData
import dev.chetan.focusguard.data.SettingsStore
import dev.chetan.focusguard.data.UsageRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Nudges {
    private const val CHANNEL = "focus_nudges"
    private const val WORK = "focusguard-nudge-check"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Focus nudges", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Gentle nudges when distraction builds up during a study block"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** WorkManager's minimum periodic interval is 15 minutes. */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /**
     * Rule-only, offline check (nudges must work without any server). Returns the decision so the
     * UI's "Check now" button can show why a nudge did or did not fire.
     */
    fun evaluate(ctx: Context, force: Boolean = false): NudgeDecision? {
        val settings = SettingsStore(ctx)
        val repo = UsageRepository(ctx)
        val zone = ZoneId.systemDefault()
        // Demo mode simulates 19:40 so the nudge can be shown on camera at any time of day.
        val now = if (settings.demoMode) DemoData.demoNow() else System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = when {
            settings.demoMode -> DemoData.sessions().filter { it.startMs < now }.map { if (it.endMs > now) it.copy(endMs = now) else it }
            repo.hasUsageAccess() -> repo.sessionsBetween(start, now)
            else -> return null
        }
        val classified = RuleClassifier().classifySync(sessions, DayContext(settings.blocks(), zone))
        val planNudges = settings.cachedPlanJson?.let { runCatching { PlanJson.parsePlan(it).nudges }.getOrNull() } ?: emptyList()
        // The background worker never fires on demo data; only the explicit "Check nudge now" does.
        if (settings.demoMode && !force) return null
        val lastNudge = if (force) 0L else settings.lastNudgeMs
        val decision = NudgePolicy.decide(
            now, classified, settings.blocks(), settings.nudgeThresholdMin, lastNudge, planNudges, zone,
        ) ?: return null
        notify(ctx, decision)
        settings.lastNudgeMs = System.currentTimeMillis()
        settings.nudgesToday = settings.nudgesToday + 1
        return decision
    }

    fun notify(ctx: Context, d: NudgeDecision) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("${d.block.subject}: ${d.distractedMin} min off-task")
            .setContentText(d.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.message))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, n)
    }
}

class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { Nudges.evaluate(applicationContext) }
        return Result.success()
    }
}
