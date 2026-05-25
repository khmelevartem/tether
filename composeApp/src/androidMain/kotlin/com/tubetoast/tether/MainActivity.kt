package com.tubetoast.tether

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.arkivanov.decompose.retainedComponent
import com.tubetoast.tether.di.AppContainerProvider
import com.tubetoast.tether.network.TetherForegroundService
import com.tubetoast.tether.presentation.RootContent
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "MainActivity")

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            log.warn { "POST_NOTIFICATIONS denied — foreground service notification will not appear" }
        }
    }

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
        val component = retainedComponent { container.rootComponentFactory.create(it) }
        setContent {
            RootContent(component)
        }
    }

    /**
     * Start [TetherForegroundService].
     *
     * Called only from [onCreate] (cold start). Stop from the notification is sticky:
     * once the user taps Stop, the service stays stopped until the user cold-starts the
     * app again (or, in the future, taps a Start button in the app UI — tracked separately).
     * Returning from background does not restart the service — that would defeat the
     * Stop button's purpose for the foreground-shade case.
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
