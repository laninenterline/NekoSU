package com.nekosu.domain.model

sealed interface FlashOperation {
    data class Module(val uri: String) : FlashOperation
}

sealed interface FlashOperationUpdate {
    data class Output(val line: String) : FlashOperationUpdate
    data class ErrorOutput(val line: String) : FlashOperationUpdate
    data class Completed(val showReboot: Boolean, val code: Int) : FlashOperationUpdate
}
