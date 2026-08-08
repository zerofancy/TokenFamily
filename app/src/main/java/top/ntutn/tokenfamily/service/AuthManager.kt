package top.ntutn.tokenfamily.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(context: Context) {

    data class AuthorizedApp(
        val packageName: String,
        val firstAuthorizedAt: Long
    )

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "agentcore_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isAuthorized(callingUid: Int, context: Context): Boolean {
        val packageName = resolvePackageName(callingUid, context) ?: return false
        return prefs.getBoolean(packageName, false)
    }

    fun grantAuthorization(callingUid: Int, context: Context): Boolean {
        val packageName = resolvePackageName(callingUid, context) ?: return false
        prefs.edit().putBoolean(packageName, true).apply()
        return true
    }

    fun revokeAuthorization(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }

    fun getAuthorizedApps(): List<AuthorizedApp> {
        return prefs.all.mapNotNull { (key, value) ->
            if (value is Boolean && value) {
                AuthorizedApp(
                    packageName = key,
                    firstAuthorizedAt = 0L
                )
            } else null
        }
    }

    private fun resolvePackageName(uid: Int, context: Context): String? {
        return try {
            val packages = context.packageManager.getPackagesForUid(uid)
            packages?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
