package com.tubetoast.tether.config

import platform.UIKit.UIDevice

actual fun defaultDeviceName(): String =
    UIDevice.currentDevice.name.takeIf { it.isNotBlank() } ?: "iPhone"
