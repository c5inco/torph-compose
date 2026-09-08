package io.torph.demo.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import io.torph.compose.TextMorph
import io.torph.demo.Caption
import io.torph.demo.CodeBlock
import io.torph.demo.Gap
import io.torph.demo.SectionTitle
import io.torph.demo.Stage

@Composable
fun TypingScreen() {
    var field by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("1234", TextRange(4)))
    }
    Caption("Type digits. With cursorIndex the number under the caret is matched by position, so appending a digit doesn't shift every existing digit to a new place. Without it, place matching makes each digit exit and re-enter.")
    Gap(12)
    OutlinedTextField(
        value = field,
        onValueChange = { field = it },
        label = { Text("Edit me") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    SectionTitle("cursorIndex = ${field.selection.end}")
    Stage(72) {
        TextMorph(
            text = field.text,
            cursorIndex = field.selection.end,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    SectionTitle("cursorIndex = null (place matching)")
    Stage(72) {
        TextMorph(
            text = field.text,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    CodeBlock(
        """
        var field by remember { mutableStateOf(TextFieldValue("1234")) }
        BasicTextField(field, onValueChange = { field = it })
        TextMorph(text = field.text, cursorIndex = field.selection.end)
        """,
    )
    Column {}
}
