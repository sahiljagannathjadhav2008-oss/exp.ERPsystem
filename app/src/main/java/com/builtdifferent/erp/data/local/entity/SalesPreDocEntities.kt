package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SalesDocStatus { DRAFT, OPEN, CONVERTED, CANCELLED }

/** Quotations and Orders share an identical shape to each other (and are
 * pre-invoice, non-accounting, non-stock-affecting documents), so they use
 * the same item entity (SalesPreDocItemEntity) discriminated by which
 * header FK is populated. Delivery Challans affect stock (goods physically
 * leave) but not accounting (no sale recognised yet), so they get their own
 * item table referencing real stock movement. */
@Entity(
    tableName = "sales_quotations",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["quotationNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"])]
)
data class SalesQuotationEntity(
    @PrimaryKey(autoGenerate = true) val salesQuotationId: Long = 0,
    val quotationNumber: String,
    val quotationDateMillis: Long,
    val validUntilMillis: Long? = null,
    val financialYearId: Long,
    val partyId: Long,
    val subTotalPaise: Long,
    val grandTotalPaise: Long,
    val status: SalesDocStatus = SalesDocStatus.DRAFT,
    val convertedToSalesOrderId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales_orders",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["orderNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"])]
)
data class SalesOrderEntity(
    @PrimaryKey(autoGenerate = true) val salesOrderId: Long = 0,
    val orderNumber: String,
    val orderDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val sourceQuotationId: Long? = null,
    val subTotalPaise: Long,
    val grandTotalPaise: Long,
    val status: SalesDocStatus = SalesDocStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Shared line-item shape for quotations and orders. Exactly one of
 * salesQuotationId / salesOrderId is non-null. */
@Entity(
    tableName = "sales_pre_doc_items",
    foreignKeys = [
        ForeignKey(SalesQuotationEntity::class, ["salesQuotationId"], ["salesQuotationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SalesOrderEntity::class, ["salesOrderId"], ["salesOrderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["productId"], ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["salesQuotationId"]), Index(value = ["salesOrderId"]), Index(value = ["productId"])]
)
data class SalesPreDocItemEntity(
    @PrimaryKey(autoGenerate = true) val salesPreDocItemId: Long = 0,
    val salesQuotationId: Long? = null,
    val salesOrderId: Long? = null,
    val productId: Long,
    val productNameSnapshot: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val discountPercent: Double = 0.0,
    val taxableValuePaise: Long,
    val estimatedTaxPaise: Long = 0,
    val lineTotalPaise: Long,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "delivery_challans",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(WarehouseEntity::class, ["warehouseId"], ["warehouseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["challanNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"]), Index(value = ["warehouseId"])]
)
data class DeliveryChallanEntity(
    @PrimaryKey(autoGenerate = true) val deliveryChallanId: Long = 0,
    val challanNumber: String,
    val challanDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val warehouseId: Long,
    val sourceSalesOrderId: Long? = null,
    val invoicedSalesInvoiceId: Long? = null,
    val status: SalesDocStatus = SalesDocStatus.OPEN,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "delivery_challan_items",
    foreignKeys = [
        ForeignKey(DeliveryChallanEntity::class, ["deliveryChallanId"], ["deliveryChallanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["productId"], ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["deliveryChallanId"]), Index(value = ["productId"])]
)
data class DeliveryChallanItemEntity(
    @PrimaryKey(autoGenerate = true) val deliveryChallanItemId: Long = 0,
    val deliveryChallanId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantity: Double,
    val sortOrder: Int = 0
)
