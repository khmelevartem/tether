package com.tubetoast.tether

import android.app.Application
import com.tubetoast.tether.di.AndroidAppContainer
import com.tubetoast.tether.di.AppContainerProvider
import com.tubetoast.tether.di.TetherAppConfig

class TetherApp :
    Application(),
    AppContainerProvider {
    override val container: AndroidAppContainer by lazy {
        AndroidAppContainer(TetherAppConfig(application = this))
    }
}
