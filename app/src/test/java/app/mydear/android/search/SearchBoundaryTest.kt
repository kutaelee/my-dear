package app.mydear.android.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBoundaryTest {
    @Test fun `query is bounded and control characters removed`() {
        val value = "  날씨\u0000" + "가".repeat(600)
        val normalized = SearchBoundary.normalizedQuery(value)
        assertEquals(512, normalized.length)
        assertFalse(normalized.contains('\u0000'))
    }

    @Test fun `only clean https source urls pass`() {
        assertTrue(SearchBoundary.isAllowedSourceUrl("https://weather.go.kr/a"))
        assertFalse(SearchBoundary.isAllowedSourceUrl("http://weather.go.kr/a"))
        assertFalse(SearchBoundary.isAllowedSourceUrl("file:///etc/passwd"))
        assertFalse(SearchBoundary.isAllowedSourceUrl("https://user:pass@example.com/a"))
    }
}
