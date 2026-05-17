package com.tubetoast.tether

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.arkivanov.decompose.retainedComponent
import com.tubetoast.tether.di.AppContainerProvider
import com.tubetoast.tether.network.TetherForegroundService
import com.tubetoast.tether.presentation.RootComponent
import com.tubetoast.tether.transfer.AndroidFilePicker

private const val TAG = "MainActivity"
private const val EXTRA_INTENT_CONSUMED = "tether_intent_consumed"

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Log.w(TAG, "POST_NOTIFICATIONS denied — foreground service notification will not appear")
        }
    }

    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        startService()

        val container = (application as AppContainerProvider).container
        val filePicker = AndroidFilePicker(this, contentResolver)

        root = retainedComponent { componentContext ->
            RootComponent(
                componentContext = componentContext,
                discovery = container.mdnsDiscovery,
                fileClient = container.fileClient,
                filePicker = filePicker,
            )
        }

        if (intent?.getBooleanExtra(EXTRA_INTENT_CONSUMED, false) != true) {
            intent?.toFileSources(contentResolver)?.takeIf { it.isNotEmpty() }?.let { sources ->
                intent.putExtra(EXTRA_INTENT_CONSUMED, true)
                root.onPendingIntent(sources)
            }
        }

        setContent {
            App(root)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toFileSources(contentResolver).takeIf { it.isNotEmpty() }?.let { sources ->
            root.onPendingIntent(sources)
        }
    }

    /**
     * Stop from the notification is sticky: once the user taps Stop, the service stays stopped
     * until the next cold start. Returning from background does not restart it.
     *
     * Calling [ContextCompat.startForegroundService] on an already-running service is safe —
     * the system routes it to [TetherForegroundService.onStartCommand] which returns
     * [android.app.Service.START_STICKY] without re-initialising anything.
     * Rotation-driven double-starts are prevented by [android:configChanges] in the manifest.
     */
    private fun startService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TetherForegroundService::class.java),
        )
    }
}
