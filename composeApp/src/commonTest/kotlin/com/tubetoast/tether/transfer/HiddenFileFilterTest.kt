package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenFileFilterTest {
    @Test
    fun dotfileIsHidden() {
        assertTrue(isHidden(".gitignore"))
        assertTrue(isHidden(".DS_Store"))
        assertTrue(isHidden(".hidden"))
    }

    @Test
    fun windowsSystemFilesAreHidden() {
        assertTrue(isHidden("Thumbs.db"))
        assertTrue(isHidden("desktop.ini"))
    }

    @Test
    fun normalFilesAreNotHidden() {
        assertFalse(isHidden("photo.jpg"))
        assertFalse(isHidden("document.pdf"))
        assertFalse(isHidden("video.mp4"))
        assertFalse(isHidden("README"))
    }

    @Test
    fun emptySringIsHidden() {
        assertFalse(isHidden(""))
    }

    @Test
    fun similarWindowsFilenamesAreNotHidden() {
        assertFalse(isHidden("Thumbs.png"))
        assertFalse(isHidden("README.ini"))
    }

    @Test
    fun whitespaceAndSymbolNamesAreNotHidden() {
        assertFalse(isHidden(" "))
        assertFalse(isHidden("_"))
        assertFalse(isHidden("😭"))
    }
}
