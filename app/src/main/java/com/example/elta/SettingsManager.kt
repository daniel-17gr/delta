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
    }
    
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_THEME_KEY] ?: true // dark theme by default
        }
        
    val currencyCode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENCY_KEY] ?: "EUR"
        }
        
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
}
