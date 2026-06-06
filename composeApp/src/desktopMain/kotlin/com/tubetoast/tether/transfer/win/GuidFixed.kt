// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).

package com.tubetoast.tether.transfer.win

import com.sun.jna.platform.win32.Guid

/**
 * Workaround for ProGuard/obfuscation: `Guid.CLSID`/`Guid.IID` with `@FieldOrder` break when
 * class names are obfuscated. Declaring `getFieldOrder()` explicitly survives obfuscation.
 */
internal object GuidFixed {
    class CLSID(
        guid: String,
    ) : Guid.CLSID(guid) {
        override fun getFieldOrder(): List<String> =
            listOf("Data1", "Data2", "Data3", "Data4")
    }

    class IID(
        iid: String,
    ) : Guid.IID(iid) {
        override fun getFieldOrder(): List<String> =
            listOf("Data1", "Data2", "Data3", "Data4")
    }
}
