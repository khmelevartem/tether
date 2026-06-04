package com.tubetoast.tether.di

import com.tubetoast.tether.protocol.DeviceType

class CliAppContainer(
    config: DesktopAppConfig,
) : DesktopAppContainer(
        config = config,
        ownDeviceType = DeviceType.Cli,
    )
