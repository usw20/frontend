package com.cookandroid.phantom.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TokenDataStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "phantom_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }

    suspend fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    suspend fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    suspend fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun getTokenFlow(): Flow<String?> = flow {
        emit(getToken())
    }
}