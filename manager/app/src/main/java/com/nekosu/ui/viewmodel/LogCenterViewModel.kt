package com.nekosu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nekosu.data.logging.LogCenterRepository
import com.nekosu.data.logging.LogSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogCenterUiState(
    val source: LogSource = LogSource.ALL,
    val content: String = "",
    val isLoading: Boolean = true,
)

class LogCenterViewModel(
    private val repository: LogCenterRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LogCenterUiState())
    val state: StateFlow<LogCenterUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun select(source: LogSource) {
        if (source == mutableState.value.source && !mutableState.value.isLoading) {
            refresh()
            return
        }
        mutableState.value = mutableState.value.copy(source = source)
        refresh()
    }

    fun refresh() {
        val source = mutableState.value.source
        mutableState.value = mutableState.value.copy(isLoading = true)
        viewModelScope.launch {
            val content = repository.load(source)
            mutableState.value = mutableState.value.copy(content = content, isLoading = false)
        }
    }

    fun clear(onResult: (Boolean) -> Unit) {
        val source = mutableState.value.source
        viewModelScope.launch {
            val ok = repository.clear(source)
            onResult(ok)
            if (ok) refresh()
        }
    }
}
