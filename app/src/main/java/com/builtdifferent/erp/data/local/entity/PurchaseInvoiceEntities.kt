package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["orderNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"])]
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val purchaseOrderId: Long = 0,
    val orderNumber: String,
    val orderDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val subTotalPaise: Long,
    val grandTotalPaise: Long,
    val status: SalesDocStatus = SalesDocStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_order_items",
    foreignKeys = [
        ForeignKey(PurchaseOrderEntity::class, ["purchaseOrderId"], ["purchaseOrderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["productId"], ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["purchaseOrderId"]), Index(value = ["productId"])]
)
data class PurchaseOrderItemEntity(
    @PrimaryKey(autoGenerate = true) val purchaseOrderItemId: Long = 0,
    val purchaseOrderId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val taxableValuePaise: Long,
    val estimatedTaxPaise: Long = 0,
    val lineTotalPaise: Long,
    val sortOrder: Int = 0
)

/**
 * Purchase invoice mirrors SalesInvoiceEntity's snapshot philosophy exactly:
 * company/supplier details, price, and applied GST rate/amounts are all
 * captured at posting time. Posting increases stock (StockMovementType
 * PURCHASE) and creates a balanced journal entry (Dr Purchases, Dr CGST/
 * SGST/IGST Input, Cr Supplier/Cash).
 */
@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(WarehouseEntity::class, ["warehouseId"], ["warehouseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["invoiceNumber", "partyId"], unique = true),
        Index(value = ["partyId"]),
        Index(value = ["financialYearId"]),
        Index(value = ["invoiceDateMillis"]),
        Index(value = ["status"]),
        Index(value = ["warehouseId"])
    ]
)
data class PurchaseInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val purchaseInvoiceId: Long = 0,
    /** The SUPPLIER's own invoice number (not ours) — not globally unique
     * across suppliers, hence the composite unique index with partyId. */
    val invoiceNumber: String,
    val invoiceDateMillis: Long,
    val entryDateMillis: Long = System.currentTimeMillis(),
    val financialYearId: Long,
    val partyId: Long,
    val warehouseId: Long,
    val sourcePurchaseOrderId: Long? = null,
    val isInterState: Boolean,
    val companySnapshotJson: String,
    val partySnapshotJson: String,
    val subTotalPaise: Long,
    val totalDiscountPaise: Long = 0,
    val totalTaxableValuePaise: Long,
    val totalCgstPaise: Long = 0,
    val totalSgstPaise: Long = 0,
    val totalIgstPaise: Long = 0,
    val totalCessPaise: Long = 0,
    val roundOffPaise: Long = 0,
    val grandTotalPaise: Long,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_invoice_items",
    foreignKeys = [
        ForeignKey(PurchaseInvoiceEntity::class, ["purchaseInvoiceId"], ["purchaseInvoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["productId"], ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["purchaseInvoiceId"]), Index(value = ["productId"])]
)
data class PurchaseInvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val purchaseInvoiceItemId: Long = 0,
    val purchaseInvoiceId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val hsnSacCodeSnapshot: String,
    val quantity: Double,
    val unitLabelSnapshot: String,
    val unitPricePaise: Long,
    val discountPercent: Double = 0.0,
    val discountAmountPaise: Long = 0,
    val taxableValuePaise: Long,
    val appliedCgstRatePercent: Double = 0.0,
    val appliedSgstRatePercent: Double = 0.0,
    val appliedIgstRatePercent: Double = 0.0,
    val appliedCessRatePercent: Double = 0.0,
    val cgstAmountPaise: Long = 0,
    val sgstAmountPaise: Long = 0,
    val igstAmountPaise: Long = 0,
    val cessAmountPaise: Long = 0,
    val lineTotalPaise: Long,
    val sortOrder: Int = 0
)
