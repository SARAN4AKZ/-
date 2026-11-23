package com.example

import com.example.data.model.AccountingDocument
import com.example.data.model.DocumentLineItem
import com.example.domain.DocumentCalculator
import com.example.domain.InnValidationResult
import com.example.domain.InnValidator
import com.example.domain.RussianNumberToWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingDomainTest {

    @Test
    fun innValidator_acceptsValidLegalEntityAndIp() {
        val validLl = InnValidator.validate("7707083893") // Sberbank
        assertTrue(validLl is InnValidationResult.Valid)
        assertEquals(false, (validLl as InnValidationResult.Valid).isIp)

        val invalidLl = InnValidator.validate("7707083894")
        assertTrue(invalidLl is InnValidationResult.Invalid)
    }

    @Test
    fun russianNumberToWords_formatsRublesAndKopecks() {
        assertEquals(
            "Сто двадцать тысяч рублей 00 копеек",
            RussianNumberToWords.formatRublesInWords(120000.0)
        )
        assertEquals(
            "Одна тысяча двести тридцать один рубль 02 копейки",
            RussianNumberToWords.formatRublesInWords(1231.02)
        )
    }

    @Test
    fun documentCalculator_computesVatAndTotals() {
        val doc = DocumentCalculator.recalculateDocument(
            AccountingDocument(
                items = listOf(
                    DocumentLineItem(name = "Товар А", quantity = 2.0, priceWithoutVat = 1000.0, vatRate = 20),
                    DocumentLineItem(name = "Услуга Б", quantity = 1.0, priceWithoutVat = 500.0, vatRate = -1)
                )
            )
        )
        assertEquals(2500.0, doc.subtotalWithoutVat, 0.001)
        assertEquals(400.0, doc.totalVat, 0.001)
        assertEquals(2900.0, doc.totalWithVat, 0.001)
    }
}
