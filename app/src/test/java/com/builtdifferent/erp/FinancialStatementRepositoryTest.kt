package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.*
import com.builtdifferent.erp.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinancialStatementRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var salesInvoiceRepository: SalesInvoiceRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var financialStatementRepository: FinancialStatementRepository

    private var financialYearId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
    private var rentAccountId: Long = 0
    private lateinit var company: CompanyEntity
    private lateinit var customer: PartyEntity

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryRepository = InventoryRepository(db)
        accountingRepository = AccountingRepository(db)
        salesInvoiceRepository = SalesInvoiceRepository(db, inventoryRepository, accountingRepository)
        expenseRepository = ExpenseRepository(db, accountingRepository)
        financialStatementRepository = FinancialStatementRepository(db)

        db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, isSystemAccount = true))
        db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Sales Account", group = AccountGroup.INCOME, subGroup = AccountSubGroup.DIRECT_INCOME, isSystemAccount = true))
        listOf("CGST Output", "SGST Output", "IGST Output").forEach {
            db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = it, group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true))
        }
        db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Round Off", group = AccountGroup.INCOME, subGroup = AccountSubGroup.INDIRECT_INCOME, isSystemAccount = true))
        rentAccountId = db.ledgerAccountDao().insert(LedgerAccountEntity(accountName = "Rent", group = AccountGroup.EXPENSE, subGroup = AccountSubGroup.INDIRECT_EXPENSE))

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co", stateCode = "27"))
        company = db.companyDao().getActiveCompany()!!
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
        warehouseId = db.warehouseDao().insert(WarehouseEntity(name = "Main", isDefault = true))

        val customerLedgerId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Customer", group = AccountGroup.ASSET, subGroup = AccountSubGroup.RECEIVABLE, normalBalanceIsDebit = true)
        )
        val customerId = db.partyDao().insert(PartyEntity(partyName = "Customer", type = PartyType.CUSTOMER, billingStateCode = "27", ledgerAccountId = customerLedgerId))
        customer = db.partyDao().getById(customerId)!!

        val unitId = db.unitDao().insert(UnitEntity(unitCode = "NOS", displayLabel = "Pieces"))
        val hsnSacId = db.hsnSacDao().insert(HsnSacEntity(code = "1234", type = HsnSacType.HSN))
        productId = db.productDao().insert(ProductEntity(productName = "Widget", sku = "W1", unitId = unitId, hsnSacId = hsnSacId))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `trial balance stays balanced after mixed sale and expense postings`() = runBlocking {
        salesInvoiceRepository.postInvoice(
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
        expenseRepository.record(
            financialYearId = financialYearId, expenseLedgerAccountId = rentAccountId, amountPaise = 20000,
            expenseDateMillis = 1500L, mode = PaymentMode.CASH, bankAccountId = null, vendorPartyId = null, description = "Rent"
        )

        val trialBalance = financialStatementRepository.getTrialBalance(0L, Long.MAX_VALUE)
        assertTrue(trialBalance.isBalanced)
        assertEquals(trialBalance.totalDebitPaise, trialBalance.totalCreditPaise)
    }

    @Test
    fun `profit and loss nets sales against expenses for net profit`() = runBlocking {
        salesInvoiceRepository.postInvoice(
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
        expenseRepository.record(
            financialYearId = financialYearId, expenseLedgerAccountId = rentAccountId, amountPaise = 20000,
            expenseDateMillis = 1500L, mode = PaymentMode.CASH, bankAccountId = null, vendorPartyId = null, description = "Rent"
        )

        val pnl = financialStatementRepository.getProfitAndLoss(0L, Long.MAX_VALUE)
        assertEquals(100000L, pnl.totalIncomePaise)
        assertEquals(20000L, pnl.totalExpensePaise)
        assertEquals(80000L, pnl.netProfitPaise)
    }
}
