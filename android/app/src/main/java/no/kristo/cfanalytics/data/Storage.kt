package no.kristo.cfanalytics.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("settings")

data class Prefs(
    val domains: List<Domain>,
    val selectedId: String?,
    val tab: Int,
    val range: Range,
    val period: Period,
)

class PrefsStore(private val context: Context) {
    private val kDomains = stringPreferencesKey("domains")
    private val kSelected = stringPreferencesKey("selected")
    private val kTab = intPreferencesKey("tab")
    private val kRange = stringPreferencesKey("range")
    private val kPeriod = stringPreferencesKey("period")

    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            domains = p[kDomains]?.let { runCatching { Json.decodeFromString<List<Domain>>(it) }.getOrNull() }.orEmpty(),
            selectedId = p[kSelected],
            tab = p[kTab] ?: 0,
            range = p[kRange]?.let { runCatching { Range.valueOf(it) }.getOrNull() } ?: Range.LAST_24H,
            period = p[kPeriod]?.let { runCatching { Period.valueOf(it) }.getOrNull() } ?: Period.D30,
        )
    }

    suspend fun current(): Prefs = prefs.first()

    suspend fun setDomains(domains: List<Domain>) =
        context.dataStore.edit { it[kDomains] = Json.encodeToString(domains) }

    suspend fun setSelected(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(kSelected) else it[kSelected] = id
    }

    suspend fun setTab(tab: Int) = context.dataStore.edit { it[kTab] = tab }
    suspend fun setRange(range: Range) = context.dataStore.edit { it[kRange] = range.name }
    suspend fun setPeriod(period: Period) = context.dataStore.edit { it[kPeriod] = period.name }
}

/** API tokens, encrypted with an AES-GCM key that never leaves the Android Keystore. One token per domain id. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokens", Context.MODE_PRIVATE)
    private val alias = "cf_token_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(domainId: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = cipher.iv + cipher.doFinal(token.toByteArray())
        prefs.edit().putString(domainId, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(domainId: String): String? {
        val blob = prefs.getString(domainId, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
            String(cipher.doFinal(blob, 12, blob.size - 12))
        }.getOrNull()
    }

    fun remove(domainId: String) = prefs.edit().remove(domainId).apply()
}
