package com.tubetoast.tether.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun TetherTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorMessage: String? = null,
    contentDescription: String = "",
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val shapes = TetherTheme.shapes
    val typography = TetherTheme.typography

    val borderColor = if (errorMessage != null) colors.error else colors.border

    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = typography.bodyMedium.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onSearch = { onImeAction() },
                onSend = { onImeAction() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(colors.surfaceRaised, shapes.sm)
                        .border(spacing.borderWidth, borderColor, shapes.sm)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                        BodyText(text = placeholder, color = colors.textMuted)
                    }
                    innerTextField()
                }
            },
        )

        if (errorMessage != null) {
            CaptionText(
                text = errorMessage,
                color = colors.error,
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Preview(name = "TetherTextField — normal")
@Composable
private fun PreviewTetherTextFieldNormal(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        var value by remember { mutableStateOf(TextFieldValue("Alice's MacBook")) }
        TetherTextField(
            value = value,
            onValueChange = { value = it },
            contentDescription = "Device name",
            modifier = Modifier
                .fillMaxWidth()
                .padding(TetherTheme.spacing.md),
        )
    }

@Preview(name = "TetherTextField — error")
@Composable
private fun PreviewTetherTextFieldError(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        var value by remember { mutableStateOf(TextFieldValue("")) }
        TetherTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = "Device name",
            errorMessage = "Enter a name.",
            contentDescription = "Device name",
            modifier = Modifier
                .fillMaxWidth()
                .padding(TetherTheme.spacing.md),
        )
    }

@Preview(name = "TetherTextField — too long error")
@Composable
private fun PreviewTetherTextFieldTooLong(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        var value by remember {
            mutableStateOf(
                TextFieldValue("A name that exceeds the fifty codepoint limit of this field"),
            )
        }
        TetherTextField(
            value = value,
            onValueChange = { value = it },
            errorMessage = "Use 50 characters or fewer.",
            contentDescription = "Device name",
            modifier = Modifier
                .fillMaxWidth()
                .padding(TetherTheme.spacing.md),
        )
    }
