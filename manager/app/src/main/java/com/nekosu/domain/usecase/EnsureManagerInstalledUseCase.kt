package com.nekosu.domain.usecase

import com.nekosu.data.application.ApplicationControlRepository

class EnsureManagerInstalledUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.ensureManagerInstalled()
}
