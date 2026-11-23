package com.example.data.remote

import com.example.data.model.BikLookupResult
import com.example.data.model.InnLookupResult
import com.example.domain.InnValidationResult
import com.example.domain.InnValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FnsEgrulService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun lookupByInn(rawInn: String): Result<InnLookupResult> = withContext(Dispatchers.IO) {
        val validation = InnValidator.validate(rawInn)
        if (validation is InnValidationResult.Invalid) {
            return@withContext Result.failure(IllegalArgumentException(validation.message))
        }
        val inn = (validation as InnValidationResult.Valid).normalized

        try {
            val formBody = FormBody.Builder()
                .add("vyp3CaptchaToken", "")
                .add("page", "")
                .add("query", inn)
                .add("region", "")
                .add("PreventChromeAutocomplete", "")
                .build()

            val postReq = Request.Builder()
                .url("https://egrul.nalog.ru/")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "https://egrul.nalog.ru/index.html")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val token = client.newCall(postReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Сервис ФНС временно недоступен (код ${resp.code}). Введите реквизиты вручную или повторите позже.")
                    )
                }
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                if (json.optBoolean("captchaRequired", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("ФНС запросила проверку. Повторите поиск через несколько секунд или заполните реквизиты вручную.")
                    )
                }
                json.optString("t")
            }

            if (token.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Не удалось получить токен поиска ФНС.")
                )
            }

            // Poll up to 4 times if FNS returns "wait" status
            var rowsJson: JSONObject? = null
            for (attempt in 0 until 4) {
                if (attempt > 0) delay(450L)
                val getReq = Request.Builder()
                    .url("https://egrul.nalog.ru/search-result/$token")
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Referer", "https://egrul.nalog.ru/index.html")
                    .build()

                val parsed = client.newCall(getReq).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else JSONObject(resp.body?.string().orEmpty())
                }
                if (parsed != null && parsed.optString("status") != "wait") {
                    rowsJson = parsed
                    break
                }
            }

            val rows = rowsJson?.optJSONArray("rows")
            if (rows == null || rows.length() == 0) {
                return@withContext Result.failure(
                    NoSuchElementException("По ИНН $inn организация или ИП в ЕГРЮЛ/ЕГРИП не найдены.")
                )
            }

            // Prefer active record (without termination date 'e') or exact INN match
            var chosen = rows.getJSONObject(0)
            for (i in 0 until rows.length()) {
                val candidate = rows.getJSONObject(i)
                if (candidate.optString("i") == inn && candidate.optString("e").isBlank()) {
                    chosen = candidate
                    break
                }
            }

            val kind = chosen.optString("k")
            val isIp = kind == "fl" || inn.length == 12
            val fullName = decodeHtml(chosen.optString("n")).trim()
            val shortName = decodeHtml(chosen.optString("c")).trim()
            val displayName = when {
                isIp -> {
                    val base = shortName.ifBlank { fullName }
                    if (base.startsWith("ИП ", ignoreCase = true) || base.startsWith("Индивидуальный предприниматель", ignoreCase = true)) {
                        base
                    } else {
                        "ИП $base"
                    }
                }
                shortName.isNotBlank() -> shortName
                else -> fullName
            }

            val directorRaw = decodeHtml(chosen.optString("g")).trim()
            val (dirTitle, dirName) = if (isIp) {
                "Индивидуальный предприниматель" to fullName
            } else if (directorRaw.contains(":")) {
                val idx = directorRaw.indexOf(":")
                directorRaw.substring(0, idx).trim() to directorRaw.substring(idx + 1).trim()
            } else {
                "Генеральный директор" to directorRaw
            }

            Result.success(
                InnLookupResult(
                    name = displayName,
                    fullName = fullName.ifBlank { displayName },
                    inn = chosen.optString("i").ifBlank { inn },
                    kpp = if (isIp) "" else chosen.optString("p"),
                    ogrn = chosen.optString("o"),
                    address = decodeHtml(chosen.optString("a")).trim(),
                    directorTitle = dirTitle.ifBlank { "Генеральный директор" },
                    directorName = dirName,
                    isIndividualEntrepreneur = isIp,
                    registrationDate = chosen.optString("r")
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Ошибка запроса к ЕГРЮЛ/ЕГРИП: ${e.localizedMessage ?: "проверьте подключение к интернету"}")
            )
        }
    }

    suspend fun lookupByBik(rawBik: String): Result<BikLookupResult> = withContext(Dispatchers.IO) {
        val bik = rawBik.filter { it.isDigit() }
        if (bik.length != 9) {
            return@withContext Result.failure(IllegalArgumentException("БИК должен содержать 9 цифр"))
        }
        try {
            val req = Request.Builder()
                .url("https://bik-info.ru/api.html?type=json&bik=$bik")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Справочник БИК недоступен (${resp.code})"))
                }
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                if (json.has("error")) {
                    return@withContext Result.failure(NoSuchElementException("Банк с БИК $bik не найден"))
                }
                val name = decodeHtml(json.optString("name")).trim()
                val ks = json.optString("ks").trim()
                val city = decodeHtml(json.optString("city")).trim()
                if (name.isBlank()) {
                    return@withContext Result.failure(NoSuchElementException("Банк с БИК $bik не найден"))
                }
                Result.success(
                    BikLookupResult(
                        bik = bik,
                        bankName = if (city.isNotBlank() && !name.contains(city, ignoreCase = true)) "$name, г. $city" else name,
                        correspondentAccount = ks,
                        city = city
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Ошибка поиска БИК: ${e.localizedMessage ?: "нет сети"}"))
        }
    }

    private fun decodeHtml(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#34;", "\"")
    }
}
