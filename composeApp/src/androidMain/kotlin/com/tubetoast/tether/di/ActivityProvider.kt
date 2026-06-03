package com.tubetoast.tether.di

import androidx.activity.ComponentActivity

actual typealias PlatformActivity = ComponentActivity

class AndroidActivityProvider : ActivityProvider, android.app.Application.ActivityLifecycleCallbacks {
    @Volatile private var resumed: ComponentActivity? = null

    override val current: PlatformActivity?
        get() = resumed

    override fun onActivityResumed(activity: android.app.Activity) {
        if (activity is ComponentActivity) resumed = activity
    }

    override fun onActivityPaused(activity: android.app.Activity) {
        if (resumed === activity) resumed = null
    }

    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
    override fun onActivityStarted(activity: android.app.Activity) = Unit
    override fun onActivityStopped(activity: android.app.Activity) = Unit
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
    override fun onActivityDestroyed(activity: android.app.Activity) = Unit
}
