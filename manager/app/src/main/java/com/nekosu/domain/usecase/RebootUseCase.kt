package com.nekosu.domain.usecase

import com.nekosu.data.application.ApplicationControlRepository

class RebootUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(reason: String = "") = repository.reboot(reason)
}
