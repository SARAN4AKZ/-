package com.example.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.authDataStore by preferencesDataStore(name = "local_session_prefs")

data class LocalSession(
    val username: String,
    val displayName: String
)

class LocalAuthRepository(
    private val context: Context,
    private val userDao: UserDao
) {
    private val sessionUsernameKey = stringPreferencesKey("session_username")
    private val sessionDisplayKey = stringPreferencesKey("session_display_name")

    val currentSession: Flow<LocalSession?> = context.authDataStore.data.map { prefs ->
        val username = prefs[sessionUsernameKey]
        if (username.isNullOrBlank()) null
        else LocalSession(
            username = username,
            displayName = prefs[sessionDisplayKey] ?: username
        )
    }

    val hasRegisteredUsers: Flow<Boolean> = userDao.observeUserCount().map { it > 0 }

    suspend fun register(
        rawUsername: String,
        rawPassword: String
    ): Result<LocalSession> = withContext(Dispatchers.IO) {
        val display = rawUsername.trim()
        val normalized = display.lowercase()
        if (normalized.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Логин должен содержать минимум 3 символа"))
        }
        if (rawPassword.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Пароль должен содержать минимум 4 символа"))
        }
        val existing = userDao.getUser(normalized)
        if (existing != null) {
            return@withContext Result.failure(IllegalStateException("Пользователь с таким логином уже существует"))
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(rawPassword, salt)
        val user = UserEntity(
            username = normalized,
            displayName = display,
            passwordSaltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            passwordHashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            createdAtMillis = System.currentTimeMillis()
        )
        userDao.insertUser(user)
        saveSession(normalized, display)
        Result.success(LocalSession(normalized, display))
    }

    suspend fun login(
        rawUsername: String,
        rawPassword: String
    ): Result<LocalSession> = withContext(Dispatchers.IO) {
        val normalized = rawUsername.trim().lowercase()
        if (normalized.isBlank() || rawPassword.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите логин и пароль"))
        }
        val user = userDao.getUser(normalized)
            ?: return@withContext Result.failure(IllegalArgumentException("Неверный логин или пароль"))

        val salt = Base64.decode(user.passwordSaltBase64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(user.passwordHashBase64, Base64.NO_WRAP)
        val actualHash = pbkdf2(rawPassword, salt)

        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            return@withContext Result.failure(IllegalArgumentException("Неверный логин или пароль"))
        }

        saveSession(user.username, user.displayName)
        Result.success(LocalSession(user.username, user.displayName))
    }

    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(sessionUsernameKey)
            prefs.remove(sessionDisplayKey)
        }
    }

    private suspend fun saveSession(username: String, displayName: String) {
        context.authDataStore.edit { prefs ->
            prefs[sessionUsernameKey] = username
            prefs[sessionDisplayKey] = displayName
        }
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private companion object {
        const val PBKDF2_ITERATIONS = 100_000
        const val KEY_LENGTH_BITS = 256
    }
}
