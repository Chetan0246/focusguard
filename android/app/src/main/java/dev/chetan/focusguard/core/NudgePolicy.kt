package dev.chetan.focusguard.core

import java.time.Instant
import java.time.ZoneId

data class NudgeDecision(val block: StudyBlock, val distractedMin: Int, val message: String)

/**
 * Decides whether to nudge right now: we are inside a study block, distracted time inside
 * this block so far has crossed the threshold, and the cooldown since the last nudge passed.
 */
object NudgePolicy {
    const val COOLDOWN_MS = 20 * 60_000L

    fun decide(
        nowMs: Long,
        classified: List<ClassifiedSession>,
        blocks: List<StudyBlock>,
        thresholdMin: Int,
        lastNudgeMs: Long,
        planNudges: List<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NudgeDecision? {
        if (nowMs - lastNudgeMs < COOLDOWN_MS) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val minute = now.hour * 60 + now.minute
        val block = blocks.firstOrNull { it.contains(now.dayOfWeek.value, minute) } ?: return null
        val blockStart = now.toLocalDate().atStartOfDay(zone).plusMinutes(block.startMin.toLong()).toInstant().toEpochMilli()
        val distractedMs = classified.filter { it.label == Label.DISTRACTED }.sumOf { c ->
            val s = maxOf(c.session.startMs, blockStart)
            val e = minOf(c.session.endMs, nowMs)
            (e - s).coerceAtLeast(0)
        }
        val mins = (distractedMs / 60_000).toInt()
        if (mins < thresholdMin) return null
        val msg = planNudges.firstOrNull()
            ?: "$mins min off-task in your ${block.subject} block. Ready to get back to it?"
        return NudgeDecision(block, mins, msg)
    }
}
