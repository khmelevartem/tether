@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.tubetoast.tether.logging

fun initLogging() {
    initTetherLogging(debugEnabled = Platform.isDebugBinary)
}
