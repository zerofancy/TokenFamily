package top.ntutn.tokenfamily.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.ntutn.tokenfamily.data.model.ApiKeyConfig

class KeyStoreManager(context: Context) {

    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "agentcore_keystore",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveKey(config: ApiKeyConfig) {
        val keys = getAllKeysInternal().toMutableList()
        val existingIndex = keys.indexOfFirst { it.id == config.id }
        if (existingIndex >= 0) {
            keys[existingIndex] = config
        } else {
            keys.add(config)
        }
        persistKeys(keys)
    }

    fun deleteKey(id: String) {
        val keys = getAllKeysInternal().toMutableList()
        keys.removeAll { it.id == id }
        persistKeys(keys)
    }

    fun getAllKeys(): List<ApiKeyConfig> = getAllKeysInternal()

    fun getDefaultKey(): ApiKeyConfig? =
        getAllKeysInternal().firstOrNull { it.isDefault }

    fun setDefaultKey(id: String) {
        val keys = getAllKeysInternal().map { config ->
            config.copy(isDefault = config.id == id)
        }
        persistKeys(keys)
    }

    private fun getAllKeysInternal(): List<ApiKeyConfig> {
        val json = prefs.getString(PREF_KEYS_LIST, null) ?: return emptyList()
        val type = object : TypeToken<List<ApiKeyConfig>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistKeys(keys: List<ApiKeyConfig>) {
        prefs.edit().putString(PREF_KEYS_LIST, gson.toJson(keys)).apply()
    }

    companion object {
        private const val PREF_KEYS_LIST = "agentcore_keys_list"

        @Volatile
        private var instance: KeyStoreManager? = null

        fun getInstance(context: Context): KeyStoreManager {
            return instance ?: synchronized(this) {
                instance ?: KeyStoreManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
