package app.mydear.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.MainActivity
import app.mydear.android.runtime.screen.ScreenContextBoundaryStore
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
        context
            .getSharedPreferences(InternetPreferenceStore.FILE_NAME, 0)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(SeniorUiPreferenceStore.FILE_NAME, 0)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(ScreenContextBoundaryStore.FILE_NAME, 0)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("private-chat").deleteRecursively()
        context.filesDir.resolve("private-memory").deleteRecursively()
        context.filesDir.resolve("models/active-gemma-4-e2b-it-mobile.txt").delete()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    private fun skipTutorial() {
        rule.onNodeWithText("건너뛰기").performClick()
        rule.onNodeWithText("동의하고 켜기").performClick()
    }

    @Test fun primaryNavigationHasExactlyThreeVisibleDestinations() {
        skipTutorial()
        rule.onNodeWithTag("primary-navigation").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText("채팅").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("채팅 목록").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("설정").fetchSemanticsNodes().size)
    }

    @Test fun screenShareControlIsVisibleAndExplainsPrivacyBeforeSystemConsent() {
        skipTutorial()
        rule.onNodeWithTag("screen-share-control").assertIsDisplayed().performClick()
        rule.onNodeWithText("화면을 함께 볼까요?").assertIsDisplayed()
        rule.onNodeWithText("화면은 저장하거나 인터넷으로 보내지 않아요.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("화면 선택하기").assertIsDisplayed()
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
        rule.onNodeWithText("오프라인 AI가 아직 준비되지 않았어요", substring = true).assertIsDisplayed()
    }

    @Test fun settingsUsesPlainKoreanInsteadOfModelImplementationTerms() {
        skipTutorial()
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("오프라인 AI 준비"))
        rule.onNodeWithText("오프라인 AI 준비").assertIsDisplayed()
        rule.onNodeWithText("기본 AI").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("고급 AI").performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Supertonic", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("QAT", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("MTP", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("E2B", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("E4B", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun internetHelpIsOnByDefaultAndCanBeTurnedOff() {
        skipTutorial()
        assertEquals(0, rule.onAllNodesWithTag("internet-toggle").fetchSemanticsNodes().size)
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("internet-toggle"))
        rule.onNodeWithContentDescription("인터넷 도움").assertIsDisplayed()
        rule.onNodeWithTag("internet-toggle").assertIsOn().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("internet-toggle").assertIsOff()
    }

    @Test fun largeTextUsesSwitchInsteadOfNavigationArrow() {
        skipTutorial()
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("large-text-toggle"))
        rule.onNodeWithContentDescription("큰 글자").assertIsDisplayed()
        rule.onNodeWithTag("large-text-toggle").assertIsOff().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("large-text-toggle"))
        rule.onNodeWithTag("large-text-toggle").assertIsOn()
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

    @Test fun currentPresidentUsesStablePublicAnswerAndVisibleSourceWithoutModel() {
        skipTutorial()
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("우리나라 대통령 이름")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("현재 대한민국 대통령은 이재명입니다.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("현재 대한민국 대통령은 이재명입니다.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("출처").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("출처 링크: 대한민국 대통령 목록")
            .assertIsDisplayed()
            .assertHasClickAction()
        assertEquals(0, rule.onAllNodesWithText("[자료", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun explicitPersonalMemoryWorksWithoutModelAndCanBeReviewed() {
        skipTutorial()
        rule.onNodeWithText("메시지를 입력하세요").performTextInput("내 이름은 민수야 기억해줘")
        rule.onNodeWithContentDescription("메시지 보내기").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("기억해둘게요: 내 이름은 민수야", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("내 정보 기억"))
        rule.onNodeWithText("내 정보 기억").performClick()
        rule.onNodeWithText("• 내 이름은 민수야").assertIsDisplayed()
    }
}
