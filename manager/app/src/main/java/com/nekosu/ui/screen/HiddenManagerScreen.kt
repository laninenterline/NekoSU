package com.nekosu.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nekosu.R
import com.nekosu.data.system.ManagerAppearance
import com.nekosu.data.update.ManagerUpdateRepository
import com.nekosu.domain.model.ManagerUpdateChecksumState
import com.nekosu.data.system.ManagerIcon
import com.nekosu.ui.component.ConfirmResult
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.rememberConfirmDialog
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import com.nekosu.ui.viewmodel.HiddenManagerUiAction
import com.nekosu.ui.viewmodel.HiddenManagerUiEvent
import com.nekosu.ui.viewmodel.HiddenManagerViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiddenManagerScreen() {
    val viewModel = koinViewModel<HiddenManagerViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val cardConfig: CardConfig = koinInject()
    val managerUpdateRepository: ManagerUpdateRepository = koinInject()
    val updateSourceStatus by managerUpdateRepository.sourceStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val confirmDialog = rememberConfirmDialog()

    var name by rememberSaveable { mutableStateOf("") }
    var iconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var iconError by remember { mutableStateOf(false) }
    var customRepositoryInput by rememberSaveable {
        mutableStateOf(managerUpdateRepository.customRepository.orEmpty())
    }
    var updateSourceMessage by remember { mutableStateOf<String?>(null) }

    val defaultName = stringResource(R.string.hidden_manager_default_name)
    val validatedName = remember(name, defaultName) {
        runCatching {
            ManagerAppearance.validateHiddenName(name.ifEmpty { defaultName })
        }.getOrNull()
    }
    val nameError = validatedName == null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            iconUri = uri.toString()
            iconError = false
        }
    }

    LaunchedEffect(iconUri) {
        preview = null
        iconError = false
        val selected = iconUri ?: return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { ManagerIcon.decode(context, Uri.parse(selected)) }
        }.onSuccess {
            preview = it.asImageBitmap()
        }.onFailure {
            iconError = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HiddenManagerUiEvent.Completed -> {
                    val message = when {
                        !event.success -> event.message ?: context.getString(R.string.hidden_manager_transfer_failed)
                        event.hidden -> context.getString(R.string.hidden_manager_success_hidden)
                        else -> context.getString(R.string.hidden_manager_success_restore)
                    }
                    snackbarHost.showReplacingSnackbar(message)
                }
            }
        }
    }

    val confirmTitle = stringResource(R.string.hidden_manager_confirm_title)
    val confirmText = stringResource(R.string.confirm)

    fun confirmHide() {
        if (!state.supported || state.currentUserId != 0 || state.busy || validatedName == null) return
        scope.launch {
            val result = confirmDialog.awaitConfirm(
                title = confirmTitle,
                content = context.getString(R.string.hidden_manager_confirm_hide),
                confirm = confirmText,
            )
            if (result == ConfirmResult.Confirmed) {
                viewModel.dispatch(
                    HiddenManagerUiAction.Hide(requireNotNull(validatedName), iconUri?.let(Uri::parse)),
                )
            }
        }
    }

    fun confirmRestore() {
        if (!state.supported || state.currentUserId != 0 || state.busy) return
        scope.launch {
            val result = confirmDialog.awaitConfirm(
                title = confirmTitle,
                content = context.getString(R.string.hidden_manager_confirm_restore),
                confirm = confirmText,
            )
            if (result == ConfirmResult.Confirmed) viewModel.dispatch(HiddenManagerUiAction.Restore)
        }
    }

    val savedSourceMessage = stringResource(R.string.manager_update_source_saved)
    val invalidSourceMessage = stringResource(R.string.manager_update_source_invalid)
    val checksumStateText = when (updateSourceStatus.checksumState) {
        ManagerUpdateChecksumState.NOT_CHECKED -> stringResource(R.string.manager_update_checksum_not_checked)
        ManagerUpdateChecksumState.PROVIDED ->
            stringResource(R.string.manager_update_checksum_provided, updateSourceStatus.checksumEntries)
        ManagerUpdateChecksumState.MISSING -> stringResource(R.string.manager_update_checksum_missing)
        ManagerUpdateChecksumState.FAILED -> stringResource(R.string.manager_update_checksum_failed)
        ManagerUpdateChecksumState.NIGHTLY -> stringResource(R.string.manager_update_checksum_nightly)
    }

    fun saveUpdateSource() {
        val result = managerUpdateRepository.setCustomRepository(customRepositoryInput)
        updateSourceMessage = if (result.isSuccess) {
            customRepositoryInput = managerUpdateRepository.customRepository.orEmpty()
            savedSourceMessage
        } else {
            result.exceptionOrNull()?.message ?: invalidSourceMessage
        }
    }

    fun resetUpdateSource() {
        val result = managerUpdateRepository.setCustomRepository(null)
        customRepositoryInput = ""
        updateSourceMessage = if (result.isSuccess) {
            savedSourceMessage
        } else {
            result.exceptionOrNull()?.message ?: invalidSourceMessage
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hidden_manager_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.hidden_manager_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            item {
                Text(stringResource(R.string.hidden_manager_description))
            }
            item {
                Text(
                    text = stringResource(R.string.hidden_manager_status) + ": " +
                        if (state.hidden) {
                            stringResource(R.string.hidden_manager_status_hidden, state.currentPackage)
                        } else {
                            stringResource(R.string.hidden_manager_status_official, state.currentPackage)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.currentUserId != 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.hidden_manager_multiuser_unsupported,
                            state.currentUserId,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.multiUserDevice) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_manager_multiuser_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!state.hidden) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        singleLine = true,
                        isError = nameError,
                        label = { Text(stringResource(R.string.hidden_manager_name)) },
                        placeholder = { Text(defaultName) },
                    )
                }
                if (nameError) {
                    item {
                        Text(
                            text = stringResource(R.string.hidden_manager_name_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        preview?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(64.dp),
                            )
                        }
                        Column {
                            TextButton(
                                enabled = !state.busy,
                                onClick = { picker.launch("image/*") },
                            ) {
                                Text(stringResource(R.string.hidden_manager_icon_pick))
                            }
                            if (iconError) {
                                Text(
                                    text = stringResource(R.string.hidden_manager_icon_invalid),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            if (state.pendingTransfer) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_manager_pending),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                if (state.hidden) {
                    Button(
                        onClick = ::confirmRestore,
                        enabled = state.supported && state.currentUserId == 0 && !state.busy &&
                            !state.pendingTransfer && state.hasOfficialCopy,
                    ) {
                        Text(stringResource(R.string.hidden_manager_restore))
                    }
                } else {
                    Button(
                        onClick = ::confirmHide,
                        enabled = state.supported && state.currentUserId == 0 && !state.busy &&
                            validatedName != null && !state.pendingTransfer,
                    ) {
                        Text(stringResource(R.string.hidden_manager_hide))
                    }
                }
            }
            if (!state.supported) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_manager_unsupported),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.busy) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.hidden_manager_busy))
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.hidden_manager_reboot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.manager_update_source_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            item {
                Text(stringResource(R.string.manager_update_source_summary))
            }
            item {
                Text(
                    text = stringResource(
                        R.string.manager_update_source_current,
                        updateSourceStatus.repository,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = customRepositoryInput,
                    onValueChange = { customRepositoryInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.manager_update_source_custom)) },
                    placeholder = { Text(managerUpdateRepository.defaultRepository) },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { saveUpdateSource() }) {
                        Text(stringResource(R.string.manager_update_source_save))
                    }
                    TextButton(onClick = { resetUpdateSource() }) {
                        Text(stringResource(R.string.manager_update_source_reset))
                    }
                }
                updateSourceMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.manager_update_checksum_status, checksumStateText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.manager_update_source_default,
                        managerUpdateRepository.defaultRepository,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.hidden) {
                item {
                    Text(
                        text = stringResource(R.string.manager_update_hidden_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.pendingUpdate) {
                    item {
                        Text(
                            text = stringResource(R.string.hidden_manager_update_retry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.hidden) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_manager_update_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text(
                        text = if (state.hasOfficialCopy) {
                            stringResource(R.string.hidden_manager_has_copy)
                        } else {
                            stringResource(R.string.hidden_manager_no_copy)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val savedDisplayName = state.displayName
            if (!state.hidden && savedDisplayName != null) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_manager_saved_name, savedDisplayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
