package com.example.data.local

import com.example.data.model.AccountingDocument
import com.example.data.model.Counterparty
import com.example.data.model.DocumentStatus
import com.example.data.model.OrganizationProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AccountingRepository(
    private val db: AppDatabase
) {
    private val orgDao = db.organizationDao()
    private val partyDao = db.counterpartyDao()
    private val docDao = db.documentDao()

    fun observeOrganizations(userId: String): Flow<List<OrganizationProfile>> =
        orgDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    fun observeCounterparties(userId: String): Flow<List<Counterparty>> =
        partyDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    fun observeDocuments(userId: String): Flow<List<AccountingDocument>> =
        docDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    suspend fun saveOrganization(
        userId: String,
        org: OrganizationProfile
    ): Result<String> = try {
        require(org.name.trim().isNotEmpty()) { "Название организации не может быть пустым" }
        require(org.inn.trim().length in 10..12) { "ИНН должен содержать 10 или 12 цифр" }

        val now = System.currentTimeMillis()
        val id = org.id.ifBlank { UUID.randomUUID().toString() }
        val entity = OrganizationEntity.fromDomain(
            org.copy(
                id = id,
                userId = userId,
                name = org.name.trim(),
                inn = org.inn.trim(),
                createdAtMillis = if (org.createdAtMillis > 0L) org.createdAtMillis else now,
                updatedAtMillis = now
            )
        )
        if (entity.defaultProfile) {
            orgDao.clearDefaultExcept(userId, id)
        }
        orgDao.upsert(entity)
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteOrganization(userId: String, orgId: String): Result<Unit> = try {
        require(orgId.isNotBlank()) { "ID организации пуст" }
        orgDao.deleteById(userId, orgId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveCounterparty(
        userId: String,
        party: Counterparty
    ): Result<String> = try {
        require(party.name.trim().isNotEmpty()) { "Название контрагента не может быть пустым" }
        require(party.inn.trim().length in 10..12) { "ИНН должен содержать 10 или 12 цифр" }

        val now = System.currentTimeMillis()
        val id = party.id.ifBlank { UUID.randomUUID().toString() }
        val entity = CounterpartyEntity.fromDomain(
            party.copy(
                id = id,
                userId = userId,
                name = party.name.trim(),
                inn = party.inn.trim(),
                createdAtMillis = if (party.createdAtMillis > 0L) party.createdAtMillis else now,
                updatedAtMillis = now
            )
        )
        partyDao.upsert(entity)
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteCounterparty(userId: String, partyId: String): Result<Unit> = try {
        require(partyId.isNotBlank()) { "ID контрагента пуст" }
        partyDao.deleteById(userId, partyId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveDocument(
        userId: String,
        doc: AccountingDocument,
        targetStatus: DocumentStatus = doc.resolvedStatus
    ): Result<String> = try {
        require(doc.docNumber.trim().isNotEmpty()) { "Укажите номер документа" }
        require(doc.docDate.trim().isNotEmpty()) { "Укажите дату документа" }
        require(doc.sellerName.trim().isNotEmpty()) { "Укажите продавца" }
        require(doc.sellerInn.trim().length in 10..12) { "Укажите корректный ИНН продавца" }
        require(doc.buyerName.trim().isNotEmpty()) { "Укажите покупателя" }
        require(doc.buyerInn.trim().length in 10..12) { "Укажите корректный ИНН покупателя" }
        require(doc.items.isNotEmpty() && doc.items.size <= 50) { "Добавьте от 1 до 50 позиций" }

        val now = System.currentTimeMillis()
        val id = doc.id.ifBlank { UUID.randomUUID().toString() }
        val entity = DocumentEntity.fromDomain(
            doc.copy(
                id = id,
                userId = userId,
                status = targetStatus.name,
                createdAtMillis = if (doc.createdAtMillis > 0L) doc.createdAtMillis else now,
                updatedAtMillis = now
            )
        )
        docDao.upsert(entity)
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun issueDocument(userId: String, docId: String): Result<Unit> = try {
        require(docId.isNotBlank()) { "ID документа пуст" }
        docDao.updateStatus(userId, docId, DocumentStatus.ISSUED.name, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteDocument(userId: String, docId: String): Result<Unit> = try {
        require(docId.isNotBlank()) { "ID документа пуст" }
        docDao.deleteById(userId, docId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
