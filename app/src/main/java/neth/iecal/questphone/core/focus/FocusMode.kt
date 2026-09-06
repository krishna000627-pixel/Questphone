package neth.iecal.questphone.core.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import java.util.Calendar
import neth.iecal.questphone.core.notifications.NotificationBlockerService

const val ACTION_FOCUS_START = "neth.iecal.questphone.FOCUS_START"
const val ACTION_FOCUS_END   = "neth.iecal.questphone.FOCUS_END"
const val PREF_FOCUS = "focus_mode"
const val KEY_IS_FOCUS = "is_focus_mode"
const val KEY_FOCUS_ENABLED = "focus_scheduling_enabled"

// Study whitelist - apps allowed during focus
val STUDY_WHITELIST = setOf(
    "xyz.penpencil.physicswala",      // PhysicsWallah
    "com.google.android.youtube",     // YouTube (for study)
    "neth.iecal.questphone",          // This app
    "com.android.chrome",
    "org.wikipedia",
)

fun Context.isFocusMode(): Boolean {
    return getSharedPreferences(PREF_FOCUS, Context.MODE_PRIVATE)
        .getBoolean(KEY_IS_FOCUS, false)
}

fun Context.setFocusMode(active: Boolean) {
    getSharedPreferences(PREF_FOCUS, Context.MODE_PRIVATE)
        .edit { putBoolean(KEY_IS_FOCUS, active) }
    Log.d("FocusMode", "Focus mode: $active")
}

fun Context.isFocusSchedulingEnabled(): Boolean {
    return getSharedPreferences(PREF_FOCUS, Context.MODE_PRIVATE)
        .getBoolean(KEY_FOCUS_ENABLED, false)
}

fun Context.setFocusSchedulingEnabled(enabled: Boolean) {
    getSharedPreferences(PREF_FOCUS, Context.MODE_PRIVATE)
        .edit { putBoolean(KEY_FOCUS_ENABLED, enabled) }
    if (enabled) scheduleFocusAlarms() else cancelFocusAlarms()
}

fun Context.scheduleFocusAlarms() {
    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun dailyAlarm(hour: Int, minute: Int, action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis())
                add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        return pi
    }

    dailyAlarm(7, 50, ACTION_FOCUS_START)
    dailyAlarm(16, 0, ACTION_FOCUS_END)
    Log.d("FocusMode", "Alarms scheduled: 07:50 start, 16:00 end")
}

fun Context.cancelFocusAlarms() {
    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    for (action in listOf(ACTION_FOCUS_START, ACTION_FOCUS_END)) {
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: continue
        am.cancel(pi)
    }
    setFocusMode(false)
}

class FocusModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FOCUS_START -> {
                val cal = Calendar.getInstance()
                val day = cal.get(Calendar.DAY_OF_WEEK)
                // Mon=2 ... Fri=6
                if (day in Calendar.MONDAY..Calendar.FRIDAY) {
                    context.setFocusMode(true)
                    // Auto-enable DND if user has granted access and setting enabled
                    NotificationBlockerService.setDND(context, true)
                    Log.d("FocusMode", "Focus started (weekday)")
                }
            }
            ACTION_FOCUS_END -> {
                context.setFocusMode(false)
                NotificationBlockerService.setDND(context, false)
                Log.d("FocusMode", "Focus ended")
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (context.isFocusSchedulingEnabled()) {
                    context.scheduleFocusAlarms()
                    // Restore focus if currently in window
                    val cal = Calendar.getInstance()
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val day  = cal.get(Calendar.DAY_OF_WEEK)
                    if (day in Calendar.MONDAY..Calendar.FRIDAY && hour in 7..15) {
                        context.setFocusMode(true)
                    }
                }
            }
        }
    }
}
