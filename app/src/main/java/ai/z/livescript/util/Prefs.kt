package ai.z.livescript.util

import android.content.Context

object Prefs {
    private const val FILE = "livescript_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_OFFLINE_MODE = "force_offline_mode"
    private const val DEFAULT_SERVER_URL = "https://k1mqe7car3r1-d.space-z.ai"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun setServerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun isForcedOfflineMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OFFLINE_MODE, false)

    fun setForcedOfflineMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    }
}
