package top.ntutn.tokenfamily.sdk.binder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AidlContractSyncTest {

    @Test
    fun `app and sdk AIDL contracts are identical`() {
        val relativePaths = listOf(
            "ChatCompletionRequest.aidl",
            "ChatCompletionResponse.aidl",
            "IChatCompletionService.aidl",
            "IChatStreamCallback.aidl"
        )
        relativePaths.forEach { fileName ->
            val appFile = File(projectRoot, "app/src/main/aidl/top/ntutn/tokenfamily/aidl/$fileName")
            val sdkFile = File(projectRoot, "sdk/src/main/aidl/top/ntutn/tokenfamily/aidl/$fileName")
            assertEquals("AIDL contract differs: $fileName", appFile.readText(), sdkFile.readText())
        }
    }

    @Test
    fun `listModels is appended after existing service transactions`() {
        val serviceContract = File(
            projectRoot,
            "sdk/src/main/aidl/top/ntutn/tokenfamily/aidl/IChatCompletionService.aidl"
        ).readText()
        val declarations = serviceContract
            .substringAfter("interface IChatCompletionService {")
            .substringBeforeLast("}")
            .split(';')
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }

        assertEquals(
            listOf(
                "ChatCompletionResponse chat(in ChatCompletionRequest request)",
                "ChatCompletionResponse streamChat(in ChatCompletionRequest request, IChatStreamCallback callback)",
                "oneway void cancelStream(String requestId)",
                "ChatCompletionResponse listModels()"
            ),
            declarations
        )
    }

    private val projectRoot: File
        get() = generateSequence(
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        ) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
}
