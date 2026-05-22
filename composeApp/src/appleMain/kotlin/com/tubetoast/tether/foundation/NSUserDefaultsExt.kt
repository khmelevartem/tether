package com.tubetoast.tether.foundation

import platform.Foundation.NSUserDefaults

internal fun NSUserDefaults.writeOrThrow(key: String, value: String) {
    setObject(value, forKey = key)
    if (!synchronize()) {
        throw IllegalStateException("NSUserDefaults.synchronize() returned false for key=$key")
    }
}
