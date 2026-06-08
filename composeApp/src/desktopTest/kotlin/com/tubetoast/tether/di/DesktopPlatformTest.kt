package com.tubetoast.tether.di

import kotlin.test.Test
import kotlin.test.assertSame

class DesktopPlatformTest {
    @Test
    fun `Mac OS X maps to MacOs`() {
        assertSame(DesktopPlatform.MacOs, desktopPlatformFrom("Mac OS X"))
    }

    @Test
    fun `Windows 11 maps to Windows`() {
        assertSame(DesktopPlatform.Windows, desktopPlatformFrom("Windows 11"))
    }

    @Test
    fun `Linux maps to Linux`() {
        assertSame(DesktopPlatform.Linux, desktopPlatformFrom("Linux"))
    }

    @Test
    fun `empty string maps to Linux`() {
        assertSame(DesktopPlatform.Linux, desktopPlatformFrom(""))
    }
}
