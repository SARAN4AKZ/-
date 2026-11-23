package com.example.data.model

enum class DocumentType(val label: String, val shortLabel: String) {
    INVOICE("Счёт-фактура", "СФ"),
    TORG12("Накладная ТОРГ-12", "ТОРГ-12");

    companion object {
        fun fromRaw(raw: String?): DocumentType =
            entries.firstOrNull { it.name == raw } ?: INVOICE
    }
}

enum class DocumentStatus(val label: String) {
    DRAFT("Черновик"),
    ISSUED("Проведён");

    companion object {
        fun fromRaw(raw: String?): DocumentStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

enum class VatRate(val percent: Int, val label: String) {
    RATE_20(20, "20%"),
    RATE_10(10, "10%"),
    RATE_7(7, "7%"),
    RATE_5(5, "5%"),
    RATE_0(0, "0%"),
    NO_VAT(-1, "Без НДС");

    companion object {
        fun fromPercent(percent: Int): VatRate =
            entries.firstOrNull { it.percent == percent } ?: RATE_20
    }
}

enum class CreateMode(val label: String, val description: String) {
    BOTH("Комплект (СФ + ТОРГ-12)", "Создаёт оба документа с общими товарами и реквизитами"),
    INVOICE("Только Счёт-фактура", "Форма по Постановлению Правительства РФ № 1137"),
    TORG12("Только ТОРГ-12", "Унифицированная форма № ТОРГ-12 (ОКУД 0330212)")
}

data class UnitPreset(val code: String, val name: String)

val STANDARD_UNIT_PRESETS = listOf(
    UnitPreset("796", "шт"),
    UnitPreset("778", "упак"),
    UnitPreset("166", "кг"),
    UnitPreset("006", "м"),
    UnitPreset("112", "л"),
    UnitPreset("356", "час"),
    UnitPreset("-", "усл")
)

data class OrganizationProfile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val inn: String = "",
    val kpp: String = "",
    val ogrn: String = "",
    val address: String = "",
    val phone: String = "",
    val bankName: String = "",
    val bik: String = "",
    val checkingAccount: String = "",
    val correspondentAccount: String = "",
    val directorTitle: String = "Генеральный директор",
    val directorName: String = "",
    val accountantName: String = "",
    val defaultVatRate: Int = 20,
    val defaultProfile: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class Counterparty(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val inn: String = "",
    val kpp: String = "",
    val ogrn: String = "",
    val address: String = "",
    val phone: String = "",
    val bankName: String = "",
    val bik: String = "",
    val checkingAccount: String = "",
    val correspondentAccount: String = "",
    val directorName: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class DocumentLineItem(
    val name: String = "",
    val unitCode: String = "796",
    val unitName: String = "шт",
    val quantity: Double = 1.0,
    val priceWithoutVat: Double = 0.0,
    val vatRate: Int = 20,
    val vatAmount: Double = 0.0,
    val totalWithVat: Double = 0.0,
    val countryCode: String = "643",
    val countryName: String = "Россия",
    val customsDecl: String = "-"
)

data class AccountingDocument(
    val id: String = "",
    val userId: String = "",
    val docType: String = DocumentType.INVOICE.name,
    val docNumber: String = "1",
    val docDate: String = "",
    val basisDoc: String = "",
    val paymentDoc: String = "",
    val status: String = DocumentStatus.DRAFT.name,
    val sellerName: String = "",
    val sellerInn: String = "",
    val sellerKpp: String = "",
    val sellerOgrn: String = "",
    val sellerAddress: String = "",
    val sellerPhone: String = "",
    val sellerBankName: String = "",
    val sellerBik: String = "",
    val sellerCheckingAccount: String = "",
    val sellerCorrAccount: String = "",
    val sellerDirectorTitle: String = "Генеральный директор",
    val sellerDirectorName: String = "",
    val sellerAccountantName: String = "",
    val buyerName: String = "",
    val buyerInn: String = "",
    val buyerKpp: String = "",
    val buyerOgrn: String = "",
    val buyerAddress: String = "",
    val buyerPhone: String = "",
    val buyerBankName: String = "",
    val buyerBik: String = "",
    val buyerCheckingAccount: String = "",
    val buyerCorrAccount: String = "",
    val consignorInfo: String = "Он же",
    val consigneeInfo: String = "Он же",
    val items: List<DocumentLineItem> = emptyList(),
    val subtotalWithoutVat: Double = 0.0,
    val totalVat: Double = 0.0,
    val totalWithVat: Double = 0.0,
    val totalQuantity: Double = 0.0,
    val totalPlaces: Int = 1,
    val totalWeightKg: Double = 0.0,
    val amountInWords: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val resolvedType: DocumentType get() = DocumentType.fromRaw(docType)
    val resolvedStatus: DocumentStatus get() = DocumentStatus.fromRaw(status)
}

data class InnLookupResult(
    val name: String,
    val fullName: String,
    val inn: String,
    val kpp: String,
    val ogrn: String,
    val address: String,
    val directorTitle: String,
    val directorName: String,
    val isIndividualEntrepreneur: Boolean,
    val registrationDate: String
)

data class BikLookupResult(
    val bik: String,
    val bankName: String,
    val correspondentAccount: String,
    val city: String
)
