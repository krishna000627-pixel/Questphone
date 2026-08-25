package neth.iecal.questphone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.sync.SyncOnUnlockReceiver
import neth.iecal.questphone.backed.sync.SyncTrigger
import neth.iecal.questphone.core.services.reloadServiceInfo
import nethical.questphone.core.core.utils.CrashLogger
import nethical.questphone.core.core.utils.VibrationHelper
import javax.inject.Inject


@HiltAndroidApp(Application::class)
class MyApp : Application() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var questRepository: QuestRepository

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
    }

}