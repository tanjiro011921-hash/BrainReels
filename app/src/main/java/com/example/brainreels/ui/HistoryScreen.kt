package com.example.brainreels.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen(s: DashboardState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader("History", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.history.isEmpty()) Text("No history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.history.forEach { d ->
                BrCard {
                    Text("${d.date}", style = MaterialTheme.typography.titleSmall)
                    Text("Total ${d.total} · Instagram ${d.instagram} · Facebook ${d.facebook}")
                    Text(
                        "Automatic ${d.automatic} · Manual ${d.manual}" + if (d.uncertain > 0) " · Uncertain ${d.uncertain}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("Recent diagnostics", style = MaterialTheme.typography.titleSmall)
            if (s.diagnostics.isEmpty()) Text("No detector decisions in this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.diagnostics.take(30).forEach {
                Text("${it.outcome}: ${it.message}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
