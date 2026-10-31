package com.ntvslide.backend

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/** Whatever can actually change another app's loudness. Public Android APIs cannot, so this is privileged. */
interface AppVolumeBackend {
    val ready: Boolean
    fun activeUids(): List<Int>
    fun setVolume(uid: Int, volume: Float)
}

object ShizukuBackend : AppVolumeBackend {

    @Volatile private var service: IPrivilegedVolume? = null

    override val ready: Boolean get() = service?.asBinder()?.isBinderAlive == true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IPrivilegedVolume.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun shizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun permissionGranted(): Boolean = try {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission(requestCode: Int) {
        if (shizukuRunning() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(requestCode)
    }

    /** Idempotent: safe to call every time the accessibility service (re)starts. */
    fun connect(context: Context) {
        if (ready || !permissionGranted()) return
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedVolumeService::class.java.name))
            .processNameSuffix("volume")
            .daemon(false)
            .version(1)
        try { Shizuku.bindUserService(args, connection) } catch (e: Throwable) { /* retried on next connect() */ }
    }

    override fun activeUids(): List<Int> = try {
        service?.activeUids()?.toList().orEmpty()
    } catch (e: Throwable) { emptyList() }

    override fun setVolume(uid: Int, volume: Float) {
        try { service?.setVolume(uid, volume) } catch (e: Throwable) { /* service died; connect() will rebind */ }
    }
}
