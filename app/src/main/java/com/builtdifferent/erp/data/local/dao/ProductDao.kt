package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE productId = :productId")
    suspend fun getById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE productId = :productId")
    fun observeById(productId: Long): Flow<ProductEntity?>

    @Query(
        """SELECT * FROM products
           WHERE isActive = 1 AND (productName LIKE '%' || :search || '%' OR sku LIKE '%' || :search || '%')
           ORDER BY productName ASC"""
    )
    fun search(search: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    fun observeActiveProductCount(): Flow<Int>

    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): ProductEntity?

    @Query("UPDATE products SET isActive = 0 WHERE productId = :productId")
    suspend fun softDelete(productId: Long)

    @Query(
        """UPDATE products SET sellingPricePaise = :pricePaise, updatedAt = :updatedAt
           WHERE productId = :productId"""
    )
    suspend fun updateSellingPrice(productId: Long, pricePaise: Long, updatedAt: Long)

    @Query(
        """UPDATE products SET purchasePricePaise = :pricePaise, updatedAt = :updatedAt
           WHERE productId = :productId"""
    )
    suspend fun updatePurchasePrice(productId: Long, pricePaise: Long, updatedAt: Long)
}
