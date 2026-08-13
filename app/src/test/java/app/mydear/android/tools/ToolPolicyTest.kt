package app.mydear.android.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {
    @Test fun `external action requires confirmation`() {
        val decision = ToolPolicy.evaluate(ToolProposal(ToolName.PrepareCall, mapOf("phone" to "021234567"), "turn"))
        assertTrue(decision.allowed)
        assertTrue(decision.requiresConfirmation)
    }

    @Test fun `unknown fields fail closed`() {
        val decision = ToolPolicy.evaluate(ToolProposal(ToolName.PrepareCall, mapOf("phone" to "021234567", "execute" to "true"), "turn"))
        assertFalse(decision.allowed)
    }
}
