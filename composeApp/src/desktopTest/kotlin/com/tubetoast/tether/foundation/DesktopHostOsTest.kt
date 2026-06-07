package com.tubetoast.tether.foundation

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopHostOsTest {
    @Test
    fun `Mac OS X maps to MacOs`() {
        assertEquals(DesktopHostOs.MacOs, hostOsFrom("Mac OS X"))
    }

    @Test
    fun `Windows 11 maps to Windows`() {
        assertEquals(DesktopHostOs.Windows, hostOsFrom("Windows 11"))
    }

    @Test
    fun `Linux maps to Linux`() {
        assertEquals(DesktopHostOs.Linux, hostOsFrom("Linux"))
    }

    @Test
    fun `empty string maps to Linux`() {
        assertEquals(DesktopHostOs.Linux, hostOsFrom(""))
    }
}
