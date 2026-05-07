package com.tubetoast.tether.di

interface IosAppConfig : AppleAppConfig

class DefaultIosAppConfig(
    override val deviceName: String,
) : IosAppConfig
