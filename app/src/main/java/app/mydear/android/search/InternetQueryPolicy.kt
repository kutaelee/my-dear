package app.mydear.android.search

object InternetQueryPolicy {
    enum class DetailedKnowledgeKind { Leader, Winner }

    private val weatherTerms = listOf("날씨", "기온", "예보", "강수", "비 와", "비와", "눈 와", "눈와")
    private val publicKnowledgeTerms = listOf("대통령", "총리")
    private val unambiguousFreshnessSubjects = listOf(
        "최저임금", "환율", "주가", "뉴스", "속보", "경기 결과", "영업시간", "운행",
    )
    private val explicitSearchTerms = listOf("검색해", "인터넷에서", "웹에서")
    private val timeOrValueTerms = listOf("현재", "지금", "오늘", "내일", "최신", "올해", "얼마", "언제", "몇 시")
    private val personQuestionTerms = listOf("누구", "현직", "이름")
    private val publicFactQuestionTerms = listOf("누구", "무엇", "뭐야", "어디", "언제", "뜻")
    private val publicRoleTerms = listOf(
        "리더", "대표", "ceo", "회장", "사장", "감독", "가수", "배우", "작가", "멤버", "소속사",
        "우승자", "우승", "수상자", "챔피언", "1위",
    )
    private val privateOrDeviceTerms = listOf(
        "내 이름", "내 일정", "내 전화번호", "내 연락처", "내 주소", "내 설정", "우리 엄마", "우리 아빠", "우리 가족",
        "엄마는", "아빠는", "엄마 전화번호", "아빠 전화번호", "휴대폰 설정", "이 앱", "이 화면",
    )
    private val firstPersonPrefixes = listOf("내가", "나는", "제가", "저는")
    private val personalOrganizationPattern = Regex(
        "(?:내|제|저희|우리)(?:회사|동아리|모임|팀|반|가게|학교|교회|아파트)",
    )
    private val personalRelationPattern = Regex(
        "(?:내|제|저희|우리)(?:남편|아내|딸|아들|엄마|아빠|부모|가족|친구|동생|형|누나|언니|오빠|선생님|의사)",
    )
    private val genericSubjects = setOf("회사", "동아리", "모임", "팀", "가게", "학교", "반", "교회")
    private val localTaskTerms = listOf("만드는 법", "만들어", "정리해", "작성해", "짜줘")
    private val publicScheduleTerms = listOf("경기", "공연", "버스", "기차", "항공", "비행기", "행사")
    private val majorCityMayors = listOf("서울시장", "부산시장", "대구시장", "인천시장", "광주시장", "대전시장", "울산시장", "세종시장")

