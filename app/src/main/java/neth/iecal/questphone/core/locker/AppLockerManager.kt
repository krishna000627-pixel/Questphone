package neth.iecal.questphone.core.locker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppLockerManager {

    private const val PREFS = "app_locker_prefs"
    private const val KEY_PIN = "locker_pin"
    private const val KEY_APPS = "locked_apps"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPin(ctx: Context): String = prefs(ctx).getString(KEY_PIN, "") ?: ""
    fun setPin(ctx: Context, pin: String) = prefs(ctx).edit { putString(KEY_PIN, pin) }
    fun isPinSet(ctx: Context): Boolean = getPin(ctx).isNotBlank()

    fun getLockedApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_APPS, emptySet()) ?: emptySet()

    fun lockApp(ctx: Context, pkg: String) {
        val apps = getLockedApps(ctx).toMutableSet().also { it.add(pkg) }
        prefs(ctx).edit { putStringSet(KEY_APPS, apps) }
    }

    fun unlockApp(ctx: Context, pkg: String) {
        val apps = getLockedApps(ctx).toMutableSet().also { it.remove(pkg) }
        prefs(ctx).edit { putStringSet(KEY_APPS, apps) }
    }

    fun isLocked(ctx: Context, pkg: String): Boolean = getLockedApps(ctx).contains(pkg)

    // Called just before launching a locked app — gives 3s bypass window
    fun setLockerLaunch(ctx: Context) =
        prefs(ctx).edit { putLong("locker_launch_at", System.currentTimeMillis()) }

    fun isLockerLaunch(ctx: Context): Boolean {
        val t = prefs(ctx).getLong("locker_launch_at", 0L)
        return System.currentTimeMillis() - t < 3000L
    }
}
