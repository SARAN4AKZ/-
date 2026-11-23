package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AccountingDocument
import com.example.data.model.Counterparty
import com.example.data.model.DocumentLineItem
import com.example.data.model.OrganizationProfile
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val displayName: String,
    val passwordSaltBase64: String,
    val passwordHashBase64: String,
    val createdAtMillis: Long
)

@Entity(
    tableName = "organizations",
    indices = [Index(value = ["userId"])]
)
data class OrganizationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val inn: String,
    val kpp: String,
    val ogrn: String,
    val address: String,
    val phone: String,
    val bankName: String,
    val bik: String,
    val checkingAccount: String,
    val correspondentAccount: String,
    val directorTitle: String,
    val directorName: String,
    val accountantName: String,
    val defaultVatRate: Int,
    val defaultProfile: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): OrganizationProfile = OrganizationProfile(
        id = id,
        userId = userId,
        name = name,
        inn = inn,
        kpp = kpp,
        ogrn = ogrn,
        address = address,
        phone = phone,
        bankName = bankName,
        bik = bik,
        checkingAccount = checkingAccount,
        correspondentAccount = correspondentAccount,
        directorTitle = directorTitle,
        directorName = directorName,
        accountantName = accountantName,
        defaultVatRate = defaultVatRate,
        defaultProfile = defaultProfile,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(model: OrganizationProfile): OrganizationEntity = OrganizationEntity(
            id = model.id,
            userId = model.userId,
            name = model.name,
            inn = model.inn,
            kpp = model.kpp,
            ogrn = model.ogrn,
            address = model.address,
            phone = model.phone,
            bankName = model.bankName,
            bik = model.bik,
            checkingAccount = model.checkingAccount,
            correspondentAccount = model.correspondentAccount,
            directorTitle = model.directorTitle,
            directorName = model.directorName,
            accountantName = model.accountantName,
            defaultVatRate = model.defaultVatRate,
            defaultProfile = model.defaultProfile,
            createdAtMillis = model.createdAtMillis,
            updatedAtMillis = model.updatedAtMillis
        )
    }
}

@Entity(
    tableName = "counterparties",
    indices = [Index(value = ["userId"])]
)
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val inn: String,
    val kpp: String,
    val ogrn: String,
    val address: String,
    val phone: String,
    val bankName: String,
    val bik: String,
    val checkingAccount: String,
    val correspondentAccount: String,
    val directorName: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): Counterparty = Counterparty(
        id = id,
        userId = userId,
        name = name,
        inn = inn,
        kpp = kpp,
        ogrn = ogrn,
        address = address,
        phone = phone,
        bankName = bankName,
        bik = bik,
        checkingAccount = checkingAccount,
        correspondentAccount = correspondentAccount,
        directorName = directorName,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(model: Counterparty): CounterpartyEntity = CounterpartyEntity(
            id = model.id,
            userId = model.userId,
            name = model.name,
            inn = model.inn,
            kpp = model.kpp,
            ogrn = model.ogrn,
            address = model.address,
            phone = model.phone,
            bankName = model.bankName,
            bik = model.bik,
            checkingAccount = model.checkingAccount,
            correspondentAccount = model.correspondentAccount,
            directorName = model.directorName,
            createdAtMillis = model.createdAtMillis,
            updatedAtMillis = model.updatedAtMillis
        )
    }
}

