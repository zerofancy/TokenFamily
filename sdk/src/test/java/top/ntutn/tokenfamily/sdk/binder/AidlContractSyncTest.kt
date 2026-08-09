package top.ntutn.tokenfamily.sdk.binder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AidlContractSyncTest {

    @Test
    fun `app and sdk AIDL contracts are identical`() {
        val root = generateSequence(
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        ) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val relativePaths = listOf(
            "ChatCompletionRequest.aidl",
            "ChatCompletionResponse.aidl",
            "IChatCompletionService.aidl",
            "IChatStreamCallback.aidl"
        )
        relativePaths.forEach { fileName ->
            val appFile = File(root, "app/src/main/aidl/top/ntutn/tokenfamily/aidl/$fileName")
            val sdkFile = File(root, "sdk/src/main/aidl/top/ntutn/tokenfamily/aidl/$fileName")
            assertEquals("AIDL contract differs: $fileName", appFile.readText(), sdkFile.readText())
        }
    }
}
