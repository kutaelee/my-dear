package app.mydear.android.ui

import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRetryPolicyTest {
    @Test fun completedTtsInstallKeepsTheWrittenAnswerAndOffersImmediateReplay() {
        val installed = ChatUiState(
            messages = listOf(ChatMessage("answer", Role.Assistant, "글로 표시된 답변")),
            isDownloadingTts = true,
            ttsSetupRequired = true,
        ).afterTtsInstall(installed = true)

        assertTrue(installed.ttsInstalled)
        assertFalse(installed.ttsSetupRequired)
        assertTrue(installed.ttsPlaybackFailed)
        assertTrue(installed.notice?.contains("답변을 들어보세요") == true)
        assertEquals("글로 표시된 답변", installed.messages.single().text)
    }

    @Test fun completedBackgroundTtsInstallWithoutAnAwaitingAnswerDoesNotShowReplay() {
        val installed = ChatUiState(isDownloadingTts = true).afterTtsInstall(installed = true)

        assertTrue(installed.ttsInstalled)
        assertFalse(installed.ttsPlaybackFailed)
    }

    @Test fun offlineLanguageModelRetriesOnlyOnceBeforeOfferingRecovery() {
        assertTrue(canRetryOfflineModel(0))
        assertFalse(canRetryOfflineModel(1))
        assertFalse(canRetryOfflineModel(2))
    }

    @Test fun recognizerStartExceptionReturnsToRecoverableVoiceState() = runBlocking {
        var failure: Pair<String, Boolean>? = null

        runSpeechRecognitionGuard(
            isOffline = true,
            onFailure = { message, fallback -> failure = message to fallback },
        ) {
            throw IllegalStateException("recognizer start failed")
        }

        assertTrue(failure?.first?.contains("오프라인 음성 인식을 시작하지 못했어요") == true)
        assertEquals(true, failure?.second)
    }

    @Test fun recognitionCancellationIsNotConvertedIntoAnError() = runBlocking {
        var failed = false
        var cancellationPropagated = false

        try {
            runSpeechRecognitionGuard(
                isOffline = true,
                onFailure = { _, _ -> failed = true },
            ) {
                throw CancellationException("user stopped listening")
            }
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
        assertFalse(failed)
    }

    @Test fun recognitionWithoutATerminalCallbackTimesOut() = runBlocking {
        assertFalse(awaitSpeechRecognition(timeoutMs = 20) { awaitCancellation() })
    }

    @Test fun longAnswersAreSplitWithoutDroppingTheEnding() {
        val answer = (1..80).joinToString(" ") { index -> "문장${index}입니다." }

        val chunks = speechChunks(answer, maxChars = 120)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 120 })
        assertEquals(answer, chunks.joinToString(" "))
        assertTrue(chunks.last().contains("문장80입니다."))
    }

    @Test fun speechChunksReplaceLinksAndFormattingBeforeSynthesis() {
        val chunks = speechChunks("**자세한 내용**은 https://example.com/item 에서 확인하세요.", maxChars = 120)

        assertEquals(listOf("자세한 내용은 링크 에서 확인하세요."), chunks)
    }
}
