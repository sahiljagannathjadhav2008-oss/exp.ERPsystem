package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "warehouses")
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true)
    val warehouseId: Long = 0,
    val name: String,
    val addressLine1: String = "",
    val city: String = "",
    val isDefault: Boolean = false,
    val isActive: Boolean = true
)

/**
 * One row per (product, warehouse): the current on-hand quantity and value.
 * This is a derived/cached table — the source of truth is the append-only
 * StockMovementEntity ledger below — but keeping a maintained running
 * balance here is what makes "current stock value" and "low-stock items" on
 * the dashboard (Section 9) a cheap indexed read instead of summing the
 * entire movement history on every screen open. Every write to
 * StockMovementEntity happens in the same DB transaction as the
 * corresponding update to this row (see InventoryRepository), so the two
 * can never drift.
 */
@Entity(
    tableName = "stock",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["warehouseId"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["productId", "warehouseId"], unique = true)]
)
data class StockEntity(
    @PrimaryKey(autoGenerate = true)
    val stockId: Long = 0,
    val productId: Long,
    val warehouseId: Long,
    val quantityOnHand: Double = 0.0,
    /** Moving-average cost value of the on-hand quantity, in paise. Used to
     * value COGS on sale and stock value on the dashboard. */
    val stockValuePaise: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class StockMovementType {
    OPENING, PURCHASE, PURCHASE_RETURN, SALE, SALE_RETURN,
    ADJUSTMENT_IN, ADJUSTMENT_OUT, TRANSFER_IN, TRANSFER_OUT, DELIVERY_OUT
}

enum class StockRefType {
    PRODUCT_OPENING, PURCHASE_INVOICE, PURCHASE_RETURN, SALES_INVOICE,
    SALES_RETURN, STOCK_ADJUSTMENT, STOCK_TRANSFER, DELIVERY_CHALLAN
}

/**
 * Append-only, immutable audit trail of every stock change. Rows are never
 * updated or deleted after insert — a correction is made by inserting a
 * reversing movement, never by editing history (same principle as the
 * accounting ledger below). This is the authoritative record reports and
 * stock-reconciliation reconcile StockEntity against.
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["warehouseId"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["productId", "warehouseId"]),
        Index(value = ["refType", "refId"]),
        Index(value = ["movementDateMillis"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val stockMovementId: Long = 0,
    val productId: Long,
    val warehouseId: Long,
    val type: StockMovementType,
    /** Always positive; direction (in/out) is implied by [type]. */
    val quantity: Double,
    val unitCostPaise: Long,
    val refType: StockRefType,
    /** Id of the source document row (invoice, adjustment, etc.). */
    val refId: Long,
    val financialYearId: Long,
    val movementDateMillis: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class StockAdjustmentReason { DAMAGE, THEFT_LOSS, RECOUNT, EXPIRY, OTHER }

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["warehouseId"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["productId", "warehouseId"]), Index(value = ["financialYearId"])]
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val stockAdjustmentId: Long = 0,
    val productId: Long,
    val warehouseId: Long,
    val financialYearId: Long,
    /** Signed: positive increases stock, negative decreases it. */
    val quantityDelta: Double,
    val unitCostPaise: Long,
    val reason: StockAdjustmentReason,
    val note: String = "",
    val adjustmentDateMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
