package com.nekosu.domain.usecase

import com.nekosu.data.update.ManagerUpdateRepository
import com.nekosu.domain.model.ManagerUpdateChannel
import com.nekosu.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? = when (channel) {
        ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
        ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
    }
}
