package com.policyalarm.util

import kotlin.random.Random

/**
 * 표시용 자동 닉네임 생성기. 형용사 + 명사 + 4자리 숫자(예: "용감한바다거북3847").
 * 유니크를 강제하지 않으므로 충돌해도 무해하다. 외부 의존성 없는 순수 함수.
 */
object NicknameGenerator {

    private val adjectives = listOf(
        "용감한", "포근한", "잔잔한", "씩씩한", "엉뚱한", "느긋한", "다정한", "어진",
        "신나는", "보드란", "차분한", "재빠른", "현명한", "솔직한", "활기찬", "기특한",
        "상냥한", "당당한", "푸른", "맑은", "따뜻한", "수줍은", "부지런한", "명랑한",
        "고요한", "달콤한", "다부진", "튼튼한", "유쾌한", "사랑스런", "꿋꿋한", "정다운",
        "환한", "산뜻한", "고운", "어여쁜", "착한", "든든한", "야무진", "넉넉한",
    )

    private val nouns = listOf(
        "바다거북", "고라니", "다람쥐", "수달", "여우", "너구리", "고슴도치", "올빼미",
        "참새", "두루미", "기린", "판다", "펭귄", "돌고래", "강아지", "고양이",
        "토끼", "사슴", "오리", "거위", "두더지", "햄스터", "코알라", "원숭이",
        "북극곰", "물범", "청설모", "박새", "동박새", "딱따구리", "제비", "까치",
        "호랑이", "표범", "늑대", "사자", "코끼리", "하마", "기러기", "백조",
    )

    fun generate(random: Random = Random.Default): String {
        val adj = adjectives[random.nextInt(adjectives.size)]
        val noun = nouns[random.nextInt(nouns.size)]
        val num = random.nextInt(10000).toString().padStart(4, '0')
        return "$adj$noun$num"
    }
}
