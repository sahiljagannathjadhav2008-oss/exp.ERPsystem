package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class StockValuationRow(
    val productId: Long,
    val productName: String,
    val sku: String,
    val warehouseId: Long,
    val warehouseName: String,
    val quantityOnHand: Double,
    val stockValuePaise: Long,
    val reorderLevelQty: Double
)

/**
 * A dedicated report DAO rather than adding joined queries onto StockDao:
 * StockDao owns the stock table's identity operations (find, upsert), this
 * owns cross-table read-only reporting queries, so a change to how stock is
 * written never risks breaking a report query by accident, and vice versa.
 */
@Dao
interface StockReportDao {

    @Query(
        """SELECT s.productId, p.productName, p.sku, s.warehouseId, w.name as warehouseName,
           s.quantityOnHand, s.stockValuePaise, p.reorderLevelQty
           FROM stock s
           INNER JOIN products p ON p.productId = s.productId
           INNER JOIN warehouses w ON w.warehouseId = s.warehouseId
           WHERE p.isActive = 1
           ORDER BY p.productName ASC"""
    )
    fun observeStockValuation(): Flow<List<StockValuationRow>>

    @Query(
        """SELECT s.productId, p.productName, p.sku, s.warehouseId, w.name as warehouseName,
           s.quantityOnHand, s.stockValuePaise, p.reorderLevelQty
           FROM stock s
           INNER JOIN products p ON p.productId = s.productId
           INNER JOIN warehouses w ON w.warehouseId = s.warehouseId
           WHERE p.isActive = 1 AND s.quantityOnHand <= p.reorderLevelQty AND p.trackInventory = 1
           ORDER BY p.productName ASC"""
    )
    fun observeLowStockRows(): Flow<List<StockValuationRow>>

    @Query("SELECT COALESCE(SUM(s.stockValuePaise), 0) FROM stock s INNER JOIN products p ON p.productId = s.productId WHERE p.isActive = 1")
    fun observeTotalStockValue(): Flow<Long>
}
