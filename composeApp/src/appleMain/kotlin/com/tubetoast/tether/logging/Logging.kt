package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.AppleLogger
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.PrintLogger
import ru.pocketbyte.kydra.log.collection.LoggersSet
import ru.pocketbyte.kydra.log.wrapper.filtered
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore

// PrintLogger covers headless macOS-native runs and Xcode console; AppleLogger keeps OSLog
// the canonical sink so Console.app and TestFlight crash reports still receive structured logs.
actual fun initTetherLogging(debugEnabled: Boolean) {
    val level = if (debugEnabled) LogLevel.DEBUG else LogLevel.INFO
    KydraLog.initOrIgnore(LoggersSet(AppleLogger(), PrintLogger()).filtered(level))
}
