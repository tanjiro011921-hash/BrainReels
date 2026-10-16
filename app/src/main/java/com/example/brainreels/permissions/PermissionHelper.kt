package com.example.brainreels.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.brainreels.data.SourceApp
import com.example.brainreels.tracking.ReelAccessibilityService
import java.time.ZoneId
import com.example.brainreels.utils.DateUtils

data class PermissionState(
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = true
)

/** Usage time (NOT Reel counts) for a supported app. */
data class UsageSummary(val minutesByApp: Map<SourceApp, Long>)

object PermissionHelper {

    fun state(context: Context) = PermissionState(
        overlay = Settings.canDrawOverlays(context),
        accessibility = isAccessibilityServiceEnabled(context),
        usageAccess = hasUsageAccess(context),
        notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val mine = ComponentName(context, ReelAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == mine) return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time today per supported app. Only meaningful when Usage Access is granted. */
    fun usageToday(context: Context, zone: ZoneId = ZoneId.systemDefault()): UsageSummary? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val start = DateUtils.dayStartMillis(DateUtils.localDate(now, zone), zone)
        val stats = usm.queryAndAggregateUsageStats(start, now)
        val result = SourceApp.entries.associateWith { (stats[it.packageName]?.totalTimeInForeground ?: 0L) / 60_000L }
        return UsageSummary(result)
    }

    fun overlaySettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun usageAccessSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun batterySettingsIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
