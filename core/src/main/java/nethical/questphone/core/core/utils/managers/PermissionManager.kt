package nethical.questphone.core.core.utils.managers

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

fun checkUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            "android:get_usage_stats",
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun checkNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    return true
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = ("package:" + context.packageName).toUri()
    }
    context.startActivity(intent)
}

fun isSetToDefaultLauncher(context: Context): Boolean{
    return context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo?.packageName == context.packageName
}
fun openDefaultLauncherSettings(context: Context){
    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
    context.startActivity(intent)
}

fun isSetAsAssistantApp(context: Context): Boolean {
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        "assistant"
    ) ?: return false
    // value is "package/ComponentName", e.g. "neth.iecal.questphone/.MainActivity"
    return settingValue.startsWith(context.packageName)
}

fun openAssistantSettings(context: Context) {
    // Opens the "Default digital assistant" page directly on supported ROMs;
    // falls back to the general Voice Input / Manage Default Apps page on others.
    val intents = listOf(
        Intent("android.settings.VOICE_INPUT_SETTINGS"),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    )
    for (intent in intents) {
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            context.startActivity(intent)
            return
        }
    }
    // Last-resort: open main Settings
    context.startActivity(Intent(Settings.ACTION_SETTINGS))
}