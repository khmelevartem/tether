package com.tubetoast.tether.presentation.devicename

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.designsystem.TetherTextField
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.X

private val IconSize = 20.dp

@Composable
fun ThisDeviceStripScreen(component: DeviceNameComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
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
    val spacing = TetherTheme.spacing

    val contentModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.lg, vertical = spacing.sm)

    when (state) {
        is DeviceNameState.Display -> DisplayMode(
            name = state.name,
            onEditClick = onEditClick,
            modifier = contentModifier,
        )
        is DeviceNameState.Editing -> EditingMode(
            state = state,
            onDraftChange = onDraftChange,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = contentModifier,
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
        LabelText(text = "This device", modifier = Modifier.alignByBaseline())
        BodyText(
            text = ": $name",
            modifier = Modifier
                .weight(1f)
                .padding(start = TetherTheme.spacing.xs)
                .alignByBaseline(),
        )
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
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val isValid = state.error == null
    val focusRequester = remember { FocusRequester() }

    // Pre-fill with all text selected when entering edit mode.
    // fieldValue is local to this composable; drift from state.draft is intentional
    // (the field owns the cursor while the component owns the committed text).
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = state.draft, selection = TextRange(0, state.draft.length)))
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val errorMessage = when (state.error) {
        DeviceNameError.EmptyName -> "Enter a name."
        DeviceNameError.TooLong -> "Use 50 characters or fewer."
        DeviceNameError.SaveFailed -> "Couldn't save the name. Try again."
        null -> null
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
        TetherTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onDraftChange(newValue.text)
            },
            placeholder = "Device name",
            errorMessage = errorMessage,
            contentDescription = "Device name field",
            imeAction = ImeAction.Done,
            onImeAction = onConfirm,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )

        Box(
            modifier = Modifier
                .tetherMinTouchTarget()
                .clickable(enabled = isValid, onClick = onConfirm)
                .semantics {
                    contentDescription = "Save name"
                    role = Role.Button
                    if (!isValid) disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(TablerIcons.Check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (isValid) colors.accent else colors.textMuted,
                ),
                alpha = if (isValid) 1f else 0.38f,
                modifier = Modifier.size(IconSize),
            )
        }

        Box(
            modifier = Modifier
                .tetherMinTouchTarget()
                .clickable(onClick = onCancel)
                .semantics {
                    contentDescription = "Cancel rename"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(TablerIcons.X),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textMuted),
                modifier = Modifier.size(IconSize),
            )
        }
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

@Preview(name = "ThisDeviceStrip — Editing (save failed)")
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
