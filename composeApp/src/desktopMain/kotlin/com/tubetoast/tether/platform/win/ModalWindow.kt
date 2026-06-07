// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).
@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionName",
    "ktlint:standard:property-naming",
    "ConstPropertyName",
)

package com.tubetoast.tether.platform.win

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT

internal open class ModalWindow :
    Unknown,
    IModalWindow {
    constructor()

    constructor(pvInstance: Pointer?) : super(pvInstance)

    // VTBL Id indexing starts at 3 after Unknown's 0, 1, 2
    override fun Show(hwndOwner: WinDef.HWND?): WinNT.HRESULT = _invokeNativeObject(
        3,
        arrayOf(this.pointer, hwndOwner),
        WinNT.HRESULT::class.java,
    ) as WinNT.HRESULT
}
