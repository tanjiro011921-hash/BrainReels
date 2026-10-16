package com.example.brainreels

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.brainreels.data.SettingsKeys
import com.example.brainreels.permissions.PermissionHelper
import com.example.brainreels.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it) }
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh() // the user may have just changed permissions in Android Settings
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(PermissionHelper.appDetailsIntent(this)) }
        }
    }

    @Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        val usage by vm.usage.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

        var screen by rememberSaveable { mutableStateOf("dashboard") }
        var showCorrection by remember { mutableStateOf(false) }

        BrainReelsTheme(light = state?.settings?.lightTheme ?: false) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val s = state
                if (s == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Surface
                }
                val current = if (!s.settings.onboardingDone && screen == "dashboard") "permissions" else screen
                BackHandler(enabled = current != "dashboard") { screen = "dashboard" }
                val back = { screen = "dashboard" }

                when (current) {
                    "dashboard" -> DashboardScreen(
                        s, usage,
                        DashboardActions(
                            openSettings = { screen = "settings" }, openHistory = { screen = "history" },
                            openPrivacy = { screen = "privacy" }, openLimitations = { screen = "limitations" },
                            openPermissions = { screen = "permissions" }, correct = { showCorrection = true }
                        )
                    )
                    "history" -> HistoryScreen(s, back)
                    "privacy" -> InfoScreen("Privacy", InfoTexts.privacy, back)
                    "limitations" -> InfoScreen("Known limitations", InfoTexts.limitations, back)
                    "permissions" -> PermissionScreen(
                        s.permissions,
                        PermissionActions(
                            openOverlay = { open(PermissionHelper.overlaySettingsIntent(this@MainActivity)) },
                            openAccessibility = { open(PermissionHelper.accessibilitySettingsIntent()) },
                            openUsage = { open(PermissionHelper.usageAccessSettingsIntent()) },
                            requestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            openBattery = { open(PermissionHelper.batterySettingsIntent()) },
                            done = { vm.set(SettingsKeys.ONBOARDING, true); screen = "dashboard" }
                        ),
                        onBack = if (s.settings.onboardingDone) back else null
                    )
                    "settings" -> SettingsScreen(
                        s.settings,
                        SettingsActions(
                            set = { k, v -> vm.set(k, v) },
                            setMinView = { vm.set(com.example.brainreels.data.SettingsKeys.MIN_VIEW_MS, it) },
                            setPolicy = { vm.set(SettingsKeys.POLICY, it.name) },
                            setPosition = { vm.set(SettingsKeys.POSITION, it.name) },
                            resetToday = { vm.resetToday() },
                            correct = { showCorrection = true },
                            export = { exportLauncher.launch("brainreels-history.csv") },
                            deleteAll = { vm.deleteAllData() },
                            openPermissions = { screen = "permissions" },
                            openPrivacy = { screen = "privacy" },
                            openLimitations = { screen = "limitations" }
                        ),
                        back
                    )
                }

                if (showCorrection) ManualCorrectionDialog(
                    onDismiss = { showCorrection = false },
                    onApply = { src, delta, note -> showCorrection = false; vm.manualAdjust(src, delta, note) }
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
            }
        }
    }
}
