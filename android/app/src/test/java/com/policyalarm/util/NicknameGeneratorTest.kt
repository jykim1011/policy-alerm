package com.policyalarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NicknameGeneratorTest {

    @Test
    fun `생성된 닉네임은 형용사+명사+4자리 숫자로 끝난다`() {
        val nick = NicknameGenerator.generate(Random(0))
        // 마지막 4글자는 숫자
        val digits = nick.takeLast(4)
        assertEquals(4, digits.length)
        assertTrue("끝 4자리는 숫자여야 함: $nick", digits.all { it.isDigit() })
        // 숫자 앞에는 한글(형용사+명사)이 있어야 함
        val word = nick.dropLast(4)
        assertTrue("형용사+명사 부분이 비어있음: $nick", word.length >= 2)
        assertTrue("한글이어야 함: $nick", word.all { it in '가'..'힣' })
    }

    @Test
    fun `같은 시드는 같은 닉네임을 같은 결과로 만든다`() {
        assertEquals(NicknameGenerator.generate(Random(42)), NicknameGenerator.generate(Random(42)))
    }

    @Test
    fun `다른 시드는 대체로 다른 닉네임을 만든다`() {
        val a = NicknameGenerator.generate(Random(1))
        val b = NicknameGenerator.generate(Random(2))
        assertTrue("서로 다른 시드인데 동일: $a", a != b)
    }
}
