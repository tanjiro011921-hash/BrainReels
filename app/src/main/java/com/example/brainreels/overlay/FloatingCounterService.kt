package com.example.brainreels.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.brainreels.MainActivity
import com.example.brainreels.R
import com.example.brainreels.data.AppDatabase
import com.example.brainreels.data.AppSettings
import com.example.brainreels.data.ReelRepository
import com.example.brainreels.data.SettingsRepository
import com.example.brainreels.data.WidgetPosition
import com.example.brainreels.utils.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Dynamic-Island-style pill drawn with WindowManager + TYPE_APPLICATION_OVERLAY.
 * The window is WRAP_CONTENT and NOT_TOUCH_MODAL, so touches outside the pill pass through.
 * The count is driven by a Room Flow (no polling).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FloatingCounterService : LifecycleService() {

    companion object {
        const val ACTION_HIDE = "com.example.brainreels.action.HIDE_WIDGET"
        private const val CHANNEL_ID = "floating_counter"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "FloatingCounter"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepo: SettingsRepository
    private var pill: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var settings = AppSettings()
    private var count = -1

    override fun onCreate() {
        super.onCreate()
        if (!startInForeground()) { stopSelf(); return }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "BrainReels needs \"Display over other apps\" permission to show the counter.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsRepo = SettingsRepository(this)
        createPill()

        lifecycleScope.launch {
            settingsRepo.flow.collect { s ->
                settings = s
                if (!s.widgetEnabled) stopSelf() else applyPosition()
            }
        }
        val repo = ReelRepository(AppDatabase.get(this).reelDao())
        lifecycleScope.launch {
            DayClock.currentDay(this@FloatingCounterService)
                .flatMapLatest { key -> repo.observeAll().map { events -> com.example.brainreels.utils.Stats.forDay(events, key.date, key.zone).total } }
                .distinctUntilChanged()
                .collect { updateCount(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_HIDE) {
            lifecycleScope.launch {
                SettingsRepository(this@FloatingCounterService).setWidgetEnabled(false)
                stopSelf()
            }
        }
        return START_STICKY // after a kill, Android restarts us and we rebuild state from DataStore + Room
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pill?.post { applyPosition() }
    }

    override fun onDestroy() {
        pill?.let { runCatching { windowManager.removeView(it) } }
        pill = null
        super.onDestroy()
    }

    // --- foreground notification ---------------------------------------------------------

    private fun startInForeground(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.fgs_channel_name), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hide = PendingIntent.getService(
            this, 1, Intent(this, FloatingCounterService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setContentTitle(getString(R.string.fgs_title))
            .setContentIntent(open)
            .addAction(0, getString(R.string.fgs_hide), hide)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID, n)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not enter foreground", e)
            false
        }
    }

    // --- pill view -------------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createPill() {
        val view = TextView(this).apply {
            text = "🧠 0"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(0xFF050507.toInt())
                setStroke(dp(1), 0xFF7C4DFF.toInt())
            }
            setShadowLayer(dp(5).toFloat(), 0f, 0f, 0xFF22D3EE.toInt()) // subtle cyan glow
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = getString(R.string.widget_content_description, 0)
            setOnClickListener {
                startActivity(
                    Intent(this@FloatingCounterService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.setOnTouchListener(DragListener(view, lp))
        params = lp
        pill = view
        try {
            windowManager.addView(view, lp)
        } catch (e: Exception) {
            Log.w(TAG, "addView failed", e)
            stopSelf()
            return
        }
        view.post { applyPosition() }
    }

    private inner class DragListener(private val v: View, private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@FloatingCounterService).scaledTouchSlop
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var moved = false

        override fun onTouch(view: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        val (sw, sh) = screenSize()
                        lp.x = (startX + dx).toInt().coerceIn(0, (sw - v.width).coerceAtLeast(0))
                        lp.y = (startY + dy).toInt().coerceIn(0, (sh - v.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val (sw, sh) = screenSize()
                        lifecycleScope.launch {
                            settingsRepo.saveCustomPosition(lp.x / sw.toFloat(), lp.y / sh.toFloat())
                        }
                    } else v.performClick()
                }
            }
            return true
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    /** Height of the status bar / display cutout so the pill sits below it by default. */
    private fun topInset(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val insets = windowManager.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                if (insets.top > 0) return insets.top
            }
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun applyPosition() {
        val view = pill ?: return
        val lp = params ?: return
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = view.measuredWidth; val h = view.measuredHeight
        val (sw, sh) = screenSize()
        val margin = dp(12)
        val topY = topInset() + dp(4)
        when (settings.widgetPosition) {
            WidgetPosition.TOP_CENTER -> { lp.x = (sw - w) / 2; lp.y = topY }
            WidgetPosition.TOP_LEFT -> { lp.x = margin; lp.y = topY }
            WidgetPosition.TOP_RIGHT -> { lp.x = sw - w - margin; lp.y = topY }
            WidgetPosition.CUSTOM -> { lp.x = (settings.customXFrac * sw).toInt(); lp.y = (settings.customYFrac * sh).toInt() }
        }
        lp.x = lp.x.coerceIn(0, (sw - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (sh - h).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun updateCount(n: Int) {
        val view = pill ?: return
        val first = count < 0
        count = n
        val render = {
            view.text = "🧠 $n"
            view.contentDescription = getString(R.string.widget_content_description, n)
            view.post { applyPosition() }
        }
        if (first || !settings.animations) { render(); return }
        view.animate().scaleX(1.18f).scaleY(1.18f).alpha(0.6f).setDuration(110).withEndAction {
            render()
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
        }.start()
    }
}
