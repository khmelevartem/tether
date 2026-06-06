// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).
@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionName",
    "ktlint:standard:property-naming",
    "ConstPropertyName",
)

package com.tubetoast.tether.transfer.win

import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference

internal interface IFileOpenDialog : IFileDialog {
    fun GetResults(ppenum: PointerByReference?): WinNT.HRESULT?

    fun GetSelectedItems(ppsai: PointerByReference?): WinNT.HRESULT?

    companion object {
        val IID_IFILEOPENDIALOG: GuidFixed.IID = GuidFixed.IID("{d57c7288-d4ad-4768-be02-9d969532d960}")
        val CLSID_FILEOPENDIALOG: GuidFixed.CLSID = GuidFixed.CLSID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
    }
}
