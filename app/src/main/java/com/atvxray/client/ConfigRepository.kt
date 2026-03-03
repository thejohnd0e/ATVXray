package com.atvxray.client

import android.content.Context

class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun saveVlessLink(link: String) {
        prefs.edit().putString(AppConstants.KEY_VLESS_LINK, link.trim()).apply()
    }

    fun getVlessLink(): String? {
        return prefs.getString(AppConstants.KEY_VLESS_LINK, null)
    }

    fun hasConfig(): Boolean = !getVlessLink().isNullOrBlank()
}
