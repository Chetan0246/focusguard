package dev.chetan.focusguard.core

import java.time.LocalDate

/**
 * Plain-text timetable so it is fast to edit on a phone:
 *   Mon-Fri 18:00-20:00 DSA
 *   Sat,Sun 10:00-12:30 Project
 *   Daily 06:30-07:15 Revision
 * Exams:
 *   2026-11-20 DBMS
 */
object Timetable {
    val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val LINE = Regex("""^\s*([A-Za-z,\-]+)\s+(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s+(.+?)\s*$""")
    private val EXAM = Regex("""^\s*(\d{4}-\d{2}-\d{2})\s+(.+?)\s*$""")

    data class ParseResult<T>(val items: List<T>, val errors: List<String>)

    fun parseDays(spec: String): List<Int>? {
        val s = spec.trim().lowercase()
        if (s == "daily" || s == "everyday") return (1..7).toList()
        if (s == "weekdays") return (1..5).toList()
        if (s == "weekends") return listOf(6, 7)
        val out = mutableListOf<Int>()
        for (part in s.split(',')) {
            val p = part.trim()
            if ('-' in p) {
                val (a, b) = p.split('-', limit = 2).map { dayIndex(it) ?: return null }
                if (a <= b) out += (a..b) else { out += (a..7); out += (1..b) }
            } else out += dayIndex(p) ?: return null
        }
        return out.distinct()
    }

    private fun dayIndex(s: String): Int? =
        DAY_NAMES.indexOfFirst { it.lowercase() == s.trim().take(3).lowercase() }.takeIf { it >= 0 }?.plus(1)

    fun parseBlocks(text: String): ParseResult<StudyBlock> {
        val items = mutableListOf<StudyBlock>()
        val errors = mutableListOf<String>()
        text.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }.forEach { line ->
            val m = LINE.matchEntire(line)
            val days = m?.let { parseDays(it.groupValues[1]) }
            if (m == null || days == null) { errors += "Can't read: \"$line\""; return@forEach }
            val g = m.groupValues
            val start = g[2].toInt() * 60 + g[3].toInt()
            val end = g[4].toInt() * 60 + g[5].toInt()
            if (start >= end || end > 24 * 60 || g[3].toInt() > 59 || g[5].toInt() > 59) {
                errors += "Bad time range: \"$line\""; return@forEach
            }
            days.forEach { d -> items += StudyBlock(d, start, end, g[6].take(40)) }
        }
        return ParseResult(items, errors)
    }

    fun parseExams(text: String): ParseResult<Exam> {
        val items = mutableListOf<Exam>()
        val errors = mutableListOf<String>()
        text.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }.forEach { line ->
            val m = EXAM.matchEntire(line)
            val date = m?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }
            if (m == null || date == null) errors += "Can't read exam: \"$line\""
            else items += Exam(m.groupValues[2].take(40), date)
        }
        return ParseResult(items, errors)
    }

    fun hhmm(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