    fun shouldUseInternet(query: String, broadSearchAvailable: Boolean): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase()
        if (mustStayOnDevice(normalized)) return false
        return isPublicFallbackSupported(normalized) ||
            (broadSearchAvailable && (requiresFreshness(normalized) || explicitSearchTerms.any(normalized::contains)))
    }

    fun isWeather(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase()
        return weatherTerms.any(normalized::contains)
    }

    fun requiresFreshness(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase()
        if (weatherTerms.any(normalized::contains) || publicKnowledgeTerms.any(normalized::contains)) return true
        if (unambiguousFreshnessSubjects.any(normalized::contains)) return true
        val hasTimeOrValue = timeOrValueTerms.any(normalized::contains)
        val asksPerson = personQuestionTerms.any(normalized::contains)
        val isLocalTask = localTaskTerms.any(normalized::contains)
        val asksPublicOfficer = listOf("장관", "도지사").any(normalized::contains) && (asksPerson || hasTimeOrValue)
        val hasMayorOffice = majorCityMayors.any(normalized::contains) || Regex("[가-힣]{2,8}시\\s*시장").containsMatchIn(normalized)
        val asksCityMayor = hasMayorOffice && (asksPerson || hasTimeOrValue)
        val asksPrice = listOf("가격", "시세").any(normalized::contains) && hasTimeOrValue && !isLocalTask
        val asksPublicSchedule = normalized.contains("일정") && hasTimeOrValue && publicScheduleTerms.any(normalized::contains) && !isLocalTask
        return asksPublicOfficer || asksCityMayor || asksPrice || asksPublicSchedule
    }

    fun isPublicFallbackSupported(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query)
        return isWeather(normalized) || isPublicKnowledgeLookup(normalized)
    }

    fun needsExternalKnowledge(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query)
        if (mustStayOnDevice(normalized.lowercase())) return false
        return requiresFreshness(normalized) || isPublicKnowledgeLookup(normalized) || explicitSearchTerms.any(normalized::contains)
    }

    fun isPublicKnowledgeLookup(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase()
        if (normalized.length < 3) return false
        if (privateOrDeviceTerms.any(normalized::contains)) return false
        if (isPersonalContext(normalized)) return false
        if (publicKnowledgeTerms.any(normalized::contains)) return true
        val asksPublicFact = publicFactQuestionTerms.any(normalized::contains)
        val asksPublicRole = hasExplicitPublicSubject(normalized) &&
            (
                normalized.contains('?') || normalized.endsWith("는") || normalized.endsWith("은") ||
                    asksPublicFact || explicitSearchTerms.any(normalized::contains)
            )
        return asksPublicRole
    }

    private fun hasExplicitPublicSubject(normalized: String): Boolean {
        if (isPersonalContext(normalized)) return false
        return publicRoleTerms.any { role ->
            val roleIndex = normalized.indexOf(role)
            if (roleIndex < 0) return@any false
            val subject = normalized.substring(0, roleIndex)
                .trim()
                .removeSuffix("의")
                .trim()
            subject.length >= 2 && subject !in genericSubjects && subject.any(Char::isLetterOrDigit)
        }
    }

    private fun isPersonalContext(normalized: String): Boolean {
        val compact = normalized.replace(Regex("\\s+"), "")
        return firstPersonPrefixes.any(compact::startsWith) ||
            personalOrganizationPattern.containsMatchIn(compact) ||
            personalRelationPattern.containsMatchIn(compact)
    }

    private fun mustStayOnDevice(normalized: String): Boolean =
        privateOrDeviceTerms.any(normalized::contains) ||
            isPersonalContext(normalized)

    fun isUnsupportedByPublicFallback(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query)
        return needsExternalKnowledge(normalized) && !isPublicFallbackSupported(normalized)
    }

    fun isEllipticalFollowUp(query: String): Boolean {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase().replace(" ", "")
        return normalized in setOf("누구라고", "누구야", "그래서누구야", "이름이뭐라고", "뭐라고")
    }

    fun needsDetailedKnowledge(query: String): Boolean {
        return detailedKnowledgeKind(query) != null
    }

    fun detailedKnowledgeKind(query: String): DetailedKnowledgeKind? {
        val normalized = SearchBoundary.normalizedQuery(query).lowercase()
        return when {
            listOf("우승자", "우승", "수상자", "챔피언", "1위").any(normalized::contains) ->
                DetailedKnowledgeKind.Winner
            normalized.contains("리더") -> DetailedKnowledgeKind.Leader
            else -> null
        }
    }

    fun weatherLocation(query: String): String? {
        var location = SearchBoundary.normalizedQuery(query)
        val removable = weatherTerms + listOf(
            "오늘", "내일", "모레", "지금", "현재", "이번 주", "주간", "시간별", "알려 주세요",
            "알려주세요", "알려 줘", "알려줘", "어때요", "어때", "어떻게 돼", "어떻게돼", "확인해 주세요",
            "확인해주세요", "확인해줘", "좀",
        )
        removable.sortedByDescending(String::length).forEach { term ->
            location = location.replace(term, " ", ignoreCase = true)
        }
        return location
            .replace(Regex("[?!.。,，]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("의")
            .trim()
            .takeIf { it.length >= 2 }
    }

    fun knowledgeQuery(query: String): String {
        val normalized = SearchBoundary.normalizedQuery(query)
        val namesAnotherCountry = listOf("미국", "일본", "중국", "프랑스", "독일", "러시아", "영국", "캐나다")
            .any(normalized::contains)
        return when {
            normalized.contains("대통령") && !namesAnotherCountry -> "대한민국 대통령 목록"
            normalized.contains("총리") && !namesAnotherCountry -> "대한민국 국무총리"
            else -> normalized
        }
    }
}
