package com.tubetoast.tether.transfer

import com.sun.jna.Native
import com.sun.jna.platform.win32.COM.COMUtils.FAILED
import com.sun.jna.platform.win32.COM.COMUtils.SUCCEEDED
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Ole32.COINIT_APARTMENTTHREADED
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError.ERROR_CANCELLED
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.tubetoast.tether.platform.win.FileDialog
import com.tubetoast.tether.platform.win.FileOpenDialog
import com.tubetoast.tether.platform.win.IFileOpenDialog
import com.tubetoast.tether.platform.win.ShTypes.FILEOPENDIALOGOPTIONS.Companion.FOS_ALLOWMULTISELECT
import com.tubetoast.tether.platform.win.ShTypes.FILEOPENDIALOGOPTIONS.Companion.FOS_FORCEFILESYSTEM
import com.tubetoast.tether.platform.win.ShTypes.SIGDN.Companion.SIGDN_FILESYSPATH
import com.tubetoast.tether.platform.win.ShellItem
import com.tubetoast.tether.platform.win.ShellItemArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.awt.Window
import java.io.File

private val log = KydraLog.withTag(default = "WindowsFilePicker")

internal class WindowsFilePicker(
    private val windowHolder: WindowHolder,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> =
        runCatching { pickViaComDialog() }
            .getOrElse { e ->
                log.warn { "Native Windows picker failed; falling back to AWT dialog — ${e.message}" }
                awtPickFiles(windowHolder)
            }

    override suspend fun pickFolder(): List<FileSource> = jvmPickFolder(windowHolder)

    override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")

    private suspend fun pickViaComDialog(): List<FileSource> = withContext(Dispatchers.IO) {
        var dialog: FileOpenDialog? = null
        var comInitialized = false
        try {
            val hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE)
            if (SUCCEEDED(hr)) {
                comInitialized = true
            } else {
                throw RuntimeException("CoInitializeEx failed: 0x${hr.toInt().toString(16)}")
            }

            val pbrDialog = PointerByReference()
            Ole32.INSTANCE
                .CoCreateInstance(
                    IFileOpenDialog.CLSID_FILEOPENDIALOG,
                    null,
                    WTypes.CLSCTX_ALL,
                    IFileOpenDialog.IID_IFILEOPENDIALOG,
                    pbrDialog,
                ).verify("CoCreateInstance failed")
            dialog = FileOpenDialog(pbrDialog.value)

            dialog.setFlag(FOS_ALLOWMULTISELECT or FOS_FORCEFILESYSTEM)

            val showResult = dialog.Show(windowHolder.window.toHwnd())
            val canceledException = Win32Exception(ERROR_CANCELLED)
            if (showResult == canceledException.hr) {
                log.info { "pickFiles (windows): cancelled" }
                return@withContext emptyList()
            }
            if (FAILED(showResult)) {
                throw RuntimeException("Show failed")
            }

            val files = dialog.getResults()
            log.info { "pickFiles (windows): ${files.size} file(s) selected" }
            files.flatMap { it.toFileSources() }
        } catch (e: Exception) {
            log.warn { "pickFiles (windows): failed — ${e.message}" }
            throw e
        } finally {
            dialog?.Release()
            if (comInitialized) Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun FileDialog.setFlag(flag: Int) {
        val ref = IntByReference()
        GetOptions(ref).verify("GetOptions failed")
        SetOptions(ref.value or flag).verify("SetOptions failed")
    }

    private fun FileOpenDialog.getResults(): List<File> {
        var itemArray: ShellItemArray? = null
        try {
            val pbrItemArray = PointerByReference()
            GetResults(pbrItemArray).verify("GetResults failed")
            itemArray = ShellItemArray(pbrItemArray.value)

            val countRef = IntByReference()
            itemArray.GetCount(countRef).verify("GetCount failed")

            val files = mutableListOf<File>()
            for (i in 0 until countRef.value) {
                val pbrItem = PointerByReference()
                itemArray.GetItemAt(i, pbrItem).verify("GetItemAt failed")
                val item = ShellItem(pbrItem.value)
                try {
                    val pbrDisplayName = PointerByReference()
                    item.GetDisplayName(SIGDN_FILESYSPATH, pbrDisplayName).verify("GetDisplayName failed")
                    val path = pbrDisplayName.value.getWideString(0)
                    files.add(File(path))
                    Ole32.INSTANCE.CoTaskMemFree(pbrDisplayName.value)
                } finally {
                    item.Release()
                }
            }
            return files
        } finally {
            itemArray?.Release()
        }
    }

    private fun HRESULT.verify(message: String): HRESULT {
        if (FAILED(this)) throw RuntimeException(message)
        return this
    }

    private fun Window?.toHwnd(): WinDef.HWND? = when (this) {
        null -> null
        else -> WinDef.HWND(Native.getWindowPointer(this))
    }
}
