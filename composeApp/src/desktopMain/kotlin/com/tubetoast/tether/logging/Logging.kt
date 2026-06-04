package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.print.JvmLogMessageFormatter
import ru.pocketbyte.kydra.log.print.Printer
import ru.pocketbyte.kydra.log.print.SimplePrintLogger
import ru.pocketbyte.kydra.log.wrapper.filtered
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.PrintStream

actual fun initTetherLogging(debugEnabled: Boolean) {
    val level = if (debugEnabled) LogLevel.DEBUG else LogLevel.INFO
    val stream = if (logToStdout()) System.out else System.err
    KydraLog.initOrIgnore(
        PrintStreamLogger(stream)
            .filtered(level)
            .withTag(prefix = TAG_PREFIX),
    )
}

fun isDebugEnabled(): Boolean =
    System.getProperty("tether.log.debug")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_DEBUG")?.toBooleanStrictOrNull() == true

fun logToStdout(): Boolean =
    System.getProperty("tether.log.stdout")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_STDOUT")?.toBooleanStrictOrNull() == true

private class PrintStreamLogger(
    stream: PrintStream,
) : SimplePrintLogger(
        printer = object : Printer {
            override fun print(message: String) = stream.println(message)
        },
        logMessageFormatter = JvmLogMessageFormatter(),
    )
