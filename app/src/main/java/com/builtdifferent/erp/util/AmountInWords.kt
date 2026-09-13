package com.builtdifferent.erp.util

/**
 * Converts a rupee amount to words using the Indian numbering system
 * (Lakh/Crore, not Million/Billion) — e.g. 123456.78 -> "One Lakh Twenty
 * Three Thousand Four Hundred Fifty Six Rupees and Seventy Eight Paise
 * Only". Every posted invoice prints this, as is standard on Indian tax
 * invoices (and what Tally-style layouts always include).
 */
object AmountInWords {

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    fun convert(money: Money): String {
        val rupees = money.paise / 100
        val paise = money.paise % 100
        val rupeeWords = if (rupees == 0L) "Zero" else convertIndianGroups(rupees)
        val sb = StringBuilder()
        sb.append(rupeeWords).append(" Rupees")
        if (paise > 0) {
            sb.append(" and ").append(convertBelowThousand(paise.toInt())).append(" Paise")
        }
        sb.append(" Only")
        return sb.toString()
    }

    private fun convertIndianGroups(number: Long): String {
        if (number == 0L) return ""
        val crore = number / 10000000
        val lakh = (number / 100000) % 100
        val thousand = (number / 1000) % 100
        val hundredsBlock = number % 1000

        val parts = mutableListOf<String>()
        if (crore > 0) parts.add(convertBelowThousand(crore.toInt()) + " Crore")
        if (lakh > 0) parts.add(convertBelowHundred(lakh.toInt()) + " Lakh")
        if (thousand > 0) parts.add(convertBelowHundred(thousand.toInt()) + " Thousand")
        if (hundredsBlock > 0) parts.add(convertBelowThousand(hundredsBlock.toInt()))
        return parts.joinToString(" ")
    }

    private fun convertBelowHundred(number: Int): String {
        return if (number < 20) ones[number] else {
            val t = tens[number / 10]
            val o = ones[number % 10]
            if (o.isEmpty()) t else "$t $o"
        }
    }

    private fun convertBelowThousand(number: Int): String {
        val hundredsDigit = number / 100
        val remainder = number % 100
        return when {
            hundredsDigit > 0 && remainder > 0 -> "${ones[hundredsDigit]} Hundred ${convertBelowHundred(remainder)}"
            hundredsDigit > 0 -> "${ones[hundredsDigit]} Hundred"
            else -> convertBelowHundred(remainder)
        }
    }
}
