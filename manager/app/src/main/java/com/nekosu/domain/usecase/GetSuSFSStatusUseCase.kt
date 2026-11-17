package com.nekosu.domain.usecase

import com.nekosu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

class GetSusfsIntegrationStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getIntegrationStatus()
}

class SetSusfsIntegrationEnabledUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setIntegrationEnabled(enabled)
}
