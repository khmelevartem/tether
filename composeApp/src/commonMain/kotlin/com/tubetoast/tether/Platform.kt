package com.tubetoast.tether

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
