package com.nekosu.domain.usecase

import com.nekosu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}
