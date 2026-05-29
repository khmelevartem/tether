package com.tubetoast.tether.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

/**
 * A labelled toggle row for the auto-send setting.
 *
 * @param enabled Current toggle state.
 * @param onToggle Invoked with the new value when the user taps.
 * @param label Overridable display label; defaults to "Auto-send".
 * @param accessibilityHint Appended to the switch content description to give context
 *   (e.g. the peer name). Pass `null` to omit.
 */
@Composable
fun AutoSendToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Auto-send",
    accessibilityHint: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        BodyText(
            text = label,
            modifier = Modifier.weight(1f),
        )

        val stateLabel = if (enabled) "On" else "Off"
        val description = buildString {
            append(label)
            if (accessibilityHint != null) append(" — $accessibilityHint")
            append(", currently $stateLabel")
        }

        Toggle(
            checked = enabled,
            onCheckedChange = onToggle,
            contentDescription = description,
        )
    }
}

@Preview(name = "AutoSendToggle — off")
@Composable
private fun PreviewAutoSendToggleOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        AutoSendToggle(enabled = false, onToggle = {})
    }

@Preview(name = "AutoSendToggle — on")
@Composable
private fun PreviewAutoSendToggleOn(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        AutoSendToggle(enabled = true, onToggle = {})
    }

@Preview(name = "AutoSendToggle — on with hint")
@Composable
private fun PreviewAutoSendToggleOnWithHint(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        AutoSendToggle(
            enabled = true,
            onToggle = {},
            accessibilityHint = "when Alice's Phone is the only online device",
        )
    }
