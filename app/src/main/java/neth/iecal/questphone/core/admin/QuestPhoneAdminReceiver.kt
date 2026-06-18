package neth.iecal.questphone.core.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin Receiver.
 * When active, prevents QuestPhone from being uninstalled via Settings.
 * Activation is OPTIONAL — user enables it explicitly. Never force-activated.
 */
class QuestPhoneAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "⚔️ QuestPhone Admin activated — uninstall locked.", Toast.LENGTH_LONG).show()
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "QuestPhone Admin deactivated.", Toast.LENGTH_SHORT).show()
    }
}
