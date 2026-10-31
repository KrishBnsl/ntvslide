package com.ntvslide

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ntvslide.backend.ShizukuBackend
import com.ntvslide.ui.NothingTheme
import com.ntvslide.ui.PanelView

/**
 * Shows the ntvslide panel beside the system volume panel whenever a volume key is pressed.
 * The key event is never consumed, so the system panel and master volume behave exactly as before.
 */
class VolumeKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var controller: AppVolumeController
    private lateinit var windowManager: WindowManager
    private var panel: PanelView? = null

    private val hide = Runnable { removePanel() }

    // Re-applies saved levels so an app that starts playing later (or got reset by the system) is corrected.
    private val tick = object : Runnable {
        override fun run() {
            ShizukuBackend.connect(this@VolumeKeyService)
            if (getSystemService(PowerManager::class.java).isInteractive) controller.apply()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onServiceConnected() {
        controller = AppVolumeController(this, AppVolumeStore(this), ShizukuBackend)
        windowManager = getSystemService(WindowManager::class.java)
        handler.post(tick)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) show()
        return false // let the system handle the key too
    }

    private fun show() {
        val entries = controller.entries()
        val view = panel ?: PanelView(this).also { v ->
            v.onInteraction = { scheduleHide() }
            // The system panel is ~72dp wide, flush with the right edge; sit just left of it.
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = NothingTheme.dp(this@VolumeKeyService, 88f).toInt()
            }
            windowManager.addView(v, lp)
            panel = v
        }
        view.bind(entries, controller)
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, 4_000)
    }

    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = removePanel()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removePanel()
        super.onDestroy()
    }
}
