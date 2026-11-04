package dev.chetan.focusguard.core

/** One continuous stretch of time an app was in the foreground. Never leaves the phone. */
data class AppSession(
    val packageName: String,
    val appLabel: String,
    val startMs: Long,
    val endMs: Long,
    /** Android's ApplicationInfo.category hint, or null if unknown. */
    val systemCategory: Int? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    val durationMin: Double get() = durationMs / 60_000.0
}

/** Category names match the relay's privacy contract (backend/app/schemas.py). */
enum class Category(val wire: String) {
    STUDY("study"), PRODUCTIVE("productive"), COMMUNICATION("communication"), SOCIAL("social"),
    VIDEO("video"), GAMES("games"), BROWSER("browser"), OTHER("other");
}

enum class Label { FOCUSED, DISTRACTED, NEUTRAL }

data class ClassifiedSession(
    val session: AppSession,
    val category: Category,
    val label: Label,
    /** 0..1. Below [RuleClassifier.AMBIGUOUS_BELOW] the hybrid classifier asks Muse Glimmer. */
    val confidence: Double,
    val reason: String,
    val inStudyBlock: Boolean,
    /** "rules", "muse-glimmer", or "rules (glimmer unavailable)". */
    val source: String,
)

/** A recurring weekly study block. day: 1 = Monday ... 7 = Sunday (java.time.DayOfWeek values). */
data class StudyBlock(val day: Int, val startMin: Int, val endMin: Int, val subject: String) {
    fun contains(dayOfWeek: Int, minuteOfDay: Int) = day == dayOfWeek && minuteOfDay in startMin until endMin
}

data class Exam(val subject: String, val date: java.time.LocalDate)

data class PlanBlock(val start: String, val end: String, val task: String, val why: String)

data class StudyPlan(
    val headline: String,
    val focusScore: Int,
    val blocks: List<PlanBlock>,
    val nudges: List<String>,
    val examStrategy: String,
    val tomorrowGoalMin: Int,
    val source: String,
)

data class CheckinResult(
    val transcript: String,
    val subject: String,
    val plannedMinutes: Int,
    val blockers: List<String>,
    val summary: String,
    val source: String,
)
