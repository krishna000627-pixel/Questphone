package neth.iecal.questphone.core.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import neth.iecal.questphone.backed.repositories.UserRepositoryEntryPoint
import dagger.hilt.EntryPoints

/**
 * Intercepts notifications and cancels those from distracting apps
 * when Focus Mode or Notification Block is active.
 *
 * Requires Notification Access (Settings → Special App Access → Notification Access).
 */
class NotificationBlockerService : NotificationListenerService() {

    private val TAG = "NotifBlocker"

    private val userRepository by lazy {
        EntryPoints.get(applicationContext, UserRepositoryEntryPoint::class.java).userRepository()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val info = userRepository.userInfo
        if (!info.notificationBlockEnabled) return
        val pkg = sbn.packageName
        if (pkg == packageName) return   // never block own notifications

        val blocked = userRepository.getBlockedPackages()
        val shouldBlock = when {
            info.isFocusModeOn && info.blockAllNotificationsInFocus -> true
            blocked.contains(pkg) && info.blockDistractingAppNotifications -> true
            else -> false
        }
        if (shouldBlock) {
            try { cancelNotification(sbn.key) }
            catch (e: Exception) { Log.e(TAG, "cancel failed: ${e.message}") }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    companion object {
        fun isGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.contains(ComponentName(context, NotificationBlockerService::class.java).flattenToString())
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun isDNDGranted(context: Context) =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted

        fun openDNDSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun setDND(context: Context, enable: Boolean) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }
}
