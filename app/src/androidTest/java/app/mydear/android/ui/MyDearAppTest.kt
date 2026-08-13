package app.mydear.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mydear.android.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MyDearAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

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
        rule.onNodeWithText("요금제 보기").performClick()
        rule.onNodeWithText("내새끼 플러스 · 월 3,900원 예정").assertIsDisplayed()
        rule.onNodeWithText("실제 결제는 아직 연결되지 않았어요. 온라인 기능을 쓸 때만 필요한 질문이 서버로 전송돼요.").assertIsDisplayed()
    }
}
