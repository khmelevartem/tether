package com.tubetoast.tether.foundation

enum class DesktopHostOs { MacOs, Windows, Linux }

fun hostOsFrom(osName: String): DesktopHostOs = when {
    osName.lowercase().contains("mac") -> DesktopHostOs.MacOs
    osName.lowercase().contains("win") -> DesktopHostOs.Windows
    else -> DesktopHostOs.Linux
}

val currentHostOs: DesktopHostOs = hostOsFrom(System.getProperty("os.name").orEmpty())
