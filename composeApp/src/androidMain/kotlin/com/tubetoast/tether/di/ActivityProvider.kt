package com.tubetoast.tether.di

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks the foreground [Activity] via [Application.ActivityLifecycleCallbacks].
 *
 * Auto-registers itself on construction. [current] returns the activity between
 * `onResume` and `onPause`, or `null` if no activity is currently foreground —
 * callers that need an activity reference (e.g. to launch an `ActivityResultLauncher`)
 * must handle the null case.
 */
class ActivityProvider(
    application: Application,
) {
    @Volatile private var foreground: Activity? = null

    val current: Activity? get() = foreground

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    foreground = activity
                }

                override fun onActivityPaused(activity: Activity) {
                    if (foreground === activity) foreground = null
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (foreground === activity) foreground = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            },
        )
    }
}
