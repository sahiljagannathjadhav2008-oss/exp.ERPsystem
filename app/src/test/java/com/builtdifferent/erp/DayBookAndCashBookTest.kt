package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.*
import com.builtdifferent.erp.data.repository.*
import com.builtdifferent.erp.util.CsvUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DayBookAndCashBookTest {

    private lateinit var db: AppDatabase
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var dayBookRepository: DayBookRepository
    private lateinit var cashBookRepository: CashBookRepository
    private var financialYearId: Long = 0
    private var rentAccountId: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountingRepository = AccountingRepository(db)
        expenseRepository = ExpenseRepository(db, accountingRepository)
        dayBookRepository = DayBookRepository(db)
        cashBookRepository = CashBookRepository(db)

        db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, isSystemAccount = true))
        rentAccountId = db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Rent", group = AccountGroup.EXPENSE, subGroup = AccountSubGroup.INDIRECT_EXPENSE))

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co"))
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `day book lists posted entries with balanced lines`() = runBlocking {
        expenseRepository.record(
            financialYearId = financialYearId, expenseLedgerAccountId = rentAccountId, amountPaise = 15000,
            expenseDateMillis = 1000L, mode = PaymentMode.CASH, bankAccountId = null, vendorPartyId = null, description = "Rent"
        )

        val dayBook = dayBookRepository.getEntriesForFinancialYear(financialYearId)
        assertEquals(1, dayBook.size)
        val entry = dayBook.first()
        assertEquals(2, entry.lines.size)
        assertEquals(entry.lines.sumOf { it.debitPaise }, entry.lines.sumOf { it.creditPaise })
    }

    @Test
    fun `cash book shows a running balance across two transactions`() = runBlocking {
        expenseRepository.record(
            financialYearId = financialYearId, expenseLedgerAccountId = rentAccountId, amountPaise = 15000,
            expenseDateMillis = 1000L, mode = PaymentMode.CASH, bankAccountId = null, vendorPartyId = null, description = "Rent 1"
        )
        expenseRepository.record(
            financialYearId = financialYearId, expenseLedgerAccountId = rentAccountId, amountPaise = 5000,
            expenseDateMillis = 2000L, mode = PaymentMode.CASH, bankAccountId = null, vendorPartyId = null, description = "Rent 2"
        )

        val rows = cashBookRepository.getRows(0L, Long.MAX_VALUE)
        assertEquals(2, rows.size)
        assertEquals(-15000L, rows[0].runningBalancePaise)
        assertEquals(-20000L, rows[1].runningBalancePaise)
    }
}

class CsvUtilTest {

    @Test
    fun `csv round-trips a value containing a comma and quotes`() {
        val tempFile = File.createTempFile("test", ".csv")
        tempFile.deleteOnExit()

        CsvUtil.writeCsv(
            tempFile,
            header = listOf("Name", "Note"),
            rows = listOf(listOf("Acme, Inc.", "Said \"hello\" to us"))
        )

        val (header, rows) = CsvUtil.readCsv(tempFile)
        assertEquals(listOf("Name", "Note"), header)
        assertEquals(listOf("Acme, Inc.", "Said \"hello\" to us"), rows.first())
    }
}
