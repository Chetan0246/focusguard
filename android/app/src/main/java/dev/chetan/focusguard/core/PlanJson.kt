package dev.chetan.focusguard.core

import org.json.JSONArray
import org.json.JSONObject

/** (De)serialises plans and check-ins returned by the relay. */
object PlanJson {
    fun parsePlan(json: String): StudyPlan {
        val o = JSONObject(json)
        val blocks = o.optJSONArray("blocks") ?: JSONArray()
        val nudges = o.optJSONArray("nudges") ?: JSONArray()
        return StudyPlan(
            headline = o.optString("headline"),
            focusScore = o.optInt("focus_score"),
            blocks = (0 until blocks.length()).map { i ->
                val b = blocks.getJSONObject(i)
                PlanBlock(b.optString("start"), b.optString("end"), b.optString("task"), b.optString("why"))
            },
            nudges = (0 until nudges.length()).map { nudges.getString(it) },
            examStrategy = o.optString("exam_strategy"),
            tomorrowGoalMin = o.optInt("tomorrow_focus_goal_min"),
            source = o.optString("source", "unknown"),
        )
    }

    fun parseCheckin(json: String): CheckinResult {
        val o = JSONObject(json)
        val i = o.getJSONObject("intent")
        val bl = i.optJSONArray("blockers") ?: JSONArray()
        return CheckinResult(
            transcript = o.optString("transcript"),
            subject = i.optString("subject"),
            plannedMinutes = i.optInt("planned_minutes"),
            blockers = (0 until bl.length()).map { bl.getString(it) },
            summary = i.optString("summary"),
            source = o.optString("source"),
        )
    }

    /**
     * OFFLINE fallback used only when the relay can't be reached, so the demo never dead-ends.
     * Clearly labelled source = "offline-fallback (no model)".
     */
    fun offlinePlan(stats: DayStats, tomorrowBlocks: List<StudyBlock>, examSubjects: List<String>): StudyPlan {
        val total = stats.focusedMin + stats.distractedMin
        val score = if (total == 0) 50 else 100 * stats.focusedMin / total
        val worst = stats.distractedByHour.withIndex().maxByOrNull { it.value }?.index ?: 21
        val subjects = (examSubjects + tomorrowBlocks.map { it.subject }).ifEmpty { listOf("Revision") }
        var cursor = tomorrowBlocks.firstOrNull()?.startMin ?: (18 * 60)
        val blocks = (0 until 3).map { i ->
            val s = cursor; val e = minOf(s + 45, 23 * 60 + 59); cursor = e + 10
            PlanBlock(Timetable.hhmm(s), Timetable.hhmm(e), "${subjects[i % subjects.size]}: deep work", "rotate subjects")
        }
        return StudyPlan(
            headline = "${stats.focusedMin} min focused, ${stats.distractedMin} min distracted (offline estimate).",
            focusScore = score,
            blocks = blocks,
            nudges = listOf("%02d:00 is your usual slump. One more 25-minute sprint?".format(worst)),
            examStrategy = "Relay unreachable: connect to generate a Muse Spark plan.",
            tomorrowGoalMin = maxOf(30, stats.focusedMin * 6 / 5),
            source = "offline-fallback (no model)",
        )
    }

    fun planToJson(p: StudyPlan): String = JSONObject()
        .put("headline", p.headline).put("focus_score", p.focusScore)
        .put("blocks", JSONArray().apply {
            p.blocks.forEach { put(JSONObject().put("start", it.start).put("end", it.end).put("task", it.task).put("why", it.why)) }
        })
        .put("nudges", JSONArray(p.nudges)).put("exam_strategy", p.examStrategy)
        .put("tomorrow_focus_goal_min", p.tomorrowGoalMin).put("source", p.source)
        .toString()
}
