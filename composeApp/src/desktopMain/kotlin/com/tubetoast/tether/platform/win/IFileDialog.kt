// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).
@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionName",
    "ktlint:standard:property-naming",
    "ConstPropertyName",
)

package com.tubetoast.tether.platform.win

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.tubetoast.tether.platform.win.ShTypes.COMDLG_FILTERSPEC

internal interface IFileDialog : IModalWindow {
    fun SetFileTypes(
        FileTypes: Int,
        rgFilterSpec: Array<COMDLG_FILTERSPEC?>?,
    ): WinNT.HRESULT?

    fun SetFileTypeIndex(iFileType: Int): WinNT.HRESULT?

    fun GetFileTypeIndex(piFileType: IntByReference?): WinNT.HRESULT?

    fun Advise(
        pfde: Pointer?,
        pdwCookie: IntByReference?,
    ): WinNT.HRESULT?

    fun Unadvise(dwCookie: Int): WinNT.HRESULT?

    fun SetOptions(fos: Int): WinNT.HRESULT?

    fun GetOptions(pfos: IntByReference?): WinNT.HRESULT?

    fun SetDefaultFolder(psi: Pointer?): WinNT.HRESULT?

    fun SetFolder(psi: Pointer?): WinNT.HRESULT?

    fun GetFolder(ppsi: PointerByReference?): WinNT.HRESULT?

    fun GetCurrentSelection(ppsi: PointerByReference?): WinNT.HRESULT?

    fun SetFileName(pszName: WString?): WinNT.HRESULT?

    fun GetFileName(pszName: PointerByReference?): WinNT.HRESULT?

    fun SetTitle(pszTitle: WString?): WinNT.HRESULT?

    fun SetOkButtonLabel(pszText: WString?): WinNT.HRESULT?

    fun SetFileNameLabel(pszLabel: WString?): WinNT.HRESULT?

    fun GetResult(ppsi: PointerByReference?): WinNT.HRESULT?

    fun AddPlace(psi: Pointer?, fdap: Int): WinNT.HRESULT?

    fun SetDefaultExtension(pszDefaultExtension: WString?): WinNT.HRESULT?

    fun Close(hr: WinNT.HRESULT?): WinNT.HRESULT?

    fun SetClientGuid(guid: Guid.GUID.ByReference?): WinNT.HRESULT?

    fun ClearClientData(): WinNT.HRESULT?

    fun SetFilter(pFilter: Pointer?): WinNT.HRESULT?

    companion object {
        val IID_IFILEDIALOG: GuidFixed.IID = GuidFixed.IID("{42f85136-db7e-439c-85f1-e4075d135fc8}")
    }
}
