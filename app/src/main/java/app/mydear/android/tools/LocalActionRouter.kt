package app.mydear.android.tools

object LocalActionRouter {
    private val phone = Regex("([0-9][0-9 -]{7,15})(?:.{0,12}?)(?:전화|통화)|(?:전화|통화)(?:.{0,12}?)([0-9][0-9 -]{7,15})")
    private val alarm = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?")

    fun route(text: String, turnId: String): ToolProposal? {
        val normalized = text.replace(Regex("\\s+"), " ").trim().take(512)
        phone.find(normalized)?.let { match ->
            val number = match.groupValues.drop(1).first { it.isNotBlank() }.filter(Char::isDigit)
            return ToolProposal(ToolName.PrepareCall, mapOf("phone" to number), turnId)
        }
        if (("알람" in normalized || "깨워" in normalized) && alarm.containsMatchIn(normalized)) {
            val match = alarm.find(normalized)!!
            var hour = match.groupValues[2].toInt()
            if (match.groupValues[1] == "오후" && hour < 12) hour += 12
            if (match.groupValues[1] == "오전" && hour == 12) hour = 0
            val minute = match.groupValues[3].ifBlank { "0" }.toInt()
            return ToolProposal(
                ToolName.PrepareAlarm,
                mapOf("hour" to hour.toString(), "minute" to minute.toString(), "label" to "내새끼 알림"),
                turnId,
            )
        }
        if ("설정 열" in normalized || normalized == "설정") return ToolProposal(ToolName.OpenSettings, emptyMap(), turnId)
        if ("지도" in normalized && ("열" in normalized || "찾" in normalized)) {
            val query = normalized.substringBefore("지도").trim().ifBlank { normalized.replace("지도", "").trim() }.take(120)
            return ToolProposal(ToolName.OpenMap, mapOf("query" to query), turnId)
        }
        return null
    }
}
