package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.StockAdjustmentEntity
import com.builtdifferent.erp.data.local.entity.StockAdjustmentReason
import com.builtdifferent.erp.data.local.entity.StockMovementEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * Every stock quantity change in the app — sale, purchase, return,
 * adjustment, transfer — goes through recordMovement(), which writes the
 * immutable StockMovementEntity row and updates the cached StockEntity
 * balance in the SAME transaction (see StockDao.upsertAddQuantity). No
 * other repository is allowed to touch stock_movements or stock directly;
 * this is what keeps the two tables from ever disagreeing.
 */
class InventoryRepository(private val db: AppDatabase) {

    private val stockDao = db.stockDao()
    private val stockMovementDao = db.stockMovementDao()

    fun observeForWarehouse(warehouseId: Long) = stockDao.observeForWarehouse(warehouseId)
    fun observeTotalStockValuePaise(): Flow<Long> = stockDao.observeTotalStockValuePaise()
    fun observeLowStockCount(): Flow<Int> = stockDao.observeLowStockCount()

    /** quantityDelta/valueDeltaPaise are signed: positive for stock coming
     * in (purchase, sales return, opening, positive adjustment), negative
     * for stock going out (sale, purchase return, negative adjustment). */
    suspend fun recordMovement(
        productId: Long,
        warehouseId: Long,
        type: StockMovementType,
        quantityDelta: Double,
        valueDeltaPaise: Long,
        unitCostPaise: Long,
        refType: StockRefType,
        refId: Long,
        financialYearId: Long,
        movementDateMillis: Long = DateUtils.nowMillis(),
        note: String = ""
    ): Long = db.withTransaction {
        stockDao.upsertAddQuantity(productId, warehouseId, quantityDelta, valueDeltaPaise, DateUtils.nowMillis())
        stockMovementDao.insert(
            StockMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                type = type,
                quantity = kotlin.math.abs(quantityDelta),
                unitCostPaise = unitCostPaise,
                refType = refType,
                refId = refId,
                financialYearId = financialYearId,
                movementDateMillis = movementDateMillis,
                note = note
            )
        )
    }

    /** Manual stock correction (damage, theft, recount, expiry) — the only
     * user-facing action that calls recordMovement directly rather than
     * going through a sales/purchase document. */
    suspend fun adjustStock(
        productId: Long,
        warehouseId: Long,
        financialYearId: Long,
        quantityDelta: Double,
        unitCostPaise: Long,
        reason: StockAdjustmentReason,
        note: String
    ): Long = db.withTransaction {
        val adjustmentId = db.stockAdjustmentDao().insert(
            StockAdjustmentEntity(
                productId = productId,
                warehouseId = warehouseId,
                financialYearId = financialYearId,
                quantityDelta = quantityDelta,
                unitCostPaise = unitCostPaise,
                reason = reason,
                note = note
            )
        )
        val valueDeltaPaise = (quantityDelta * unitCostPaise).toLong()
        recordMovement(
            productId = productId,
            warehouseId = warehouseId,
            type = if (quantityDelta >= 0) StockMovementType.ADJUSTMENT_IN else StockMovementType.ADJUSTMENT_OUT,
            quantityDelta = quantityDelta,
            valueDeltaPaise = valueDeltaPaise,
            unitCostPaise = unitCostPaise,
            refType = StockRefType.STOCK_ADJUSTMENT,
            refId = adjustmentId,
            financialYearId = financialYearId,
            note = note
        )
        adjustmentId
    }
}
