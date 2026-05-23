package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.DefaultLoggerFactory
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.wrapper.filtered
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore

actual fun initTetherLogging(debugEnabled: Boolean) {
    val level = if (debugEnabled) LogLevel.DEBUG else LogLevel.INFO
    KydraLog.initOrIgnore(DefaultLoggerFactory.create().filtered(level))
}

fun isDebugEnabled(): Boolean =
    System.getProperty("tether.log.debug")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_DEBUG")?.toBooleanStrictOrNull() == true
