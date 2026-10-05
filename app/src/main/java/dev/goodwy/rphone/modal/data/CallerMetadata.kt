package dev.goodwy.rphone.modal.data

data class CallerMetadata(
    val number: String,
    val name: String,
    val isLocalContact: Boolean,
    val photoUri: String? = null
)
