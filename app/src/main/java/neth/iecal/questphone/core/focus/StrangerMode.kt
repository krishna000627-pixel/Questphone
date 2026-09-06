package neth.iecal.questphone.core.focus

import android.content.Context
import androidx.core.content.edit

const val PREF_STRANGER = "stranger_mode"
const val KEY_STRANGER_ACTIVE = "is_stranger_mode"
const val KEY_WHITELIST = "stranger_whitelist"

fun Context.isStrangerMode(): Boolean =
    getSharedPreferences(PREF_STRANGER, Context.MODE_PRIVATE)
        .getBoolean(KEY_STRANGER_ACTIVE, false)

fun Context.setStrangerMode(active: Boolean) =
    getSharedPreferences(PREF_STRANGER, Context.MODE_PRIVATE)
        .edit { putBoolean(KEY_STRANGER_ACTIVE, active) }

fun Context.getStrangerWhitelist(): Set<String> =
    getSharedPreferences(PREF_STRANGER, Context.MODE_PRIVATE)
        .getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()

fun Context.saveStrangerWhitelist(set: Set<String>) =
    getSharedPreferences(PREF_STRANGER, Context.MODE_PRIVATE)
        .edit { putStringSet(KEY_WHITELIST, set) }
