package com.tubetoast.tether

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.discovery.MdnsDiscovery

class MainActivity : ComponentActivity() {
    private val discovery = MdnsDiscovery()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // TODO #4: replace with actual FileServer port once FileServer is available on Android
        discovery.start(deviceName = "Tether-${Build.MODEL}", port = 8080)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discovery.stop()
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
