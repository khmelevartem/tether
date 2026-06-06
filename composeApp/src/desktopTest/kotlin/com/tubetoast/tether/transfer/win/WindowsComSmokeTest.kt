package com.tubetoast.tether.transfer.win

import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Ole32.COINIT_APARTMENTTHREADED
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.PointerByReference
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsComSmokeTest {
    @Test
    fun `IFileOpenDialog CoCreateInstance succeeds on Windows`() {
        Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("win"))

        Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE)
        try {
            val pbrDialog = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(
                IFileOpenDialog.CLSID_FILEOPENDIALOG,
                null,
                WTypes.CLSCTX_ALL,
                IFileOpenDialog.IID_IFILEOPENDIALOG,
                pbrDialog,
            )
            assertTrue(COMUtils.SUCCEEDED(hr), "CoCreateInstance must succeed; HRESULT=0x${hr.toInt().toString(16)}")
            FileOpenDialog(pbrDialog.value).Release()
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }
}
