package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.JournalEntryEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import kotlinx.coroutines.flow.first

data class DayBookEntry(
    val entry: JournalEntryEntity,
    val lines: List<JournalEntryLineEntity>,
    val accountNamesByLedgerId: Map<Long, String>
)

/**
 * The Day Book is simply every journal entry in date order with its lines
 * expanded — it is a VIEW over journal_entries/journal_entry_lines, not a
 * separate record of transactions, so it is impossible for the Day Book to
 * show something the ledger doesn't agree with.
 */
class DayBookRepository(private val db: AppDatabase) {

    suspend fun getEntriesForFinancialYear(financialYearId: Long): List<DayBookEntry> {
        val entryList = db.journalEntryDao().observeForFinancialYear(financialYearId).first()
        val allAccounts = db.ledgerAccountDao().observeAll().first()
        val namesById = allAccounts.associate { it.ledgerAccountId to it.accountName }

        return entryList.map { entry ->
            val lines = db.journalEntryDao().getLines(entry.journalEntryId)
            DayBookEntry(entry = entry, lines = lines, accountNamesByLedgerId = namesById)
        }
    }
}
