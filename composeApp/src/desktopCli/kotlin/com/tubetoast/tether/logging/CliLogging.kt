package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.LoggerStub
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore

fun initCliLogging() {
    KydraLog.initOrIgnore(if (isDebugEnabled()) consoleLogger(LogLevel.DEBUG) else LoggerStub())
}
