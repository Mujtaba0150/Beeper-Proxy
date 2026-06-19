package com.beeperproxy

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

object AuthTokenManager {

    private const val PREFS_FILE = "beeper_proxy_secure_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val TOKEN_BYTE_LENGTH = 32

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreateToken(context: Context): String {
        val prefs = getPrefs(context)
        val existing = prefs.getString(KEY_AUTH_TOKEN, null)
        if (!existing.isNullOrEmpty()) return existing
        return generateAndSave(context)
    }

    fun refreshToken(context: Context): String {
        return generateAndSave(context)
    }

    fun validateToken(context: Context, token: String): Boolean {
        val prefs = getPrefs(context)
        val stored = prefs.getString(KEY_AUTH_TOKEN, null) ?: return false
        return stored == token
    }

    private fun generateAndSave(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
        return token
    }
}
