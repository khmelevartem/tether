package com.tubetoast.tether

import android.app.Application
import android.content.Context

class TetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }

    companion object {
        lateinit var context: Context
            private set
    }
}
