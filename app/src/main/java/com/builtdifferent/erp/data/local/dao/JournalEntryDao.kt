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
     *
     * Deliberately does NOT filter on e.isReversed: isReversed is purely a
     * descriptive marker on the ORIGINAL entry (see JournalEntryDao.markReversed
     * and AccountingRepository.reverseJournalEntry) — the original entry is
     * never deleted or excluded, only cancelled out by a second, equal-and-
     * opposite entry. Both the original and its reversal are real, immutable
     * postings and must both count toward every sum; excluding the original
     * once flagged would count the reversal's effect without its offsetting
     * counterpart, permanently skewing the balance by the reversed amount
     * instead of netting it to zero.
     */
    @Query(
        """SELECT COALESCE(SUM(l.debitPaise), 0) - COALESCE(SUM(l.creditPaise), 0)
           FROM journal_entry_lines l
           INNER JOIN journal_entries e ON e.journalEntryId = l.journalEntryId
           WHERE l.ledgerAccountId = :ledgerAccountId
           AND e.entryDateMillis <= :asOfMillis"""
    )
    suspend fun netDebitBalance(ledgerAccountId: Long, asOfMillis: Long): Long

    /** Same isReversed reasoning as netDebitBalance above: a reversed entry
     * still appears here, and so does its reversal — showing both is what
     * lets the reader see the correction happened, and the two lines net
     * to zero together, exactly as posted. */
    @Query(
        """SELECT l.*, e.entryDateMillis as entryDate, e.narration as narrationText FROM journal_entry_lines l
           INNER JOIN journal_entries e ON e.journalEntryId = l.journalEntryId
           WHERE l.ledgerAccountId = :ledgerAccountId
           AND e.entryDateMillis BETWEEN :fromMillis AND :toMillis
           ORDER BY e.entryDateMillis ASC, l.journalEntryLineId ASC"""
    )
    suspend fun getLedgerLinesInRange(ledgerAccountId: Long, fromMillis: Long, toMillis: Long): List<LedgerLineWithDate>

    /**
     * NOTE on `` `group` ``: LedgerAccountEntity.group has no @ColumnInfo
     * override, so Room names the actual SQLite column literally "group".
     * GROUP is a reserved SQL keyword, so any raw-SQL reference to that
     * column MUST be backtick-quoted (as done below) or SQLite's parser
     * reads it as the start of a GROUP BY clause instead of a column
     * reference and fails with "near "group": syntax error". This does
     * NOT rename the column or touch the real GROUP BY clause later in
     * this same query — it only quotes the one identifier that collides
     * with the keyword.
     *
     * Also deliberately does NOT filter on e.isReversed, for the same
     * reason documented on netDebitBalance above: a reversed entry and its
     * reversal must both be included so they net to zero together in the
     * trial balance, rather than one-sidedly skewing it.
     */
    @Query(
        """SELECT a.ledgerAccountId, a.accountName, a.`group` as accountGroup, a.normalBalanceIsDebit,
           COALESCE(SUM(l.debitPaise), 0) as totalDebit, COALESCE(SUM(l.creditPaise), 0) as totalCredit
           FROM ledger_accounts a
           LEFT JOIN journal_entry_lines l ON l.ledgerAccountId = a.ledgerAccountId
           LEFT JOIN journal_entries e ON e.journalEntryId = l.journalEntryId
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
