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

    // Called just before launching a vault app — tracks which package was launched
    fun setVaultLaunch(ctx: Context, pkg: String) =
        prefs(ctx).edit { putString("vault_launched_pkg", pkg) }

    fun clearVaultLaunch(ctx: Context) =
        prefs(ctx).edit { remove("vault_launched_pkg") }

    // Returns true if the given package is the one we launched from the vault
    fun isVaultLaunch(ctx: Context, currentPkg: String? = null): Boolean {
        val launchedPkg = prefs(ctx).getString("vault_launched_pkg", "") ?: ""
        if (launchedPkg.isBlank()) return false
        return currentPkg == null || currentPkg == launchedPkg
    }

    // ── Temporary vault disable ───────────────────────────────────────────────
    // disabledUntil = System.currentTimeMillis() + duration, or Long.MAX_VALUE for indefinite
    private const val KEY_DISABLED_UNTIL = "vault_disabled_until"

    fun setVaultDisabledUntil(ctx: Context, untilMs: Long) =
        prefs(ctx).edit { putLong(KEY_DISABLED_UNTIL, untilMs) }

    fun clearVaultDisabled(ctx: Context) =
        prefs(ctx).edit { remove(KEY_DISABLED_UNTIL) }

    fun isVaultDisabled(ctx: Context): Boolean {
        val until = prefs(ctx).getLong(KEY_DISABLED_UNTIL, 0L)
        if (until == 0L) return false
        if (until == Long.MAX_VALUE) return true          // indefinite
        return if (System.currentTimeMillis() < until) true
        else { clearVaultDisabled(ctx); false }           // expired — auto-clean
    }

    fun vaultDisabledUntilMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_DISABLED_UNTIL, 0L)
}
