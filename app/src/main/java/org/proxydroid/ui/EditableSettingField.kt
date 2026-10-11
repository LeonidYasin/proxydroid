package org.proxydroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation

/** A focusable button, not a text editor. DPAD focus never opens the IME. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditableSettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    validate: (String) -> String? = { null },
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        onClick = { draft = value; error = null; editing = true },
        enabled = enabled,
        modifier = modifier.tvRemoteFocus().testTag("setting-$label"),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                if (value.isEmpty()) "Not set" else
                    visualTransformation.filter(AnnotatedString(value)).text.text,
                maxLines = 1,
            )
        }
    }

    if (editing) {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit $label") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; error = null },
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester).testTag("editor-$label"),
                )
                // AlertDialog subcomposes its content: request focus from the
                // editor's own composition, only after its node is attached.
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.tvRemoteFocus(), onClick = {
                    error = validate(draft)
                    if (error == null) {
                        onValueChange(draft)
                        editing = false
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(modifier = Modifier.tvRemoteFocus(), onClick = { editing = false }) { Text("Cancel") }
            },
        )
    }
}
