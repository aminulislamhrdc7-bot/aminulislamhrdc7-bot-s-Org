package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_ACCOUNT_ID
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_hisab_prefs")

data class LocalAppPreferences(
    val language: AppLanguage = AppLanguage.BN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sarToBdtRate: Double = 32.5,
    val preferredCurrency: CurrencyCode = CurrencyCode.BDT,
    val appLockEnabled: Boolean = false,
    val pinHash: String = "",
    val activeAccountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val userLoginRequired: Boolean = true
)

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext

    val preferencesFlow: Flow<LocalAppPreferences> = appContext.dataStore.data.map { prefs ->
        LocalAppPreferences(
            language = AppLanguage.fromCode(prefs[KEY_LANGUAGE]),
            themeMode = ThemeMode.fromCode(prefs[KEY_THEME_MODE]),
            sarToBdtRate = prefs[KEY_SAR_TO_BDT_RATE] ?: 32.5,
            preferredCurrency = CurrencyCode.fromCode(prefs[KEY_PREFERRED_CURRENCY]),
            appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
            pinHash = prefs[KEY_PIN_HASH] ?: "",
            activeAccountId = prefs[KEY_ACTIVE_ACCOUNT_ID] ?: DEFAULT_ADMIN_ACCOUNT_ID,
            userLoginRequired = prefs[KEY_USER_LOGIN_REQUIRED] ?: true
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        appContext.dataStore.edit { it[KEY_LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.dataStore.edit { it[KEY_THEME_MODE] = mode.code }
    }

    suspend fun setSarToBdtRate(rate: Double) {
        appContext.dataStore.edit { it[KEY_SAR_TO_BDT_RATE] = rate.coerceIn(0.01, 10000.0) }
    }

    suspend fun setPreferredCurrency(currency: CurrencyCode) {
        appContext.dataStore.edit { it[KEY_PREFERRED_CURRENCY] = currency.code }
    }

    suspend fun setActiveAccountSession(accountId: String, requireLogin: Boolean = false) {
        appContext.dataStore.edit {
            it[KEY_ACTIVE_ACCOUNT_ID] = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID }
            it[KEY_USER_LOGIN_REQUIRED] = requireLogin
        }
    }

    suspend fun setUserLoginRequired(required: Boolean) {
        appContext.dataStore.edit { it[KEY_USER_LOGIN_REQUIRED] = required }
    }

    suspend fun setAppLockPin(pin: String) {
        val hash = hashPin(pin)
        appContext.dataStore.edit {
            it[KEY_PIN_HASH] = hash
            it[KEY_APP_LOCK_ENABLED] = true
        }
    }

    suspend fun disableAppLock() {
        appContext.dataStore.edit {
            it[KEY_APP_LOCK_ENABLED] = false
            it[KEY_PIN_HASH] = ""
        }
    }

    fun verifyPin(enteredPin: String, storedHash: String): Boolean {
        return storedHash.isNotBlank() && hashPin(enteredPin) == storedHash
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("my_hisab_salt_v1:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SAR_TO_BDT_RATE = doublePreferencesKey("sar_to_bdt_rate")
        val KEY_PREFERRED_CURRENCY = stringPreferencesKey("preferred_currency")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        val KEY_USER_LOGIN_REQUIRED = booleanPreferencesKey("user_login_required")
    }
}
