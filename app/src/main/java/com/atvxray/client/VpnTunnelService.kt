package com.atvxray.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.Executors

class VpnTunnelService : VpnService() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var configRepo: ConfigRepository
    private lateinit var libXray: LibXrayBridge
    private lateinit var tun2socks: Tun2SocksProcess

    private var tunInterface: ParcelFileDescriptor? = null
    @Volatile
    private var lastError: String? = null

    companion object {
        @Volatile
        var isConnected: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        configRepo = ConfigRepository(applicationContext)
        libXray = LibXrayBridge(applicationContext)
        tun2socks = Tun2SocksProcess(applicationContext)
        startForeground(1, buildNotification("Disconnected"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_CONNECT -> executor.execute { connectInternal() }
            AppConstants.ACTION_DISCONNECT -> executor.execute { disconnectInternal() }
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        disconnectInternal()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        disconnectInternal()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    private fun connectInternal() {
        if (isConnected) return
        val link = configRepo.getVlessLink() ?: return

        try {
            updateNotification("Connecting")
            lastError = null
            val runtimeConfig = libXray.prepareRuntimeConfig(link)
            setupTun()
            libXray.initControllers { fd -> protect(fd) }

            val datDir = filesDir.absolutePath
            val mphCachePath = File(filesDir, "mph").absolutePath
            libXray.runXray(runtimeConfig, datDir, mphCachePath)

            tunInterface?.let { tun2socks.start(it, AppConstants.SOCKS_ADDR) }

            isConnected = true
            updateNotification("Connected")
            broadcastState()
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        runCatching { tun2socks.stop() }
        runCatching { libXray.resetDns() }
        runCatching { libXray.stopXray() }
        runCatching { tunInterface?.close() }
        tunInterface = null

        isConnected = false
        updateNotification(lastError?.let { "Error: $it" } ?: "Disconnected")
        broadcastState()
    }

    private fun setupTun() {
        if (tunInterface != null) return

        val builder = Builder()
            .setSession("ATVxray")
            .setMtu(AppConstants.VPN_MTU)
            .addAddress(AppConstants.VPN_ADDR, AppConstants.VPN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tunInterface = builder.establish()
            ?: throw IllegalStateException("Cannot establish VPN interface")
    }

    private fun broadcastState() {
        sendBroadcast(
            Intent(AppConstants.ACTION_STATE)
                .putExtra(AppConstants.EXTRA_CONNECTED, isConnected)
                .putExtra(AppConstants.EXTRA_ERROR, lastError)
        )
    }

    private fun buildNotification(status: String): Notification {
        val channelId = "atvxray_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ATVxray VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager()?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("ATVxray")
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        notificationManager()?.notify(1, buildNotification(status))
    }

    private fun notificationManager(): NotificationManager? {
        @Suppress("DEPRECATION")
        return getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
}
