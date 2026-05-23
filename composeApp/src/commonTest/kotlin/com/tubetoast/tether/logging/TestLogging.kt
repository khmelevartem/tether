package com.tubetoast.tether.logging

import ru.pocketbyte.kydra.log.DefaultLoggerFactory
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.LogLevel
import ru.pocketbyte.kydra.log.wrapper.filtered
import ru.pocketbyte.kydra.log.wrapper.initOrIgnore

fun suppressTestLogs() {
    KydraLog.initOrIgnore(DefaultLoggerFactory.create().filtered(LogLevel.WARNING))
}
