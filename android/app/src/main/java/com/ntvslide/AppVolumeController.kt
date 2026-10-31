package com.ntvslide

import android.content.Context
import android.graphics.drawable.Drawable
import com.ntvslide.backend.AppVolumeBackend

data class AppEntry(val pkg: String, val uid: Int?, val label: String, val icon: Drawable?)

/** Glue between "which apps are making sound", the saved levels, and the privileged backend. */
class AppVolumeController(
    private val context: Context,
    val store: AppVolumeStore,
    private val backend: AppVolumeBackend
) {
    private val pm = context.packageManager

    private fun packageOf(uid: Int): String? = pm.getPackagesForUid(uid)?.firstOrNull()

    /** Apps currently playing first, then apps the user adjusted before (so they can pre-set levels). */
    fun entries(): List<AppEntry> {
        val playing = backend.activeUids()
            .filter { it >= 10_000 }                         // skip system uids
            .mapNotNull { uid -> packageOf(uid)?.let { it to uid } }
            .filter { it.first != context.packageName }
        val byPkg = LinkedHashMap<String, Int?>()
        playing.forEach { (pkg, uid) -> byPkg[pkg] = uid }
        store.known().forEach { byPkg.putIfAbsent(it, null) }
        return byPkg.mapNotNull { (pkg, uid) ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                AppEntry(pkg, uid, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
            } catch (e: Exception) {
                null // uninstalled since we last saw it
            }
        }
    }

    /** Pushes stored levels to every app that is playing right now. Cheap; called on a timer and on slider changes. */
    fun apply() {
        if (!backend.ready) return
        val adjusted = store.known().toSet()
        for (uid in backend.activeUids()) {
            if (uid < 10_000) continue
            val pkg = packageOf(uid) ?: continue
            if (pkg !in adjusted) continue // never touch apps the user has not adjusted
            backend.setVolume(uid, store.effective(pkg))
        }
    }

    fun apply(entry: AppEntry) {
        entry.uid?.let { backend.setVolume(it, store.effective(entry.pkg)) }
    }
}
