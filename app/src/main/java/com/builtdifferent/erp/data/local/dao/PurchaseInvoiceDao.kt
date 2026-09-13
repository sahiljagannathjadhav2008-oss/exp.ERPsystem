package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.InvoiceStatus
import com.builtdifferent.erp.data.local.entity.PurchaseInvoiceEntity
import com.builtdifferent.erp.data.local.entity.PurchaseInvoiceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseInvoiceDao {

    @Insert
    suspend fun insert(invoice: PurchaseInvoiceEntity): Long

    @Update
    suspend fun update(invoice: PurchaseInvoiceEntity)

    @Query("SELECT * FROM purchase_invoices WHERE purchaseInvoiceId = :id")
    suspend fun getById(id: Long): PurchaseInvoiceEntity?

    @Query(
        """SELECT * FROM purchase_invoices
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           AND (:status IS NULL OR status = :status)
           ORDER BY invoiceDateMillis DESC, purchaseInvoiceId DESC"""
    )
    fun observeInvoices(financialYearId: Long?, status: InvoiceStatus?): Flow<List<PurchaseInvoiceEntity>>

    @Query("SELECT COUNT(*) FROM purchase_invoices WHERE financialYearId = :financialYearId AND status = 'POSTED'")
    fun observePostedCount(financialYearId: Long): Flow<Int>
}

@Dao
interface PurchaseInvoiceItemDao {

    @Insert
    suspend fun insertAll(items: List<PurchaseInvoiceItemEntity>): List<Long>

    @Query("SELECT * FROM purchase_invoice_items WHERE purchaseInvoiceId = :invoiceId ORDER BY sortOrder ASC")
    suspend fun getForInvoice(invoiceId: Long): List<PurchaseInvoiceItemEntity>
}
