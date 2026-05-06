package com.tubetoast.tether

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.tubetoast.tether.network.TetherForegroundService

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Log.w(TAG, "POST_NOTIFICATIONS denied — foreground service notification will not appear")
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

        startServiceIfNotRunning()

        setContent {
            App()
        }
    }

    /**
     * Start [TetherForegroundService] only if it is not already running.
     *
     * Strategy: [bindService] with flags=0 (no BIND_AUTO_CREATE) returns `true` only if the
     * service is already alive. If `true` → service is up, release the binding immediately and
     * do nothing. If `false` → service is not running → start it.
     *
     * This prevents double-starts on configuration changes (rotation) while letting the service
     * restart naturally when the user opens the app after explicitly stopping it.
     */
    private fun startServiceIfNotRunning() {
        val intent = Intent(this, TetherForegroundService::class.java)
        val alreadyRunning = bindService(
            intent,
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    // Service is running — release the check binding immediately.
                    unbindService(this)
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            },
            0, // no BIND_AUTO_CREATE — only binds if service is already running
        )
        if (!alreadyRunning) {
            ContextCompat.startForegroundService(this, intent)
        }
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
