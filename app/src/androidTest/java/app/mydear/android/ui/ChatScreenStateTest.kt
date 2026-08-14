package app.mydear.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
                        messages = (1..20).map { index ->
                            ChatMessage("voice-history-$index", Role.Assistant, "이전 답변 $index ".repeat(6))
                        },
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
        rule.onNodeWithText("휴대폰 기본 음성 입력으로 다시 듣기").assertIsDisplayed()
        rule.onNodeWithTag("system-speech-fallback").assertIsDisplayed().performClick()
        rule.onNodeWithText("기본 음성 입력을 사용할까요?").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
    }

    @Test fun listeningShowsTheRecognizedWordsOutsideTheInputField() {
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        voiceState = VoiceState.Listening(TurnId("live-transcript")),
                        voiceTranscript = "오늘 와부읍 날씨 알려줘",
                        draft = "오늘 와부읍 날씨 알려줘",
                        notice = "듣고 있어요 · 아래 문장이 맞는지 확인해 주세요",
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

        rule.onNodeWithTag("live-voice-transcript").assertIsDisplayed()
        rule.onNodeWithText("들은 말").assertIsDisplayed()
        rule.onNodeWithTag("voice-stop").assertIsDisplayed()
    }

    @Test fun longLiveTranscriptAtDoubleFontScaleDoesNotPushComposerControlsOffScreen() {
        rule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f),
            ) {
                MaterialTheme {
                    ChatScreen(
                        padding = PaddingValues(),
                        state = ChatUiState(
                            voiceState = VoiceState.Listening(TurnId("long-live-transcript")),
                            voiceTranscript = "긴 문장을 계속 말해도 들은 말 영역은 세 줄만 보여야 합니다. ".repeat(20),
                            draft = "긴 문장을 계속 말해도 들은 말 영역은 세 줄만 보여야 합니다.",
                            notice = "듣고 있어요",
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
        }

        rule.onNodeWithTag("live-voice-transcript").assertIsDisplayed()
        rule.onNodeWithTag("screen-share-control").assertIsDisplayed()
        rule.onNodeWithTag("voice-control").assertIsDisplayed()
    }

    @Test fun missingTtsOffersAVisibleInstallActionBesideTheComposer() {
        var installRequested = false
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        messages = listOf(ChatMessage("answer", Role.Assistant, "글로 만든 답변")),
                        ttsInstalled = false,
                        ttsSetupRequired = true,
                        notice = "답변은 글로 표시했어요. 한국어 목소리를 받으면 다음부터 읽어드려요.",
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
                    onInstallTts = { installRequested = true },
                )
            }
        }

        rule.onNodeWithTag("tts-setup-card").assertIsDisplayed()
        rule.onNodeWithText("목소리 받기").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(installRequested) }
    }

    @Test fun playbackFailureKeepsTheWrittenAnswerAndOffersRetry() {
        var retryRequested = false
        rule.setContent {
            MaterialTheme {
                ChatScreen(
                    padding = PaddingValues(),
                    state = ChatUiState(
                        messages = listOf(ChatMessage("answer", Role.Assistant, "재생에 실패해도 남아 있는 답변")),
                        ttsInstalled = true,
                        ttsPlaybackFailed = true,
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
                    onRetryTts = { retryRequested = true },
                )
            }
        }

        rule.onNodeWithText("재생에 실패해도 남아 있는 답변").assertIsDisplayed()
        rule.onNodeWithTag("retry-tts-playback").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(retryRequested) }
    }
}
