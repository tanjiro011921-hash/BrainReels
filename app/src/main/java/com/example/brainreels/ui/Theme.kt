package com.example.brainreels.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val BrainPurple = Color(0xFFB388FF)
val BrainCyan = Color(0xFF22D3EE)
val BrainOk = Color(0xFF4ADE80)
val BrainWarn = Color(0xFFFBBF24)

private val DarkColors = darkColorScheme(
    primary = BrainPurple, secondary = BrainCyan,
    background = Color(0xFF0B0B10), surface = Color(0xFF15151C), surfaceVariant = Color(0xFF1E1E28),
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFB8B8C6),
    onPrimary = Color(0xFF14002E), error = Color(0xFFFF6B6B)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF6F3FD9), secondary = Color(0xFF0891B2),
    background = Color(0xFFF7F6FB), surface = Color.White, surfaceVariant = Color(0xFFECEAF4),
    onBackground = Color(0xFF15151C), onSurface = Color(0xFF15151C), onSurfaceVariant = Color(0xFF55556A)
)

@Composable
fun BrainReelsTheme(light: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (light) LightColors else DarkColors, content = content)
}

@Composable
fun BrCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun StatusRow(label: String, good: Boolean?, detail: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val color = when (good) { true -> BrainOk; false -> BrainWarn; null -> MaterialTheme.colorScheme.onSurfaceVariant }
        Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.size(10.dp)) {}
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun InfoScreen(title: String, body: List<String>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            body.forEach { BrCard { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
