package app.mydear.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import app.mydear.android.runtime.screen.ScreenShareState
import app.mydear.android.voice.VoiceState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatScreenStateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun firstAnswerShowsAVisibleLoadingStateBeforeTheFirstToken() {
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        messages = listOf(
                            ChatMessage("user", Role.User, "안녕"),
                            ChatMessage("assistant", Role.Assistant, ""),
                        ),
                        isGenerating = true,
                    ),
                    onDraftChange = {},
                    onSend = {},
                    onQuickPrompt = {},
                    onVoiceClick = {},
                    onVoiceCancel = {},
                    onUseSystemSpeech = {},
                    screenShareState = ScreenShareState.Inactive,
                    onScreenShareClick = {},
                    onUseOverAnotherApp = {},
                    onOpenSpeechSettings = {},
                )
            }
        }

        rule.onNodeWithTag("answer-loading").assertIsDisplayed()
        rule.onNodeWithText("첫 답변을 준비하고 있어요").assertIsDisplayed()
    }

    @Test fun manualScrollStopsAutoFollowWhileStreamingAndOffersAJumpBack() {
        val initialMessages = (1..18).flatMap { index ->
            listOf(
                ChatMessage("u-$index", Role.User, "질문 $index"),
                ChatMessage("a-$index", Role.Assistant, "답변 $index ".repeat(8)),
            )
        }
        var state by mutableStateOf(ChatUiState(messages = initialMessages, isGenerating = true))
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = state,
                    onDraftChange = {},
                    onSend = {},
                    onQuickPrompt = {},
                    onVoiceClick = {},
                    onVoiceCancel = {},
                    onUseSystemSpeech = {},
                    screenShareState = ScreenShareState.Inactive,
                    onScreenShareClick = {},
                    onUseOverAnotherApp = {},
                    onOpenSpeechSettings = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("chat-list").performTouchInput { swipeDown() }
        rule.runOnIdle {
            state = state.copy(
                messages = state.messages.dropLast(1) + state.messages.last().copy(
                    text = state.messages.last().text + "새로 생성된 문장",
                ),
            )
        }

        rule.onNodeWithTag("jump-to-latest").assertIsDisplayed().performClick()
        rule.onNodeWithText("새로 생성된 문장", substring = true).assertIsDisplayed()
        rule.waitUntil(3_000) {
            runCatching {
                rule.onNodeWithTag("chat-bottom-anchor").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        rule.onNodeWithTag("chat-bottom-anchor").assertIsDisplayed()
    }

    @Test fun speechModelPreparationShowsItsCancelMessageWhileListening() {
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        voiceState = VoiceState.Listening(TurnId("speech-download")),
                        notice = "한국어 음성 모델을 내려받고 있어요 · 끝내기를 누르면 취소돼요",
                    ),
                    onDraftChange = {},
                    onSend = {},
                    onQuickPrompt = {},
                    onVoiceClick = {},
                    onVoiceCancel = {},
                    onUseSystemSpeech = {},
                    screenShareState = ScreenShareState.Inactive,
                    onScreenShareClick = {},
                    onUseOverAnotherApp = {},
                    onOpenSpeechSettings = {},
                )
            }
        }

        rule.onNodeWithText("한국어 음성 모델을 내려받고 있어요", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("voice-stop").assertIsDisplayed()
    }

    @Test fun failedVoiceModeCanBeClosedAndOffersConsentedSystemFallback() {
        var stopped = false
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        voiceState = VoiceState.Failed("한국어 모델 없음"),
                        notice = "오프라인 한국어 음성 모델이 없어요.",
                        systemSpeechFallbackAvailable = true,
                    ),
                    onDraftChange = {},
                    onSend = {},
                    onQuickPrompt = {},
                    onVoiceClick = {},
                    onVoiceCancel = { stopped = true },
                    onUseSystemSpeech = {},
                    screenShareState = ScreenShareState.Inactive,
                    onScreenShareClick = {},
                    onUseOverAnotherApp = {},
                    onOpenSpeechSettings = {},
                )
            }
        }

        rule.onNodeWithTag("voice-stop").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(stopped) }
        rule.onNodeWithTag("system-speech-fallback").assertIsDisplayed().performClick()
        rule.onNodeWithText("기본 음성 입력을 사용할까요?").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
    }
}
