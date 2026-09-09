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
class GstReportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var salesInvoiceRepository: SalesInvoiceRepository
    private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository
    private lateinit var gstReportRepository: GstReportRepository

    private var financialYearId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
    private lateinit var company: CompanyEntity
    private lateinit var customer: PartyEntity
    private lateinit var supplier: PartyEntity

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryRepository = InventoryRepository(db)
        accountingRepository = AccountingRepository(db)
        salesInvoiceRepository = SalesInvoiceRepository(db, inventoryRepository, accountingRepository)
        purchaseInvoiceRepository = PurchaseInvoiceRepository(db, inventoryRepository, accountingRepository)
        gstReportRepository = GstReportRepository(db)

        listOf(
            "Sales Account" to (AccountGroup.INCOME to AccountSubGroup.DIRECT_INCOME),
            "Purchase Account" to (AccountGroup.EXPENSE to AccountSubGroup.DIRECT_EXPENSE),
            "CGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "SGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "IGST Output" to (AccountGroup.LIABILITY to AccountSubGroup.DUTIES_AND_TAXES),
            "CGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
            "SGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
            "IGST Input" to (AccountGroup.ASSET to AccountSubGroup.DUTIES_AND_TAXES),
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
            LedgerAccountEntity(accountName = "Customer", group = AccountGroup.ASSET, subGroup = AccountSubGroup.RECEIVABLE, normalBalanceIsDebit = true)
        )
        val customerId = db.partyDao().insert(PartyEntity(partyName = "Customer", type = PartyType.CUSTOMER, billingStateCode = "27", ledgerAccountId = customerLedgerId))
        customer = db.partyDao().getById(customerId)!!

        val supplierLedgerId = db.ledgerAccountDao().insert(
            LedgerAccountEntity(accountName = "Supplier", group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.PAYABLE, normalBalanceIsDebit = false)
        )
        val supplierId = db.partyDao().insert(PartyEntity(partyName = "Supplier", type = PartyType.SUPPLIER, billingStateCode = "27", ledgerAccountId = supplierLedgerId))
        supplier = db.partyDao().getById(supplierId)!!

        val unitId = db.unitDao().insert(UnitEntity(unitCode = "NOS", displayLabel = "Pieces"))
        val hsnSacId = db.hsnSacDao().insert(HsnSacEntity(code = "1234", type = HsnSacType.HSN))
        productId = db.productDao().insert(ProductEntity(productName = "Widget", sku = "W1", unitId = unitId, hsnSacId = hsnSacId))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `net tax payable equals output tax minus input tax credit`() = runBlocking {
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
        purchaseInvoiceRepository.postInvoice(
            company = company, supplier = supplier, warehouseId = warehouseId, financialYearId = financialYearId,
            supplierInvoiceNumber = "SUP-1", invoiceDateMillis = 1000L, isInterState = false,
            lines = listOf(
                PurchaseInvoiceLineDraft(
                    productId = productId, productName = "Widget", hsnSacCode = "1234",
                    quantity = 1.0, unitLabel = "NOS", unitPricePaise = 40000,
                    discountPercent = 0.0, cgstRatePercent = 9.0, sgstRatePercent = 9.0,
                    igstRatePercent = 18.0, cessRatePercent = 0.0
                )
            )
        )

        val report = gstReportRepository.getReport(financialYearId, 0L, Long.MAX_VALUE)
        assertEquals(9000L, report.outward.cgst)
        assertEquals(3600L, report.inward.cgst)
        assertEquals(5400L, report.netCgstPayablePaise)
        assertEquals(5400L, report.netSgstPayablePaise)
        assertEquals(10800L, report.netTaxPayablePaise)
    }
}
