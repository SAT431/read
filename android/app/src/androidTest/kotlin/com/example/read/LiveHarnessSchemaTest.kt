package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.AppCommand
import dev.folio.remote.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** No learner source in this diagnostic; only fixed classifications leave the transport. */
class LiveHarnessSchemaTest {
    @Test fun currentHarnessToolSchemaIsAcceptedByConfiguredGemini()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive")=="true")
        val base=ApplicationProvider.getApplicationContext<Context>()
        val f=PreparationDeviceFixture(base,CoroutineScope(SupervisorJob()+Dispatchers.Default))
        try {
            f.seed();f.act(AppCommand.StudyPdf(f.pdf.id))
            var hint="none"
            val diagnostic=ProviderTransport{request -> withContext(Dispatchers.IO) {
                val connection=java.net.URL("https://generativelanguage.googleapis.com${request.path}").openConnection() as javax.net.ssl.HttpsURLConnection
                try {
                    connection.requestMethod="POST";connection.doOutput=true;connection.instanceFollowRedirects=false
                    connection.connectTimeout=10000;connection.readTimeout=45000
                    connection.setRequestProperty("x-goog-api-key",request.key);connection.setRequestProperty("Content-Type",request.contentType)
                    connection.outputStream.use{it.write(request.body)}
                    val status=connection.responseCode
                    if(status==400) {
                        // Bounded ephemeral inspection; only fixed labels, never raw provider text or causes.
                        val error=connection.errorStream?.use{it.readNBytes(8192).toString(Charsets.UTF_8)}.orEmpty()
                        hint=when {
                            error.contains("too many states",true) -> "schema_states"
                            error.contains("schema",true) && error.contains("complex",true) -> "schema_complexity"
                            error.contains("schema",true) -> "schema_validation"
                            error.contains("token",true) -> "token_limit"
                            error.contains("Unknown name",true) -> "unknown_field"
                            error.contains("thinking",true) -> "thinking_configuration"
                            else -> "unclassified_${error.length}_" + listOf("constraint","union","anyOf","properties","fields","unsupported","invalid","argument","request","payload","depth","nested","limit","function","length","large","many","generation","batch","type","json","total","states","size","recursive","definitions","null","enum","format","characters").filter{error.contains(it,true)}.joinToString("_")
                        }
                    }
                    ServiceReply(status,if(status==200) connection.inputStream.use{it.readBytes().toString(Charsets.UTF_8)} else "")
                }finally{connection.disconnect()}
            }}
            val provider=DirectGeminiMentor(requireNotNull(KeystoreProviderProfileStore(base).load()),
                base.assets.open("gemini-orchestrator.md").bufferedReader().use{it.readText()},diagnostic)
            val assistant=f.assistant(provider,documentStudy=true)
            assistant.ask("Briefly describe how I can study a topic here. Do not take any app actions.")
            assertEquals("Fixed provider diagnostic: $hint; ${assistant.state.value.error}",AssistantStatus.COMPLETED,assistant.state.value.status)
        }finally{f.close()}
    }
}
