package com.nekosu.ui.webui

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.ui.component.settings.SettingsTextFieldWidget

/**
 * WebUI 的 Material 风格对话框 / 文件选择处理（NekoSU 原有行为）。
 */
@Composable
fun HandleWebUIEventMaterial(
    webUIState: WebUIState,
    fileLauncher: ActivityResultLauncher<Intent>,
) {
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.ShowAlert -> {
            var showDialog by remember(event) { mutableStateOf(true) }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onAlertResult()
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Text(event.message)
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onAlertResult()
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                )
            }
        }

        is WebUIEvent.ShowConfirm -> {
            var showDialog by remember(event) { mutableStateOf(true) }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onConfirmResult(false)
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Text(event.message)
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onConfirmResult(true)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            webUIState.onConfirmResult(false)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }

        is WebUIEvent.ShowPrompt -> {
            var showDialog by remember(event) { mutableStateOf(true) }
            val state = rememberTextFieldState(event.defaultValue)

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onPromptResult(null)
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                            color = MaterialTheme.colorScheme.surfaceBright,
                        ) {
                            SettingsTextFieldWidget(
                                state = state,
                                title = event.message,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                useLabelAsPlaceholder = true,
                                renderBackgroundBlur = false,
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onPromptResult(state.text.toString())
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            webUIState.onPromptResult(null)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }

        is WebUIEvent.ShowFileChooser -> {
            LaunchedEffect(event) {
                try {
                    fileLauncher.launch(event.intent)
                } catch (_: Exception) {
                    webUIState.onFileChooserResult(null)
                }
            }
        }

        else -> {}
    }
}
