package com.tubetoast.tether.di

import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.NoOpFilePicker

class CliAppContainer(
    config: DesktopAppConfig,
) : DesktopAppContainer(
        config = config,
        ownDeviceType = DeviceType.Cli,
    ) {
    override val filePicker: FilePicker = NoOpFilePicker
}
