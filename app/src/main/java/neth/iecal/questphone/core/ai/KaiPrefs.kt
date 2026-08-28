package neth.iecal.questphone.core.ai

import android.content.Context
import androidx.core.content.edit

/**
 * Stores Kai API key in a SEPARATE SharedPrefs file that is NEVER included in GitHub/WiFi sync.
 * UserInfo.gemmaApiKey is kept empty — only this file holds the real key.
 */
object KaiPrefs {
    private const val PREFS = "kai_secure_prefs"
    private const val KEY_API = "api_key"

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""

    fun saveApiKey(context: Context, key: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_API, key.trim()) }

    fun hasKey(context: Context): Boolean = getApiKey(context).isNotBlank()
}
