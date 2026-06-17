package com.tubetoast.tether.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.designsystem.BackChevronButton
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun SettingsScreen(component: SettingsComponent, modifier: Modifier = Modifier) {
    SettingsScreenContent(
        fileTransferComponent = component.fileTransfer,
        onBack = component.onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreenContent(
    fileTransferComponent: FileTransferSettingsComponent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SettingsTopBar(onBack = onBack, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            FileTransferSettingsSection(
                component = fileTransferComponent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing

    Row(
        modifier = modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BackChevronButton(
            onClick = onBack,
            contentDescription = "Back",
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TitleText(
                text = "Settings",
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Preview(name = "SettingsScreen — iOS default (gallery on, warning on)")
@Composable
private fun PreviewIosDefault(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        SettingsScreenContentPreview(state = PreviewFixtures.FileTransferSettings.iosDefault)
    }

@Preview(name = "SettingsScreen — non-iOS (gallery row absent)")
@Composable
private fun PreviewNonIosDefault(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        SettingsScreenContentPreview(state = PreviewFixtures.FileTransferSettings.nonIosDefault)
    }

@Composable
private fun SettingsScreenContentPreview(state: FileTransferSettingsState) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(onBack = {}, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            FileTransferSettingsSectionContent(
                state = state,
                onSetLargeSelectionWarning = {},
                onSetSaveToGallery = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
