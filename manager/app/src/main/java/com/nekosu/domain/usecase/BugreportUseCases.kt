package com.nekosu.domain.usecase

import java.io.File
import com.nekosu.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
