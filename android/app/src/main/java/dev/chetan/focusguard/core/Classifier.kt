package dev.chetan.focusguard.core

import java.time.Instant
import java.time.ZoneId

/** Everything a classifier may know about the day besides the sessions themselves. */
data class DayContext(
    val blocks: List<StudyBlock>,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** Common interface for all on-device / own-hardware classifiers. */
interface SessionClassifier {
    val id: String
    suspend fun classify(sessions: List<AppSession>, ctx: DayContext): List<ClassifiedSession>
}

fun DayContext.studyBlockAt(ms: Long): StudyBlock? {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val minute = t.hour * 60 + t.minute
    return blocks.firstOrNull { it.contains(t.dayOfWeek.value, minute) }
}

fun DayContext.inStudyBlock(s: AppSession): Boolean = studyBlockAt(s.startMs + s.durationMs / 2) != null

/**
 * Deterministic rule classifier. Always available, runs in microseconds, no network.
 * It is the fallback for every other classifier and the only classifier the background
 * nudge worker uses (nudges must work offline).
 */
class RuleClassifier : SessionClassifier {
    override val id = "rules"

    companion object {
        /** Sessions classified with lower confidence than this are "ambiguous". */
        const val AMBIGUOUS_BELOW = 0.6
        const val GLANCE_MS = 20_000L
    }

    fun classifyOne(s: AppSession, ctx: DayContext): ClassifiedSession {
        val cat = AppCategories.categorize(s.packageName, s.appLabel, s.systemCategory)
        val inBlock = ctx.inStudyBlock(s)
        val min = s.durationMin
        fun r(label: Label, conf: Double, why: String) =
            ClassifiedSession(s, cat, label, conf, why, inBlock, id)

        if (s.durationMs < GLANCE_MS) return r(Label.NEUTRAL, 0.9, "quick glance (<20s)")
        return when (cat) {
            Category.STUDY -> r(Label.FOCUSED, 0.9, "study app")
            Category.PRODUCTIVE ->
                if (inBlock) r(Label.FOCUSED, 0.8, "productive app during study block")
                else r(Label.FOCUSED, 0.65, "productive app")
            Category.COMMUNICATION -> when {
                min < 3 -> r(Label.NEUTRAL, 0.7, "short message check")
                inBlock -> r(Label.DISTRACTED, 0.55, "long chat during study block")
                else -> r(Label.NEUTRAL, 0.6, "messaging")
            }
            Category.SOCIAL ->
                if (inBlock) r(Label.DISTRACTED, 0.9, "social feed during study block")
                else r(Label.DISTRACTED, 0.75, "social feed")
            Category.GAMES ->
                if (inBlock) r(Label.DISTRACTED, 0.95, "game during study block")
                else r(Label.DISTRACTED, 0.7, "game")
            // YouTube can be a lecture or a rabbit hole: rules can't tell, so low confidence.
            Category.VIDEO ->
                if (inBlock) r(Label.DISTRACTED, 0.45, "video during study block (could be a lecture)")
                else r(Label.DISTRACTED, 0.6, "video")
            Category.BROWSER -> r(Label.NEUTRAL, 0.4, "browser (could be research or browsing)")
            Category.OTHER -> r(Label.NEUTRAL, 0.5, "uncategorised app")
        }
    }

    override suspend fun classify(sessions: List<AppSession>, ctx: DayContext) = sessions.map { classifyOne(it, ctx) }
    fun classifySync(sessions: List<AppSession>, ctx: DayContext) = sessions.map { classifyOne(it, ctx) }
}

/** A remote judge for ambiguous sessions (implemented by MuseGlimmerClassifier on Android). */
interface AmbiguityJudge {
    val id: String
    /** Returns new labels for the given ambiguous sessions, keyed by index into [ambiguous]. Throws on failure. */
    suspend fun judge(ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>): Map<Int, Pair<Label, String>>
}

/**
 * Rules first; only the ambiguous sessions (low rule confidence) are sent to Muse Glimmer
 * running on the student's own hardware. Any failure falls back to the rule labels, so the
 * app always works.
 */
class HybridClassifier(
    private val rules: RuleClassifier,
    private val judge: AmbiguityJudge?,
    private val maxAmbiguous: Int = 40,
) : SessionClassifier {
    override val id = "hybrid"
    var lastError: String? = null
        private set

    override suspend fun classify(sessions: List<AppSession>, ctx: DayContext): List<ClassifiedSession> {
        val base = rules.classifySync(sessions, ctx)
        if (judge == null) return base
        val ambiguousIdx = base.indices
            .filter { base[it].confidence < RuleClassifier.AMBIGUOUS_BELOW }
            .sortedByDescending { base[it].session.durationMs }
            .take(maxAmbiguous)
        if (ambiguousIdx.isEmpty()) return base
        return try {
            val verdicts = judge.judge(ambiguousIdx.map { base[it] }, base)
            lastError = null
            base.toMutableList().also { out ->
                verdicts.forEach { (k, v) ->
                    val i = ambiguousIdx.getOrNull(k) ?: return@forEach
                    out[i] = out[i].copy(label = v.first, reason = v.second, confidence = 0.8, source = judge.id)
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            base.map { if (it.confidence < RuleClassifier.AMBIGUOUS_BELOW) it.copy(source = "rules (glimmer unavailable)") else it }
        }
    }
}
