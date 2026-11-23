package com.example.domain

sealed class InnValidationResult {
    data class Valid(val normalized: String, val isIp: Boolean) : InnValidationResult()
    data class Invalid(val message: String) : InnValidationResult()
}

object InnValidator {
    private val weights10 = intArrayOf(2, 4, 10, 3, 5, 9, 4, 6, 8)
    private val weights12Step1 = intArrayOf(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
    private val weights12Step2 = intArrayOf(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)

    fun validate(rawInn: String): InnValidationResult {
        val inn = rawInn.filter { it.isDigit() }
        if (inn.isEmpty()) {
            return InnValidationResult.Invalid("Введите ИНН (10 цифр для ООО или 12 для ИП)")
        }
        if (inn.length != 10 && inn.length != 12) {
            return InnValidationResult.Invalid("ИНН должен содержать 10 (ЮЛ) или 12 (ИП) цифр")
        }
        val digits = inn.map { it.digitToInt() }
        if (inn.length == 10) {
            val sum = weights10.indices.sumOf { i -> weights10[i] * digits[i] }
            val control = (sum % 11) % 10
            if (control != digits[9]) {
                return InnValidationResult.Invalid("Некорректное контрольное число ИНН")
            }
            return InnValidationResult.Valid(inn, isIp = false)
        } else {
            val sum11 = weights12Step1.indices.sumOf { i -> weights12Step1[i] * digits[i] }
            val c11 = (sum11 % 11) % 10
            val sum12 = weights12Step2.indices.sumOf { i -> weights12Step2[i] * digits[i] }
            val c12 = (sum12 % 11) % 10
            if (c11 != digits[10] || c12 != digits[11]) {
                return InnValidationResult.Invalid("Некорректное контрольное число ИНН ИП")
            }
            return InnValidationResult.Valid(inn, isIp = true)
        }
    }

    fun isValidBik(rawBik: String): Boolean {
        val clean = rawBik.filter { it.isDigit() }
        return clean.length == 9
    }
}
