package dev.chetan.focusguard.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.chetan.focusguard.classify.MuseGlimmerClassifier
import dev.chetan.focusguard.core.CheckinResult
import dev.chetan.focusguard.core.ClassifiedSession
import dev.chetan.focusguard.core.DayContext
import dev.chetan.focusguard.core.DayStats
import dev.chetan.focusguard.core.HybridClassifier
import dev.chetan.focusguard.core.PlanJson
import dev.chetan.focusguard.core.RuleClassifier
import dev.chetan.focusguard.core.StudyPlan
import dev.chetan.focusguard.core.SummaryBuilder
import dev.chetan.focusguard.data.DemoData
import dev.chetan.focusguard.data.SettingsStore
import dev.chetan.focusguard.data.UsageRepository
import dev.chetan.focusguard.net.RelayClient
import dev.chetan.focusguard.voice.WavRecorder
import dev.chetan.focusguard.work.Nudges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app)
    private val repo = UsageRepository(app)
    private val zone = ZoneId.systemDefault()

    var hasUsageAccess by mutableStateOf(repo.hasUsageAccess())
    var loading by mutableStateOf(false)
    var sessions by mutableStateOf<List<ClassifiedSession>>(emptyList())
    var stats by mutableStateOf<DayStats?>(null)
    var classifierStatus by mutableStateOf("rules")
    var plan by mutableStateOf(settings.cachedPlanJson?.let { runCatching { PlanJson.parsePlan(it) }.getOrNull() })
    var planLoading by mutableStateOf(false)
    var planError by mutableStateOf<String?>(null)
    var summaryPreview by mutableStateOf<String?>(null)
    var relayStatus by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var recording by mutableStateOf(false)
    var checkin by mutableStateOf<CheckinResult?>(null)
    var glimmerTest by mutableStateOf<String?>(null)

    private val recorder = WavRecorder()

    fun refreshAccess() { hasUsageAccess = repo.hasUsageAccess() }

    private fun glimmer() = MuseGlimmerClassifier(
        settings.glimmerUrl, settings.glimmerModel, settings.glimmerKey, settings.allowPublicGlimmerHost, zone,
    )

    fun loadToday() {
        refreshAccess()
        viewModelScope.launch {
            loading = true
            val now = System.currentTimeMillis()
            val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val raw = withContext(Dispatchers.IO) {
                when {
                    settings.demoMode -> DemoData.sessions()
                    hasUsageAccess -> repo.sessionsBetween(start, now)
                    else -> emptyList()
                }
            }
            val ctx = DayContext(settings.blocks(), zone)
            val hybrid = HybridClassifier(RuleClassifier(), if (settings.glimmerEnabled) glimmer() else null)
            val out = hybrid.classify(raw, ctx)
            sessions = out.sortedByDescending { it.session.startMs }
            stats = SummaryBuilder.stats(out, zone)
            val judged = out.count { it.source == "muse-glimmer" }
            classifierStatus = when {
                !settings.glimmerEnabled -> "On-device rules"
                hybrid.lastError != null -> "Rules (Muse Glimmer unreachable: ${hybrid.lastError?.take(60)})"
                else -> "Hybrid: rules + Muse Glimmer ($judged sessions judged)"
            }
            loading = false
        }
    }

    private fun classifierId(): String =
        if (settings.glimmerEnabled && sessions.any { it.source == "muse-glimmer" }) "hybrid" else "rules"

    fun buildSummary(): JSONObject? {
        val s = stats ?: return null
        return SummaryBuilder.summaryJson(
            s, LocalDate.now(), settings.blocks(), settings.exams(), classifierId(),
            settings.nudgesToday, settings.checkinNote,
        )
    }

    fun previewSummary() { summaryPreview = buildSummary()?.toString(2) }

    fun generatePlan() {
        val summary = buildSummary() ?: run { planError = "Load today's usage first."; return }
        summaryPreview = summary.toString(2)
        viewModelScope.launch {
            planLoading = true; planError = null
            try {
                val client = RelayClient(settings.relayUrl, settings.relayToken)
                val (status, p) = withContext(Dispatchers.IO) {
                    val h = runCatching { client.health().let { "Relay: ${it.first} (${it.second})" } }.getOrNull()
                    h to client.plan(summary)
                }
                relayStatus = status
                applyPlan(p)
            } catch (e: Exception) {
                planError = "Relay unreachable (${e.message?.take(120)}). Showing offline estimate."
                val tomorrow = LocalDate.now().plusDays(1).dayOfWeek.value
                val exams = settings.exams().filter { !it.date.isBefore(LocalDate.now()) }.sortedBy { it.date }.map { it.subject }
                applyPlan(PlanJson.offlinePlan(stats!!, settings.blocks().filter { it.day == tomorrow }, exams))
            } finally {
                planLoading = false
            }
        }
    }

    private fun applyPlan(p: StudyPlan) {
        plan = p
        settings.cachedPlanJson = PlanJson.planToJson(p)
    }

    fun checkNudgeNow() {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { Nudges.evaluate(getApplication(), force = true) }
            message = d?.let { "Nudge sent: ${it.distractedMin} min off-task in ${it.block.subject}" }
                ?: "No nudge: not in a study block, or distraction below ${settings.nudgeThresholdMin} min."
        }
    }

    fun startCheckin() {
        if (recording) return
        recording = true
        viewModelScope.launch {
            try {
                val wav = recorder.record(maxSeconds = 20)
                recording = false
                message = "Transcribing with Muse Voice…"
                val r = withContext(Dispatchers.IO) { RelayClient(settings.relayUrl, settings.relayToken).checkin(wav) }
                checkin = r
                settings.checkinNote = SummaryBuilder.scrub(r.summary + (if (r.blockers.isNotEmpty()) " Blockers: " + r.blockers.joinToString("; ") else ""))
                message = null
            } catch (e: Exception) {
                message = "Check-in failed: ${e.message?.take(120)}"
            } finally {
                recording = false
            }
        }
    }

    fun stopCheckin() = recorder.stop()

    fun testGlimmer() {
        viewModelScope.launch {
            glimmerTest = "Testing…"
            glimmerTest = try { "OK: " + glimmer().ping() } catch (e: Exception) { "Failed: ${e.message?.take(160)}" }
        }
    }
}
