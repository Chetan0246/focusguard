package dev.chetan.focusguard.net

import dev.chetan.focusguard.core.CheckinResult
import dev.chetan.focusguard.core.PlanJson
import dev.chetan.focusguard.core.StudyPlan
import org.json.JSONObject

/**
 * Talks to the FocusGuard relay (backend/), which holds the Meta Model API key.
 * Sends ONLY the anonymized summary JSON produced by SummaryBuilder, or a short voice WAV.
 */
class RelayClient(private val baseUrl: String, private val token: String) {
    private val headers get() = if (token.isNotBlank()) mapOf("X-Relay-Token" to token) else emptyMap()

    /** Returns (mode, model) from /health, e.g. ("live", "muse-spark-1.3") or ("mock", ...). */
    fun health(): Pair<String, String> {
        val o = JSONObject(Http.get("$baseUrl/health"))
        return o.optString("mode") to o.optString("model")
    }

    fun plan(summary: JSONObject): StudyPlan =
        PlanJson.parsePlan(Http.postJson("$baseUrl/v1/plan", summary.toString(), headers, timeoutMs = 120_000))

    fun checkin(wav: ByteArray): CheckinResult =
        PlanJson.parseCheckin(Http.postMultipartFile("$baseUrl/v1/checkin", "audio", "checkin.wav", "audio/wav", wav, headers))
}
