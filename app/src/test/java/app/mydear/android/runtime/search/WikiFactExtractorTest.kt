package app.mydear.android.runtime.search

import org.junit.Assert.assertEquals
import org.junit.Test

class WikiFactExtractorTest {
    @Test fun leaderNameIsReadOnlyFromTheSameMemberRow() {
        val wikitext = """
            |-
            |'''첫째'''
            |* 포지션: 보컬
            |-
            |'''원이'''
            |* 본명: 정원이
            |* 포지션: 리더
            |-
            |'''셋째'''
            |* 포지션: 댄서
        """.trimIndent()

        assertEquals("원이", WikiFactExtractor.leader(wikitext))
    }

    @Test fun winnerFieldRemovesWikiLinkMarkup() {
        assertEquals("송가인", WikiFactExtractor.winner("|우승자 = [[송가인]]<!-- 주석 -->"))
    }

    @Test fun styledRowSeparatorCannotJoinAnotherMembersNameToLeaderPosition() {
        val wikitext = """
            |- style="background: white"
            |'''첫째'''
            |* 포지션: 보컬
            |- class="leader-row"
            |'''원이'''
            |* 포지션: 리더
        """.trimIndent()

        assertEquals("원이", WikiFactExtractor.leader(wikitext))
    }
}
