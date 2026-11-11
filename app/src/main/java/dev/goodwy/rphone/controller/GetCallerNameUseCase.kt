package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.util.alias.CallerAliasManager
import dev.goodwy.rphone.modal.data.CallerMetadata
import dev.goodwy.rphone.modal.`interface`.ICallerRepository

class GetCallerNameUseCase(private val repository: ICallerRepository) {
    suspend operator fun invoke(incomingNumber: String, cnamName: String?): CallerMetadata {
        val alias = CallerAliasManager.getAliasForNumber(incomingNumber)
        if (alias != null) {
            return CallerMetadata(
                number = if (alias.hideNumber) "••••••••" else incomingNumber,
                name = alias.aliasName,
                isLocalContact = true,
                photoUri = null
            )
        }

        val localContact = repository.getContactByNumber(incomingNumber)
        
        val hasLocalName = localContact != null && (
            localContact.givenName.isNotBlank() || 
            localContact.familyName.isNotBlank() || 
            localContact.nickname.isNotBlank() || 
            localContact.company.isNotBlank()
        )

        return when {
            hasLocalName -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            !cnamName.isNullOrBlank() -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = cnamName,
                    isLocalContact = false
                )
            }
            localContact != null -> {
                // We have a contact but no name, maybe just use its display name (which might be the number)
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            else -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = incomingNumber.ifBlank { "Unknown Number" },
                    isLocalContact = false
                )
            }
        }
    }
}
