package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.ProductPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceDao {
    @Insert
    suspend fun insert(price: ProductPriceEntity): Long

    @Query("SELECT * FROM product_prices WHERE productId = :productId ORDER BY effectiveFromMillis DESC")
    fun observeHistory(productId: Long): Flow<List<ProductPriceEntity>>
}
