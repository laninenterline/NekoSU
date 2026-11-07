package com.nekosu.domain.usecase

import com.nekosu.data.flash.FlashRepository
import com.nekosu.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
