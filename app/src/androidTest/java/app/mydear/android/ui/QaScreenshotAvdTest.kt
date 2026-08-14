package app.mydear.android.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import app.mydear.android.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QaScreenshotAvdTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var viewModel: VoiceChatViewModel

    @Before fun prepareState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OnboardingStore(context).markCompleted()
        context.getSharedPreferences(InternetPreferenceStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(InternetPreferenceStore.KEY_ENABLED, true)
            .commit()
        SeniorUiPreferenceStore(context).setLargeText(false)
        context.filesDir.resolve("private-chat").deleteRecursively()
        context.filesDir.resolve("private-memory").deleteRecursively()
        rule.activityRule.scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[VoiceChatViewModel::class.java]
            viewModel.setWebSearch(true)
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun captureStablePresidentAnswer() {
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("우리나라 대통령 이름")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(20_000) {
            !viewModel.state.value.isGenerating &&
                viewModel.state.value.messages.lastOrNull()?.text?.contains("현재 대한민국 대통령은 이재명입니다.") == true
        }
        hideKeyboard()
        saveScreenshot("preview4-president-with-source.png")
    }

    @Test fun capturePersonalMemoryReview() {
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("내 이름은 민수야 기억해줘")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("기억해둘게요: 내 이름은 민수야", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("내 정보 기억"))
        rule.onNodeWithText("내 정보 기억").performClick()
        rule.waitForIdle()
        saveScreenshot("preview4-personal-memory.png")
    }

    @Test fun capturePublicRoleAnswerWithReadableSource() {
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("리센느 리더는?")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(20_000) {
            !viewModel.state.value.isGenerating &&
                viewModel.state.value.messages.lastOrNull()?.text?.contains("리센느의 리더는 원이입니다.") == true
        }
        rule.onNodeWithText("리센느의 리더는 원이입니다.", substring = true).assertIsDisplayed()
        hideKeyboard()
        saveScreenshot("preview6-readable-search-source.png")
    }

    @Test fun capturePreview8ProductShortcutKeepsItsCategory() {
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("요즘 잘나가는 방향제 추천해줘")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(10_000) { !viewModel.state.value.isGenerating }
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("가성비 제품으로 링크줘")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(10_000) {
            !viewModel.state.value.isGenerating &&
                viewModel.state.value.messages.lastOrNull()?.provenance is app.mydear.android.domain.Provenance.ActionLink
        }
        rule.onNodeWithText("바로가기").assertIsDisplayed()
        rule.onNodeWithContentDescription("바로가기 링크: 가성비 방향제 찾아보기").assertIsDisplayed()
        hideKeyboard()
        saveScreenshot("preview8-correct-product-shortcut.png")
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SystemClock.sleep(600)
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name"),
        ).use { commandOutput ->
            commandOutput.readBytes()
        }
    }

    private fun hideKeyboard() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.currentFocus?.clearFocus()
            val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        rule.waitForIdle()
    }
}
