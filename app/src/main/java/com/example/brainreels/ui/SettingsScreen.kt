package com.example.brainreels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.brainreels.data.*

data class SettingsActions(
    val set: (androidx.datastore.preferences.core.Preferences.Key<Boolean>, Boolean) -> Unit,
    val setMinView: (Int) -> Unit,
    val setPolicy: (ConfidencePolicy) -> Unit,
    val setPosition: (WidgetPosition) -> Unit,
    val resetToday: () -> Unit,
    val correct: () -> Unit,
    val export: () -> Unit,
    val deleteAll: () -> Unit,
    val openPermissions: () -> Unit,
    val openPrivacy: () -> Unit,
    val openLimitations: () -> Unit
)

@Composable
fun SettingsScreen(s: AppSettings, a: SettingsActions, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var seconds by remember(s.minViewMs) { mutableFloatStateOf(s.minViewMs.toFloat()) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrCard {
                SectionTitle("Detection")
                SwitchRow("Automatic detection", s.detectionEnabled) { a.set(SettingsKeys.DETECTION, it) }
                SwitchRow("Track Instagram", s.instagramEnabled) { a.set(SettingsKeys.INSTAGRAM, it) }
                SwitchRow("Track Facebook", s.facebookEnabled) { a.set(SettingsKeys.FACEBOOK, it) }
                SwitchRow("Track Facebook Lite", s.facebookLiteEnabled) { a.set(SettingsKeys.FACEBOOK_LITE, it) }
                Text("Minimum viewing time: ${"%.1f".format(seconds / 1000f)} s")
                Slider(
                    value = seconds, onValueChange = { seconds = it }, valueRange = 1000f..6000f, steps = 9,
                    onValueChangeFinished = { a.setMinView(seconds.toInt()) }
                )
                Text("Confidence policy")
                ConfidencePolicy.entries.forEach { p ->
                    RadioRow(p.label, s.policy == p) { a.setPolicy(p) }
                }
            }
            BrCard {
                SectionTitle("Floating widget")
                SwitchRow("Show floating counter", s.widgetEnabled) { a.set(SettingsKeys.WIDGET, it) }
                WidgetPosition.entries.filter { it != WidgetPosition.CUSTOM }.forEach { p ->
                    RadioRow(p.label, s.widgetPosition == p) { a.setPosition(p) }
                }
                if (s.widgetPosition == WidgetPosition.CUSTOM) RadioRow(WidgetPosition.CUSTOM.label, true) {}
                Text("Tip: drag the pill to place it anywhere; tap it to open BrainReels.", style = MaterialTheme.typography.bodySmall)
                SwitchRow("Animations", s.animations) { a.set(SettingsKeys.ANIMATIONS, it) }
                SwitchRow("Light theme", s.lightTheme) { a.set(SettingsKeys.LIGHT, it) }
            }
            BrCard {
                SectionTitle("Data")
                OutlinedButton(onClick = a.correct, modifier = Modifier.fillMaxWidth()) { Text("Correct today's count") }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset today's count") }
                OutlinedButton(onClick = a.export, modifier = Modifier.fillMaxWidth()) { Text("Export history to CSV") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all tracking data") }
            }
            BrCard {
                SectionTitle("About")
                TextButton(onClick = a.openPermissions) { Text("Permission instructions") }
                TextButton(onClick = a.openPrivacy) { Text("Privacy disclosure") }
                TextButton(onClick = a.openLimitations) { Text("Known detection limitations") }
            }
        }
    }

    if (confirmReset) ConfirmDialog(
        "Reset today's count?",
        "Today's total goes back to zero. Nothing is deleted: negative manual adjustments are added to the audit trail.",
        "Reset", { confirmReset = false }) { confirmReset = false; a.resetToday() }
    if (confirmDelete) ConfirmDialog(
        "Delete all tracking data?",
        "This permanently erases every stored event and statistic on this device. It cannot be undone.",
        "Delete everything", { confirmDelete = false }) { confirmDelete = false; a.deleteAll() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ManualCorrectionDialog(onDismiss: () -> Unit, onApply: (SourceApp, Int, String) -> Unit) {
    var source by remember { mutableStateOf(SourceApp.INSTAGRAM) }
    var amount by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    val delta = amount.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual correction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Adds a clearly marked manual entry. Use a negative number to subtract. Automatic detections are never edited.", style = MaterialTheme.typography.bodySmall)
                RadioRow("Instagram", source == SourceApp.INSTAGRAM) { source = SourceApp.INSTAGRAM }
                RadioRow("Facebook", source == SourceApp.FACEBOOK) { source = SourceApp.FACEBOOK }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(5) }, label = { Text("Change (e.g. 2 or -1)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = delta == null || delta == 0
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = delta != null && delta != 0, onClick = { onApply(source, delta!!, note) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
