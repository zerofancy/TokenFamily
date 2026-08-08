package top.ntutn.tokenfamily.sdk

import android.content.Context
import top.ntutn.tokenfamily.sdk.binder.ServiceConnector

object TokenFamily {

    private var serviceConnector: ServiceConnector? = null

    fun init(context: Context) {
        if (serviceConnector == null) {
            serviceConnector = ServiceConnector(context.applicationContext)
        }
    }

    fun getServiceConnector(): ServiceConnector {
        return serviceConnector ?: throw IllegalStateException(
            "TokenFamily not initialized. Call TokenFamily.init(context) first."
        )
    }
}
