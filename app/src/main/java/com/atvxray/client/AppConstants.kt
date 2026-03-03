package com.atvxray.client

object AppConstants {
    const val PREFS_NAME = "atvxray_prefs"
    const val KEY_VLESS_LINK = "vless_link"

    const val ACTION_CONNECT = "com.atvxray.client.action.CONNECT"
    const val ACTION_DISCONNECT = "com.atvxray.client.action.DISCONNECT"
    const val ACTION_STATE = "com.atvxray.client.action.STATE"
    const val EXTRA_CONNECTED = "connected"
    const val EXTRA_ERROR = "error"

    const val VPN_MTU = 1500
    const val VPN_ADDR = "10.88.0.1"
    const val VPN_PREFIX = 30
    const val TUN_IP = "10.88.0.2"
    const val TUN_MASK = "255.255.255.252"

    const val SOCKS_ADDR = "127.0.0.1:10808"
    const val DIRECT_TAG = "direct"

    val PRIVATE_BYPASS = listOf(
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "100.64.0.0/10"
    )
}
