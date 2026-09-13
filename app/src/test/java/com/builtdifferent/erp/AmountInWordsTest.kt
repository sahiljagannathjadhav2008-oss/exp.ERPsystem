package com.builtdifferent.erp

import com.builtdifferent.erp.util.AmountInWords
import com.builtdifferent.erp.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class AmountInWordsTest {

    @Test
    fun `converts a lakh-scale amount using Indian numbering`() {
        val words = AmountInWords.convert(Money.fromRupees(BigDecimal("123456")))
        assertEquals("One Lakh Twenty Three Thousand Four Hundred Fifty Six Rupees Only", words)
    }

    @Test
    fun `includes paise when present`() {
        val words = AmountInWords.convert(Money.fromRupees(BigDecimal("100.50")))
        assertEquals("One Hundred Rupees and Fifty Paise Only", words)
    }

    @Test
    fun `zero rupees still renders Zero`() {
        val words = AmountInWords.convert(Money.ZERO)
        assertEquals("Zero Rupees Only", words)
    }
}
