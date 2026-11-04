package dev.chetan.focusguard.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chetan.focusguard.core.ClassifiedSession
import dev.chetan.focusguard.core.Label
import dev.chetan.focusguard.core.Timetable
import dev.chetan.focusguard.data.DemoData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Focus = Color(0xFF2E7D32)
val Distract = Color(0xFFC62828)
val Neutral = Color(0xFF607D8B)

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FocusGuard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("An on-device focus coach for students. It senses how you actually use your phone during study blocks and plans tomorrow around it.")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How your data is handled", fontWeight = FontWeight.SemiBold)
                Text("• App sessions are read and classified on this phone.")
                Text("• Optional: ambiguous sessions go to Muse Glimmer on YOUR own laptop over local Wi-Fi.")
                Text("• Only an anonymized daily summary (minutes per category, no app names) goes to Muse Spark for your plan. You can preview it first.")
            }
        }
        Text("Step 1: allow Usage Access", fontWeight = FontWeight.SemiBold)
        Text(if (vm.hasUsageAccess) "✓ Usage Access granted" else "Opens Android settings. Find FocusGuard and turn on \"Permit usage access\".")
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open Usage Access settings") }
        OutlinedButton(onClick = { vm.refreshAccess() }) { Text("I've granted it, re-check") }
        Text("No real usage on this device (emulator)?", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            vm.settings.demoMode = true
            vm.settings.timetableText = DemoData.TIMETABLE
            vm.settings.examsText = DemoData.exams()
            vm.settings.onboarded = true
            onDone()
        }) { Text("Use labelled demo data instead") }
        Spacer(Modifier.height(8.dp))
        Button(enabled = vm.hasUsageAccess, onClick = { vm.settings.onboarded = true; onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
fun TodayScreen(vm: AppViewModel) {
    val s = vm.stats
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (vm.settings.demoMode) AssistChip(onClick = {}, label = { Text("DEMO DATA") })
            }
            Text(vm.classifierStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (!vm.hasUsageAccess && !vm.settings.demoMode) item {
            Card { Text("Usage Access is off. Enable it in Settings > Usage access, or turn on demo data.", Modifier.padding(16.dp)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Focused", s?.focusedMin, Focus, Modifier.weight(1f))
                StatCard("Distracted", s?.distractedMin, Distract, Modifier.weight(1f))
                StatCard("Neutral", s?.neutralMin, Neutral, Modifier.weight(1f))
            }
        }
        if (s != null) item {
            val total = (s.focusedMin + s.distractedMin).coerceAtLeast(1)
            Column {
                Row(Modifier.fillMaxWidth().height(10.dp)) {
                    if (s.focusedMin > 0) Box(Modifier.weight(s.focusedMin.toFloat()).fillMaxSize().background(Focus))
                    if (s.distractedMin > 0) Box(Modifier.weight(s.distractedMin.toFloat()).fillMaxSize().background(Distract))
                }
                Spacer(Modifier.height(6.dp))
                Text("Focus ratio ${100 * s.focusedMin / total}% · ${s.distractedInBlocksMin} min distracted inside study blocks · longest streak ${s.longestFocusStreakMin} min · ${s.appSwitches} app switches",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.loadToday() }) { Text(if (vm.loading) "Classifying…" else "Refresh") }
                OutlinedButton(onClick = { vm.checkNudgeNow() }) { Text("Check nudge now") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voice study check-in (Muse Voice Transcribe)", fontWeight = FontWeight.SemiBold)
                    Text("Say what you're about to study and what's getting in the way. The intent is folded into tonight's plan.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!vm.recording) Button(onClick = { vm.startCheckin() }) { Text("🎙 Record (max 20s)") }
                        else Button(onClick = { vm.stopCheckin() }) { Text("■ Stop & send") }
                    }
                    vm.checkin?.let {
                        Text("“${it.transcript}”", style = MaterialTheme.typography.bodyMedium)
                        Text("→ ${it.subject}, ${it.plannedMinutes} min" + if (it.blockers.isNotEmpty()) " · blockers: ${it.blockers.joinToString()}" else "",
                            fontWeight = FontWeight.SemiBold)
                        Text("source: ${it.source}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        vm.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { Text("Sessions (${vm.sessions.size}) — never leave this phone", fontWeight = FontWeight.SemiBold) }
        items(vm.sessions) { SessionRow(it, fmt) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StatCard(title: String, minutes: Int?, color: Color, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color)
            Text(minutes?.let { "$it" } ?: "–", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("min", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SessionRow(c: ClassifiedSession, fmt: SimpleDateFormat) {
    val color = when (c.label) { Label.FOCUSED -> Focus; Label.DISTRACTED -> Distract; Label.NEUTRAL -> Neutral }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(40.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${fmt.format(Date(c.session.startMs))}  ${c.session.appLabel}  ·  ${"%.0f".format(c.session.durationMin.coerceAtLeast(1.0))} min",
                fontWeight = FontWeight.Medium)
            Text("${c.label.name.lowercase()} · ${c.category.wire}${if (c.inStudyBlock) " · in study block" else ""} · ${c.reason}",
                style = MaterialTheme.typography.bodySmall)
            Text(c.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun PlanScreen(vm: AppViewModel) {
    var showPayload by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tomorrow's plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Built by Muse Spark (Meta Model API) from an anonymized summary. Your app list never leaves the phone.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.generatePlan() }, enabled = !vm.planLoading) { Text(if (vm.planLoading) "Muse Spark is planning…" else "Generate plan") }
            OutlinedButton(onClick = { vm.previewSummary(); showPayload = !showPayload }) { Text(if (showPayload) "Hide payload" else "What leaves my phone?") }
        }
        if (vm.planLoading) CircularProgressIndicator()
        vm.relayStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        vm.planError?.let { Text(it, color = Distract, style = MaterialTheme.typography.bodySmall) }
        if (showPayload) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))) {
            Text(vm.summaryPreview ?: "Load Today first.", Modifier.padding(12.dp), color = Color(0xFFB2FF59),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        vm.plan?.let { p ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.focusScore}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Focus)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("focus score", style = MaterialTheme.typography.labelMedium)
                            Text("goal tomorrow: ${p.tomorrowGoalMin} focused min", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(p.headline)
                    Text("source: ${p.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text("Blocks", fontWeight = FontWeight.SemiBold)
            p.blocks.forEach { b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${b.start}–${b.end}  ${b.task}", fontWeight = FontWeight.Medium)
                        Text(b.why, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Exam strategy", fontWeight = FontWeight.SemiBold)
            Text(p.examStrategy)
            Text("Nudges FocusGuard will use", fontWeight = FontWeight.SemiBold)
            p.nudges.forEach { Text("• $it") }
        } ?: Text("No plan yet. Load Today, then tap Generate plan.")
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st = vm.settings
    var relay by remember { mutableStateOf(st.relayUrl) }
    var token by remember { mutableStateOf(st.relayToken) }
    var gOn by remember { mutableStateOf(st.glimmerEnabled) }
    var gUrl by remember { mutableStateOf(st.glimmerUrl) }
    var gModel by remember { mutableStateOf(st.glimmerModel) }
    var gKey by remember { mutableStateOf(st.glimmerKey) }
    var gPublic by remember { mutableStateOf(st.allowPublicGlimmerHost) }
    var tt by remember { mutableStateOf(st.timetableText) }
    var ex by remember { mutableStateOf(st.examsText) }
    var thr by remember { mutableStateOf(st.nudgeThresholdMin.toFloat()) }
    var demo by remember { mutableStateOf(st.demoMode) }
    val ttParse = Timetable.parseBlocks(tt)
    val exParse = Timetable.parseExams(ex)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("Timetable (study blocks)", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(tt, { tt = it; st.timetableText = it }, Modifier.fillMaxWidth(), minLines = 3,
            supportingText = { Text(if (ttParse.errors.isEmpty()) "${ttParse.items.size} weekly blocks · format: Mon-Fri 18:00-20:00 DSA" else ttParse.errors.first()) })
        Text("Exams", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(ex, { ex = it; st.examsText = it }, Modifier.fillMaxWidth(), minLines = 2,
            supportingText = { Text(if (exParse.errors.isEmpty()) "${exParse.items.size} exams · format: 2026-11-20 DBMS" else exParse.errors.first()) })

        Text("Nudge after ${thr.toInt()} distracted minutes in a study block", fontWeight = FontWeight.SemiBold)
        Slider(thr, { thr = it; st.nudgeThresholdMin = it.toInt() }, valueRange = 3f..30f, steps = 26)

        HorizontalDivider()
        Text("Muse Spark relay", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(relay, { relay = it; st.relayUrl = it }, Modifier.fillMaxWidth(), label = { Text("Relay URL") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("Emulator → host: http://10.0.2.2:8000 · phone: http://<laptop-ip>:8000. The Meta API key lives on the relay, never on the phone.") })
        OutlinedTextField(token, { token = it; st.relayToken = it }, Modifier.fillMaxWidth(), label = { Text("Relay token (optional)") })

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Muse Glimmer judge (own hardware)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(gOn, { gOn = it; st.glimmerEnabled = it })
        }
        Text("Ambiguous sessions (YouTube, browser, long chats in study blocks) are sent to Meta's open-weight Muse Glimmer running on your laptop, e.g. llama serve … -a muse-glimmer --host 0.0.0.0. Rules are used when it's off or unreachable.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(gUrl, { gUrl = it; st.glimmerUrl = it }, Modifier.fillMaxWidth(), label = { Text("Glimmer base URL (OpenAI-compatible)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(gModel, { gModel = it; st.glimmerModel = it }, Modifier.fillMaxWidth(), label = { Text("Model name") })
        OutlinedTextField(gKey, { gKey = it; st.glimmerKey = it }, Modifier.fillMaxWidth(), label = { Text("Local server API key (optional)") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Allow non-local host (not recommended)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(gPublic, { gPublic = it; st.allowPublicGlimmerHost = it })
        }
        OutlinedButton(onClick = { vm.testGlimmer() }) { Text("Test Glimmer connection") }
        vm.glimmerTest?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Demo data (labelled synthetic day)", modifier = Modifier.weight(1f))
            Switch(demo, { demo = it; st.demoMode = it })
        }
        Spacer(Modifier.height(24.dp))
    }
}
