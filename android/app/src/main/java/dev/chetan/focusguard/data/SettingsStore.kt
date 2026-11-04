package dev.chetan.focusguard.data

import android.content.Context
import dev.chetan.focusguard.core.Exam
import dev.chetan.focusguard.core.StudyBlock
import dev.chetan.focusguard.core.Timetable

/** All user settings in SharedPreferences (private app storage). No API keys live here. */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("focusguard", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    /** Relay (backend/) URL. 10.0.2.2 = the host machine from the Android emulator. */
    var relayUrl: String
        get() = p.getString("relay_url", "http://10.0.2.2:8000")!!
        set(v) = p.edit().putString("relay_url", v.trim().trimEnd('/')).apply()

    var relayToken: String
        get() = p.getString("relay_token", "")!!
        set(v) = p.edit().putString("relay_token", v.trim()).apply()

    var glimmerEnabled: Boolean
        get() = p.getBoolean("glimmer_enabled", false)
        set(v) = p.edit().putBoolean("glimmer_enabled", v).apply()

    /** OpenAI-compatible base URL of Muse Glimmer on the student's own machine. */
    var glimmerUrl: String
        get() = p.getString("glimmer_url", "http://192.168.1.10:8080/v1")!!
        set(v) = p.edit().putString("glimmer_url", v.trim().trimEnd('/')).apply()

    var glimmerModel: String
        get() = p.getString("glimmer_model", "muse-glimmer")!!
        set(v) = p.edit().putString("glimmer_model", v.trim()).apply()

    /** Optional `--api-key` you started llama.cpp with. Stored only in private app storage. */
    var glimmerKey: String
        get() = p.getString("glimmer_key", "")!!
        set(v) = p.edit().putString("glimmer_key", v.trim()).apply()

    var allowPublicGlimmerHost: Boolean
        get() = p.getBoolean("glimmer_public", false)
        set(v) = p.edit().putBoolean("glimmer_public", v).apply()

    var timetableText: String
        get() = p.getString("timetable", "Mon-Fri 18:00-20:00 DSA")!!
        set(v) = p.edit().putString("timetable", v).apply()

    var examsText: String
        get() = p.getString("exams", "")!!
        set(v) = p.edit().putString("exams", v).apply()

    var nudgeThresholdMin: Int
        get() = p.getInt("nudge_threshold", 10)
        set(v) = p.edit().putInt("nudge_threshold", v).apply()

    var demoMode: Boolean
        get() = p.getBoolean("demo_mode", false)
        set(v) = p.edit().putBoolean("demo_mode", v).apply()

    var lastNudgeMs: Long
        get() = p.getLong("last_nudge", 0L)
        set(v) = p.edit().putLong("last_nudge", v).apply()

    var nudgesToday: Int
        get() = if (p.getString("nudge_day", "") == java.time.LocalDate.now().toString()) p.getInt("nudges_today", 0) else 0
        set(v) = p.edit().putString("nudge_day", java.time.LocalDate.now().toString()).putInt("nudges_today", v).apply()

    var cachedPlanJson: String?
        get() = p.getString("plan_json", null)
        set(v) = p.edit().putString("plan_json", v).apply()

    var checkinNote: String?
        get() = if (p.getString("checkin_day", "") == java.time.LocalDate.now().toString()) p.getString("checkin_note", null) else null
        set(v) = p.edit().putString("checkin_day", java.time.LocalDate.now().toString()).putString("checkin_note", v).apply()

    fun blocks(): List<StudyBlock> = Timetable.parseBlocks(timetableText).items
    fun exams(): List<Exam> = Timetable.parseExams(examsText).items
}