@Entity(
    tableName = "documents",
    indices = [Index(value = ["userId"])]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val docType: String,
    val docNumber: String,
    val docDate: String,
    val basisDoc: String,
    val paymentDoc: String,
    val status: String,
    val sellerName: String,
    val sellerInn: String,
    val sellerKpp: String,
    val sellerOgrn: String,
    val sellerAddress: String,
    val sellerPhone: String,
    val sellerBankName: String,
    val sellerBik: String,
    val sellerCheckingAccount: String,
    val sellerCorrAccount: String,
    val sellerDirectorTitle: String,
    val sellerDirectorName: String,
    val sellerAccountantName: String,
    val buyerName: String,
    val buyerInn: String,
    val buyerKpp: String,
    val buyerOgrn: String,
    val buyerAddress: String,
    val buyerPhone: String,
    val buyerBankName: String,
    val buyerBik: String,
    val buyerCheckingAccount: String,
    val buyerCorrAccount: String,
    val consignorInfo: String,
    val consigneeInfo: String,
    val itemsJson: String,
    val subtotalWithoutVat: Double,
    val totalVat: Double,
    val totalWithVat: Double,
    val totalQuantity: Double,
    val totalPlaces: Int,
    val totalWeightKg: Double,
    val amountInWords: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): AccountingDocument = AccountingDocument(
        id = id,
        userId = userId,
        docType = docType,
        docNumber = docNumber,
        docDate = docDate,
        basisDoc = basisDoc,
        paymentDoc = paymentDoc,
        status = status,
        sellerName = sellerName,
        sellerInn = sellerInn,
        sellerKpp = sellerKpp,
        sellerOgrn = sellerOgrn,
        sellerAddress = sellerAddress,
        sellerPhone = sellerPhone,
        sellerBankName = sellerBankName,
        sellerBik = sellerBik,
        sellerCheckingAccount = sellerCheckingAccount,
        sellerCorrAccount = sellerCorrAccount,
        sellerDirectorTitle = sellerDirectorTitle,
        sellerDirectorName = sellerDirectorName,
        sellerAccountantName = sellerAccountantName,
        buyerName = buyerName,
        buyerInn = buyerInn,
        buyerKpp = buyerKpp,
        buyerOgrn = buyerOgrn,
        buyerAddress = buyerAddress,
        buyerPhone = buyerPhone,
        buyerBankName = buyerBankName,
        buyerBik = buyerBik,
        buyerCheckingAccount = buyerCheckingAccount,
        buyerCorrAccount = buyerCorrAccount,
        consignorInfo = consignorInfo,
        consigneeInfo = consigneeInfo,
        items = deserializeItems(itemsJson),
        subtotalWithoutVat = subtotalWithoutVat,
        totalVat = totalVat,
        totalWithVat = totalWithVat,
        totalQuantity = totalQuantity,
        totalPlaces = totalPlaces,
        totalWeightKg = totalWeightKg,
        amountInWords = amountInWords,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(model: AccountingDocument): DocumentEntity = DocumentEntity(
            id = model.id,
            userId = model.userId,
            docType = model.docType,
            docNumber = model.docNumber,
            docDate = model.docDate,
            basisDoc = model.basisDoc,
            paymentDoc = model.paymentDoc,
            status = model.status,
            sellerName = model.sellerName,
            sellerInn = model.sellerInn,
            sellerKpp = model.sellerKpp,
            sellerOgrn = model.sellerOgrn,
            sellerAddress = model.sellerAddress,
            sellerPhone = model.sellerPhone,
            sellerBankName = model.sellerBankName,
            sellerBik = model.sellerBik,
            sellerCheckingAccount = model.sellerCheckingAccount,
            sellerCorrAccount = model.sellerCorrAccount,
            sellerDirectorTitle = model.sellerDirectorTitle,
            sellerDirectorName = model.sellerDirectorName,
            sellerAccountantName = model.sellerAccountantName,
            buyerName = model.buyerName,
            buyerInn = model.buyerInn,
            buyerKpp = model.buyerKpp,
            buyerOgrn = model.buyerOgrn,
            buyerAddress = model.buyerAddress,
            buyerPhone = model.buyerPhone,
            buyerBankName = model.buyerBankName,
            buyerBik = model.buyerBik,
            buyerCheckingAccount = model.buyerCheckingAccount,
            buyerCorrAccount = model.buyerCorrAccount,
            consignorInfo = model.consignorInfo,
            consigneeInfo = model.consigneeInfo,
            itemsJson = serializeItems(model.items),
            subtotalWithoutVat = model.subtotalWithoutVat,
            totalVat = model.totalVat,
            totalWithVat = model.totalWithVat,
            totalQuantity = model.totalQuantity,
            totalPlaces = model.totalPlaces,
            totalWeightKg = model.totalWeightKg,
            amountInWords = model.amountInWords,
            createdAtMillis = model.createdAtMillis,
            updatedAtMillis = model.updatedAtMillis
        )

        private fun serializeItems(items: List<DocumentLineItem>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("name", item.name)
                        put("unitCode", item.unitCode)
                        put("unitName", item.unitName)
                        put("quantity", item.quantity)
                        put("priceWithoutVat", item.priceWithoutVat)
                        put("vatRate", item.vatRate)
                        put("vatAmount", item.vatAmount)
                        put("totalWithVat", item.totalWithVat)
                        put("countryCode", item.countryCode)
                        put("countryName", item.countryName)
                        put("customsDecl", item.customsDecl)
                    }
                )
            }
            return arr.toString()
        }

        private fun deserializeItems(json: String): List<DocumentLineItem> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            DocumentLineItem(
                                name = obj.optString("name"),
                                unitCode = obj.optString("unitCode", "796"),
                                unitName = obj.optString("unitName", "шт"),
                                quantity = obj.optDouble("quantity", 1.0),
                                priceWithoutVat = obj.optDouble("priceWithoutVat", 0.0),
                                vatRate = obj.optInt("vatRate", 20),
                                vatAmount = obj.optDouble("vatAmount", 0.0),
                                totalWithVat = obj.optDouble("totalWithVat", 0.0),
                                countryCode = obj.optString("countryCode", "643"),
                                countryName = obj.optString("countryName", "Россия"),
                                customsDecl = obj.optString("customsDecl", "-")
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
