package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.wrapper.initDefault

actual fun initTetherLogging(debugEnabled: Boolean) {
    KydraLog.initDefault(level = if (debugEnabled) LogLevel.DEBUG else LogLevel.INFO)
}

fun isDebugEnabled(): Boolean =
    System.getProperty("tether.log.debug")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_DEBUG")?.toBooleanStrictOrNull() == true
