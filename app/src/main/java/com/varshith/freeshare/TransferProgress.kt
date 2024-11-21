package com.varshith.freeshare

data class TransferProgress(
    val isTransferring: Boolean = false,
    val progress: Float = 0f,
    val type: TransferType = TransferType.NONE
)

enum class TransferType {
    UPLOAD, DOWNLOAD, NONE
}