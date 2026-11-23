package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object RussianNumberToWords {

    private val unitsMale = arrayOf(
        "", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    )
    private val unitsFemale = arrayOf(
        "", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    )
    private val teens = arrayOf(
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    )
    private val tens = arrayOf(
        "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
        "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    )
    private val hundreds = arrayOf(
        "", "сто", "двести", "триста", "четыреста", "пятьсот",
        "шестьсот", "семьсот", "восемьсот", "девятьсот"
    )

    private data class Scale(val one: String, val few: String, val many: String, val female: Boolean)

    private val scales = listOf(
        Scale("рубль", "рубля", "рублей", female = false),
        Scale("тысяча", "тысячи", "тысяч", female = true),
        Scale("миллион", "миллиона", "миллионов", female = false),
        Scale("миллиард", "миллиарда", "миллиардов", female = false)
    )

    fun formatRublesInWords(amount: Double): String {
        val bd = BigDecimal.valueOf(amount.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
        val rubles = bd.toLong()
        val kopecks = bd.remainder(BigDecimal.ONE).movePointRight(2).toInt()

        val parts = mutableListOf<String>()
        if (rubles == 0L) {
            parts.add("ноль рублей")
        } else {
            var remaining = rubles
            var scaleIdx = 0
            val groupWords = mutableListOf<String>()
            while (remaining > 0 && scaleIdx < scales.size) {
                val triplet = (remaining % 1000).toInt()
                remaining /= 1000
                val scale = scales[scaleIdx]
                if (triplet > 0 || scaleIdx == 0) {
                    val words = tripletToWords(triplet, scale.female)
                    val unitWord = morph(triplet, scale.one, scale.few, scale.many)
                    val combined = if (words.isNotEmpty()) "$words $unitWord" else unitWord
                    groupWords.add(0, combined)
                }
                scaleIdx++
            }
            parts.addAll(groupWords)
        }

        val kopFormatted = kopecks.toString().padStart(2, '0')
        val kopWord = morph(kopecks, "копейка", "копейки", "копеек")
        val raw = "${parts.joinToString(" ")} $kopFormatted $kopWord".trim()
        return raw.replaceFirstChar { it.titlecase(Locale("ru")) }
    }

    private fun tripletToWords(n: Int, female: Boolean): String {
        val result = mutableListOf<String>()
        val h = n / 100
        val t = (n % 100) / 10
        val u = n % 10
        if (h > 0) result.add(hundreds[h])
        if (t == 1) {
            result.add(teens[u])
        } else {
            if (t > 1) result.add(tens[t])
            if (u > 0) result.add(if (female) unitsFemale[u] else unitsMale[u])
        }
        return result.joinToString(" ")
    }

    private fun morph(n: Int, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..19 -> many
            n10 == 1 -> one
            n10 in 2..4 -> few
            else -> many
        }
    }

    fun formatMoney(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale("ru")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        return DecimalFormat("#,##0.00", symbols).format(amount)
    }

    fun formatQuantity(qty: Double): String {
        val bd = BigDecimal.valueOf(qty).stripTrailingZeros()
        return if (bd.scale() <= 0) {
            bd.toPlainString()
        } else {
            val symbols = DecimalFormatSymbols(Locale("ru")).apply {
                decimalSeparator = ','
                groupingSeparator = ' '
            }
            DecimalFormat("#,##0.###", symbols).format(qty)
        }
    }
}
