package app.mydear.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.Role
import app.mydear.android.domain.Provenance
import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.SearchRequest
import app.mydear.android.domain.SttAvailability
import app.mydear.android.domain.SttEvent
import app.mydear.android.domain.TurnId
import app.mydear.android.runtime.stt.AndroidOnDeviceSttEngine
import app.mydear.android.runtime.audio.AudioTrackPcmOutput
import app.mydear.android.runtime.litert.LiteRtConversationEngine
import app.mydear.android.runtime.sherpa.SupertonicTtsEngine
import app.mydear.android.runtime.search.HttpsSearchProxyGateway
import app.mydear.android.runtime.search.InternetSearchException
import app.mydear.android.runtime.search.PublicInternetSearchGateway
import app.mydear.android.search.InternetQueryPolicy
import app.mydear.android.search.SearchAnswerPolicy
import app.mydear.android.BuildConfig
import app.mydear.android.models.GemmaTier
import app.mydear.android.models.InstalledModelResolver
import app.mydear.android.models.ModelPreferenceStore
import app.mydear.android.models.ModelDownloadWorker
import app.mydear.android.models.SupertonicDownloadWorker
import app.mydear.android.memory.LocalMemoryRetriever
import app.mydear.android.memory.MemoryCommand
import app.mydear.android.memory.MemoryIntentParser
import app.mydear.android.memory.MemoryContextBoundaryStore
import app.mydear.android.memory.messagesAfterMemoryBoundary
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.mydear.android.voice.VoiceEvent
import app.mydear.android.voice.VoiceReducer
import app.mydear.android.voice.VoiceState
import app.mydear.android.voice.HalfDuplexGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import app.mydear.android.tools.LocalActionRouter
import app.mydear.android.tools.ToolPolicy
import app.mydear.android.tools.ToolProposal
import app.mydear.android.storage.EncryptedChatStore
import app.mydear.android.storage.EncryptedMemoryStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val voiceState: VoiceState = VoiceState.Idle,
    val notice: String? = null,
    val selectedModelTier: GemmaTier = GemmaTier.E2B,
    val modelInstalled: Boolean = false,
    val ttsInstalled: Boolean = false,
    val isGenerating: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val modelDownloadProgress: Int = 0,
    val isDownloadingTts: Boolean = false,
    val ttsDownloadProgress: Int = 0,
    val useWebSearch: Boolean = true,
    val searchConfigured: Boolean = false,
    val savedMemories: List<String> = emptyList(),
    val pendingTool: ToolProposal? = null,
)

