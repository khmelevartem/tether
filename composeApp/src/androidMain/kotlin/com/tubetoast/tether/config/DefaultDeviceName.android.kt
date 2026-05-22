package com.tubetoast.tether.config

import android.os.Build

actual fun defaultDeviceName(): String =
    Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
