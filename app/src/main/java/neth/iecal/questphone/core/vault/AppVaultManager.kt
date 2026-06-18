package neth.iecal.questphone.core.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppVaultManager {

    private const val PREFS = "app_vault_prefs"
    private const val KEY_PIN = "vault_pin"
    private const val KEY_APPS = "vault_apps"
    private const val KEY_SECRET_CODE = "vault_secret_code"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPin(ctx: Context): String = prefs(ctx).getString(KEY_PIN, "") ?: ""
    fun setPin(ctx: Context, pin: String) = prefs(ctx).edit { putString(KEY_PIN, pin) }
    fun isPinSet(ctx: Context): Boolean = getPin(ctx).isNotBlank()

    // Secret code entered in calculator before = to open vault
    fun getSecretCode(ctx: Context): String = prefs(ctx).getString(KEY_SECRET_CODE, "") ?: ""
    fun setSecretCode(ctx: Context, code: String) = prefs(ctx).edit { putString(KEY_SECRET_CODE, code) }

    fun getVaultApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_APPS, emptySet()) ?: emptySet()

    fun addToVault(ctx: Context, pkg: String) {
        val apps = getVaultApps(ctx).toMutableSet().also { it.add(pkg) }
        prefs(ctx).edit { putStringSet(KEY_APPS, apps) }
    }

    fun removeFromVault(ctx: Context, pkg: String) {
        val apps = getVaultApps(ctx).toMutableSet().also { it.remove(pkg) }
        prefs(ctx).edit { putStringSet(KEY_APPS, apps) }
    }

    fun isInVault(ctx: Context, pkg: String): Boolean = getVaultApps(ctx).contains(pkg)

    // Called just before launching a vault app — gives 3s bypass window
    fun setVaultLaunch(ctx: Context) =
        prefs(ctx).edit { putLong("vault_launch_at", System.currentTimeMillis()) }

    fun isVaultLaunch(ctx: Context): Boolean {
        val t = prefs(ctx).getLong("vault_launch_at", 0L)
        return System.currentTimeMillis() - t < 3000L
    }
}
