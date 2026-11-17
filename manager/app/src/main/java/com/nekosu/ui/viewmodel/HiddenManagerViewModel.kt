package com.nekosu.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nekosu.domain.model.HiddenManagerState
import com.nekosu.domain.usecase.HideManagerUseCase
import com.nekosu.domain.usecase.ObserveHiddenManagerStateUseCase
import com.nekosu.domain.usecase.RefreshHiddenManagerUseCase
import com.nekosu.domain.usecase.RestoreManagerUseCase

@Immutable
data class HiddenManagerUiState(
    val supported: Boolean = false,
    val hidden: Boolean = false,
    val busy: Boolean = false,
    val currentPackage: String = "",
    val displayName: String? = null,
    val error: String? = null,
    val pendingTransfer: Boolean = false,
    val hasOfficialCopy: Boolean = false,
    val currentUserId: Int = 0,
    val multiUserDevice: Boolean = false,
    val pendingUpdate: Boolean = false,
)

sealed interface HiddenManagerUiAction {
    data object Refresh : HiddenManagerUiAction
    data class Hide(val displayName: String, val iconUri: Uri?) : HiddenManagerUiAction
    data object Restore : HiddenManagerUiAction
}

sealed interface HiddenManagerUiEvent {
    data class Completed(
        val hidden: Boolean,
        val success: Boolean,
        val message: String?,
    ) : HiddenManagerUiEvent
}

class HiddenManagerViewModel(
    observeState: ObserveHiddenManagerStateUseCase,
    private val refreshHiddenManager: RefreshHiddenManagerUseCase,
    private val hideManager: HideManagerUseCase,
    private val restoreManager: RestoreManagerUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<HiddenManagerUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HiddenManagerUiEvent> = mutableEvents.asSharedFlow()

    val uiState: StateFlow<HiddenManagerUiState> = observeState()
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HiddenManagerUiState(),
        )

    init {
        viewModelScope.launch { refreshHiddenManager() }
    }

    fun dispatch(action: HiddenManagerUiAction) {
        when (action) {
            HiddenManagerUiAction.Refresh -> viewModelScope.launch { refreshHiddenManager() }
            is HiddenManagerUiAction.Hide -> submit(targetHidden = true) {
                hideManager(action.displayName, action.iconUri)
            }
            HiddenManagerUiAction.Restore -> submit(targetHidden = false) {
                restoreManager()
            }
        }
    }

    private fun submit(targetHidden: Boolean, command: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val result = command()
            mutableEvents.emit(
                HiddenManagerUiEvent.Completed(
                    hidden = targetHidden,
                    success = result.isSuccess,
                    message = result.exceptionOrNull()?.message,
                ),
            )
            refreshHiddenManager()
        }
    }

    private fun HiddenManagerState.toUiState() = HiddenManagerUiState(
        supported = supported,
        hidden = hidden,
        busy = busy,
        currentPackage = currentPackage,
        displayName = displayName,
        error = error,
        pendingTransfer = pendingTransfer,
        hasOfficialCopy = hasOfficialCopy,
        currentUserId = currentUserId,
        multiUserDevice = multiUserDevice,
        pendingUpdate = pendingUpdate,
    )
}
