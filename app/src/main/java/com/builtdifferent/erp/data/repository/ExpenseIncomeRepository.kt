package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.BankTransactionEntity
import com.builtdifferent.erp.data.local.entity.BankTransactionType
import com.builtdifferent.erp.data.local.entity.CashTransactionEntity
import com.builtdifferent.erp.data.local.entity.CashTransactionType
import com.builtdifferent.erp.data.local.entity.ExpenseEntity
import com.builtdifferent.erp.data.local.entity.IncomeEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.PaymentMode
import kotlinx.coroutines.flow.Flow

/**
 * Direct (non-invoice) expenses — rent, salaries, electricity. Posts
 * Dr <expense ledger>, Cr Cash/Bank, and logs the same Cash/Bank Book row
 * PaymentRepository would, using the identical resolution rule so both
 * entry points feed one consistent day-book view.
 */
class ExpenseRepository(private val db: AppDatabase, private val accountingRepository: AccountingRepository) {
    private val expenseDao = db.expenseDao()

    fun observeForFinancialYear(financialYearId: Long): Flow<List<ExpenseEntity>> = expenseDao.observeForFinancialYear(financialYearId)

    suspend fun record(
        financialYearId: Long,
        expenseLedgerAccountId: Long,
        amountPaise: Long,
        expenseDateMillis: Long,
        mode: PaymentMode,
        bankAccountId: Long?,
        vendorPartyId: Long?,
        description: String
    ): Long = db.withTransaction {
        val cashOrBankAccountId = if (mode == PaymentMode.CASH || bankAccountId == null) {
            db.ledgerAccountDao().findByName("Cash")!!.ledgerAccountId
        } else {
            db.bankAccountDao().getById(bankAccountId)?.ledgerAccountId
                ?: db.ledgerAccountDao().findByName("Cash")!!.ledgerAccountId
        }

        val expenseId = expenseDao.insert(
            ExpenseEntity(
                expenseDateMillis = expenseDateMillis,
                financialYearId = financialYearId,
                expenseLedgerAccountId = expenseLedgerAccountId,
                amountPaise = amountPaise,
                paymentMode = mode,
                bankAccountId = bankAccountId,
                vendorPartyId = vendorPartyId,
                description = description
            )
        )

        accountingRepository.postJournalEntry(
            financialYearId = financialYearId,
            entryDateMillis = expenseDateMillis,
            narration = "Expense: $description",
            sourceType = JournalSourceType.EXPENSE,
            sourceId = expenseId,
            lines = listOf(
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = expenseLedgerAccountId, debitPaise = amountPaise),
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashOrBankAccountId, creditPaise = amountPaise)
            )
        )

        if (mode == PaymentMode.CASH || bankAccountId == null) {
            db.cashTransactionDao().insert(
                CashTransactionEntity(
                    type = CashTransactionType.PAYMENT, amountPaise = amountPaise, transactionDateMillis = expenseDateMillis,
                    sourceType = JournalSourceType.EXPENSE, sourceId = expenseId, note = description
                )
            )
        } else {
            db.bankTransactionDao().insert(
                BankTransactionEntity(
                    bankAccountId = bankAccountId, type = BankTransactionType.WITHDRAWAL, amountPaise = amountPaise,
                    transactionDateMillis = expenseDateMillis, sourceType = JournalSourceType.EXPENSE, sourceId = expenseId, note = description
                )
            )
        }
        expenseId
    }
}

/** Direct (non-invoice) income — interest received, misc income. Mirrors
 * ExpenseRepository: Dr Cash/Bank, Cr <income ledger>. */
class IncomeRepository(private val db: AppDatabase, private val accountingRepository: AccountingRepository) {
    private val incomeDao = db.incomeDao()

    fun observeForFinancialYear(financialYearId: Long): Flow<List<IncomeEntity>> = incomeDao.observeForFinancialYear(financialYearId)

    suspend fun record(
        financialYearId: Long,
        incomeLedgerAccountId: Long,
        amountPaise: Long,
        incomeDateMillis: Long,
        mode: PaymentMode,
        bankAccountId: Long?,
        sourcePartyId: Long?,
        description: String
    ): Long = db.withTransaction {
        val cashOrBankAccountId = if (mode == PaymentMode.CASH || bankAccountId == null) {
            db.ledgerAccountDao().findByName("Cash")!!.ledgerAccountId
        } else {
            db.bankAccountDao().getById(bankAccountId)?.ledgerAccountId
                ?: db.ledgerAccountDao().findByName("Cash")!!.ledgerAccountId
        }

        val incomeId = incomeDao.insert(
            IncomeEntity(
                incomeDateMillis = incomeDateMillis,
                financialYearId = financialYearId,
                incomeLedgerAccountId = incomeLedgerAccountId,
                amountPaise = amountPaise,
                receiptMode = mode,
                bankAccountId = bankAccountId,
                sourcePartyId = sourcePartyId,
                description = description
            )
        )

        accountingRepository.postJournalEntry(
            financialYearId = financialYearId,
            entryDateMillis = incomeDateMillis,
            narration = "Income: $description",
            sourceType = JournalSourceType.INCOME,
            sourceId = incomeId,
            lines = listOf(
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashOrBankAccountId, debitPaise = amountPaise),
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = incomeLedgerAccountId, creditPaise = amountPaise)
            )
        )

        if (mode == PaymentMode.CASH || bankAccountId == null) {
            db.cashTransactionDao().insert(
                CashTransactionEntity(
                    type = CashTransactionType.RECEIPT, amountPaise = amountPaise, transactionDateMillis = incomeDateMillis,
                    sourceType = JournalSourceType.INCOME, sourceId = incomeId, note = description
                )
            )
        } else {
            db.bankTransactionDao().insert(
                BankTransactionEntity(
                    bankAccountId = bankAccountId, type = BankTransactionType.DEPOSIT, amountPaise = amountPaise,
                    transactionDateMillis = incomeDateMillis, sourceType = JournalSourceType.INCOME, sourceId = incomeId, note = description
                )
            )
        }
        incomeId
    }
}
