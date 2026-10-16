package com.example.brainreels.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainreels.permissions.PermissionState

data class PermissionActions(
    val openOverlay: () -> Unit,
    val openAccessibility: () -> Unit,
    val openUsage: () -> Unit,
    val requestNotifications: () -> Unit,
    val openBattery: () -> Unit,
    val done: () -> Unit
)

/** Onboarding + permission help. Nothing is enabled silently: every step opens the official Android screen. */
@Composable
fun PermissionScreen(p: PermissionState, a: PermissionActions, onBack: (() -> Unit)?) {
    var showDisclosure by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) BackHeader("Permissions", onBack)
        else Text("Set up BrainReels", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "BrainReels only does what you explicitly allow in Android Settings. You can skip any step and change your mind later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BrCard {
                SectionTitle("1. Display over other apps")
                StatusRow("Floating 🧠 counter", p.overlay, if (p.overlay) "Granted" else "Needed to draw the counter above other apps",
                    if (!p.overlay) "Open settings" else null, a.openOverlay)
            }
            BrCard {
                SectionTitle("2. Accessibility service")
                StatusRow("Automatic Reel detection", p.accessibility, if (p.accessibility) "Enabled" else "Needed to notice when you view a Reel",
                    if (!p.accessibility) "Review & enable" else null) { showDisclosure = true }
            }
            BrCard {
                SectionTitle("3. Usage access (optional)")
                StatusRow("App usage time", if (p.usageAccess) true else null,
                    if (p.usageAccess) "Granted" else "Shows time spent per app. Never used to count Reels.",
                    if (!p.usageAccess) "Open settings" else null, a.openUsage)
            }
            if (Build.VERSION.SDK_INT >= 33) BrCard {
                SectionTitle("4. Notifications (optional)")
                StatusRow("Counter status notification", if (p.notifications) true else null,
                    if (p.notifications) "Allowed" else "Android shows a small notification while the counter runs.",
                    if (!p.notifications) "Allow" else null, a.requestNotifications)
            }
            BrCard {
                SectionTitle("Battery")
                Text("If the counter or detection stops after a while, allow unrestricted battery use for BrainReels. Android or your phone maker may still limit background apps; monitoring is best-effort.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = a.openBattery) { Text("Open battery settings") }
            }
            InfoTexts.permissions.forEach { t -> Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Button(onClick = a.done, modifier = Modifier.fillMaxWidth()) { Text(if (onBack == null) "Continue" else "Done") }
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Accessibility disclosure") },
        text = {
            Text(
                "BrainReels uses Android's Accessibility API only to notice when a Reel viewer is open in Instagram or Facebook, so it can keep a daily count on your phone.\n\n" +
                    "It reads limited screen structure from those apps only. It ignores password and text fields, takes no screenshots, and never reads messages. Nothing leaves your device.\n\n" +
                    "Detection is a heuristic and may be inaccurate."
            )
        },
        confirmButton = { TextButton(onClick = { showDisclosure = false; a.openAccessibility() }) { Text("Agree & open settings") } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Not now") } }
    )
}
