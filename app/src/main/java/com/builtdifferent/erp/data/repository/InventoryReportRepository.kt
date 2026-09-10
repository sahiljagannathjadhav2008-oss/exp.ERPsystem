package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.dao.StockValuationRow
import com.builtdifferent.erp.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * All figures here are read directly from StockEntity/StockMovementEntity —
 * the same rows InventoryRepository maintains transactionally on every
 * sale/purchase/return/adjustment — so a report can never show a number
 * that disagrees with what the rest of the app has actually posted.
 */
class InventoryReportRepository(private val db: AppDatabase) {

    fun observeStockValuation(): Flow<List<StockValuationRow>> = db.stockReportDao().observeStockValuation()
    fun observeLowStockItems(): Flow<List<StockValuationRow>> = db.stockReportDao().observeLowStockRows()
    fun observeTotalStockValue(): Flow<Long> = db.stockReportDao().observeTotalStockValue()

    suspend fun getMovementHistory(productId: Long): List<StockMovementEntity> {
        // observeForProduct is a Flow ordered by date DESC; a report screen
        // wants a one-shot list, not an ongoing subscription bound to the
        // report's own lifecycle, so this takes the first emission.
        return db.stockMovementDao().observeForProduct(productId).first()
    }
}
