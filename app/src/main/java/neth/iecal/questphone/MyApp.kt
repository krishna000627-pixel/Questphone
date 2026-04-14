package neth.iecal.questphone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.services.reloadServiceInfo
import nethical.questphone.core.core.utils.CrashLogger
import nethical.questphone.core.core.utils.VibrationHelper
import javax.inject.Inject


@HiltAndroidApp(Application::class)
class MyApp : Application() {

    @Inject lateinit var userRepository: UserRepository

    override fun onCreate() {
        super.onCreate()
        VibrationHelper.init(this)
        reloadServiceInfo(this)
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    }

}