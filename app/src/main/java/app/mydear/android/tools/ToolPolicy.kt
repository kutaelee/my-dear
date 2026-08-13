package app.mydear.android.tools

enum class ToolName { OpenChat, OpenSettings, ChangeTextSize, PrepareAlarm, PrepareCall, PrepareMessage, OpenMap }
enum class ToolRisk { ReadOnly, LocalChange, ExternalAction }
data class ToolProposal(val name: ToolName, val arguments: Map<String, String>, val originTurnId: String)
data class ToolDecision(val allowed: Boolean, val requiresConfirmation: Boolean, val reason: String)

object ToolPolicy {
    private val knownArguments = mapOf(
        ToolName.OpenChat to emptySet(),
        ToolName.OpenSettings to emptySet(),
        ToolName.ChangeTextSize to setOf("size"),
        ToolName.PrepareAlarm to setOf("hour", "minute", "label"),
        ToolName.PrepareCall to setOf("phone"),
        ToolName.PrepareMessage to setOf("phone", "body"),
        ToolName.OpenMap to setOf("query"),
    )

    fun evaluate(proposal: ToolProposal): ToolDecision {
        val expected = knownArguments.getValue(proposal.name)
        if (proposal.arguments.keys.any { it !in expected }) return ToolDecision(false, false, "알 수 없는 입력")
        if (proposal.arguments.values.any { it.length > 512 }) return ToolDecision(false, false, "입력이 너무 김")
        return when (proposal.name) {
            ToolName.OpenChat, ToolName.OpenSettings -> ToolDecision(true, false, "앱 안에서 이동")
            ToolName.ChangeTextSize -> ToolDecision(true, false, "앱 안의 표시 설정")
            else -> ToolDecision(true, true, "외부 앱을 열기 전 확인 필요")
        }
    }
}
