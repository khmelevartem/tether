package com.tubetoast.tether

import android.app.Application
import android.content.pm.ApplicationInfo
import com.tubetoast.tether.di.AndroidAppContainer
import com.tubetoast.tether.di.AppContainerProvider
import com.tubetoast.tether.di.TetherAppConfig
import com.tubetoast.tether.logging.initTetherLogging

class TetherApp :
    Application(),
    AppContainerProvider {
    override val container: AndroidAppContainer by lazy {
        AndroidAppContainer(TetherAppConfig(application = this))
    }

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        initTetherLogging(debugEnabled = debuggable)
        registerActivityLifecycleCallbacks(container.activityProvider)
        container.autoSendDispatcher.start()
    }
}
