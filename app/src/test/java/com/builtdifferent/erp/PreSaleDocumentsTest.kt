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
class PreSaleDocumentsTest {

    private lateinit var db: AppDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var deliveryChallanRepository: DeliveryChallanRepository
    private lateinit var salesQuotationRepository: SalesQuotationRepository

    private var financialYearId: Long = 0
    private var warehouseId: Long = 0
    private var productId: Long = 0
    private var partyId: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryRepository = InventoryRepository(db)
        deliveryChallanRepository = DeliveryChallanRepository(db, inventoryRepository)
        salesQuotationRepository = SalesQuotationRepository(db)

        val companyId = db.companyDao().insert(CompanyEntity(legalName = "Test Co"))
        financialYearId = db.financialYearDao().insert(
            FinancialYearEntity(companyId = companyId, label = "FY", startDateMillis = 0, endDateMillis = Long.MAX_VALUE, isCurrent = true)
        )
        warehouseId = db.warehouseDao().insert(WarehouseEntity(name = "Main", isDefault = true))
        val unitId = db.unitDao().insert(UnitEntity(unitCode = "NOS", displayLabel = "Pieces"))
        val hsnSacId = db.hsnSacDao().insert(HsnSacEntity(code = "1234", type = HsnSacType.HSN))
        productId = db.productDao().insert(
            ProductEntity(productName = "Widget", sku = "W1", unitId = unitId, hsnSacId = hsnSacId, purchasePricePaise = 5000)
        )
        inventoryRepository.recordMovement(
            productId = productId, warehouseId = warehouseId, type = StockMovementType.OPENING,
            quantityDelta = 20.0, valueDeltaPaise = 100000, unitCostPaise = 5000,
            refType = StockRefType.PRODUCT_OPENING, refId = productId, financialYearId = financialYearId
        )
        partyId = db.partyDao().insert(PartyEntity(partyName = "Test Party", type = PartyType.CUSTOMER))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `delivery challan reduces stock`() = runBlocking {
        deliveryChallanRepository.create(
            financialYearId = financialYearId,
            partyId = partyId,
            warehouseId = warehouseId,
            challanDateMillis = 1000L,
            sourceSalesOrderId = null,
            notes = "Test shipment",
            lines = listOf(ChallanLineDraft(productId, "Widget", 5.0))
        )

        val stock = db.stockDao().find(productId, warehouseId)!!
        assertEquals(15.0, stock.quantityOnHand, 0.0001)
    }

    @Test
    fun `converting a quotation to an order carries the lines forward and marks it converted`() = runBlocking {
        val quotationId = salesQuotationRepository.create(
            financialYearId = financialYearId,
            partyId = partyId,
            quotationDateMillis = 1000L,
            validUntilMillis = null,
            notes = "Draft quote",
            lines = listOf(PreDocLineDraft(productId, "Widget", 3.0, 10000, 0.0, 1800))
        )

        val orderId = salesQuotationRepository.convertToOrder(quotationId)

        val quotation = salesQuotationRepository.getById(quotationId)!!
        assertEquals(SalesDocStatus.CONVERTED, quotation.status)
        assertEquals(orderId, quotation.convertedToSalesOrderId)

        val orderItems = db.salesPreDocItemDao().getForOrder(orderId)
        assertEquals(1, orderItems.size)
        assertEquals(3.0, orderItems.first().quantity, 0.0001)
    }
}
