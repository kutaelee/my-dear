package app.mydear.android.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.MainActivity
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import app.mydear.android.runtime.screen.ScreenShareState
import app.mydear.android.ui.theme.MyDearTheme
import app.mydear.android.voice.VoiceState
import org.junit.Rule
import org.junit.Test

class VoiceFlowScreenshotAvdTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun captureVisibleLiveTranscript() {
        show(
            ChatUiState(
                voiceState = VoiceState.Listening(TurnId("screenshot-listening")),
                voiceTranscript = "오늘 와부읍 날씨 알려줘",
                draft = "오늘 와부읍 날씨 알려줘",
                notice = "듣고 있어요 · 아래 문장이 맞는지 확인해 주세요",
            ),
        )
        rule.onNodeWithTag("live-voice-transcript").assertIsDisplayed()
        saveScreenshot("preview9-live-stt-transcript.png")
    }

    @Test fun capturePersistentTtsRecovery() {
        show(
            ChatUiState(
                messages = listOf(
                    ChatMessage("user", Role.User, "오늘 와부읍 날씨 알려줘"),
                    ChatMessage("assistant", Role.Assistant, "오늘 와부읍은 대체로 맑고 낮 기온은 27도예요."),
                ),
                notice = "답변은 글로 표시했어요. 한국어 목소리를 받으면 다음부터 읽어드려요.",
                ttsSetupRequired = true,
            ),
        )
        rule.onNodeWithTag("tts-setup-card").assertIsDisplayed()
        saveScreenshot("preview9-tts-recovery.png")
    }

    private fun show(state: ChatUiState) {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MyDearTheme {
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
        }
        rule.waitForIdle()
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SystemClock.sleep(500)
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name"),
        ).use { it.readBytes() }
    }
}
