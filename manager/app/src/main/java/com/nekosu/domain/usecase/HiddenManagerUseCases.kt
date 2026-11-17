package com.nekosu.domain.usecase

import android.net.Uri
import com.nekosu.data.application.HiddenManagerRepository

class ObserveHiddenManagerStateUseCase(private val repository: HiddenManagerRepository) {
    operator fun invoke() = repository.state
}

class RefreshHiddenManagerUseCase(private val repository: HiddenManagerRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class HideManagerUseCase(private val repository: HiddenManagerRepository) {
    suspend operator fun invoke(displayName: String, iconUri: Uri?) =
        repository.hide(displayName, iconUri)
}

class RestoreManagerUseCase(private val repository: HiddenManagerRepository) {
    suspend operator fun invoke() = repository.restore()
}

class CompleteManagerHandoverUseCase(private val repository: HiddenManagerRepository) {
    suspend operator fun invoke() = repository.completeHandoverIfNeeded()
}
