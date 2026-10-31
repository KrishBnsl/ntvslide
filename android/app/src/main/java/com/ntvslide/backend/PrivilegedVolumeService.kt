package com.ntvslide.backend

import android.os.IBinder

/**
 * Runs inside a Shizuku "user service" process (shell uid), which Android allows to see and
 * control other apps' audio players. Everything here goes through hidden framework APIs, so it
 * uses reflection and is the most version-sensitive part of ntvslide.
 *
 * Assumptions (AOSP 12–14): IAudioService.getActivePlaybackConfigurations() returns full
 * AudioPlaybackConfiguration objects to a shell caller, and getPlayerProxy().setVolume(float)
 * scales that player the same way the system's own ducking does.
 */
class PrivilegedVolumeService : IPrivilegedVolume.Stub() {

    private val audioService: Any by lazy {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "audio") as IBinder
        Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private fun configs(): List<Any> =
        (audioService.javaClass.getMethod("getActivePlaybackConfigurations").invoke(audioService) as List<*>)
            .filterNotNull()

    private fun uidOf(config: Any): Int = config.javaClass.getMethod("getClientUid").invoke(config) as Int

    override fun destroy() {
        System.exit(0)
    }

    override fun activeUids(): IntArray = try {
        configs().map(::uidOf).distinct().toIntArray()
    } catch (e: Throwable) {
        IntArray(0)
    }

    override fun setVolume(uid: Int, volume: Float): Boolean {
        var reached = false
        try {
            for (config in configs()) {
                if (uidOf(config) != uid) continue
                val proxy = config.javaClass.getMethod("getPlayerProxy").invoke(config) ?: continue
                proxy.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
                    .invoke(proxy, volume.coerceIn(0f, 1f))
                reached = true
            }
        } catch (e: Throwable) {
            return false
        }
        return reached
    }
}
