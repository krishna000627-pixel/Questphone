package neth.iecal.questphone.backed.sync

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository

/**
 * Call SyncTrigger.push() from anywhere after any data change.
 * It silently pushes to Render if sync is enabled.
 */
object SyncTrigger {

    private var userRepository: UserRepository? = null
    private var questRepository: QuestRepository? = null
    private var appContext: Context? = null

    fun init(ctx: Context, ur: UserRepository, qr: QuestRepository) {
        appContext = ctx.applicationContext
        userRepository = ur
        questRepository = qr
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun push() {
        val ctx = appContext ?: return
        val ur = userRepository ?: return
        val qr = questRepository ?: return
        if (!RenderSyncPrefs.isEnabled(ctx)) return
        GlobalScope.launch(Dispatchers.IO) {
            RenderSyncManager.autoPush(ctx, ur, qr)
        }
    }
}
