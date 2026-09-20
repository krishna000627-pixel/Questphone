package neth.iecal.questphone.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets code outside the Compose tree (e.g. [neth.iecal.questphone.core.utils.termux.QuestPhoneLocalServer])
 * ask MainActivity to navigate to a given [RootRoute] path. Same pattern as
 * `RewardDialogInfo` — a plain Compose-observed object rather than a full event bus,
 * since there's only ever one MainActivity/NavHost alive at a time.
 */
object PendingNavigation {
    var pendingRoute: String? by mutableStateOf(null)
}
