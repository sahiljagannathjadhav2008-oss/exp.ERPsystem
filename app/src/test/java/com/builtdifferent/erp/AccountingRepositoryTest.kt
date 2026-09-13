package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.AccountGroup
import com.builtdifferent.erp.data.local.entity.AccountSubGroup
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.FinancialYearEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.LedgerAccountEntity
import com.builtdifferent.erp.data.repository.AccountingRepository
import com.builtdifferent.erp.data.repository.UnbalancedJournalEntryException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountingRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var accountingRepository: AccountingRepository
    private var financialYearId: Long = 0
    private var cashAccountId: Long = 0
    private var salesAccountId: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountingRepository = AccountingRepository(db)

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co"))
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(
                companyId = companyId,
                label = "FY 2025-26",
                startDateMillis = 0L,
                endDateMillis = Long.MAX_VALUE,
                isCurrent = true
            )
        )
        cashAccountId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, normalBalanceIsDebit = true)
        )
        salesAccountId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Sales", group = AccountGroup.INCOME, subGroup = AccountSubGroup.DIRECT_INCOME, normalBalanceIsDebit = false)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `balanced entry posts successfully and lines sum to zero net`() = runBlocking {
        val entryId = accountingRepository.postJournalEntry(
            financialYearId = financialYearId,
            entryDateMillis = 1000L,
            narration = "Cash sale",
            sourceType = JournalSourceType.MANUAL,
            sourceId = 0,
            lines = listOf(
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashAccountId, debitPaise = 100000),
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = salesAccountId, creditPaise = 100000)
            )
        )
        val lines = db.journalEntryDao().getLines(entryId)
        val totalDebit = lines.sumOf { it.debitPaise }
        val totalCredit = lines.sumOf { it.creditPaise }
        assertEquals(totalDebit, totalCredit)
        assertEquals(100000L, totalDebit)
    }

    @Test
    fun `unbalanced entry is rejected and nothing is written`() = runBlocking {
        assertThrows(UnbalancedJournalEntryException::class.java) {
            runBlocking {
                accountingRepository.postJournalEntry(
                    financialYearId = financialYearId,
                    entryDateMillis = 1000L,
                    narration = "Broken entry",
                    sourceType = JournalSourceType.MANUAL,
                    sourceId = 0,
                    lines = listOf(
                        JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashAccountId, debitPaise = 100000),
                        JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = salesAccountId, creditPaise = 99999)
                    )
                )
            }
        }
        val balance = accountingRepository.getAccountBalance(cashAccountId, Long.MAX_VALUE)
        assertEquals(0L, balance)
    }

    @Test
    fun `reversing an entry swaps debit and credit and nets to zero`() = runBlocking {
        val originalId = accountingRepository.postJournalEntry(
            financialYearId = financialYearId,
            entryDateMillis = 1000L,
            narration = "Original",
            sourceType = JournalSourceType.MANUAL,
            sourceId = 0,
            lines = listOf(
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashAccountId, debitPaise = 50000),
                JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = salesAccountId, creditPaise = 50000)
            )
        )
        accountingRepository.reverseJournalEntry(originalId, 2000L, "Reversal")

        val balance = accountingRepository.getAccountBalance(cashAccountId, Long.MAX_VALUE)
        assertEquals(0L, balance)
    }
}
