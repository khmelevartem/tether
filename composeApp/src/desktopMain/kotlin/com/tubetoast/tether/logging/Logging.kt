package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.LoggerStub
import ru.pocketbyte.kydra.log.print.JvmLogMessageFormatter
import ru.pocketbyte.kydra.log.print.Printer
import ru.pocketbyte.kydra.log.print.SimplePrintLogger
import ru.pocketbyte.kydra.log.wrapper.filtered
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.PrintStream

private fun consoleLogger(level: LogLevel) =
    PrintStreamLogger(if (logToStdout()) System.out else System.err)
        .filtered(level)
        .withTag(prefix = TAG_PREFIX)

actual fun initTetherLogging(debugEnabled: Boolean) {
    KydraLog.initOrIgnore(consoleLogger(if (debugEnabled) LogLevel.DEBUG else LogLevel.INFO))
}

fun initCliLogging() {
    KydraLog.initOrIgnore(if (isDebugEnabled()) consoleLogger(LogLevel.DEBUG) else LoggerStub())
}

fun isDebugEnabled(): Boolean =
    System.getProperty("tether.log.debug")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_DEBUG")?.toBooleanStrictOrNull() == true

private fun logToStdout(): Boolean =
    System.getProperty("tether.log.stdout")?.toBooleanStrictOrNull() == true ||
        System.getenv("TETHER_LOG_STDOUT")?.toBooleanStrictOrNull() == true

private class PrintStreamPrinter(
    private val stream: PrintStream,
) : Printer {
    override fun print(message: String) = stream.println(message)
}

private class PrintStreamLogger(
    stream: PrintStream,
) : SimplePrintLogger(
        printer = PrintStreamPrinter(stream),
        logMessageFormatter = JvmLogMessageFormatter(),
    )
