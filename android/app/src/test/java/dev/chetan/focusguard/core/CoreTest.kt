package dev.chetan.focusguard.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoreTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 3) // a Tuesday
    private fun at(h: Int, m: Int) = day.atStartOfDay(zone).plusMinutes((h * 60 + m).toLong()).toInstant().toEpochMilli()
    private fun s(pkg: String, label: String, h: Int, m: Int, min: Int) = AppSession(pkg, label, at(h, m), at(h, m) + min * 60_000L)
    private val blocks = Timetable.parseBlocks("Mon-Fri 18:00-20:00 DSA").items
    private val ctx = DayContext(blocks, zone)

    @Test fun timetableParsesRangesListsAndErrors() {
        val r = Timetable.parseBlocks("Mon-Fri 18:00-20:00 DSA\nSat,Sun 10:00-12:30 Project\nDaily 6:30-7:15 Rev\nnonsense")
        assertEquals(5 + 2 + 7, r.items.size)
        assertEquals(1, r.errors.size)
        assertEquals(6 * 60 + 30, r.items.last().startMin)
        assertEquals(listOf(6, 7, 1), Timetable.parseDays("Sat-Mon"))
        assertTrue(Timetable.parseBlocks("Mon 20:00-18:00 X").errors.isNotEmpty())
        val ex = Timetable.parseExams("2026-11-20 DBMS\n2026-13-01 Bad")
        assertEquals(1, ex.items.size); assertEquals(1, ex.errors.size)
    }

    @Test fun rulesLabelObviousCases() {
        val r = RuleClassifier()
        assertEquals(Label.FOCUSED, r.classifyOne(s("com.google.android.apps.classroom", "Classroom", 10, 0, 30), ctx).label)
        val insta = r.classifyOne(s("com.instagram.android", "Instagram", 18, 30, 15), ctx)
        assertEquals(Label.DISTRACTED, insta.label); assertTrue(insta.inStudyBlock); assertTrue(insta.confidence >= 0.9)
        assertEquals(Label.NEUTRAL, r.classifyOne(AppSession("com.instagram.android", "Instagram", at(18, 0), at(18, 0) + 10_000), ctx).label)
    }

    @Test fun youtubeInStudyBlockIsAmbiguous() {
        val c = RuleClassifier().classifyOne(s("com.google.android.youtube", "YouTube", 18, 30, 20), ctx)
        assertTrue(c.confidence < RuleClassifier.AMBIGUOUS_BELOW)
    }

    @Test fun categoriesFallBackToSystemHintAndKeywords() {
        assertEquals(Category.GAMES, AppCategories.categorize("x.y.z", "Thing", 0))
        assertEquals(Category.STUDY, AppCategories.categorize("com.foo.learnmaths", "Learn Maths", null))
        assertEquals(Category.OTHER, AppCategories.categorize("com.foo.bar", "Bar", null))
    }

    @Test fun hybridUsesJudgeOnlyForAmbiguousAndFallsBack() = runBlocking {
        val sessions = listOf(
            s("com.adobe.reader", "Adobe Acrobat", 18, 0, 25),
            s("com.google.android.youtube", "YouTube", 18, 26, 18),
            s("com.instagram.android", "Instagram", 19, 0, 10),
        )
        var asked = 0
        val judge = object : AmbiguityJudge {
            override val id = "muse-glimmer"
            override suspend fun judge(ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>): Map<Int, Pair<Label, String>> {
                asked = ambiguous.size
                return mapOf(0 to (Label.FOCUSED to "lecture between PDF and docs"))
            }
        }
        val out = HybridClassifier(RuleClassifier(), judge).classify(sessions, ctx)
        assertEquals(1, asked)
        assertEquals(Label.FOCUSED, out[1].label); assertEquals("muse-glimmer", out[1].source)
        assertEquals("rules", out[2].source)

        val failing = object : AmbiguityJudge {
            override val id = "muse-glimmer"
            override suspend fun judge(ambiguous: List<ClassifiedSession>, all: List<ClassifiedSession>): Map<Int, Pair<Label, String>> =
                throw java.io.IOException("connection refused")
        }
        val h = HybridClassifier(RuleClassifier(), failing)
        val out2 = h.classify(sessions, ctx)
        assertEquals(Label.DISTRACTED, out2[1].label)
        assertEquals("rules (glimmer unavailable)", out2[1].source)
        assertNotNull(h.lastError)
    }

    @Test fun summaryHasNoAppIdentifiersAndMatchesContract() {
        val classified = RuleClassifier().classifySync(listOf(
            s("com.google.android.apps.classroom", "Classroom", 9, 0, 40),
            s("com.instagram.android", "Instagram", 18, 30, 15),
            s("com.supercell.clashroyale", "Clash Royale", 23, 30, 20),
        ), ctx)
        val st = SummaryBuilder.stats(classified, zone)
        assertEquals(40, st.focusedMin); assertEquals(35, st.distractedMin); assertEquals(15, st.distractedInBlocksMin)
        assertEquals(20, st.lateNightMin); assertEquals(15, st.distractedByHour[18])
        val json = SummaryBuilder.summaryJson(st, day, blocks, listOf(Exam("DBMS", day.plusDays(9)), Exam("Old", day.minusDays(1))),
            "rules", 1, "Plans DBMS; blocker com.instagram.android").toString()
        assertFalse(json.contains("instagram", ignoreCase = true))
        assertFalse(json.contains("Clash"))
        assertFalse(json.contains("Classroom"))
        val o = JSONObject(json)
        assertEquals("Tue", o.getString("weekday"))
        assertEquals(24, o.getJSONArray("distracted_by_hour").length())
        assertEquals(1, o.getJSONArray("exams").length())
        assertEquals(9, o.getJSONArray("exams").getJSONObject(0).getInt("days_until"))
        val allowed = setOf("date_label", "weekday", "focused_min", "distracted_min", "neutral_min", "minutes_by_category",
            "distracted_min_in_study_blocks", "longest_focus_streak_min", "app_switches", "late_night_min",
            "distracted_by_hour", "nudges_sent", "classifier", "study_blocks", "exams", "checkin_note")
        assertTrue(o.keys().asSequence().all { it in allowed })
    }

    @Test fun nudgeFiresOnlyInBlockAboveThresholdAndAfterCooldown() {
        val classified = RuleClassifier().classifySync(listOf(s("com.instagram.android", "Instagram", 18, 10, 15)), ctx)
        val now = at(18, 30)
        val d = NudgePolicy.decide(now, classified, blocks, 10, 0L, emptyList(), zone)
        assertNotNull(d); assertEquals(15, d!!.distractedMin); assertEquals("DSA", d.block.subject)
        assertNull(NudgePolicy.decide(now, classified, blocks, 20, 0L, emptyList(), zone))
        assertNull(NudgePolicy.decide(now, classified, blocks, 10, now - 60_000, emptyList(), zone))
        assertNull(NudgePolicy.decide(at(21, 0), classified, blocks, 10, 0L, emptyList(), zone))
    }

    @Test fun netGuardOnlyAllowsOwnNetwork() {
        assertTrue(NetGuard.isPrivateHost("192.168.1.10"))
        assertTrue(NetGuard.isPrivateHost("10.0.2.2"))
        assertTrue(NetGuard.isPrivateHost("172.20.0.5"))
        assertTrue(NetGuard.isPrivateHost("my-laptop.local"))
        assertTrue(NetGuard.isPrivateHost("localhost"))
        assertFalse(NetGuard.isPrivateHost("api.together.xyz"))
        assertFalse(NetGuard.isPrivateHost("8.8.8.8"))
        assertFalse(NetGuard.isPrivateHost("172.32.0.1"))
        assertEquals("192.168.1.10", NetGuard.hostOf("http://192.168.1.10:8080/v1"))
    }

    @Test fun glimmerProtocolRoundTrip() {
        val all = RuleClassifier().classifySync(listOf(
            s("com.adobe.reader", "Adobe Acrobat", 18, 0, 25),
            s("com.google.android.youtube", "YouTube", 18, 26, 18),
        ), ctx)
        val body = GlimmerProtocol.requestBody("muse-glimmer", listOf(all[1]), all, zone)
        assertEquals("muse-glimmer", body.getString("model"))
        val user = body.getJSONArray("messages").getJSONObject(1).getString("content")
        assertTrue(user.contains("\"prev_app\":\"Adobe Acrobat\""))
        assertFalse(user.contains("com.google"))
        val resp = """{"choices":[{"message":{"role":"assistant","content":"Here: {\"labels\":[{\"id\":0,\"label\":\"focused\",\"reason\":\"lecture\"},{\"id\":7,\"label\":\"focused\",\"reason\":\"x\"}]}"}}]}"""
        val parsed = GlimmerProtocol.parse(resp, 1)
        assertEquals(1, parsed.size); assertEquals(Label.FOCUSED, parsed[0]!!.first)
    }

    @Test fun planJsonParsesRelayResponse() {
        val p = PlanJson.parsePlan("""{"headline":"h","focus_score":61,"blocks":[{"start":"18:00","end":"18:45","task":"DBMS","why":"exam"}],
            "nudges":["n1"],"exam_strategy":"e","tomorrow_focus_goal_min":160,"source":"muse-spark-1.3"}""")
        assertEquals(61, p.focusScore); assertEquals("DBMS", p.blocks[0].task); assertEquals("muse-spark-1.3", p.source)
        assertEquals(p, PlanJson.parsePlan(PlanJson.planToJson(p)))
    }

    /** Emits a realistic summary; backend/tests/fixtures/android_summary.json is a copy, validated by pytest. */
    @Test fun emitsContractFixture() {
        val sessions = listOf(
            s("com.google.android.apps.classroom", "Classroom", 9, 30, 38),
            s("com.google.android.youtube", "YouTube", 13, 15, 22),
            s("com.adobe.reader", "Adobe Acrobat", 18, 0, 25),
            s("com.google.android.youtube", "YouTube", 18, 26, 18),
            s("com.instagram.android", "Instagram", 19, 16, 17),
            s("com.supercell.clashroyale", "Clash Royale", 23, 20, 16),
        )
        val st = SummaryBuilder.stats(RuleClassifier().classifySync(sessions, ctx), zone)
        val json = SummaryBuilder.summaryJson(st, day, blocks, listOf(Exam("DBMS", day.plusDays(9))), "rules", 2,
            "Plans 90 min on DBMS. Blockers: keeps checking Instagram").toString()
        println("CONTRACT_FIXTURE=" + json)
        assertFalse(json.contains("com."))
    }
}
