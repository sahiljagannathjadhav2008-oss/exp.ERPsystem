package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.InvoiceStatus
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.data.local.entity.SalesInvoiceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesInvoiceDao {

    @Insert
    suspend fun insert(invoice: SalesInvoiceEntity): Long

    @Update
    suspend fun update(invoice: SalesInvoiceEntity)

    @Query("SELECT * FROM sales_invoices WHERE salesInvoiceId = :id")
    suspend fun getById(id: Long): SalesInvoiceEntity?

    @Query("SELECT * FROM sales_invoices WHERE salesInvoiceId = :id")
    fun observeById(id: Long): Flow<SalesInvoiceEntity?>

    @Query(
        """SELECT * FROM sales_invoices
           WHERE (:financialYearId IS NULL OR financialYearId = :financialYearId)
           AND (:status IS NULL OR status = :status)
           ORDER BY invoiceDateMillis DESC, salesInvoiceId DESC"""
    )
    fun observeInvoices(financialYearId: Long?, status: InvoiceStatus?): Flow<List<SalesInvoiceEntity>>

    @Query("SELECT * FROM sales_invoices WHERE partyId = :partyId ORDER BY invoiceDateMillis DESC")
    fun observeForParty(partyId: Long): Flow<List<SalesInvoiceEntity>>

    @Query(
        """SELECT COALESCE(SUM(grandTotalPaise), 0) FROM sales_invoices
           WHERE financialYearId = :financialYearId AND status = 'POSTED'
           AND invoiceDateMillis BETWEEN :fromMillis AND :toMillis"""
    )
    suspend fun sumPostedTotalInRange(financialYearId: Long, fromMillis: Long, toMillis: Long): Long

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE financialYearId = :financialYearId AND status = 'POSTED'")
    fun observePostedCount(financialYearId: Long): Flow<Int>
}

@Dao
interface SalesInvoiceItemDao {

    @Insert
    suspend fun insertAll(items: List<SalesInvoiceItemEntity>): List<Long>

    @Query("SELECT * FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId ORDER BY sortOrder ASC")
    suspend fun getForInvoice(invoiceId: Long): List<SalesInvoiceItemEntity>

    @Query("SELECT * FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId ORDER BY sortOrder ASC")
    fun observeForInvoice(invoiceId: Long): Flow<List<SalesInvoiceItemEntity>>

    @Query("DELETE FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId")
    suspend fun deleteForInvoice(invoiceId: Long)
}
