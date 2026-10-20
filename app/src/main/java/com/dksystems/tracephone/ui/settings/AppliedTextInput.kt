package com.dksystems.tracephone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R

/**
 * 값을 입력하고 **적용을 눌러야** 반영되는 입력칸.
 *
 * ### 입력 도중에 반영하지 않는 이유
 * 한도 같은 값은 입력 도중의 숫자가 그대로 적용되면 곧바로 판정이 돈다.
 * "10"을 치려고 "1"을 친 순간 1이라는 한도로 판정이 돌아 알림이 쏟아진다.
 * 그래서 화면 안에서만 값을 들고 있다가 적용을 누를 때 넘긴다.
 *
 * 저장된 값이 바뀌면 입력칸도 그 값으로 되돌아간다. 다른 경로로 값이 바뀌었는데
 * 입력칸만 옛 글자를 들고 있으면 화면이 서로 다른 말을 하게 된다.
 *
 * @param storedText 저장된 값을 그대로 보여줄 문자열
 * @param isTypingAllowed 입력 도중 허용할 글자인지. 허용하지 않으면 입력을 무시한다.
 * @param onApply 적용을 눌렀을 때 입력 문자열을 넘긴다. 해석은 호출부가 맡는다.
 */
@Composable
internal fun AppliedTextInput(
    label: String,
    hint: String,
    storedText: String,
    isTypingAllowed: (String) -> Boolean,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember(storedText) { mutableStateOf(storedText) }

    val canApply = inputText.isNotBlank() && inputText != storedText

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { newText ->
                    if (isTypingAllowed(newText)) {
                        inputText = newText
                    }
                },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = { onApply(inputText) },
                enabled = canApply
            ) {
                Text(stringResource(R.string.settings_custom_limit_apply))
            }
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
