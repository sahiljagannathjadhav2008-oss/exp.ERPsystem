package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.builtdifferent.erp.data.local.entity.StockEntity
import com.builtdifferent.erp.data.local.entity.StockMovementEntity
import com.builtdifferent.erp.data.local.entity.StockRefType
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Insert
    suspend fun insert(stock: StockEntity): Long

    @Query("SELECT * FROM stock WHERE productId = :productId AND warehouseId = :warehouseId LIMIT 1")
    suspend fun find(productId: Long, warehouseId: Long): StockEntity?

    @Query(
        """UPDATE stock SET quantityOnHand = quantityOnHand + :quantityDelta,
           stockValuePaise = stockValuePaise + :valueDeltaPaise, updatedAt = :updatedAt
           WHERE productId = :productId AND warehouseId = :warehouseId"""
    )
    suspend fun applyDelta(productId: Long, warehouseId: Long, quantityDelta: Double, valueDeltaPaise: Long, updatedAt: Long)

    @Query("SELECT * FROM stock WHERE warehouseId = :warehouseId")
    fun observeForWarehouse(warehouseId: Long): Flow<List<StockEntity>>

    @Query("SELECT COALESCE(SUM(stockValuePaise), 0) FROM stock")
    fun observeTotalStockValuePaise(): Flow<Long>

    @Query(
        """SELECT COUNT(*) FROM stock s
           INNER JOIN products p ON p.productId = s.productId
           WHERE s.quantityOnHand <= p.reorderLevelQty AND p.isActive = 1 AND p.trackInventory = 1"""
    )
    fun observeLowStockCount(): Flow<Int>

    /**
     * Creates the (product, warehouse) stock row on first use and applies
     * the delta, or applies the delta to the existing row — the single
     * entry point every inventory-affecting operation (opening stock,
     * purchase, sale, adjustment, transfer) goes through, so StockEntity
     * can never silently be missing a row for a product that has movements.
     */
    @Transaction
    suspend fun upsertAddQuantity(productId: Long, warehouseId: Long, quantityDelta: Double, valueDeltaPaise: Long, updatedAt: Long) {
        val existing = find(productId, warehouseId)
        if (existing == null) {
            insert(
                StockEntity(
                    productId = productId,
                    warehouseId = warehouseId,
                    quantityOnHand = quantityDelta,
                    stockValuePaise = valueDeltaPaise,
                    updatedAt = updatedAt
                )
            )
        } else {
            applyDelta(productId, warehouseId, quantityDelta, valueDeltaPaise, updatedAt)
        }
    }
}

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY movementDateMillis DESC")
    fun observeForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE refType = :refType AND refId = :refId")
    suspend fun getForSource(refType: StockRefType, refId: Long): List<StockMovementEntity>
}
