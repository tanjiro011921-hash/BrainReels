package com.example.brainreels.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainreels.data.EventStatus
import com.example.brainreels.data.ReelEvent
import com.example.brainreels.data.SourceApp
import com.example.brainreels.permissions.UsageSummary
import com.example.brainreels.utils.DayTotals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class DashboardActions(
    val openSettings: () -> Unit,
    val openHistory: () -> Unit,
    val openPrivacy: () -> Unit,
    val openLimitations: () -> Unit,
    val openPermissions: () -> Unit,
    val correct: () -> Unit
)

enum class Level { OK, WARN, OFF }
data class DetectionStatus(val title: String, val detail: String, val level: Level)

/** Plain-language explanation of whether automatic detection is working. No fabricated numbers. */
fun detectionStatus(s: DashboardState): DetectionStatus {
    val set = s.settings
    return when {
        !set.detectionEnabled -> DetectionStatus("Detection is off", "Turn it on in Settings to count Reels automatically.", Level.OFF)
        !s.permissions.accessibility -> DetectionStatus("Needs Accessibility access", "Without it BrainReels cannot see Reel viewers. Counts stay at manual corrections only.", Level.WARN)
        !(set.instagramEnabled || set.facebookEnabled || set.facebookLiteEnabled) ->
            DetectionStatus("No apps selected", "Enable Instagram or Facebook tracking in Settings.", Level.WARN)
        s.today.uncertain > 0 && s.today.automatic == 0 -> DetectionStatus(
            "Running, but can't reliably identify Reels",
            "${s.today.uncertain} observation(s) today were too uncertain to count. This build is experimental; see Known limitations.", Level.WARN
        )
        else -> DetectionStatus(
            "Automatic detection is on (experimental)",
            if (s.today.uncertain > 0) "${s.today.uncertain} observation(s) today were uncertain and not counted."
            else "Counts are best-effort and not guaranteed accurate.", Level.OK
        )
    }
}

@Composable
fun DashboardScreen(s: DashboardState, usage: UsageSummary?, a: DashboardActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧠 BrainReels", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = a.openSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        BrCard {
            Text("Reels today", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = s.today.total,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "todayCount"
            ) { n -> Text("$n", fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
            if (s.today.manual != 0) Text(
                "Includes manual adjustments of ${signed(s.today.manual)} (automatic: ${s.today.automatic})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrCard(Modifier.weight(1f)) { Text("Instagram", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${s.today.instagram}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
            BrCard(Modifier.weight(1f)) { Text("Facebook", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${s.today.facebook}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
        }

        val st = detectionStatus(s)
        BrCard {
            SectionTitle("Automatic detection")
            StatusRow(st.title, when (st.level) { Level.OK -> true; Level.WARN -> false; Level.OFF -> null }, st.detail)
            s.diagnostics.firstOrNull { it.outcome != com.example.brainreels.tracking.Outcome.INFO }?.let {
                Text("Last decision: ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = a.openLimitations) { Text("Known limitations") }
        }

        BrCard {
            SectionTitle("Permissions & widget")
            StatusRow("Accessibility access", s.permissions.accessibility,
                if (s.permissions.accessibility) (if (s.serviceConnected) "Granted, service connected" else "Granted") else "Not granted",
                if (!s.permissions.accessibility) "Set up" else null, a.openPermissions)
            StatusRow("Overlay access", s.permissions.overlay,
                if (s.permissions.overlay) "Granted" else "Not granted: the floating counter cannot be shown",
                if (!s.permissions.overlay) "Set up" else null, a.openPermissions)
            StatusRow("Floating widget", if (s.settings.widgetEnabled && s.permissions.overlay) true else null,
                when {
                    !s.settings.widgetEnabled -> "Hidden (change in Settings)"
                    !s.permissions.overlay -> "Waiting for overlay access"
                    else -> "Visible"
                })
            TextButton(onClick = a.openPermissions) { Text("Permission instructions") }
        }

        BrCard {
            SectionTitle("Last 7 days")
            WeekBars(s.week)
            Text("This month so far: ${s.monthTotal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("7-day total: ${s.week.sumOf { it.total }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        BrCard {
            SectionTitle("Recent events")
            if (s.recent.isEmpty()) Text("Nothing recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.recent.take(8).forEach { EventRow(it, s.zone) }
            TextButton(onClick = a.openHistory) { Text("Full history") }
        }

        if (usage != null) BrCard {
            SectionTitle("App usage today (not Reel counts)")
            SourceApp.entries.forEach { app ->
                Text("${app.label}: ${usage.minutesByApp[app] ?: 0} min in foreground", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = a.correct, modifier = Modifier.weight(1f)) { Text("Manual correction") }
            OutlinedButton(onClick = a.openPrivacy, modifier = Modifier.weight(1f)) { Text("Privacy") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

@Composable
private fun WeekBars(days: List<DayTotals>) {
    val peak = max(1, days.maxOfOrNull { it.total } ?: 1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("${d.total}", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth().height(max(4, 70 * d.total / peak).dp)
                        .clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary)
                )
                Text(d.date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

@Composable
fun EventRow(e: ReelEvent, zone: ZoneId) {
    val app = SourceApp.fromName(e.sourceApp)?.label ?: e.sourceApp
    val kind = when (e.eventStatus) {
        EventStatus.COUNTED.name -> "Detected (${e.confidence.lowercase()})"
        EventStatus.UNCERTAIN.name -> "Uncertain, not counted (${e.confidence.lowercase()})"
        else -> "Manual ${if (e.delta > 0) "+" else ""}${e.delta}${e.note?.let { ": $it" } ?: ""}"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$app · ${timeFmt.format(Instant.ofEpochMilli(e.timestamp).atZone(zone))}", fontWeight = FontWeight.Medium)
        Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
