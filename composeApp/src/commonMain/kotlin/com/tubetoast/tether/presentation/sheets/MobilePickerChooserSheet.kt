package com.tubetoast.tether.presentation.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.composables.core.ModalBottomSheet
import com.composables.core.ModalBottomSheetState
import com.composables.core.Scrim
import com.composables.core.Sheet
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.Files
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Photo

enum class PickerKind { Photos, Files, Folder }

/**
 * @param onDismiss Called when the sheet is dismissed. The call site is responsible for restoring
 *   focus to the triggering PeerCard row (brief §MobilePickerChooserSheet Accessibility).
 *   TODO(#191-followup): file a separate accessibility issue — per-PeerCard FocusRequester map in PeerListScreen,
 *   restored from onDismiss. Not blocking transfer flow; pure a11y polish.
 */
@Composable
fun MobilePickerChooserSheet(
    sheetState: ModalBottomSheetState,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
    enabledKinds: Set<PickerKind> = PickerKind.entries.toSet(),
) {
    ModalBottomSheet(
        state = sheetState,
        onDismiss = onDismiss,
    ) {
        Scrim(
            modifier = Modifier.semantics { contentDescription = "Dismiss picker chooser" },
        )
        Sheet(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TetherTheme.shapes.lg)
                .background(TetherTheme.colors.surfaceRaised),
        ) {
            MobilePickerChooserSheetContent(
                enabledKinds = enabledKinds,
                onPickPhotos = onPickPhotos,
                onPickFiles = onPickFiles,
                onPickFolder = onPickFolder,
            )
        }
    }
}

@Composable
fun MobilePickerChooserSheetContent(
    enabledKinds: Set<PickerKind>,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.lg),
    ) {
        BasicText(
            text = "Send from…",
            style = typography.titleMedium.copy(color = colors.textPrimary),
            modifier = Modifier
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.md),
        )

        if (PickerKind.Photos in enabledKinds) {
            PickerRow(
                icon = TablerIcons.Photo,
                label = "Photos",
                onClick = onPickPhotos,
            )
        }
        PickerRow(
            icon = TablerIcons.Files,
            label = "Files",
            onClick = onPickFiles,
        )
        PickerRow(
            icon = TablerIcons.Folder,
            label = "Folder",
            onClick = onPickFolder,
        )
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        androidx.compose.foundation.Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter
                .tint(colors.textPrimary),
            modifier = Modifier.size(24.dp),
        )
        BasicText(
            text = label,
            style = typography.bodyLarge.copy(color = colors.textPrimary),
        )
    }
}

@Preview(name = "MobilePickerChooserSheet — all kinds")
@Composable
private fun PreviewAllKinds(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        MobilePickerChooserSheetContent(
            enabledKinds = PickerKind.entries.toSet(),
            onPickPhotos = {},
            onPickFiles = {},
            onPickFolder = {},
        )
    }

@Preview(name = "MobilePickerChooserSheet — photos disabled")
@Composable
private fun PreviewPhotosDisabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        MobilePickerChooserSheetContent(
            enabledKinds = setOf(PickerKind.Files, PickerKind.Folder),
            onPickPhotos = {},
            onPickFiles = {},
            onPickFolder = {},
        )
    }
