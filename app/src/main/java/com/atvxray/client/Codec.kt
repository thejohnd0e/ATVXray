package com.atvxray.client

import android.util.Base64

object Codec {
    fun b64Encode(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun b64Decode(text: String): String {
        return String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
    }
}
