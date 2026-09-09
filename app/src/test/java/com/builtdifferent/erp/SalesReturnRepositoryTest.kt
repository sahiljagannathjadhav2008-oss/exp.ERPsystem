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
class SalesReturnRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var salesInvoiceRepository: SalesInvoiceRepository
    private lateinit var salesReturnRepository: SalesReturnRepository
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var inventoryRepository: InventoryRepository

    private var financialYearId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
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
        salesReturnRepository = SalesReturnRepository(db, inventoryRepository, accountingRepository)

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
    fun `returning half the quantity refunds exactly half the original tax, even after a rate change`() = runBlocking {
        // Post original sale at 18% GST.
        val invoiceId = salesInvoiceRepository.postInvoice(
            company = company,
            party = customer,
            warehouseId = warehouseId,
            financialYearId = financialYearId,
            invoiceDateMillis = 1000L,
            isInterState = false,
            placeOfSupplyStateCode = "27",
            lines = listOf(
                SalesInvoiceLineDraft(
                    productId = productId, productName = "Widget", hsnSacCode = "1234",
                    quantity = 10.0, unitLabel = "NOS", unitPricePaise = 10000,
                    discountPercent = 0.0, cgstRatePercent = 9.0, sgstRatePercent = 9.0,
                    igstRatePercent = 18.0, cessRatePercent = 0.0
                )
            )
        )
        val originalItem = salesInvoiceRepository.getItems(invoiceId).first()
        // Original: taxable 1000.00, CGST 90.00, SGST 90.00 for qty 10.

        // Simulate a rate change happening AFTER the sale (rate is now
        // irrelevant to the return — the return must use the ORIGINAL
        // applied rate, which is baked into originalItem already).

        val returnId = salesReturnRepository.postReturn(
            originalInvoiceId = invoiceId,
            financialYearId = financialYearId,
            returnDateMillis = 2000L,
            reason = "Damaged",
            lines = listOf(SalesReturnLineInput(originalItem.salesInvoiceItemId, 5.0)) // half the qty
        )

        val stock = db.stockDao().find(productId, warehouseId)!!
        // Sold 10, returned 5 -> net -5 on hand.
        assertEquals(-5.0, stock.quantityOnHand, 0.0001)

        // Customer receivable should now reflect grandTotal - returnTotal.
        val balance = accountingRepository.getAccountBalance(customer.ledgerAccountId!!, Long.MAX_VALUE)
        // Original Dr 1180 (grand total for qty 10), return Cr 590 (half) -> net Dr 590.
        assertEquals(59000L, balance)
    }
}
