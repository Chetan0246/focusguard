package dev.chetan.focusguard.data

import dev.chetan.focusguard.core.AppSession
import java.time.LocalDate
import java.time.ZoneId

/**
 * A realistic synthetic student day, for emulators and demo videos where the phone has no
 * real usage history. Enabled with the "Demo data" switch in Settings and always labelled in the UI.
 */
object DemoData {
    private data class S(val h: Int, val m: Int, val min: Double, val pkg: String, val label: String)

    private val day = listOf(
        S(7, 5, 6.0, "com.whatsapp", "WhatsApp"),
        S(7, 12, 14.0, "com.instagram.android", "Instagram"),
        S(9, 30, 38.0, "com.google.android.apps.classroom", "Classroom"),
        S(10, 10, 1.0, "com.whatsapp", "WhatsApp"),
        S(13, 15, 22.0, "com.google.android.youtube", "YouTube"),
        S(14, 0, 12.0, "com.supercell.clashroyale", "Clash Royale"),
        S(18, 0, 25.0, "com.adobe.reader", "Adobe Acrobat"),
        S(18, 26, 18.0, "com.google.android.youtube", "YouTube"),          // lecture? ambiguous
        S(18, 45, 20.0, "com.google.android.apps.docs.editors.docs", "Docs"),
        S(19, 6, 9.0, "com.android.chrome", "Chrome"),                     // research? ambiguous
        S(19, 16, 17.0, "com.instagram.android", "Instagram"),
        S(19, 34, 0.25, "com.whatsapp", "WhatsApp"),
        S(19, 35, 15.0, "com.leetcode.app", "LeetCode"),
        S(21, 0, 31.0, "com.google.android.youtube", "YouTube"),
        S(21, 32, 11.0, "com.reddit.frontpage", "Reddit"),
        S(23, 20, 16.0, "com.instagram.android", "Instagram"),
    )

    /** The whole synthetic day (not cut at "now"), so a demo looks the same at any hour. */
    fun sessions(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<AppSession> {
        val base = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return day.map {
            val start = base + (it.h * 60 + it.m) * 60_000L
            AppSession(it.pkg, it.label, start, start + (it.min * 60_000).toLong())
        }
    }

    /** Simulated "now" for the demo nudge check: 19:40 today, inside the demo DSA block (weekdays). */
    fun demoNow(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).plusMinutes(19 * 60 + 40L).toInstant().toEpochMilli()

    const val TIMETABLE = "Mon-Fri 18:00-20:00 DSA\nMon,Wed,Fri 09:30-10:30 DBMS lecture\nSat,Sun 10:00-12:00 Project"
    fun exams(today: LocalDate = LocalDate.now()) = "${today.plusDays(9)} DBMS\n${today.plusDays(16)} Operating Systems"
}