class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {
    private val stt = AndroidOnDeviceSttEngine(application)
    private val modelResolver = InstalledModelResolver(application)
    private val modelPreferences = ModelPreferenceStore(application)
    private val chatStore = EncryptedChatStore(application)
    private val memoryStore = EncryptedMemoryStore(application)
    private val memoryContextBoundary = MemoryContextBoundaryStore(application)
    private val conversation = LiteRtConversationEngine(application.cacheDir.resolve("litert-chat"))
    private val tts = SupertonicTtsEngine()
    private val audioOutput = AudioTrackPcmOutput()
    private val initialTier = modelPreferences.selectedTier()
    private val internetPreferences = InternetPreferenceStore(application)
    private val configuredSearchGateway = BuildConfig.SEARCH_ENDPOINT.toHttpUrlOrNull()?.let(::HttpsSearchProxyGateway)
    private val searchGateway = configuredSearchGateway ?: PublicInternetSearchGateway()
    private val mutableState = MutableStateFlow(
        ChatUiState(
            selectedModelTier = initialTier,
            modelInstalled = modelResolver.resolveGemma(initialTier) != null,
            ttsInstalled = modelResolver.resolveSupertonic() != null,
            useWebSearch = internetPreferences.isEnabled() && internetPreferences.hasSavedChoice(),
            searchConfigured = true,
            messages = chatStore.load(),
            savedMemories = memoryStore.load().map { it.text },
        ),
    )
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    val needsInternetConsent: Boolean get() = !internetPreferences.hasSavedChoice()
    private var listeningJob: Job? = null
    private var answerJob: Job? = null
    private var modelDownloadJob: Job? = null
    private var ttsDownloadJob: Job? = null
    private var preparedModelId: String? = null
    private var preparedTts = false
    private var activeTurnId: TurnId? = null
    private var activePlaybackGeneration = 0L
    private val halfDuplexGate = HalfDuplexGate()
    private val consumedToolNonces = LinkedHashSet<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            state.map { it.messages }.distinctUntilChanged().drop(1).collectLatest { messages ->
                delay(500)
                chatStore.save(messages)
            }
        }
        restoreActiveDownloads()
    }

    fun updateDraft(value: String) = mutableState.update { it.copy(draft = value.take(4_000), notice = null) }

    fun setWebSearch(enabled: Boolean) {
        internetPreferences.setEnabled(enabled)
        mutableState.update {
            it.copy(
                useWebSearch = enabled,
                notice = if (enabled) "최신 정보가 필요할 때 인터넷에서 확인할게요" else "인터넷 도움을 껐어요",
            )
        }
    }

    fun consumeTool(message: String? = null) = mutableState.update {
        it.copy(pendingTool = null, notice = message)
    }

    fun forgetAllMemories() {
        cancelActiveAnswer()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { memoryStore.clear() }
            conversation.resetContext()
            memoryContextBoundary.markAfter(state.value.messages.lastOrNull()?.id)
            mutableState.update { it.copy(savedMemories = emptyList(), notice = "저장한 내 정보를 모두 잊었어요.") }
        }
    }

    @Synchronized fun takePendingToolForExecution(nonce: String): ToolProposal? {
        val proposal = state.value.pendingTool ?: return null
        if (proposal.nonce != nonce || nonce in consumedToolNonces || !ToolPolicy.evaluate(proposal).allowed) return null
        consumedToolNonces += nonce
        while (consumedToolNonces.size > 64) consumedToolNonces.remove(consumedToolNonces.first())
        consumeTool()
        return proposal
    }

    fun sendDraft() {
        val text = state.value.draft.trim()
        if (text.isEmpty()) return
        requestAnswer(text, speak = false)
        mutableState.update { it.copy(draft = "") }
    }

    fun sendQuickPrompt(text: String) = requestAnswer(text.take(500), speak = false)

    fun beginVoiceCapture() {
        val current = state.value.voiceState
        if (current is VoiceState.Listening) {
            cancelVoice()
            return
        }
        val previousAnswer = detachActiveAnswer()
        listeningJob?.cancel()
        listeningJob = viewModelScope.launch {
            val previousTurn = previousAnswer?.first
            val previousGeneration = previousAnswer?.second
            halfDuplexGate.beginListening(
                cancelAnswer = { if (previousTurn != null) conversation.cancel(previousTurn) },
                cancelSynthesis = { if (previousTurn != null) tts.cancel(previousTurn) },
                abortPlayback = { if (previousGeneration != null) audioOutput.abort(previousGeneration) },
                startRecognition = {
                    val turnId = TurnId.create()
                    val transition = VoiceReducer.reduce(state.value.voiceState, VoiceEvent.StartListening(turnId))
                    mutableState.update { it.copy(voiceState = transition.state, notice = "휴대폰 안에서 듣고 있어요") }
                    when (stt.availability(Locale.KOREAN)) {
                SttAvailability.Ready -> stt.recognize(turnId, Locale.KOREAN).collect { event ->
                    when (event) {
                        is SttEvent.Partial -> mutableState.update { it.copy(draft = event.text, notice = "듣고 있어요") }
                        is SttEvent.Final -> {
                            mutableState.update {
                                it.copy(
                                    draft = "",
                                    voiceState = VoiceReducer.reduce(it.voiceState, VoiceEvent.SpeechAccepted(turnId)).state,
                                    notice = null,
                                )
                            }
                            requestAnswer(event.text, speak = true, turnId = turnId)
                        }
                        is SttEvent.Failure -> failVoice(event.reason)
                    }
                }
                SttAvailability.ModelDownloadRequired -> failVoice("한국어 음성 인식 모델을 먼저 받아 주세요.")
                SttAvailability.Unsupported -> failVoice("이 휴대폰은 오프라인 음성 인식을 지원하지 않아요. 글로는 계속 이용할 수 있어요.")
                    }
                },
            )
        }
    }

    fun microphonePermissionDenied(permanently: Boolean) {
        failVoice(
            if (permanently) "마이크가 꺼져 있어요. 설정에서 마이크를 켜면 말로 물어볼 수 있어요."
            else "마이크 권한을 허용하지 않았어요. 글로는 계속 이용할 수 있어요.",
        )
    }

    fun cancelVoice() {
        val turnId = (state.value.voiceState as? VoiceState.Listening)?.turnId
        listeningJob?.cancel()
        listeningJob = null
        if (turnId != null) viewModelScope.launch { stt.cancel(turnId) }
        mutableState.update { it.copy(voiceState = VoiceState.Idle, notice = null) }
    }

    fun selectModelTier(tier: GemmaTier) {
        if (tier == state.value.selectedModelTier) return
        cancelActiveAnswer()
        modelPreferences.select(tier)
        viewModelScope.launch { conversation.release() }
        preparedModelId = null
        mutableState.update {
            it.copy(
                selectedModelTier = tier,
                modelInstalled = modelResolver.resolveGemma(tier) != null,
                notice = if (modelResolver.resolveGemma(tier) == null) "${tier.label} 모델을 먼저 설치해 주세요." else null,
            )
        }
    }

    fun refreshInstalledModels() = mutableState.update {
        it.copy(
            modelInstalled = modelResolver.resolveGemma(it.selectedModelTier) != null,
            ttsInstalled = modelResolver.resolveSupertonic() != null,
        )
    }

    fun installSelectedModel() {
        if (state.value.isDownloadingModel || state.value.modelInstalled) return
        val tier = state.value.selectedModelTier
        val request = ModelDownloadWorker.enqueue(getApplication(), tier)
        mutableState.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0, notice = "${tier.label} 다운로드를 시작했어요") }
        observeModelDownload(request.id, tier)
    }

    private fun observeModelDownload(workId: UUID, tier: GemmaTier) {
        modelDownloadJob?.cancel()
        modelDownloadJob = viewModelScope.launch {
            val workManager = WorkManager.getInstance(getApplication())
            while (true) {
                val info = withContext(Dispatchers.IO) { workManager.getWorkInfoById(workId).get() }
                if (info == null) {
                    mutableState.update { it.copy(isDownloadingModel = false, notice = "다운로드 상태를 확인하지 못했어요. 다시 눌러 주세요.") }
                    break
                }
                val progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                mutableState.update { it.copy(modelDownloadProgress = progress) }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        mutableState.update {
                            it.copy(
                                isDownloadingModel = false,
                                modelDownloadProgress = 100,
                                modelInstalled = modelResolver.resolveGemma(tier) != null,
                                notice = "${tier.label} 설치가 끝났어요",
                            )
                        }
                        break
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        mutableState.update {
                            it.copy(
                                isDownloadingModel = false,
                                notice = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "모델 설치를 마치지 못했어요",
                            )
                        }
                        break
                    }
                    else -> delay(500)
                }
            }
        }
    }

    fun installTtsModel() {
        if (state.value.isDownloadingTts || state.value.ttsInstalled) return
        val request = SupertonicDownloadWorker.enqueue(getApplication())
        mutableState.update { it.copy(isDownloadingTts = true, ttsDownloadProgress = 0, notice = "한국어 목소리 다운로드를 시작했어요") }
        observeTtsDownload(request.id)
    }

    private fun observeTtsDownload(workId: UUID) {
        ttsDownloadJob?.cancel()
        ttsDownloadJob = viewModelScope.launch {
            val workManager = WorkManager.getInstance(getApplication())
            while (true) {
                val info = withContext(Dispatchers.IO) { workManager.getWorkInfoById(workId).get() }
                if (info == null) {
                    mutableState.update { it.copy(isDownloadingTts = false, notice = "목소리 다운로드 상태를 확인하지 못했어요. 다시 눌러 주세요.") }
                    break
                }
                mutableState.update {
                    it.copy(ttsDownloadProgress = info.progress.getInt(SupertonicDownloadWorker.KEY_PROGRESS, 0))
                }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        mutableState.update {
                            it.copy(
                                isDownloadingTts = false,
                                ttsDownloadProgress = 100,
                                ttsInstalled = modelResolver.resolveSupertonic() != null,
                                notice = "한국어 목소리 설치가 끝났어요",
                            )
                        }
                        break
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        mutableState.update {
                            it.copy(
                                isDownloadingTts = false,
                                notice = info.outputData.getString(SupertonicDownloadWorker.KEY_ERROR) ?: "목소리 설치를 마치지 못했어요",
                            )
                        }
                        break
                    }
                    else -> delay(500)
                }
            }
        }
    }

    private fun restoreActiveDownloads() {
        val workManager = WorkManager.getInstance(getApplication())
        val tier = state.value.selectedModelTier
        viewModelScope.launch {
            val activeModel = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(ModelDownloadWorker.workName(tier)).get()
                    .lastOrNull { it.state in ACTIVE_WORK_STATES }
            }
            if (activeModel != null) {
                mutableState.update {
                    it.copy(
                        isDownloadingModel = true,
                        modelDownloadProgress = activeModel.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0),
                    )
                }
                observeModelDownload(activeModel.id, tier)
            }
            val activeTts = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(SupertonicDownloadWorker.WORK_NAME).get()
                    .lastOrNull { it.state in ACTIVE_WORK_STATES }
            }
            if (activeTts != null) {
                mutableState.update {
                    it.copy(
                        isDownloadingTts = true,
                        ttsDownloadProgress = activeTts.progress.getInt(SupertonicDownloadWorker.KEY_PROGRESS, 0),
                    )
                }
                observeTtsDownload(activeTts.id)
            }
        }
    }

    private companion object {
        val ACTIVE_WORK_STATES = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    }

    private fun requestAnswer(text: String, speak: Boolean, turnId: TurnId = TurnId.create()) {
        cancelActiveAnswer()
        val user = ChatMessage(UUID.randomUUID().toString(), Role.User, text)
        val assistantId = UUID.randomUUID().toString()
        LocalActionRouter.route(text, turnId.value)?.let { proposal ->
            val decision = ToolPolicy.evaluate(proposal)
            if (decision.allowed) {
                val response = when (proposal.name) {
                    app.mydear.android.tools.ToolName.OpenSettings -> "설정 화면을 열게요."
                    app.mydear.android.tools.ToolName.PrepareCall -> "전화 앱을 열기 전에 번호를 확인해 주세요."
                    app.mydear.android.tools.ToolName.PrepareAlarm -> "알람 앱을 열기 전에 시간을 확인해 주세요."
                    app.mydear.android.tools.ToolName.OpenMap -> "지도 앱을 열기 전에 장소를 확인해 주세요."
                    else -> "동작을 실행하기 전에 내용을 확인해 주세요."
                }
                mutableState.update {
                    it.copy(
                        messages = it.messages + user + ChatMessage(assistantId, Role.Assistant, response),
                        pendingTool = proposal,
                        notice = null,
                        voiceState = VoiceState.Idle,
                    )
                }
                return
            }
        }
        if (handleMemoryCommand(text, user, assistantId, speak, turnId)) return
        activeTurnId = turnId
        mutableState.update {
            it.copy(
                messages = it.messages + user + ChatMessage(assistantId, Role.Assistant, ""),
                notice = "휴대폰 안에서 답변을 만들고 있어요",
                isGenerating = true,
                voiceState = if (speak) VoiceState.PreparingAnswer(turnId) else it.voiceState,
            )
        }
        answerJob = viewModelScope.launch {
            try {
                val evidence = loadSearchEvidenceIfRequested(text)
                val publicAnswer = if (configuredSearchGateway == null && evidence != null) {
                    SearchAnswerPolicy.publicAnswer(text, checkNotNull(evidence))
                } else null
                if (publicAnswer != null) {
                    replaceAssistantText(assistantId, publicAnswer)
                    attachWebProvenance(
                        assistantId,
                        checkNotNull(evidence),
                        publicAnswer,
                        SearchAnswerPolicy.publicSourceIndices(text, checkNotNull(evidence)),
                    )
                    if (speak) speakAnswer(turnId, publicAnswer) else finishAnswer(turnId)
                    return@launch
                }
                val model = modelResolver.resolveGemma(state.value.selectedModelTier)
                if (model == null) {
                    val message = "오프라인 AI가 아직 준비되지 않았어요. 하단 ‘설정’의 ‘오프라인 AI 준비’에서 기본 AI를 내려받아 주세요."
                    replaceAssistantText(assistantId, message)
                    finishAnswer(turnId)
                    return@launch
                }
                if (preparedModelId != model.id) {
                    conversation.prepare(model)
                    preparedModelId = model.id
                }
                val requestMessages = messagesAfterMemoryBoundary(
                    state.value.messages.filter { it.id != assistantId },
                    memoryContextBoundary.messageId(),
                ).takeLast(12)
                val memories = withContext(Dispatchers.IO) {
                    LocalMemoryRetriever.retrieve(memoryStore.load(), text).map { it.text }
                }
                val webBuffer = StringBuilder()
                conversation.stream(ConversationRequest(turnId, requestMessages, evidence, memories)).collect { event ->
                    when (event) {
                        is ConversationEvent.TextDelta -> if (evidence == null) {
                            appendAssistantText(assistantId, event.value, turnId)
                        } else {
                            webBuffer.append(event.value)
                        }
                        is ConversationEvent.Failure -> throw IllegalStateException(event.userMessage)
                        ConversationEvent.Complete -> Unit
                    }
                }
                if (activeTurnId != turnId) return@launch
                val rawAnswer = if (evidence == null) {
                    state.value.messages.firstOrNull { it.id == assistantId }?.text.orEmpty()
                } else webBuffer.toString()
                val answer = cleanAssistantAnswer(rawAnswer)
                    .ifBlank { "답변을 만들지 못했어요. 질문을 조금 다르게 말씀해 주세요." }
                replaceAssistantText(assistantId, answer)
                if (evidence != null) {
                    attachWebProvenance(
                        assistantId,
                        evidence,
                        answer,
                        SearchAnswerPolicy.sourceIndices(rawAnswer, evidence.documents.size),
                    )
                }
                if (speak && answer.isNotBlank()) speakAnswer(turnId, answer)
                else finishAnswer(turnId)
            } catch (error: Exception) {
                if (activeTurnId == turnId) {
                    val userMessage = if (error is InternetSearchException) error.message.orEmpty()
                    else "답변을 만들지 못했어요. 모델을 다시 준비한 뒤 시도해 주세요."
                    replaceAssistantText(assistantId, userMessage)
                    mutableState.update { it.copy(notice = null, isGenerating = false, voiceState = VoiceState.Failed(userMessage)) }
                    activeTurnId = null
                }
            }
        }
    }

    private suspend fun loadSearchEvidenceIfRequested(query: String): SearchEvidence? {
        val requiresFreshness = InternetQueryPolicy.requiresFreshness(query)
        if (!state.value.useWebSearch) {
            if (requiresFreshness) throw InternetSearchException("바뀔 수 있는 정보라 인터넷 확인이 필요해요. 위의 ‘인터넷 도움’을 켜고 다시 물어봐 주세요.")
            return null
        }
        if (InternetQueryPolicy.isUnsupportedByPublicFallback(query) && configuredSearchGateway == null) {
            throw InternetSearchException("이 최신 정보는 아직 무료 인터넷 도움에서 정확히 확인할 수 없어요. 추측해서 답하지 않을게요.")
        }
        if (!InternetQueryPolicy.shouldUseInternet(query, configuredSearchGateway != null)) return null
        mutableState.update { it.copy(notice = "인터넷에서 최신 자료를 찾고 있어요") }
        return searchGateway.search(SearchRequest(query)).getOrElse {
            throw if (it is InternetSearchException) it else InternetSearchException("인터넷 정보를 가져오지 못했어요. 연결을 확인하고 다시 시도해 주세요.")
        }
    }

    private fun cleanAssistantAnswer(value: String): String = value
        .replace("**", "")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("\\s*\\[자료\\s*\\d+]"), "")
        .replace(Regex("[😊🙂😉😀😃😄😁👍🙏]"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .trim()

    private fun attachWebProvenance(
        messageId: String,
        evidence: SearchEvidence,
        answer: String,
        preferredIndices: List<Int>? = null,
    ) {
        val selected = preferredIndices?.filter { it in evidence.documents.indices }?.distinct()
            ?: SearchAnswerPolicy.sourceIndices(answer, evidence.documents.size)
        mutableState.update { current ->
            val sources = selected.map { evidence.documents[it] }
                .filter { it.url.startsWith("https://") }
                .distinctBy { it.url }
                .map { source ->
                    app.mydear.android.domain.SearchSource(source.title, source.host, source.url, source.publishedAt)
                }
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(provenance = Provenance.Web(System.currentTimeMillis(), sources)) else it
                },
            )
        }
    }

    private fun handleMemoryCommand(
        text: String,
        user: ChatMessage,
        assistantId: String,
        speak: Boolean,
        turnId: TurnId,
    ): Boolean {
        val command = MemoryIntentParser.parse(text) ?: return false
        activeTurnId = turnId
        mutableState.update {
            it.copy(
                messages = it.messages + user + ChatMessage(assistantId, Role.Assistant, ""),
                notice = "휴대폰 안의 기억을 정리하고 있어요",
                isGenerating = true,
                voiceState = if (speak) VoiceState.PreparingAnswer(turnId) else it.voiceState,
            )
        }
        answerJob = viewModelScope.launch {
            val (response, advanceMemoryBoundary) = withContext(Dispatchers.IO) {
                when (command) {
                    is MemoryCommand.Remember -> {
                        val saved = memoryStore.remember(command.fact)
                        "기억해둘게요: ${saved.text}" to false
                    }
                    is MemoryCommand.Forget -> {
                        val removed = memoryStore.forgetMatching(command.query)
                        val text = if (removed.isEmpty()) "‘${command.query}’에 관해 기억한 내용이 없어요."
                        else "알겠어요. ${removed.size}개의 기억을 지웠어요."
                        text to removed.isNotEmpty()
                    }
                    MemoryCommand.ForgetAll -> {
                        memoryStore.clear()
                        "저장한 내 정보를 모두 잊었어요." to true
                    }
                    MemoryCommand.Recall -> {
                        val memories = memoryStore.load()
                        val text = if (memories.isEmpty()) "아직 따로 기억해둔 내 정보가 없어요."
                        else memories.takeLast(8).joinToString(prefix = "휴대폰에 기억해둔 내용이에요.\n", separator = "\n") { "• ${it.text}" }
                        text to false
                    }
                }
            }
            conversation.resetContext()
            val currentMemories = withContext(Dispatchers.IO) { memoryStore.load().map { it.text } }
            mutableState.update { it.copy(savedMemories = currentMemories) }
            replaceAssistantText(assistantId, response)
            if (advanceMemoryBoundary) {
                memoryContextBoundary.markAfter(assistantId)
            }
            if (speak) speakAnswer(turnId, response) else finishAnswer(turnId)
        }
        return true
    }

    private suspend fun speakAnswer(turnId: TurnId, text: String) {
        val ttsModel = modelResolver.resolveSupertonic()
        if (ttsModel == null) {
            mutableState.update { it.copy(notice = "음성 모델이 없어 글로 답했어요.", ttsInstalled = false) }
            finishAnswer(turnId)
            return
        }
        if (!preparedTts) {
            tts.prepare(ttsModel)
            preparedTts = true
        }
        val generation = ++activePlaybackGeneration
        var started = false
        tts.synthesize(turnId, text.take(500)).collect { chunk ->
            if (activeTurnId != turnId) return@collect
            if (!started) {
                audioOutput.start(chunk.sampleRate, generation)
                started = true
                mutableState.update {
                    it.copy(voiceState = VoiceReducer.reduce(it.voiceState, VoiceEvent.PlaybackStarted(turnId, generation)).state, notice = null)
                }
            }
            audioOutput.write(chunk, generation)
        }
        if (started) audioOutput.finish(generation)
        if (activeTurnId == turnId) {
            mutableState.update { it.copy(voiceState = VoiceReducer.reduce(it.voiceState, VoiceEvent.PlaybackCompleted(generation)).state) }
            finishAnswer(turnId)
        }
    }

    private fun appendAssistantText(messageId: String, value: String, turnId: TurnId) {
        if (activeTurnId != turnId) return
        mutableState.update { current ->
            current.copy(messages = current.messages.map { if (it.id == messageId) it.copy(text = (it.text + value).take(12_000)) else it })
        }
    }

    private fun replaceAssistantText(messageId: String, value: String) = mutableState.update { current ->
        current.copy(messages = current.messages.map { if (it.id == messageId) it.copy(text = value) else it })
    }

    private fun finishAnswer(turnId: TurnId) {
        if (activeTurnId != turnId) return
        activeTurnId = null
        answerJob = null
        mutableState.update { it.copy(isGenerating = false, notice = null, voiceState = VoiceState.Idle) }
    }

    private fun cancelActiveAnswer() {
        val (turnId, generation) = detachActiveAnswer() ?: return
        viewModelScope.launch {
            conversation.cancel(turnId)
            tts.cancel(turnId)
            audioOutput.abort(generation)
        }
    }

    private fun detachActiveAnswer(): Pair<TurnId, Long>? {
        val turnId = activeTurnId ?: return null
        activeTurnId = null
        answerJob?.cancel()
        answerJob = null
        val generation = activePlaybackGeneration
        mutableState.update { it.copy(isGenerating = false, voiceState = VoiceState.Idle, notice = null) }
        return turnId to generation
    }

    private fun failVoice(message: String) {
        mutableState.update {
            it.copy(voiceState = VoiceReducer.reduce(it.voiceState, VoiceEvent.Failure(message)).state, notice = message)
        }
    }

    override fun onCleared() {
        listeningJob?.cancel()
        answerJob?.cancel()
        runBlocking(Dispatchers.IO) {
            activeTurnId?.let { turnId ->
                conversation.cancel(turnId)
                tts.cancel(turnId)
            }
            audioOutput.abort(activePlaybackGeneration)
            conversation.release()
            tts.release()
        }
        super.onCleared()
    }
}
