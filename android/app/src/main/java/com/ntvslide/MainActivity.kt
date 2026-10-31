package com.ntvslide

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ntvslide.backend.ShizukuBackend
import com.ntvslide.ui.NothingTheme
import rikka.shizuku.Shizuku

/** Setup checklist: accessibility service on, Shizuku running, permission granted. */
class MainActivity : Activity() {

    private lateinit var accessibilityRow: TextView
    private lateinit var shizukuRow: TextView
    private lateinit var shizukuButton: Button

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = { v: Float -> NothingTheme.dp(this, v).toInt() }

        val name = TextView(this).apply {
            text = "NTVSLIDE"
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            letterSpacing = .3f
            setTextColor(NothingTheme.DOT_ON)
        }
        val intro = text("Per-app volume sliders, shown beside the system volume panel when you press a volume key.", muted = true)
        accessibilityRow = text("")
        shizukuRow = text("")

        val a11y = button("Enable accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        shizukuButton = button("Grant Shizuku access") { ShizukuBackend.requestPermission(1) }
        val note = text("Android has no public API for another app's volume. ntvslide uses Shizuku (wireless debugging, no root) to run a small helper that scales each app's audio player.", muted = true)

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(d(28f), d(64f), d(28f), d(28f))
            addView(name)
            addView(intro, lp(top = 16))
            addView(accessibilityRow, lp(top = 40))
            addView(a11y, lp(top = 12))
            addView(shizukuRow, lp(top = 28))
            addView(shizukuButton, lp(top = 12))
            addView(note, lp(top = 40))
        })

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    private fun refresh() {
        val a11yOn = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/${VolumeKeyService::class.java.name}") == true
        accessibilityRow.text = status(a11yOn, "Accessibility service")

        val running = ShizukuBackend.shizukuRunning()
        val granted = ShizukuBackend.permissionGranted()
        shizukuRow.text = status(granted, if (running) "Shizuku access" else "Shizuku (not running)")
        shizukuButton.visibility = if (granted) View.GONE else View.VISIBLE
        shizukuButton.isEnabled = running
        if (granted) ShizukuBackend.connect(this)
    }

    private fun status(ok: Boolean, label: String) = (if (ok) "● " else "○ ") + label.uppercase()

    private fun text(s: String, muted: Boolean = false) = TextView(this).apply {
        text = s
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (muted) 12f else 13f)
        setTextColor(if (muted) NothingTheme.MUTED_TEXT else NothingTheme.DOT_ON)
        setLineSpacing(0f, 1.4f)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label.uppercase()
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(NothingTheme.DOT_ON)
        stateListAnimator = null
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = NothingTheme.dp(this@MainActivity, 999f)
            setStroke(1, NothingTheme.LINE)
            setColor(0)
        }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = NothingTheme.dp(this@MainActivity, top.toFloat()).toInt()
    }
}
