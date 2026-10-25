package com.nekosu.domain.usecase

import com.nekosu.data.flash.FlashRepository
import com.nekosu.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) = repository.getInstallEnvironment(forceRefresh)
}
