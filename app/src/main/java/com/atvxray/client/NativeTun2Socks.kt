package com.atvxray.client

class NativeTun2Socks {
    init {
        System.loadLibrary("native-lib")
    }

    external fun startTun2Socks(args: Array<String>): Int
    external fun stopTun2Socks()
}
