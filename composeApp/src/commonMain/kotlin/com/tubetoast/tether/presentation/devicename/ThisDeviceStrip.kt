package com.tubetoast.tether.presentation.devicename

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.ui.designsystem.CaptionText
import com.tubetoast.tether.ui.designsystem.DismissCloseButton
import com.tubetoast.tether.ui.designsystem.PrimaryActionIconButton
import com.tubetoast.tether.ui.designsystem.TetherTextField
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Pencil

private val IconSize = 20.dp
private val AccentBarWidth = 3.dp

@Composable
fun ThisDeviceStripScreen(component: DeviceNameComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    ThisDeviceStripContent(
        state = state,
        onEditClick = component::onEditClick,
        onDraftChange = component::onDraftChange,
        onConfirm = component::onConfirm,
        onCancel = component::onCancel,
        modifier = modifier,
    )
}

@Composable
internal fun ThisDeviceStripContent(
    state: DeviceNameState,
    onEditClick: () -> Unit,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val density = LocalDensity.current
    val borderWidthPx = with(density) { spacing.borderWidth.toPx() }
    val accentBarWidthPx = with(density) { AccentBarWidth.toPx() }
    val borderColor = colors.border
    val accentColor = colors.accent

    val shellModifier = modifier
        .fillMaxWidth()
        .background(colors.surfaceRaised)
        .drawBehind {
            drawLine(
                color = borderColor,
                start = Offset(0f, size.height - borderWidthPx / 2f),
                end = Offset(size.width, size.height - borderWidthPx / 2f),
                strokeWidth = borderWidthPx,
            )
            drawRect(
                color = accentColor,
                topLeft = Offset.Zero,
                size = Size(accentBarWidthPx, size.height),
            )
        }.padding(horizontal = spacing.lg, vertical = spacing.sm)

    when (state) {
        is DeviceNameState.Display -> DisplayMode(
            name = state.name,
            onEditClick = onEditClick,
            modifier = shellModifier,
        )
        is DeviceNameState.Editing -> EditingMode(
            state = state,
            onDraftChange = onDraftChange,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = shellModifier,
        )
    }
}

@Composable
private fun DisplayMode(
    name: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TitleText(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CaptionText(text = "This device")
        }

        Box(
            modifier = Modifier
                .tetherMinTouchTarget()
                .clickable(onClick = onEditClick)
                .semantics {
                    contentDescription = "Rename this device"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(TablerIcons.Pencil),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textMuted),
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

@Composable
private fun EditingMode(
    state: DeviceNameState.Editing,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = state.draft, selection = TextRange(0, state.draft.length)))
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onCancel()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TetherTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onDraftChange(newValue.text)
                },
                placeholder = "Device name",
                errorMessage = state.errorMessage,
                contentDescription = "Device name field",
                imeAction = ImeAction.Done,
                onImeAction = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            CaptionText(
                text = "This device",
                modifier = Modifier.padding(top = TetherTheme.spacing.xs),
            )
        }

        DismissCloseButton(onClick = onCancel, contentDescription = "Cancel rename")

        PrimaryActionIconButton(
            onClick = onConfirm,
            contentDescription = "Save name",
            icon = TablerIcons.Check,
            enabled = state.confirmEnabled,
        )
    }
}

@Preview(name = "ThisDeviceStrip — Display")
@Composable
private fun PreviewDisplay(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.display,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "ThisDeviceStrip — Editing (valid)")
@Composable
private fun PreviewEditingValid(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.editing,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "ThisDeviceStrip — Editing (empty name error)")
@Composable
private fun PreviewEditingEmptyError(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.editingEmptyError,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "ThisDeviceStrip — Editing (too long error)")
@Composable
private fun PreviewEditingTooLong(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.editingTooLongError,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

@Preview(name = "ThisDeviceStrip — Editing (save failed, confirm enabled)")
@Composable
private fun PreviewEditingSaveFailed(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.editingSaveFailed,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
