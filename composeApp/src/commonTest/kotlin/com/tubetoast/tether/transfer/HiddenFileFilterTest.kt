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
        // empty name starts with nothing but does not start with '.' — not hidden
        assertFalse(isHidden(""))
    }
}
