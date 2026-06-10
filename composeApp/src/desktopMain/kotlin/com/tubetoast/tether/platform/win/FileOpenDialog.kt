// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).
@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionName",
    "ktlint:standard:property-naming",
    "ConstPropertyName",
)

package com.tubetoast.tether.platform.win

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference

internal class FileOpenDialog :
    FileDialog,
    IFileOpenDialog {
    constructor()

    constructor(pvInstance: Pointer?) : super(pvInstance)

    // VTBL Id indexing starts at 27 after IFileDialog's 26
    override fun GetResults(ppenum: PointerByReference?): WinNT.HRESULT = _invokeNativeObject(
        27,
        arrayOf(this.pointer, ppenum),
        WinNT.HRESULT::class.java,
    ) as WinNT.HRESULT

    override fun GetSelectedItems(ppsai: PointerByReference?): WinNT.HRESULT = _invokeNativeObject(
        28,
        arrayOf(this.pointer, ppsai),
        WinNT.HRESULT::class.java,
    ) as WinNT.HRESULT
}
