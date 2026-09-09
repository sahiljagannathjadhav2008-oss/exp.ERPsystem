package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.JournalEntryEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Insert
    suspend fun insertEntry(entry: JournalEntryEntity): Long

    @Insert
    suspend fun insertLines(lines: List<JournalEntryLineEntity>): List<Long>

    @Query("SELECT * FROM journal_entries WHERE journalEntryId = :id")
    suspend fun getEntry(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entry_lines WHERE journalEntryId = :journalEntryId")
    suspend fun getLines(journalEntryId: Long): List<JournalEntryLineEntity>

    @Query(
        """SELECT * FROM journal_entries WHERE financialYearId = :financialYearId
           ORDER BY entryDateMillis DESC, journalEntryId DESC"""
    )
    fun observeForFinancialYear(financialYearId: Long): Flow<List<JournalEntryEntity>>

    @Query("UPDATE journal_entries SET isReversed = 1 WHERE journalEntryId = :id")
    suspend fun markReversed(id: Long)

    /**
     * The ledger account's running balance as of a point in time: total
     * debits minus total credits (for a normally-debit account) via lines
     * joined through their parent entry's date, so a manual back-dated
     * entry is correctly included in every later balance, not just
     * entries inserted after it.
     */
    @Query(
        """SELECT COALESCE(SUM(l.debitPaise), 0) - COALESCE(SUM(l.creditPaise), 0)
           FROM journal_entry_lines l
           INNER JOIN journal_entries e ON e.journalEntryId = l.journalEntryId
           WHERE l.ledgerAccountId = :ledgerAccountId
           AND e.entryDateMillis <= :asOfMillis
           AND e.isReversed = 0"""
    )
    suspend fun netDebitBalance(ledgerAccountId: Long, asOfMillis: Long): Long

    @Query(
        """SELECT l.*, e.entryDateMillis as entryDate, e.narration as narrationText FROM journal_entry_lines l
           INNER JOIN journal_entries e ON e.journalEntryId = l.journalEntryId
           WHERE l.ledgerAccountId = :ledgerAccountId
           AND e.entryDateMillis BETWEEN :fromMillis AND :toMillis
           AND e.isReversed = 0
           ORDER BY e.entryDateMillis ASC, l.journalEntryLineId ASC"""
    )
    suspend fun getLedgerLinesInRange(ledgerAccountId: Long, fromMillis: Long, toMillis: Long): List<LedgerLineWithDate>

    @Query(
        """SELECT a.ledgerAccountId, a.accountName, a.group as accountGroup, a.normalBalanceIsDebit,
           COALESCE(SUM(l.debitPaise), 0) as totalDebit, COALESCE(SUM(l.creditPaise), 0) as totalCredit
           FROM ledger_accounts a
           LEFT JOIN journal_entry_lines l ON l.ledgerAccountId = a.ledgerAccountId
           LEFT JOIN journal_entries e ON e.journalEntryId = l.journalEntryId AND e.isReversed = 0
               AND e.entryDateMillis BETWEEN :fromMillis AND :toMillis
           WHERE a.isActive = 1
           GROUP BY a.ledgerAccountId
           ORDER BY a.accountName ASC"""
    )
    suspend fun getTrialBalanceRows(fromMillis: Long, toMillis: Long): List<TrialBalanceRow>
}

data class LedgerLineWithDate(
    val journalEntryLineId: Long,
    val journalEntryId: Long,
    val ledgerAccountId: Long,
    val debitPaise: Long,
    val creditPaise: Long,
    val note: String,
    val entryDate: Long,
    val narrationText: String
)

data class TrialBalanceRow(
    val ledgerAccountId: Long,
    val accountName: String,
    val accountGroup: String,
    val normalBalanceIsDebit: Boolean,
    val totalDebit: Long,
    val totalCredit: Long
)
