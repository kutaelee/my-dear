package app.mydear.android.ui

import android.graphics.Bitmap
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.os.SystemClock
import androidx.compose.ui.test.hasText
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
            ViewModelProvider(activity)[VoiceChatViewModel::class.java].setWebSearch(true)
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun captureStablePresidentAnswer() {
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("우리나라 대통령 이름")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("현재 대한민국 대통령은 이재명입니다.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
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

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SystemClock.sleep(600)
        val target = checkNotNull(instrumentation.targetContext.externalCacheDir).resolve(name)
        target.outputStream().use { output ->
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, output)
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
