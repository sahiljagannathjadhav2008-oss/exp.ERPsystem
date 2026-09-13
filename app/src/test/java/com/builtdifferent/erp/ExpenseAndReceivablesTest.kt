package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.*
import com.builtdifferent.erp.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseAndReceivablesTest {

    private lateinit var db: AppDatabase
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var salesInvoiceRepository: SalesInvoiceRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var receivablesPayablesRepository: ReceivablesPayablesRepository

    private var financialYearId: Long = 0
    private var cashAccountId: Long = 0
    private var rentAccountId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
    private lateinit var company: CompanyEntity
    private lateinit var customer: PartyEntity

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountingRepository = AccountingRepository(db)
        inventoryRepository = InventoryRepository(db)
        expenseRepository = ExpenseRepository(db, accountingRepository)
        salesInvoiceRepository = SalesInvoiceRepository(db, inventoryRepository, accountingRepository)
        paymentRepository = PaymentRepository(db, accountingRepository)
        receivablesPayablesRepository = ReceivablesPayablesRepository(db)

        cashAccountId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, isSystemAccount = true)
        )
        rentAccountId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Rent", group = AccountGroup.EXPENSE, subGroup = AccountSubGroup.INDIRECT_EXPENSE)
        )
        listOf(
            "Sales Account" to (AccountGroup.INCOME to AccountSubGroup.DIRECT_INCOME),
            "CGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "SGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "IGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "Round Off" to (AccountGroup.INCOME to AccountSubGroup.INDIRECT_INCOME)
        ).forEach { (name, groups) ->
            db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = name, group = groups.first, subGroup = groups.second, isSystemAccount = true))
        }

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co", stateCode = "27"))
        company = db.companyDao().getActiveCompany()!!
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
        warehouseId = db.warehouseDao().insert(WarehouseEntity(name = "Main", isDefault = true))

        val customerLedgerId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Test Customer", group = AccountGroup.ASSET, subGroup = AccountSubGroup.RECEIVABLE, normalBalanceIsDebit = true)
        )
        val customerId = db.partyDao().insert(
            PartyEntity(partyName = "Test Customer", type = PartyType.CUSTOMER, billingStateCode = "27", ledgerAccountId = customerLedgerId)
        )
        customer = db.partyDao().getById(customerId)!!

        val unitId = db.unitDao().insert(UnitEntity(unitCode = "NOS", displayLabel = "Pieces"))
        val hsnSacId = db.hsnSacDao().insert(HsnSacEntity(code = "1234", type = HsnSacType.HSN))
        productId = db.productDao().insert(ProductEntity(productName = "Widget", sku = "W1", unitId = unitId, hsnSacId = hsnSacId))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `recording an expense posts a balanced entry and a cash book row`() = runBlocking {
        expenseRepository.record(
            financialYearId = financialYearId,
            expenseLedgerAccountId = rentAccountId,
            amountPaise = 500000,
            expenseDateMillis = 1000L,
            mode = PaymentMode.CASH,
            bankAccountId = null,
            vendorPartyId = null,
            description = "Monthly rent"
        )

        val rentBalance = accountingRepository.getAccountBalance(rentAccountId, Long.MAX_VALUE)
        assertEquals(500000L, rentBalance)

        val cashBalance = accountingRepository.getAccountBalance(cashAccountId, Long.MAX_VALUE)
        assertEquals(-500000L, cashBalance)
    }

    @Test
    fun `receivables report reflects unpaid invoice and disappears once fully paid`() = runBlocking {
        val invoiceId = salesInvoiceRepository.postInvoice(
            company = company, party = customer, warehouseId = warehouseId, financialYearId = financialYearId,
            invoiceDateMillis = 1000L, isInterState = false, placeOfSupplyStateCode = "27",
            lines = listOf(
                SalesInvoiceLineDraft(
                    productId = productId, productName = "Widget", hsnSacCode = "1234",
                    quantity = 1.0, unitLabel = "NOS", unitPricePaise = 100000,
                    discountPercent = 0.0, cgstRatePercent = 9.0, sgstRatePercent = 9.0,
                    igstRatePercent = 18.0, cessRatePercent = 0.0
                )
            )
        )

        val receivablesBefore = receivablesPayablesRepository.getReceivablesByCustomer(financialYearId)
        assertEquals(1, receivablesBefore.size)
        assertEquals(118000L, receivablesBefore.first().outstandingPaise)

        val invoice = salesInvoiceRepository.getInvoice(invoiceId)!!
        paymentRepository.recordPaymentReceived(
            partyId = customer.partyId, partyLedgerAccountId = customer.ledgerAccountId!!, financialYearId = financialYearId,
            receiptNumber = "R1", paymentDateMillis = 2000L, amountPaise = invoice.grandTotalPaise,
            mode = PaymentMode.CASH, bankAccountId = null, referenceNumber = "", note = "",
            allocations = listOf(PaymentAllocationInput(AllocationTargetType.SALES_INVOICE, invoiceId, invoice.grandTotalPaise))
        )

        val receivablesAfter = receivablesPayablesRepository.getReceivablesByCustomer(financialYearId)
        assertEquals(0, receivablesAfter.size)
    }
}
