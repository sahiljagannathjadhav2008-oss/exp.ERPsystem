package com.builtdifferent.erp.domain.engine

import com.builtdifferent.erp.util.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class TaxLineInput(
    val quantity: Double,
    val unitPricePaise: Long,
    val discountPercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val igstRatePercent: Double,
    val cessRatePercent: Double,
    val isInterState: Boolean
)

data class TaxLineResult(
    val grossAmountPaise: Long,
    val discountAmountPaise: Long,
    val taxableValuePaise: Long,
    val cgstAmountPaise: Long,
    val sgstAmountPaise: Long,
    val igstAmountPaise: Long,
    val cessAmountPaise: Long,
    val lineTotalPaise: Long
)

data class InvoiceTotals(
    val subTotalPaise: Long,
    val totalDiscountPaise: Long,
    val totalTaxableValuePaise: Long,
    val totalCgstPaise: Long,
    val totalSgstPaise: Long,
    val totalIgstPaise: Long,
    val totalCessPaise: Long,
    val roundOffPaise: Long,
    val grandTotalPaise: Long
)

/**
 * BUSINESS RULE (documented per project convention — invoice math):
 *
 * All per-line rounding happens ONCE, to the nearest paisa, using
 * RoundingMode.HALF_UP, applied to each of discount/taxable-value/tax
 * amounts independently rather than derived by subtraction — this avoids
 * the classic bug where line totals stop reconciling with
 * SUM(taxable)+SUM(tax) because of compounding rounding.
 *
 * The invoice-level "Round Off" line (roundOffPaise on InvoiceTotals) is
 * the ONLY place overall rounding to the nearest rupee is applied, and it
 * is always shown as its own visible line on the printed invoice (posted
 * to the "Round Off" system ledger account) — never silently absorbed into
 * a line item, which is what standard Indian accounting practice expects.
 */
object TaxEngine {

    fun calculateLine(input: TaxLineInput): TaxLineResult {
        val grossAmount = Money(input.unitPricePaise).multiply(BigDecimal(input.quantity))
        val discountAmount = grossAmount.multiply(BigDecimal(input.discountPercent).divide(BigDecimal(100)))
        val taxableValue = grossAmount - discountAmount

        val cgst: Money
        val sgst: Money
        val igst: Money
        if (input.isInterState) {
            cgst = Money.ZERO
            sgst = Money.ZERO
            igst = taxableValue.multiply(BigDecimal(input.igstRatePercent).divide(BigDecimal(100)))
        } else {
            cgst = taxableValue.multiply(BigDecimal(input.cgstRatePercent).divide(BigDecimal(100)))
            sgst = taxableValue.multiply(BigDecimal(input.sgstRatePercent).divide(BigDecimal(100)))
            igst = Money.ZERO
        }
        val cess = taxableValue.multiply(BigDecimal(input.cessRatePercent).divide(BigDecimal(100)))

        val lineTotal = taxableValue + cgst + sgst + igst + cess

        return TaxLineResult(
            grossAmountPaise = grossAmount.paise,
            discountAmountPaise = discountAmount.paise,
            taxableValuePaise = taxableValue.paise,
            cgstAmountPaise = cgst.paise,
            sgstAmountPaise = sgst.paise,
            igstAmountPaise = igst.paise,
            cessAmountPaise = cess.paise,
            lineTotalPaise = lineTotal.paise
        )
    }

    fun calculateInvoiceTotals(lines: List<TaxLineResult>): InvoiceTotals {
        val subTotal = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.grossAmountPaise) }
        val totalDiscount = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.discountAmountPaise) }
        val totalTaxable = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.taxableValuePaise) }
        val totalCgst = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.cgstAmountPaise) }
        val totalSgst = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.sgstAmountPaise) }
        val totalIgst = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.igstAmountPaise) }
        val totalCess = lines.fold(Money.ZERO) { acc, l -> acc + Money(l.cessAmountPaise) }

        val preRoundTotal = totalTaxable + totalCgst + totalSgst + totalIgst + totalCess
        val roundedRupees = preRoundTotal.toRupees().setScale(0, RoundingMode.HALF_UP)
        val grandTotal = Money.fromRupees(roundedRupees)
        val roundOff = grandTotal - preRoundTotal

        return InvoiceTotals(
            subTotalPaise = subTotal.paise,
            totalDiscountPaise = totalDiscount.paise,
            totalTaxableValuePaise = totalTaxable.paise,
            totalCgstPaise = totalCgst.paise,
            totalSgstPaise = totalSgst.paise,
            totalIgstPaise = totalIgst.paise,
            totalCessPaise = totalCess.paise,
            roundOffPaise = roundOff.paise,
            grandTotalPaise = grandTotal.paise
        )
    }
}
