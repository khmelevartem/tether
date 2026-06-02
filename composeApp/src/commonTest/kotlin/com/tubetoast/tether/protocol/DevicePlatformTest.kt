package com.tubetoast.tether.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevicePlatformTest {
    @Test
    fun macBookProIsLaptop() = assertEquals(DevicePlatform.Laptop, inferDevicePlatform("MacBook Pro"))

    @Test
    fun macBookIsLaptop() = assertEquals(DevicePlatform.Laptop, inferDevicePlatform("MacBook"))

    @Test
    fun ipadAirIsTablet() = assertEquals(DevicePlatform.Tablet, inferDevicePlatform("iPad Air"))

    @Test
    fun iphone15IsSmartphone() = assertEquals(DevicePlatform.Smartphone, inferDevicePlatform("iPhone 15"))

    @Test
    fun pixel8IsSmartphone() = assertEquals(DevicePlatform.Smartphone, inferDevicePlatform("Pixel 8"))

    @Test
    fun imacIsDesktop() = assertEquals(DevicePlatform.Desktop, inferDevicePlatform("iMac"))

    @Test
    fun macMiniIsDesktop() = assertEquals(DevicePlatform.Desktop, inferDevicePlatform("Mac mini"))

    @Test
    fun alicesPcIsDesktop() = assertEquals(DevicePlatform.Desktop, inferDevicePlatform("Alice's PC"))

    @Test
    fun unknownDeviceIsNull() = assertNull(inferDevicePlatform("Unknown Device"))
}
