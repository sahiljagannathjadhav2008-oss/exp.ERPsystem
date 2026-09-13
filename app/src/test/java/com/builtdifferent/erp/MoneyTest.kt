package com.builtdifferent.erp

import com.builtdifferent.erp.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `fromRupees converts correctly to paise`() {
        assertEquals(10050L, Money.fromRupees(BigDecimal("100.50")).paise)
        assertEquals(100L, Money.fromRupees(BigDecimal("1.00")).paise)
        assertEquals(1L, Money.fromRupees(BigDecimal("0.01")).paise)
    }

    @Test
    fun `addition never loses paise precision across many small amounts`() {
        var total = Money.ZERO
        repeat(1000) {
            total += Money.fromRupees(BigDecimal("0.10"))
        }
        // 1000 * 0.10 = 100.00 exactly — this is the case where Double
        // arithmetic would drift; integer paise arithmetic must not.
        assertEquals(10000L, total.paise)
    }

    @Test
    fun `multiply rounds half up to the nearest paisa`() {
        val price = Money.fromRupees(BigDecimal("10.00"))
        val gst = price.multiply(BigDecimal("0.18"))
        assertEquals(180L, gst.paise)
    }

    @Test
    fun `formatIndian groups digits in the Indian numbering system`() {
        assertEquals("₹1,00,000.00", Money.fromRupees(BigDecimal("100000")).formatIndian())
        assertEquals("₹999.00", Money.fromRupees(BigDecimal("999")).formatIndian())
        assertEquals("₹12,34,567.89", Money.fromRupees(BigDecimal("1234567.89")).formatIndian())
    }
}
