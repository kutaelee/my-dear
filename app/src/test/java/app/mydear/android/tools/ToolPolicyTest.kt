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

    @Test fun `expired or future approval fails closed`() {
        val now = 1_000_000L
        val expired = ToolProposal(ToolName.PrepareCall, mapOf("phone" to "021234567"), "turn", now - ToolPolicy.APPROVAL_TTL_MS - 1, "nonce-1234567890")
        val future = expired.copy(createdAtEpochMs = now + 5_001)
        assertFalse(ToolPolicy.evaluate(expired, now).allowed)
        assertFalse(ToolPolicy.evaluate(future, now).allowed)
    }
}
