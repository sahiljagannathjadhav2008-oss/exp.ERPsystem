package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.*
import com.builtdifferent.erp.data.repository.AccountingRepository
import com.builtdifferent.erp.data.repository.PaymentAllocationInput
import com.builtdifferent.erp.data.repository.PaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var accountingRepository: AccountingRepository
    private var financialYearId: Long = 0
    private var customerLedgerAccountId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountingRepository = AccountingRepository(db)
        paymentRepository = PaymentRepository(db, accountingRepository)

        db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, isSystemAccount = true)
        )
        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co"))
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
        customerLedgerAccountId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Test Customer", group = AccountGroup.ASSET, subGroup = AccountSubGroup.RECEIVABLE, normalBalanceIsDebit = true)
        )
        customerId = db.partyDao().insert(
            PartyEntity(partyName = "Test Customer", type = PartyType.CUSTOMER, ledgerAccountId = customerLedgerAccountId)
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `partial payment leaves the correct outstanding balance`() = runBlocking {
        val fakeInvoiceId = 999L
        val grandTotal = 118000L // Rs 1180

        val outstandingBefore = paymentRepository.getOutstanding(AllocationTargetType.SALES_INVOICE, fakeInvoiceId, grandTotal)
        assertEquals(118000L, outstandingBefore)

        paymentRepository.recordPaymentReceived(
            partyId = customerId,
            partyLedgerAccountId = customerLedgerAccountId,
            financialYearId = financialYearId,
            receiptNumber = "RCPT-1",
            paymentDateMillis = 1000L,
            amountPaise = 50000L,
            mode = PaymentMode.CASH,
            bankAccountId = null,
            referenceNumber = "",
            note = "",
            allocations = listOf(PaymentAllocationInput(AllocationTargetType.SALES_INVOICE, fakeInvoiceId, 50000L))
        )

        val outstandingAfter = paymentRepository.getOutstanding(AllocationTargetType.SALES_INVOICE, fakeInvoiceId, grandTotal)
        assertEquals(68000L, outstandingAfter)

        val balance = accountingRepository.getAccountBalance(customerLedgerAccountId, Long.MAX_VALUE)
        assertEquals(-50000L, balance)
    }

    @Test
    fun `full payment brings outstanding to zero`() = runBlocking {
        val fakeInvoiceId = 1000L
        val grandTotal = 100000L

        paymentRepository.recordPaymentReceived(
            partyId = customerId,
            partyLedgerAccountId = customerLedgerAccountId,
            financialYearId = financialYearId,
            receiptNumber = "RCPT-2",
            paymentDateMillis = 1000L,
            amountPaise = grandTotal,
            mode = PaymentMode.CASH,
            bankAccountId = null,
            referenceNumber = "",
            note = "",
            allocations = listOf(PaymentAllocationInput(AllocationTargetType.SALES_INVOICE, fakeInvoiceId, grandTotal))
        )

        val outstanding = paymentRepository.getOutstanding(AllocationTargetType.SALES_INVOICE, fakeInvoiceId, grandTotal)
        assertEquals(0L, outstanding)
    }
}
