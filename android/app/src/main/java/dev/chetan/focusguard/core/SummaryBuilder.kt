package dev.chetan.focusguard.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Today's numbers, computed on-device from classified sessions. */
data class DayStats(
    val focusedMin: Int,
    val distractedMin: Int,
    val neutralMin: Int,
    val minutesByCategory: Map<Category, Int>,
    val distractedInBlocksMin: Int,
    val longestFocusStreakMin: Int,
    val appSwitches: Int,
    val lateNightMin: Int,
    val distractedByHour: List<Int>,
)

/**
 * Turns raw classified sessions into the ONLY thing that leaves the phone: an anonymized
 * aggregate summary. No app names, package names or per-session timestamps are included.
 * The JSON matches backend/app/schemas.py::DailySummary exactly (the relay rejects extras).
 */
object SummaryBuilder {

    fun stats(sessions: List<ClassifiedSession>, zone: ZoneId = ZoneId.systemDefault()): DayStats {
        var f = 0.0; var d = 0.0; var n = 0.0; var dIn = 0.0; var late = 0.0
        val byCat = mutableMapOf<Category, Double>()
        val byHour = DoubleArray(24)
        val sorted = sessions.sortedBy { it.session.startMs }
        for (c in sorted) {
            val m = c.session.durationMin
            when (c.label) {
                Label.FOCUSED -> f += m
                Label.DISTRACTED -> { d += m; if (c.inStudyBlock) dIn += m }
                Label.NEUTRAL -> n += m
            }
            byCat[c.category] = (byCat[c.category] ?: 0.0) + m
            val t = Instant.ofEpochMilli(c.session.startMs).atZone(zone)
            if (c.label == Label.DISTRACTED) byHour[t.hour] += m
            if (t.hour >= 23 || t.hour < 5) late += m
        }
        // Longest streak of consecutive focused (or neutral glance) sessions with gaps < 5 min.
        var best = 0.0; var cur = 0.0; var lastEnd = 0L
        for (c in sorted) {
            val gapOk = lastEnd == 0L || c.session.startMs - lastEnd < 5 * 60_000
            when {
                c.label == Label.FOCUSED && gapOk -> cur += c.session.durationMin
                c.label == Label.FOCUSED -> cur = c.session.durationMin
                c.label == Label.NEUTRAL && c.session.durationMs < 60_000 && gapOk -> Unit
                else -> cur = 0.0
            }
            best = maxOf(best, cur)
            lastEnd = c.session.endMs
        }
        val switches = sorted.zipWithNext().count { (a, b) -> a.session.packageName != b.session.packageName }
        return DayStats(
            f.roundToInt(), d.roundToInt(), n.roundToInt(),
            byCat.mapValues { it.value.roundToInt() }.filterValues { it > 0 },
            dIn.roundToInt(), best.roundToInt(), switches, late.roundToInt(),
            byHour.map { it.roundToInt().coerceIn(0, 60) },
        )
    }

    fun summaryJson(
        stats: DayStats,
        today: LocalDate,
        blocks: List<StudyBlock>,
        exams: List<Exam>,
        classifierId: String,
        nudgesSent: Int,
        checkinNote: String?,
    ): JSONObject {
        val o = JSONObject()
        o.put("date_label", "today")
        o.put("weekday", Timetable.DAY_NAMES[today.dayOfWeek.value - 1])
        o.put("focused_min", stats.focusedMin.coerceIn(0, 1440))
        o.put("distracted_min", stats.distractedMin.coerceIn(0, 1440))
        o.put("neutral_min", stats.neutralMin.coerceIn(0, 1440))
        o.put("minutes_by_category", JSONObject().apply {
            stats.minutesByCategory.forEach { (k, v) -> put(k.wire, v.coerceIn(0, 1440)) }
        })
        o.put("distracted_min_in_study_blocks", stats.distractedInBlocksMin.coerceIn(0, 1440))
        o.put("longest_focus_streak_min", stats.longestFocusStreakMin.coerceIn(0, 1440))
        o.put("app_switches", stats.appSwitches.coerceIn(0, 10000))
        o.put("late_night_min", stats.lateNightMin.coerceIn(0, 1440))
        o.put("distracted_by_hour", JSONArray(stats.distractedByHour))
        o.put("nudges_sent", nudgesSent.coerceIn(0, 100))
        o.put("classifier", if (classifierId in setOf("rules", "muse-glimmer", "hybrid")) classifierId else "rules")
        o.put("study_blocks", JSONArray().apply {
            blocks.take(50).forEach { b ->
                put(JSONObject().put("day", Timetable.DAY_NAMES[b.day - 1])
                    .put("start", Timetable.hhmm(b.startMin)).put("end", Timetable.hhmm(b.endMin))
                    .put("subject", b.subject.take(40)))
            }
        })
        o.put("exams", JSONArray().apply {
            exams.map { it to ChronoUnit.DAYS.between(today, it.date) }
                .filter { it.second in 0..365 }
                .sortedBy { it.second }
                .take(20)
                .forEach { (e, days) -> put(JSONObject().put("subject", e.subject.take(40)).put("days_until", days)) }
        })
        if (!checkinNote.isNullOrBlank()) o.put("checkin_note", scrub(checkinNote).take(500))
        return o
    }

    private val PKG = Regex("""\b[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+){2,}\b""")
    /** Belt-and-braces: strip anything that looks like a package name from free text. */
    fun scrub(text: String): String = PKG.replace(text, "[app]")
}
