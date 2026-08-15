package app.mydear.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalActionRouterTest {
    @Test fun `phone request becomes confirmation-only proposal`() {
        val proposal = LocalActionRouter.route("010 1234 5678로 전화해줘", "turn-1")!!
        assertEquals(ToolName.PrepareCall, proposal.name)
        assertEquals("01012345678", proposal.arguments["phone"])
        assertTrue(ToolPolicy.evaluate(proposal).requiresConfirmation)
    }

    @Test fun `Korean afternoon alarm is normalized`() {
        val proposal = LocalActionRouter.route("오후 3시 20분에 알람 맞춰줘", "turn-2")!!
        assertEquals("15", proposal.arguments["hour"])
        assertEquals("20", proposal.arguments["minute"])
    }

    @Test fun `ordinary chat never invents a tool`() {
        assertNull(LocalActionRouter.route("오늘 기분이 좋아", "turn-3"))
    }
}
