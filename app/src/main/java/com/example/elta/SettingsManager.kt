package com.example.elta

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val isCustom: Boolean = false,
    val id: Long = 0
)

enum class AppCurrency(val code: String, val symbol: String) {
    NONE("NONE", ""),
    EUR("EUR", "€"),
    USD("USD", "$"),
    GBP("GBP", "£"),
    JPY("JPY", "¥"),
    CAD("CAD", "CA$"),
    AUD("AUD", "AU$"),
    INR("INR", "₹"),
    CNY("CNY", "CN¥"),
    CHF("CHF", "CHF"),
    SGD("SGD", "SG$"),
    NZD("NZD", "NZ$"),
    BRL("BRL", "R$"),
    MXN("MXN", "MX$"),
    KRW("KRW", "₩"),
    ZAR("ZAR", "R");

    companion object {
        fun fromCode(code: String): AppCurrency {
            return entries.firstOrNull { it.code == code } ?: EUR
        }
    }
}

class SettingsManager(private val context: Context) {
    
    companion object {
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        val CURRENCY_KEY = stringPreferencesKey("currency")
        val SYNC_UID_KEY = stringPreferencesKey("sync_uid")
        val USERNAME_KEY = stringPreferencesKey("username")
        val SYNC_PAUSED_KEY = booleanPreferencesKey("sync_paused")
    }
    
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_THEME_KEY] ?: true
        }
        
    val currencyCode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENCY_KEY] ?: "EUR"
        }

    val syncUid: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[SYNC_UID_KEY] }

    val username: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USERNAME_KEY] }

    val isSyncPaused: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SYNC_PAUSED_KEY] ?: false }
        
    suspend fun toggleTheme() {
        context.dataStore.edit { preferences ->
            val current = preferences[DARK_THEME_KEY] ?: true
            preferences[DARK_THEME_KEY] = !current
        }
    }
    
    suspend fun setCurrencyCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENCY_KEY] = code
        }
    }

    suspend fun setSyncUid(uid: String?) {
        context.dataStore.edit { preferences ->
            if (uid == null) preferences.remove(SYNC_UID_KEY)
            else preferences[SYNC_UID_KEY] = uid
        }
    }

    suspend fun setUsername(name: String?) {
        context.dataStore.edit { preferences ->
            if (name == null) preferences.remove(USERNAME_KEY)
            else preferences[USERNAME_KEY] = name
        }
    }

    suspend fun setSyncPaused(paused: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_PAUSED_KEY] = paused
        }
    }
}
