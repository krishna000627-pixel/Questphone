package neth.iecal.questphone.core.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object DeviceAdminManager {

    fun getComponentName(context: Context): ComponentName =
        ComponentName(context, QuestPhoneAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(getComponentName(context))
    }

    /** Returns an Intent to launch the system "Activate device admin" dialog. */
    fun buildActivationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents QuestPhone from being uninstalled while quests are active. " +
                        "You can deactivate this at any time in Settings → Security → Device Admins."
            )
        }

    /** Deactivates admin — call before allowing uninstall or on user request. */
    fun deactivate(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(getComponentName(context))
    }
}
