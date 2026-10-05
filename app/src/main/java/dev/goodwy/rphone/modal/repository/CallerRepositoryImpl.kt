package dev.goodwy.rphone.modal.repository

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.ICallerRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository

class CallerRepositoryImpl(
    private val contactsRepository: IContactsRepository
) : ICallerRepository {
    override suspend fun getContactByNumber(number: String): Contact? {
        return contactsRepository.getContactByNumber(number)
    }
}
