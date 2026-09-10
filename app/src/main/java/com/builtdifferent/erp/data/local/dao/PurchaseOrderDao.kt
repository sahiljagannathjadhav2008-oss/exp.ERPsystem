package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.PurchaseOrderEntity
import com.builtdifferent.erp.data.local.entity.PurchaseOrderItemEntity
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOrderDao {
    @Insert
    suspend fun insert(order: PurchaseOrderEntity): Long

    @Update
    suspend fun update(order: PurchaseOrderEntity)

    @Query("SELECT * FROM purchase_orders WHERE purchaseOrderId = :id")
    suspend fun getById(id: Long): PurchaseOrderEntity?

    @Query(
        """SELECT * FROM purchase_orders
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           ORDER BY orderDateMillis DESC"""
    )
    fun observeAll(financialYearId: Long?): Flow<List<PurchaseOrderEntity>>

    @Query("UPDATE purchase_orders SET status = :status WHERE purchaseOrderId = :id")
    suspend fun updateStatus(id: Long, status: SalesDocStatus)
}

@Dao
interface PurchaseOrderItemDao {
    @Insert
    suspend fun insertAll(items: List<PurchaseOrderItemEntity>): List<Long>

    @Query("SELECT * FROM purchase_order_items WHERE purchaseOrderId = :orderId ORDER BY sortOrder ASC")
    suspend fun getForOrder(orderId: Long): List<PurchaseOrderItemEntity>
}
