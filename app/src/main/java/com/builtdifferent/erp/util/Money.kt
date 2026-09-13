package com.builtdifferent.erp.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * BUSINESS RULE (documented per project convention):
 *
 * Every monetary amount anywhere in this application — invoice line items,
 * tax amounts, ledger balances, payment amounts — is persisted in the
 * database as a Long representing PAISE (1 Rupee = 100 paise), never as a
 * Double/Float and never as a formatted string.
 *
 * Reasoning: floating point binary representation cannot exactly represent
 * values like 0.1 or 33.33, and repeated addition/subtraction across an
 * accounting ledger will drift and eventually fail to balance (debit total
 * != credit total) purely from representation error, not business logic
 * error. Integer arithmetic on paise has no such drift.
 *
 * All rounding (GST rounding, invoice round-off) happens explicitly and
 * exactly once, using RoundingMode.HALF_UP on rupee amounts, per the
 * "round-off" line item required by the invoice engine — never implicitly
 * via float truncation.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    fun toRupees(): BigDecimal = BigDecimal(paise).divide(BigDecimal(100))

    operator fun plus(other: Money): Money = Money(paise + other.paise)
    operator fun minus(other: Money): Money = Money(paise - other.paise)
    operator fun unaryMinus(): Money = Money(-paise)

    /** Multiply by a rate (e.g. quantity, or a GST percentage /100),
     * rounding to the nearest paisa. */
    fun multiply(factor: BigDecimal): Money {
        val result = BigDecimal(paise).multiply(factor).setScale(0, RoundingMode.HALF_UP)
        return Money(result.toLong())
    }

    fun isZero(): Boolean = paise == 0L
    fun isNegative(): Boolean = paise < 0L

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun formatIndian(): String {
        val rupees = toRupees().setScale(2, RoundingMode.HALF_UP)
        val negative = rupees.signum() < 0
        val plain = rupees.abs().toPlainString()
        val parts = plain.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else "00"

        // Indian digit grouping: last 3 digits, then groups of 2.
        val sb = StringBuilder()
        val len = intPart.length
        if (len <= 3) {
            sb.append(intPart)
        } else {
            sb.append(intPart.substring(len - 3))
            var remaining = intPart.substring(0, len - 3)
            while (remaining.isNotEmpty()) {
                val take = if (remaining.length >= 2) 2 else 1
                sb.insert(0, remaining.substring(remaining.length - take))
                sb.insert(take, ',')
                remaining = remaining.substring(0, remaining.length - take)
            }
            if (sb.startsWith(",")) sb.deleteCharAt(0)
        }
        return (if (negative) "-₹" else "₹") + sb.toString() + "." + decPart
    }

    companion object {
        val ZERO = Money(0)

        fun fromRupees(rupees: BigDecimal): Money =
            Money(rupees.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong())

        fun fromRupees(rupees: Double): Money = fromRupees(BigDecimal.valueOf(rupees))
    }
}
