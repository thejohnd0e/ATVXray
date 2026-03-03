package com.atvxray.client

import android.content.Context
import android.os.ParcelFileDescriptor
import java.util.concurrent.Executors

class Tun2SocksProcess(private val context: Context) {
    private val nativeTun2Socks = NativeTun2Socks()
    @Volatile
    private var running = false
    private var worker: Thread? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun start(tunFd: ParcelFileDescriptor, socksAddr: String) {
        stop()
        running = true
        val parts = socksAddr.split(":")
        val host = parts.firstOrNull() ?: "127.0.0.1"
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 10808
        val args = arrayOf(
            "badvpn-tun2socks",
            "--logger", "stdout",
            "--loglevel", "3",
            "--tunfd", tunFd.fd.toString(),
            "--tunmtu", AppConstants.VPN_MTU.toString(),
            "--netif-ipaddr", AppConstants.TUN_IP,
            "--netif-netmask", AppConstants.TUN_MASK,
            "--socks-server-addr", "$host:$port",
            "--socks5-udp"
        )
        worker = Thread {
            ioExecutor.execute {
                if (running) {
                    nativeTun2Socks.startTun2Socks(args)
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        runCatching { nativeTun2Socks.stopTun2Socks() }
        worker = null
    }
}
