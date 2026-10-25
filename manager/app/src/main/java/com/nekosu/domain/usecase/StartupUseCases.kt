package com.nekosu.domain.usecase

import com.nekosu.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
