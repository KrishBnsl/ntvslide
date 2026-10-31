package com.ntvslide

import android.content.Context

/** Per-app level (0–100) and mute flag, keyed by package name. Default is 100 = untouched. */
class AppVolumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("ntvslide", Context.MODE_PRIVATE)

    fun level(pkg: String): Int = prefs.getInt("vol.$pkg", 100)
    fun muted(pkg: String): Boolean = prefs.getBoolean("mute.$pkg", false)

    fun setLevel(pkg: String, level: Int) {
        prefs.edit().putInt("vol.$pkg", level.coerceIn(0, 100)).putBoolean("mute.$pkg", false).apply()
        remember(pkg)
    }

    fun toggleMute(pkg: String) {
        prefs.edit().putBoolean("mute.$pkg", !muted(pkg)).apply()
        remember(pkg)
    }

    /** What the backend should apply: a 0.0–1.0 multiplier on top of the system media volume. */
    fun effective(pkg: String): Float = if (muted(pkg)) 0f else level(pkg) / 100f

    /** Apps the user has ever adjusted, most recent first, so their sliders stay available while paused. */
    fun known(): List<String> = prefs.getString("known", "")!!.split(',').filter { it.isNotEmpty() }

    private fun remember(pkg: String) {
        val list = (listOf(pkg) + known().filter { it != pkg }).take(8)
        prefs.edit().putString("known", list.joinToString(",")).apply()
    }
}
