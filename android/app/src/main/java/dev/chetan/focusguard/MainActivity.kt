package dev.chetan.focusguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import dev.chetan.focusguard.ui.AppViewModel
import dev.chetan.focusguard.ui.OnboardingScreen
import dev.chetan.focusguard.ui.PlanScreen
import dev.chetan.focusguard.ui.SettingsScreen
import dev.chetan.focusguard.ui.TodayScreen
import dev.chetan.focusguard.work.Nudges

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Nudges.createChannel(this)
        Nudges.schedule(this)
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) askPerms.launch(wanted.toTypedArray())

        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF81C784))
            else lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFF455A64))
            MaterialTheme(colorScheme = scheme) {
                Surface {
                    var onboarded by remember { mutableStateOf(vm.settings.onboarded) }
                    if (!onboarded) OnboardingScreen(vm) { onboarded = true; vm.loadToday() }
                    else MainTabs(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
        if (vm.settings.onboarded) vm.loadToday()
    }
}

@androidx.compose.runtime.Composable
private fun MainTabs(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (vm.stats == null) vm.loadToday() }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0; vm.loadToday() }, { Icon(Icons.Filled.Home, null) }, label = { Text("Today") })
            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Plan") })
            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(vm)
                1 -> PlanScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
