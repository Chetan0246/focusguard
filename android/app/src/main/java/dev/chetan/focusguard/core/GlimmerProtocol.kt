package dev.chetan.focusguard.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Request/response format for asking Muse Glimmer (OpenAI-compatible server on the student's
 * own hardware, e.g. `llama serve ... -a muse-glimmer`) to adjudicate ambiguous sessions.
 * Kept JVM-pure so it is unit-tested; the HTTP call lives in classify/MuseGlimmerClassifier.
 */
object GlimmerProtocol {
    const val SYSTEM = """You label phone app sessions for a student's focus coach.
For each item decide: "focused" (studying, coursework, research, lectures), "distracted" (entertainment,
feeds, games, idle browsing) or "neutral" (logistics, quick checks). Use the neighbouring apps and whether
it happened during a study block as evidence, e.g. YouTube between a PDF reader and Google Docs during a
study block is probably a lecture. Reply with JSON only: {"labels":[{"id":0,"label":"focused","reason":"..."}]}.
Reasons max 10 words."""

    val RESPONSE_SCHEMA: JSONObject = JSONObject(
        """{"type":"object","properties":{"labels":{"type":"array","items":{"type":"object",
        "properties":{"id":{"type":"integer"},"label":{"type":"string","enum":["focused","distracted","neutral"]},
        "reason":{"type":"string"}},"required":["id","label","reason"]}}},"required":["labels"]}"""
    )

    fun userMessage(ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>, zone: ZoneId): String {
        val arr = JSONArray()
        ambiguous.forEachIndexed { i, c ->
            val idx = all.indexOf(c)
            val prev = all.getOrNull(idx - 1)?.session?.appLabel ?: "-"
            val next = all.getOrNull(idx + 1)?.session?.appLabel ?: "-"
            val t = Instant.ofEpochMilli(c.session.startMs).atZone(zone)
            arr.put(JSONObject()
                .put("id", i)
                .put("app", c.session.appLabel)
                .put("category", c.category.wire)
                .put("minutes", "%.1f".format(c.session.durationMin))
                .put("time", "%02d:%02d".format(t.hour, t.minute))
                .put("in_study_block", c.inStudyBlock)
                .put("prev_app", prev)
                .put("next_app", next))
        }
        return "Sessions to label:\n$arr"
    }

    fun requestBody(model: String, ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>, zone: ZoneId): JSONObject =
        JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", userMessage(ambiguous, all, zone))))
            .put("max_tokens", 4096)
            .put("stream", false)
            // llama.cpp: keep Muse Glimmer's reasoning short (docs: chat_template_kwargs.reasoning_strength)
            .put("chat_template_kwargs", JSONObject().put("reasoning_strength", "low"))
            .put("response_format", JSONObject().put("type", "json_schema")
                .put("json_schema", JSONObject().put("name", "labels").put("schema", RESPONSE_SCHEMA)))

    /** Parses the chat completion; tolerant of extra prose around the JSON. */
    fun parse(responseBody: String, count: Int): Map<Int, Pair<Label, String>> {
        val content = JSONObject(responseBody).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start) { "no JSON in model reply" }
        val labels = JSONObject(content.substring(start, end + 1)).getJSONArray("labels")
        val out = mutableMapOf<Int, Pair<Label, String>>()
        for (i in 0 until labels.length()) {
            val o = labels.getJSONObject(i)
            val id = o.optInt("id", -1)
            val label = when (o.optString("label").lowercase()) {
                "focused" -> Label.FOCUSED
                "distracted" -> Label.DISTRACTED
                "neutral" -> Label.NEUTRAL
                else -> null
            }
            if (id in 0 until count && label != null) out[id] = label to ("Glimmer: " + o.optString("reason").take(80))
        }
        return out
    }
}
