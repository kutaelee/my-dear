package app.mydear.android.tools

enum class ToolName { OpenChat, OpenSettings, ChangeTextSize, PrepareAlarm, PrepareCall, PrepareMessage, OpenMap }
enum class ToolRisk { ReadOnly, LocalChange, ExternalAction }
data class ToolProposal(
    val name: ToolName,
    val arguments: Map<String, String>,
    val originTurnId: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val nonce: String = java.util.UUID.randomUUID().toString(),
)
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

    fun evaluate(proposal: ToolProposal, nowEpochMs: Long = System.currentTimeMillis()): ToolDecision {
        val expected = knownArguments.getValue(proposal.name)
        if (proposal.arguments.keys.any { it !in expected }) return ToolDecision(false, false, "알 수 없는 입력")
        if (proposal.arguments.values.any { it.length > 512 }) return ToolDecision(false, false, "입력이 너무 김")
        if (proposal.originTurnId.isBlank() || proposal.originTurnId.length > 80) return ToolDecision(false, false, "대화 식별자가 올바르지 않음")
        if (proposal.nonce.length !in 16..80) return ToolDecision(false, false, "승인 식별자가 올바르지 않음")
        if (proposal.createdAtEpochMs > nowEpochMs + 5_000 || nowEpochMs - proposal.createdAtEpochMs > APPROVAL_TTL_MS) {
            return ToolDecision(false, false, "승인 시간이 만료됨")
        }
        when (proposal.name) {
            ToolName.PrepareCall -> if (!proposal.arguments.getValue("phone").matches(Regex("[0-9]{8,15}"))) return ToolDecision(false, false, "전화번호 형식 오류")
            ToolName.PrepareAlarm -> {
                val hour = proposal.arguments["hour"]?.toIntOrNull()
                val minute = proposal.arguments["minute"]?.toIntOrNull()
                if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) return ToolDecision(false, false, "시간 형식 오류")
            }
            ToolName.OpenMap -> if (proposal.arguments.getValue("query").isBlank()) return ToolDecision(false, false, "장소가 비어 있음")
            else -> Unit
        }
        return when (proposal.name) {
            ToolName.OpenChat, ToolName.OpenSettings -> ToolDecision(true, false, "앱 안에서 이동")
            ToolName.ChangeTextSize -> ToolDecision(true, false, "앱 안의 표시 설정")
            else -> ToolDecision(true, true, "외부 앱을 열기 전 확인 필요")
        }
    }

    const val APPROVAL_TTL_MS = 60_000L
}
