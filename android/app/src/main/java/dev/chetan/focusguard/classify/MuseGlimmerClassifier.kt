package dev.chetan.focusguard.classify

import dev.chetan.focusguard.core.AmbiguityJudge
import dev.chetan.focusguard.core.ClassifiedSession
import dev.chetan.focusguard.core.GlimmerProtocol
import dev.chetan.focusguard.core.Label
import dev.chetan.focusguard.core.NetGuard
import dev.chetan.focusguard.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Model-backed judge: asks Meta's open-weight Muse Glimmer (30B) to label the sessions the rules
 * could not decide. Glimmer is too large for a phone, so it runs on hardware the student owns
 * (laptop / desktop via llama.cpp, Ollama or LM Studio, all OpenAI-compatible) and is reached over
 * the local network. [NetGuard] refuses public hosts unless the user explicitly allows it, so raw
 * session details never leave the student's own devices.
 */
class MuseGlimmerClassifier(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val allowPublicHost: Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : AmbiguityJudge {
    override val id = "muse-glimmer"

    fun checkHost() {
        val host = NetGuard.hostOf(baseUrl) ?: throw IllegalArgumentException("Bad Glimmer URL: $baseUrl")
        if (!allowPublicHost && !NetGuard.isPrivateHost(host)) {
            throw SecurityException("Refusing to send session details to public host $host (not your own device)")
        }
    }

    override suspend fun judge(ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>): Map<Int, Pair<Label, String>> =
        withContext(Dispatchers.IO) {
            checkHost()
            val body = GlimmerProtocol.requestBody(model, ambiguous, all, zone)
            val headers = if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
            val resp = Http.postJson("$baseUrl/chat/completions", body.toString(), headers, timeoutMs = 120_000)
            GlimmerProtocol.parse(resp, ambiguous.size)
        }

    /** Quick connectivity check for the Settings screen. */
    suspend fun ping(): String = withContext(Dispatchers.IO) {
        checkHost()
        Http.get("$baseUrl/models", timeoutMs = 5_000).take(200)
    }
}
