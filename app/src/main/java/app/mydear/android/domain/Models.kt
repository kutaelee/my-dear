package app.mydear.android.domain

import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

@JvmInline value class TurnId(val value: String) {
    companion object { fun create() = TurnId(UUID.randomUUID().toString()) }
}

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val provenance: Provenance = Provenance.Local,
)

enum class Role { User, Assistant }
sealed interface Provenance {
    data object Local : Provenance
    data class Web(val searchedAtEpochMs: Long, val sources: List<SearchSource>) : Provenance
}

data class SearchSource(val title: String, val host: String, val url: String, val updatedAt: String?)
data class ConversationRequest(
    val turnId: TurnId,
    val messages: List<ChatMessage>,
    val evidence: SearchEvidence? = null,
    val memories: List<String> = emptyList(),
    val screenText: String? = null,
    val screenImage: ByteArray? = null,
)
data class SearchRequest(val query: String, val locale: String = "ko-KR")
data class SearchEvidence(val documents: List<SearchDocument>)
data class SearchDocument(val title: String, val host: String, val url: String, val snippet: String, val publishedAt: String?)

sealed interface ConversationEvent {
    data class TextDelta(val value: String) : ConversationEvent
    data object Complete : ConversationEvent
    data class Failure(val userMessage: String) : ConversationEvent
}

interface ConversationEngine {
    suspend fun prepare(model: InstalledModel)
    fun stream(request: ConversationRequest): Flow<ConversationEvent>
    suspend fun cancel(turnId: TurnId)
    suspend fun resetContext()
    suspend fun release()
}

enum class ModelBackend { Cpu, Gpu }
data class InstalledModel(
    val id: String,
    val path: String,
    val sha256: String,
    val backend: ModelBackend = ModelBackend.Cpu,
)

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data object ModelDownloadRequired : SttEvent
    data class Failure(val reason: String) : SttEvent
}

interface SpeechToTextEngine {
    suspend fun availability(locale: Locale): SttAvailability
    suspend fun requestLanguageModel(locale: Locale): SttModelRequest
    fun recognize(turnId: TurnId, locale: Locale): Flow<SttEvent>
    suspend fun cancel(turnId: TurnId)
}

enum class SttAvailability { Ready, ModelDownloadRequired, Unsupported }
enum class SttModelRequest { Ready, Scheduled, ManualInstallRequired, Failed }
data class PcmChunk(val samples: ShortArray, val sampleRate: Int)

interface TextToSpeechEngine {
    suspend fun prepare(model: InstalledModel)
    fun synthesize(turnId: TurnId, text: String): Flow<PcmChunk>
    suspend fun cancel(turnId: TurnId)
    suspend fun release()
}

interface PcmOutput {
    suspend fun start(sampleRate: Int, generation: Long)
    suspend fun write(chunk: PcmChunk, generation: Long)
    suspend fun finish(generation: Long)
    suspend fun abort(generation: Long)
}

interface SearchGateway { suspend fun search(request: SearchRequest): Result<SearchEvidence> }
