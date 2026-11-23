package com.example.domain

import com.example.data.model.AccountingDocument
import com.example.data.model.DocumentLineItem
import java.math.BigDecimal
import java.math.RoundingMode

object DocumentCalculator {

    fun recalculateLineItem(
        name: String,
        unitCode: String,
        unitName: String,
        quantity: Double,
        priceWithoutVat: Double,
        vatRate: Int,
        countryCode: String,
        countryName: String,
        customsDecl: String
    ): DocumentLineItem {
        val q = BigDecimal.valueOf(quantity.coerceAtLeast(0.0))
        val p = BigDecimal.valueOf(priceWithoutVat.coerceAtLeast(0.0))
        val net = q.multiply(p).setScale(2, RoundingMode.HALF_UP)
        val vat = if (vatRate > 0) {
            net.multiply(BigDecimal.valueOf(vatRate.toLong()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }
        val total = net.add(vat).setScale(2, RoundingMode.HALF_UP)

        return DocumentLineItem(
            name = name,
            unitCode = unitCode.ifBlank { "-" },
            unitName = unitName.ifBlank { "шт" },
            quantity = quantity.coerceAtLeast(0.0),
            priceWithoutVat = p.setScale(2, RoundingMode.HALF_UP).toDouble(),
            vatRate = vatRate,
            vatAmount = vat.toDouble(),
            totalWithVat = total.toDouble(),
            countryCode = countryCode.ifBlank { "-" },
            countryName = countryName.ifBlank { "-" },
            customsDecl = customsDecl.ifBlank { "-" }
        )
    }

    fun recalculateDocument(doc: AccountingDocument): AccountingDocument {
        val recalculatedItems = doc.items.map { item ->
            recalculateLineItem(
                name = item.name,
                unitCode = item.unitCode,
                unitName = item.unitName,
                quantity = item.quantity,
                priceWithoutVat = item.priceWithoutVat,
                vatRate = item.vatRate,
                countryCode = item.countryCode,
                countryName = item.countryName,
                customsDecl = item.customsDecl
            )
        }
        val subtotal = recalculatedItems.fold(BigDecimal.ZERO) { acc, item ->
            val lineNet = BigDecimal.valueOf(item.quantity)
                .multiply(BigDecimal.valueOf(item.priceWithoutVat))
                .setScale(2, RoundingMode.HALF_UP)
            acc.add(lineNet)
        }.setScale(2, RoundingMode.HALF_UP)

        val totalVat = recalculatedItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(BigDecimal.valueOf(item.vatAmount))
        }.setScale(2, RoundingMode.HALF_UP)

        val totalWithVat = recalculatedItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(BigDecimal.valueOf(item.totalWithVat))
        }.setScale(2, RoundingMode.HALF_UP)

        val totalQty = recalculatedItems.sumOf { it.quantity }
        val words = RussianNumberToWords.formatRublesInWords(totalWithVat.toDouble())

        return doc.copy(
            items = recalculatedItems,
            subtotalWithoutVat = subtotal.toDouble(),
            totalVat = totalVat.toDouble(),
            totalWithVat = totalWithVat.toDouble(),
            totalQuantity = totalQty,
            amountInWords = words
        )
    }
}
