package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {
    @Insert
    suspend fun insert(adjustment: StockAdjustmentEntity): Long

    @Query("SELECT * FROM stock_adjustments WHERE productId = :productId ORDER BY adjustmentDateMillis DESC")
    fun observeForProduct(productId: Long): Flow<List<StockAdjustmentEntity>>
}
