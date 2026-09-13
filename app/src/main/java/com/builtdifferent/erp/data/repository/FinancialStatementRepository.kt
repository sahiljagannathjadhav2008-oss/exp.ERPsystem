package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.dao.TrialBalanceRow
import com.builtdifferent.erp.data.local.entity.AccountGroup

data class TrialBalanceLine(
    val ledgerAccountId: Long,
    val accountName: String,
    val group: AccountGroup,
    val debitBalancePaise: Long,
    val creditBalancePaise: Long
)

data class TrialBalance(
    val lines: List<TrialBalanceLine>,
    val totalDebitPaise: Long,
    val totalCreditPaise: Long
) {
    /** Must always be true for real data — the only path that can write
     * journal lines (AccountingRepository.postJournalEntry) refuses to
     * commit an unbalanced set, so this flag is effectively the "double-
     * entry integrity" self-audit check made visible on the report. */
    val isBalanced: Boolean get() = totalDebitPaise == totalCreditPaise
}

data class ProfitAndLoss(
    val incomeLines: List<TrialBalanceLine>,
    val expenseLines: List<TrialBalanceLine>,
    val totalIncomePaise: Long,
    val totalExpensePaise: Long,
    val netProfitPaise: Long
)

data class BalanceSheet(
    val assetLines: List<TrialBalanceLine>,
    val liabilityLines: List<TrialBalanceLine>,
    val equityLines: List<TrialBalanceLine>,
    val totalAssetsPaise: Long,
    val totalLiabilitiesPaise: Long,
    val totalEquityPaise: Long,
    /** Current-period net profit, added to equity as retained earnings so
     * the sheet balances without a separate year-end closing-entry step. */
    val currentPeriodNetProfitPaise: Long
) {
    val isBalanced: Boolean get() = totalAssetsPaise == (totalLiabilitiesPaise + totalEquityPaise + currentPeriodNetProfitPaise)
}

/**
 * All three statements are derived from the exact same source query
 * (JournalEntryDao.getTrialBalanceRows), just filtered/grouped differently
 * — Trial Balance shows every account, P&L keeps only INCOME/EXPENSE,
 * Balance Sheet keeps only ASSET/LIABILITY/EQUITY. There is no separate
 * calculation per statement that could drift from the ledger or from each
 * other; they are three views of one number set.
 */
class FinancialStatementRepository(private val db: AppDatabase) {

    private suspend fun rawRows(fromMillis: Long, toMillis: Long): List<TrialBalanceRow> =
        db.journalEntryDao().getTrialBalanceRows(fromMillis, toMillis)

    private fun toLine(row: TrialBalanceRow): TrialBalanceLine {
        val net = row.totalDebit - row.totalCredit
        return TrialBalanceLine(
            ledgerAccountId = row.ledgerAccountId,
            accountName = row.accountName,
            group = AccountGroup.valueOf(row.accountGroup),
            debitBalancePaise = if (net > 0) net else 0,
            creditBalancePaise = if (net < 0) -net else 0
        )
    }

    suspend fun getTrialBalance(fromMillis: Long, toMillis: Long): TrialBalance {
        val lines = rawRows(fromMillis, toMillis).map(::toLine).filter { it.debitBalancePaise != 0L || it.creditBalancePaise != 0L }
        return TrialBalance(
            lines = lines,
            totalDebitPaise = lines.sumOf { it.debitBalancePaise },
            totalCreditPaise = lines.sumOf { it.creditBalancePaise }
        )
    }

    suspend fun getProfitAndLoss(fromMillis: Long, toMillis: Long): ProfitAndLoss {
        val lines = rawRows(fromMillis, toMillis).map(::toLine)
        val incomeLines = lines.filter { it.group == AccountGroup.INCOME && it.creditBalancePaise != 0L }
        val expenseLines = lines.filter { it.group == AccountGroup.EXPENSE && it.debitBalancePaise != 0L }
        val totalIncome = incomeLines.sumOf { it.creditBalancePaise }
        val totalExpense = expenseLines.sumOf { it.debitBalancePaise }
        return ProfitAndLoss(
            incomeLines = incomeLines,
            expenseLines = expenseLines,
            totalIncomePaise = totalIncome,
            totalExpensePaise = totalExpense,
            netProfitPaise = totalIncome - totalExpense
        )
    }

    /** Balance Sheet is always as-of a single date, so it reads from 0
     * (since inception). [periodStartMillis] identifies the start of the
     * CURRENT financial year so only this year's net profit — not prior
     * years, already folded into equity via opening balances — is added
     * as retained earnings. */
    suspend fun getBalanceSheet(periodStartMillis: Long, asOfMillis: Long): BalanceSheet {
        val lines = rawRows(0L, asOfMillis).map(::toLine)
        val assetLines = lines.filter { it.group == AccountGroup.ASSET && it.debitBalancePaise != 0L }
        val liabilityLines = lines.filter { it.group == AccountGroup.LIABILITY && it.creditBalancePaise != 0L }
        val equityLines = lines.filter { it.group == AccountGroup.EQUITY && it.creditBalancePaise != 0L }

        val currentPeriodPnl = getProfitAndLoss(periodStartMillis, asOfMillis)

        return BalanceSheet(
            assetLines = assetLines,
            liabilityLines = liabilityLines,
            equityLines = equityLines,
            totalAssetsPaise = assetLines.sumOf { it.debitBalancePaise },
            totalLiabilitiesPaise = liabilityLines.sumOf { it.creditBalancePaise },
            totalEquityPaise = equityLines.sumOf { it.creditBalancePaise },
            currentPeriodNetProfitPaise = currentPeriodPnl.netProfitPaise
        )
    }
}
