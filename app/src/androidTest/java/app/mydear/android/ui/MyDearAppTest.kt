package app.mydear.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class MyDearAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetOnboarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context
            .getSharedPreferences(OnboardingStore.FILE_NAME, 0)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("private-chat").deleteRecursively()
        context.filesDir.resolve("models/active-gemma-4-e2b-it-mobile.txt").delete()
        context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm").delete()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    private fun skipTutorial() = rule.onNodeWithText("건너뛰기").performClick()

    @Test fun primaryNavigationHasExactlyThreeVisibleDestinations() {
        skipTutorial()
        rule.onNodeWithTag("primary-navigation").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText("채팅").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("채팅 목록").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("설정").fetchSemanticsNodes().size)
    }

    @Test fun tutorialCanAdvanceAndBeSkippedWithoutPermissions() {
        rule.onNodeWithText("1단계").assertIsDisplayed()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("2단계").assertIsDisplayed()
        rule.onNodeWithText("건너뛰기").performClick()
        rule.onNodeWithContentDescription("메시지 보내기").assertIsDisplayed()
    }

    @Test fun settingsShowsPreviewOnlySubscriptionPlan() {
        skipTutorial()
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithText("요금제 보기").performScrollTo().performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("내새끼 플러스 · 월 3,900원 예정"))
        rule.onNodeWithText("내새끼 플러스 · 월 3,900원 예정").assertIsDisplayed()
        rule.onNodeWithText("실제 결제는 아직 연결되지 않았어요. 온라인 기능을 쓸 때만 필요한 질문이 서버로 전송돼요.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun textChatRemainsAvailableWithoutDownloadedModel() {
        skipTutorial()
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("안녕하세요")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.onNodeWithText("기본형 E2B 모델이 아직 설치되지 않았어요", substring = true).assertIsDisplayed()
    }

    @Test fun externalPhoneActionRequiresExplicitConfirmation() {
        skipTutorial()
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("010 1234 5678로 전화해줘")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.onNodeWithText("실행 전 확인").assertIsDisplayed()
        rule.onNodeWithText("01012345678 번호로 전화 앱을 열까요?").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
        assertEquals(0, rule.onAllNodesWithText("실행 전 확인").fetchSemanticsNodes().size)
    }
}
