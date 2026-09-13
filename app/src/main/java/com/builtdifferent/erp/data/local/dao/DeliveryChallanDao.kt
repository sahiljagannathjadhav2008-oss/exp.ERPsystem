package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.DeliveryChallanEntity
import com.builtdifferent.erp.data.local.entity.DeliveryChallanItemEntity
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryChallanDao {
    @Insert
    suspend fun insert(challan: DeliveryChallanEntity): Long

    @Update
    suspend fun update(challan: DeliveryChallanEntity)

    @Query("SELECT * FROM delivery_challans WHERE deliveryChallanId = :id")
    suspend fun getById(id: Long): DeliveryChallanEntity?

    @Query(
        """SELECT * FROM delivery_challans
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           ORDER BY challanDateMillis DESC"""
    )
    fun observeAll(financialYearId: Long?): Flow<List<DeliveryChallanEntity>>

    @Query("UPDATE delivery_challans SET status = :status, invoicedSalesInvoiceId = :invoiceId WHERE deliveryChallanId = :id")
    suspend fun markInvoiced(id: Long, status: SalesDocStatus, invoiceId: Long)
}

@Dao
interface DeliveryChallanItemDao {
    @Insert
    suspend fun insertAll(items: List<DeliveryChallanItemEntity>): List<Long>

    @Query("SELECT * FROM delivery_challan_items WHERE deliveryChallanId = :challanId ORDER BY sortOrder ASC")
    suspend fun getForChallan(challanId: Long): List<DeliveryChallanItemEntity>
}
