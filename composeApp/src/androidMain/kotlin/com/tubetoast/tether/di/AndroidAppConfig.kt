package com.tubetoast.tether.di

import android.app.Application

interface AndroidAppConfig : JvmAppConfig {
    val application: Application
}
