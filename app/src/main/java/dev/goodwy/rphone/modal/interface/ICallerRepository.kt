package dev.goodwy.rphone.modal.`interface`

import dev.goodwy.rphone.modal.data.Contact

interface ICallerRepository {
    suspend fun getContactByNumber(number: String): Contact?
}
