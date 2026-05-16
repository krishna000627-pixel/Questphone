package neth.iecal.questphone.core.rpg

import android.content.Context
import android.content.pm.PackageManager
import nethical.questphone.data.UserInfo

/**
 * Single point of truth for resolving an app's display name.
 *
 * Priority:
 *   1. Manual rename in userInfo.appRenames  (always respected)
 *   2. Real label from PackageManager
 *
 * This fixes the bug where renamed apps showed their real name in the
 * blocker overlay, unlock dialog, low-coins dialog, screen time, etc.
 */
object AppNameResolver {

    fun resolve(
        context: Context,
        packageName: String,
        userInfo: UserInfo,
        fallback: String = packageName
    ): String {
        // 1. Manual rename always wins
        val rename = userInfo.appRenames[packageName]
        if (!rename.isNullOrBlank()) return rename
        // 2. Real label from PackageManager
        return try {
            context.packageManager
                .getApplicationInfo(packageName, 0)
                .loadLabel(context.packageManager)
                .toString()
        } catch (_: PackageManager.NameNotFoundException) { fallback }
    }
}
