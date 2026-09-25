package neth.iecal.questphone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.sync.SyncOnUnlockReceiver
import neth.iecal.questphone.backed.sync.SyncTrigger
import neth.iecal.questphone.core.services.reloadServiceInfo
import neth.iecal.questphone.core.workers.PluginCacheCleanerWorker
import neth.iecal.questphone.core.workers.PluginUpkeepWorker
import nethical.questphone.core.core.utils.CrashLogger
import nethical.questphone.core.core.utils.VibrationHelper
import javax.inject.Inject


@HiltAndroidApp
class MyApp : Application(), Configuration.Provider {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var questRepository: QuestRepository
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    // Required so WorkManager can construct @HiltWorker classes (@AssistedInject constructors) —
    // pairs with the WorkManagerInitializer removal in AndroidManifest.xml.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        VibrationHelper.init(this)
        reloadServiceInfo(this)
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
        SyncTrigger.init(this, userRepository, questRepository)
        // Habitica-style sync: check/pull on screen unlock & screen-on,
        // not a polling loop. Launcher is always "foreground" so onResume
        // alone isn't enough to catch other devices' updates.
        SyncOnUnlockReceiver.register(this, userRepository, questRepository)
        // Plugin Store spec §2/§3 background jobs
        PluginCacheCleanerWorker.schedule(this)
        PluginUpkeepWorker.schedule(this)
    }

}