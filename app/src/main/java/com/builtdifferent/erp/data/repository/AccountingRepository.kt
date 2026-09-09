package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.JournalEntryEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType

class UnbalancedJournalEntryException(debit: Long, credit: Long) :
    IllegalStateException("Journal entry is not balanced: debit=$debit paise, credit=$credit paise")

/**
 * The ONLY function in this app that writes to journal_entries /
 * journal_entry_lines. Every module that affects accounting (sales
 * invoices, purchase invoices, payments, expenses, income, stock
 * adjustments with a cost impact) calls postJournalEntry rather than
 * inserting rows directly, so the "total debit == total credit" invariant
 * (Section 63's required unit test) is enforced in exactly one place and
 * cannot be bypassed by a future module forgetting to check it.
 */
class AccountingRepository(private val db: AppDatabase) {

    private val journalEntryDao = db.journalEntryDao()

    suspend fun postJournalEntry(
        financialYearId: Long,
        entryDateMillis: Long,
        narration: String,
        sourceType: JournalSourceType,
        sourceId: Long,
        lines: List<JournalEntryLineEntity>
    ): Long {
        val totalDebit = lines.sumOf { it.debitPaise }
        val totalCredit = lines.sumOf { it.creditPaise }
        if (totalDebit != totalCredit) {
            throw UnbalancedJournalEntryException(totalDebit, totalCredit)
        }
        if (lines.size < 2) {
            throw IllegalArgumentException("A journal entry needs at least two lines")
        }

        return db.withTransaction {
            val entryId = journalEntryDao.insertEntry(
                JournalEntryEntity(
                    financialYearId = financialYearId,
                    entryDateMillis = entryDateMillis,
                    narration = narration,
                    sourceType = sourceType,
                    sourceId = sourceId
                )
            )
            journalEntryDao.insertLines(lines.map { it.copy(journalEntryId = entryId) })
            entryId
        }
    }

    /** Posts a reversing entry (swap debit/credit on every line of the
     * original) and marks the original as reversed — used by
     * cancellation flows instead of ever deleting or editing a posted
     * entry, preserving the audit trail. */
    suspend fun reverseJournalEntry(originalEntryId: Long, reversalDateMillis: Long, narration: String): Long {
        val original = journalEntryDao.getEntry(originalEntryId)
            ?: throw IllegalArgumentException("Journal entry $originalEntryId not found")
        val originalLines = journalEntryDao.getLines(originalEntryId)

        return db.withTransaction {
            val reversalId = journalEntryDao.insertEntry(
                JournalEntryEntity(
                    financialYearId = original.financialYearId,
                    entryDateMillis = reversalDateMillis,
                    narration = narration,
                    sourceType = original.sourceType,
                    sourceId = original.sourceId,
                    reversalOfJournalEntryId = originalEntryId
                )
            )
            journalEntryDao.insertLines(
                originalLines.map {
                    it.copy(
                        journalEntryLineId = 0,
                        journalEntryId = reversalId,
                        debitPaise = it.creditPaise,
                        creditPaise = it.debitPaise
                    )
                }
            )
            journalEntryDao.markReversed(originalEntryId)
            reversalId
        }
    }

    suspend fun getAccountBalance(ledgerAccountId: Long, asOfMillis: Long): Long =
        journalEntryDao.netDebitBalance(ledgerAccountId, asOfMillis)
}
