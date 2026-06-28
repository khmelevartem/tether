package com.tubetoast.tether.presentation.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext

class SettingsComponent(
    componentContext: ComponentContext,
    fileTransferComponentFactory: (ComponentContext) -> FileTransferSettingsComponent,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {
    val fileTransfer: FileTransferSettingsComponent =
        fileTransferComponentFactory(childContext("fileTransfer"))
}
