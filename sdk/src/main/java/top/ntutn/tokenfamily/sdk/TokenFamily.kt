package top.ntutn.tokenfamily.sdk

import android.annotation.SuppressLint
import android.content.Context
import top.ntutn.tokenfamily.sdk.binder.ServiceConnector

object TokenFamily {

    @SuppressLint("StaticFieldLeak") // ServiceConnector stores applicationContext only.
    @Volatile
    private var serviceConnector: ServiceConnector? = null

    fun init(context: Context) {
        if (serviceConnector == null) {
            synchronized(this) {
                if (serviceConnector == null) {
                    serviceConnector = ServiceConnector(context.applicationContext)
                }
            }
        }
    }

    fun getServiceConnector(): ServiceConnector {
        return serviceConnector ?: throw IllegalStateException(
            "TokenFamily not initialized. Call TokenFamily.init(context) first."
        )
    }
}
