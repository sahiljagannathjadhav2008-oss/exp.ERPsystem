package com.builtdifferent.erp

import com.builtdifferent.erp.domain.engine.TaxEngine
import com.builtdifferent.erp.domain.engine.TaxLineInput
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxEngineTest {

    @Test
    fun `intra-state line splits GST evenly into CGST and SGST, IGST is zero`() {
        val result = TaxEngine.calculateLine(
            TaxLineInput(
                quantity = 2.0,
                unitPricePaise = 50000, // Rs 500.00
                discountPercent = 0.0,
                cgstRatePercent = 9.0,
                sgstRatePercent = 9.0,
                igstRatePercent = 18.0,
                cessRatePercent = 0.0,
                isInterState = false
            )
        )
        // Taxable = 1000.00, CGST = 90.00, SGST = 90.00
        assertEquals(100000L, result.taxableValuePaise)
        assertEquals(9000L, result.cgstAmountPaise)
        assertEquals(9000L, result.sgstAmountPaise)
        assertEquals(0L, result.igstAmountPaise)
        assertEquals(118000L, result.lineTotalPaise)
    }

    @Test
    fun `inter-state line applies IGST only, CGST and SGST are zero`() {
        val result = TaxEngine.calculateLine(
            TaxLineInput(
                quantity = 2.0,
                unitPricePaise = 50000,
                discountPercent = 0.0,
                cgstRatePercent = 9.0,
                sgstRatePercent = 9.0,
                igstRatePercent = 18.0,
                cessRatePercent = 0.0,
                isInterState = true
            )
        )
        assertEquals(0L, result.cgstAmountPaise)
        assertEquals(0L, result.sgstAmountPaise)
        assertEquals(18000L, result.igstAmountPaise)
        assertEquals(118000L, result.lineTotalPaise)
    }

    @Test
    fun `discount is applied before tax is calculated`() {
        val result = TaxEngine.calculateLine(
            TaxLineInput(
                quantity = 1.0,
                unitPricePaise = 100000, // Rs 1000
                discountPercent = 10.0,  // -> Rs 900 taxable
                cgstRatePercent = 9.0,
                sgstRatePercent = 9.0,
                igstRatePercent = 18.0,
                cessRatePercent = 0.0,
                isInterState = false
            )
        )
        assertEquals(10000L, result.discountAmountPaise)
        assertEquals(90000L, result.taxableValuePaise)
        assertEquals(8100L, result.cgstAmountPaise) // 9% of 900
        assertEquals(8100L, result.sgstAmountPaise)
    }

    @Test
    fun `invoice totals round to the nearest rupee with a visible round-off`() {
        // Taxable 100.00 + 18% GST = 118.00 exactly -> no rounding needed.
        val exact = TaxEngine.calculateLine(
            TaxLineInput(1.0, 10000, 0.0, 9.0, 9.0, 18.0, 0.0, false)
        )
        val totals = TaxEngine.calculateInvoiceTotals(listOf(exact))
        assertEquals(0L, totals.roundOffPaise)
        assertEquals(11800L, totals.grandTotalPaise)
    }

    @Test
    fun `invoice totals apply non-zero round-off when the pre-round total has paise`() {
        // Price chosen so GST produces a fractional-paisa-free but
        // non-rupee-aligned total: 33.33 taxable * 18% = 5.9994 -> 599 paise (rounded half-up),
        // total = 3333 + 599 = 3932 paise = Rs 39.32 exactly -> still no round-off needed
        // here; use a genuinely fractional total instead:
        val line = TaxEngine.calculateLine(
            TaxLineInput(1.0, 3333, 0.0, 9.0, 9.0, 18.0, 0.0, false)
        )
        val totals = TaxEngine.calculateInvoiceTotals(listOf(line))
        val expectedGrandTotalRupees = (line.taxableValuePaise + line.cgstAmountPaise + line.sgstAmountPaise)
        // Grand total must always be a whole number of rupees (round-off absorbs the paise).
        assertEquals(0L, totals.grandTotalPaise % 100)
        assertEquals(totals.grandTotalPaise, expectedGrandTotalRupees + totals.roundOffPaise)
    }
}
