package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import com.builtdifferent.erp.data.local.entity.SalesOrderEntity
import com.builtdifferent.erp.data.local.entity.SalesPreDocItemEntity
import com.builtdifferent.erp.data.local.entity.SalesQuotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesQuotationDao {
    @Insert
    suspend fun insert(quotation: SalesQuotationEntity): Long

    @Update
    suspend fun update(quotation: SalesQuotationEntity)

    @Query("SELECT * FROM sales_quotations WHERE salesQuotationId = :id")
    suspend fun getById(id: Long): SalesQuotationEntity?

    @Query(
        """SELECT * FROM sales_quotations
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           ORDER BY quotationDateMillis DESC"""
    )
    fun observeAll(financialYearId: Long?): Flow<List<SalesQuotationEntity>>

    @Query("UPDATE sales_quotations SET status = :status, convertedToSalesOrderId = :orderId WHERE salesQuotationId = :id")
    suspend fun markConverted(id: Long, status: SalesDocStatus, orderId: Long)
}

@Dao
interface SalesOrderDao {
    @Insert
    suspend fun insert(order: SalesOrderEntity): Long

    @Update
    suspend fun update(order: SalesOrderEntity)

    @Query("SELECT * FROM sales_orders WHERE salesOrderId = :id")
    suspend fun getById(id: Long): SalesOrderEntity?

    @Query(
        """SELECT * FROM sales_orders
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           ORDER BY orderDateMillis DESC"""
    )
    fun observeAll(financialYearId: Long?): Flow<List<SalesOrderEntity>>

    @Query("UPDATE sales_orders SET status = :status WHERE salesOrderId = :id")
    suspend fun updateStatus(id: Long, status: SalesDocStatus)
}

@Dao
interface SalesPreDocItemDao {
    @Insert
    suspend fun insertAll(items: List<SalesPreDocItemEntity>): List<Long>

    @Query("SELECT * FROM sales_pre_doc_items WHERE salesQuotationId = :quotationId ORDER BY sortOrder ASC")
    suspend fun getForQuotation(quotationId: Long): List<SalesPreDocItemEntity>

    @Query("SELECT * FROM sales_pre_doc_items WHERE salesOrderId = :orderId ORDER BY sortOrder ASC")
    suspend fun getForOrder(orderId: Long): List<SalesPreDocItemEntity>
}
