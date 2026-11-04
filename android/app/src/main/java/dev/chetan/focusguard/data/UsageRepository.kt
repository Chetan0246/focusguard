package dev.chetan.focusguard.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import dev.chetan.focusguard.core.AppSession

/** Reads foreground app sessions with UsageStatsManager. Data stays in memory on the phone. */
class UsageRepository(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private val launchers: Set<String> by lazy {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    private val ignored = setOf("com.android.systemui", "com.google.android.permissioncontroller", "com.android.settings")

    /** RESUMED ... PAUSED pairs per package become sessions; open sessions are closed at [endMs]. */
    fun sessionsBetween(startMs: Long, endMs: Long): List<AppSession> {
        val events = usm.queryEvents(startMs, endMs)
        val openSince = HashMap<String, Long>()
        val result = mutableListOf<AppSession>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openSince.putIfAbsent(e.packageName, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openSince.remove(e.packageName) ?: continue
                    result += make(e.packageName, start, e.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Screen off closes everything that was open.
                    openSince.forEach { (pkg, s) -> result += make(pkg, s, e.timeStamp) }
                    openSince.clear()
                }
            }
        }
        openSince.forEach { (pkg, s) -> result += make(pkg, s, endMs) }
        return result
            .filter { it.durationMs >= 5_000 && it.packageName != context.packageName }
            .filter { it.packageName !in launchers && it.packageName !in ignored }
            .sortedBy { it.startMs }
    }

    private fun make(pkg: String, start: Long, end: Long): AppSession {
        val (label, cat) = info(pkg)
        return AppSession(pkg, label, start, end, cat)
    }

    private val cache = HashMap<String, Pair<String, Int?>>()

    private fun info(pkg: String): Pair<String, Int?> = cache.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            val cat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category >= 0) ai.category else null
            pm.getApplicationLabel(ai).toString() to cat
        } catch (_: PackageManager.NameNotFoundException) {
            pkg to null
        }
    }
}
