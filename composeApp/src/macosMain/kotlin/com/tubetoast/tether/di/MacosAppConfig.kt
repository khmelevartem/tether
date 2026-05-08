package com.tubetoast.tether.di

interface MacosAppConfig : AppleAppConfig

class DefaultMacosAppConfig(
    override val deviceName: String,
) : MacosAppConfig
