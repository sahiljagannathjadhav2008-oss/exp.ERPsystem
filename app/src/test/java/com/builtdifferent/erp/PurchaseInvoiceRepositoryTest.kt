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
class PurchaseInvoiceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var accountingRepository: AccountingRepository

    private var financialYearId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
    private var supplier: PartyEntity = PartyEntity(partyName = "", type = PartyType.SUPPLIER)
    private lateinit var company: CompanyEntity

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryRepository = InventoryRepository(db)
        accountingRepository = AccountingRepository(db)
        purchaseInvoiceRepository = PurchaseInvoiceRepository(db, inventoryRepository, accountingRepository)

        // Seed system accounts the way DatabaseSeeder would.
        listOf(
            "Purchase Account" to (AccountGroup.EXPENSE to AccountSubGroup.DIRECT_EXPENSE),
            "CGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
            "SGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
            "IGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
            "Round Off" to (AccountGroup.INCOME to AccountSubGroup.INDIRECT_INCOME)
        ).forEach { (name, groups) ->
            db.ledgerAccountDao().insert(
                LedgerAccountEntity(accountName = name, group = groups.first, subGroup = groups.second, isSystemAccount = true)
            )
        }

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co", stateCode = "27"))
        company = db.companyDao().getActiveCompany()!!
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
        warehouseId = db.warehouseDao().insert(WarehouseEntity(name = "Main", isDefault = true))

        val supplierLedgerId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Test Supplier", group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.PAYABLE, normalBalanceIsDebit = false)
        )
        val supplierId = db.partyDao().insert(
            PartyEntity(partyName = "Test Supplier", type = PartyType.SUPPLIER, billingStateCode = "27", ledgerAccountId = supplierLedgerId)
        )
        supplier = db.partyDao().getById(supplierId)!!

        val unitId = db.unitDao().insert(UnitEntity(unitCode = "NOS", displayLabel = "Pieces"))
        val hsnSacId = db.hsnSacDao().insert(HsnSacEntity(code = "1234", type = HsnSacType.HSN))
        productId = db.productDao().insert(
            ProductEntity(productName = "Widget", sku = "W1", unitId = unitId, hsnSacId = hsnSacId, purchasePricePaise = 10000)
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `posting a purchase invoice increases stock and posts a balanced debit-heavy entry`() = runBlocking {
        val invoiceId = purchaseInvoiceRepository.postInvoice(
            company = company,
            supplier = supplier,
            warehouseId = warehouseId,
            financialYearId = financialYearId,
            supplierInvoiceNumber = "SUP-001",
            invoiceDateMillis = 1000L,
            isInterState = false,
            lines = listOf(
                PurchaseInvoiceLineDraft(
                    productId = productId,
                    productName = "Widget",
                    hsnSacCode = "1234",
                    quantity = 10.0,
                    unitLabel = "NOS",
                    unitPricePaise = 10000, // Rs 100
                    discountPercent = 0.0,
                    cgstRatePercent = 9.0,
                    sgstRatePercent = 9.0,
                    igstRatePercent = 18.0,
                    cessRatePercent = 0.0
                )
            )
        )

        val invoice = purchaseInvoiceRepository.getInvoice(invoiceId)!!
        // Taxable = 1000.00, tax = 180.00 (9%+9%), grand total = 1180.00
        assertEquals(100000L, invoice.totalTaxableValuePaise)
        assertEquals(118000L, invoice.grandTotalPaise)

        val stock = db.stockDao().find(productId, warehouseId)!!
        assertEquals(10.0, stock.quantityOnHand, 0.0001)

        val supplierBalance = accountingRepository.getAccountBalance(supplier.ledgerAccountId!!, Long.MAX_VALUE)
        // Supplier is a credit-side (payable) account; net debit balance
        // should be negative by the grand total (we owe them).
        assertEquals(-118000L, supplierBalance)
    }
}
