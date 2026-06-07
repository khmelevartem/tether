// Adapted from FileKit (MIT, github.com/vinceglb/FileKit).
@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionName",
    "ktlint:standard:property-naming",
    "ConstPropertyName",
)

package com.tubetoast.tether.platform.win

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.IUnknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

internal interface IShellItem : IUnknown {
    fun BindToHandler(
        pbc: Pointer?,
        bhid: Guid.GUID.ByReference?,
        riid: Guid.REFIID?,
        ppv: PointerByReference?,
    ): WinNT.HRESULT?

    fun GetParent(
        ppsi: PointerByReference?,
    ): WinNT.HRESULT?

    fun GetDisplayName(
        sigdnName: Long,
        ppszName: PointerByReference?,
    ): WinNT.HRESULT?

    fun GetAttributes(
        sfgaoMask: Int,
        psfgaoAttribs: IntByReference?,
    ): WinNT.HRESULT?

    fun Compare(
        psi: Pointer?,
        hint: Int,
        piOrder: IntByReference?,
    ): WinNT.HRESULT?

    companion object {
        val IID_ISHELLITEM: GuidFixed.IID = GuidFixed.IID("{43826d1e-e718-42ee-bc55-a1e261c37bfe}")
        val CLSID_SHELLITEM: GuidFixed.CLSID = GuidFixed.CLSID("{9ac9fbe1-e0a2-4ad6-b4ee-e212013ea917}")
    }
}
