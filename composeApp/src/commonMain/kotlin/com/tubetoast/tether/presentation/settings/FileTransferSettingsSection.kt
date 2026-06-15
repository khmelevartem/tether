package com.tubetoast.tether.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.foundation.IsGalleryToggleShown
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.CaptionText
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.designsystem.Toggle
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun FileTransferSettingsSection(component: FileTransferSettingsComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    FileTransferSettingsSectionContent(
        state = state,
        onSetLargeSelectionWarning = component::onSetLargeSelectionWarning,
        onSetSaveToGallery = component::onSetSaveToGallery,
        modifier = modifier,
    )
}

@Composable
internal fun FileTransferSettingsSectionContent(
    state: FileTransferSettingsState,
    onSetLargeSelectionWarning: (Boolean) -> Unit,
    onSetSaveToGallery: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing

    Column(modifier = modifier) {
        TitleText(
            text = "File Transfer",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        )

        SaveLocationRow(
            saveLocation = state.saveLocation,
            modifier = Modifier.fillMaxWidth(),
        )

        if (IsGalleryToggleShown) {
            SaveToPhotosRow(
                enabled = state.saveToGallery,
                onToggle = onSetSaveToGallery,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LargeSelectionWarningRow(
            enabled = state.largeSelectionWarning,
            onToggle = onSetLargeSelectionWarning,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SaveLocationRow(
    saveLocation: String,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors

    Column(
        modifier = modifier
            .padding(horizontal = spacing.lg)
            .padding(top = spacing.md, bottom = spacing.sm),
    ) {
        BodyText(text = "Save location")
        BodyText(
            text = saveLocation,
            color = colors.textMuted,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Composable
private fun SaveToPhotosRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val stateLabel = if (enabled) "On" else "Off"

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(top = spacing.md),
        ) {
            BodyText(
                text = "Save photos & videos to Photos",
                modifier = Modifier.weight(1f),
            )
            Toggle(
                checked = enabled,
                onCheckedChange = onToggle,
                contentDescription = "Save received photos and videos to Photos, currently $stateLabel",
            )
        }
        CaptionText(
            text = "Received photos and videos are also added to your Photos library. " +
                "They're always kept in Files too.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(top = spacing.xs, bottom = spacing.md),
        )
    }
}

@Composable
private fun LargeSelectionWarningRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val stateLabel = if (enabled) "On" else "Off"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = spacing.lg)
            .padding(vertical = spacing.md),
    ) {
        BodyText(
            text = "Show large-selection warnings",
            modifier = Modifier.weight(1f),
        )
        Toggle(
            checked = enabled,
            onCheckedChange = onToggle,
            contentDescription = "Show large-selection warnings, currently $stateLabel",
        )
    }
}

@Preview(name = "FileTransferSettings — iOS default (gallery on, warning on)")
@Composable
private fun PreviewIosDefault(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        FileTransferSettingsSectionContent(
            state = PreviewFixtures.FileTransferSettings.iosDefault,
            onSetLargeSelectionWarning = {},
            onSetSaveToGallery = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "FileTransferSettings — iOS gallery off")
@Composable
private fun PreviewIosGalleryOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        FileTransferSettingsSectionContent(
            state = PreviewFixtures.FileTransferSettings.iosGalleryOff,
            onSetLargeSelectionWarning = {},
            onSetSaveToGallery = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "FileTransferSettings — iOS large-selection warning off")
@Composable
private fun PreviewIosWarningOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        FileTransferSettingsSectionContent(
            state = PreviewFixtures.FileTransferSettings.iosWarningOff,
            onSetLargeSelectionWarning = {},
            onSetSaveToGallery = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
