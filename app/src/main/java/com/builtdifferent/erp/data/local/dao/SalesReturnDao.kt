package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.CreditNoteEntity
import com.builtdifferent.erp.data.local.entity.SalesReturnEntity
import com.builtdifferent.erp.data.local.entity.SalesReturnItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesReturnDao {
    @Insert
    suspend fun insert(salesReturn: SalesReturnEntity): Long

    @Query("SELECT * FROM sales_returns WHERE originalSalesInvoiceId = :invoiceId")
    suspend fun getForInvoice(invoiceId: Long): List<SalesReturnEntity>

    @Query("SELECT * FROM sales_returns WHERE financialYearId = :financialYearId ORDER BY returnDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<SalesReturnEntity>>

    /** Total quantity already returned for a given original invoice item —
     * used to prevent a return exceeding what was actually sold. */
    @Query(
        """SELECT COALESCE(SUM(quantity), 0) FROM sales_return_items
           WHERE originalSalesInvoiceItemId = :originalItemId"""
    )
    suspend fun getReturnedQuantity(originalItemId: Long): Double
}

@Dao
interface SalesReturnItemDao {
    @Insert
    suspend fun insertAll(items: List<SalesReturnItemEntity>): List<Long>

    @Query("SELECT * FROM sales_return_items WHERE salesReturnId = :returnId")
    suspend fun getForReturn(returnId: Long): List<SalesReturnItemEntity>
}

@Dao
interface CreditNoteDao {
    @Insert
    suspend fun insert(creditNote: CreditNoteEntity): Long

    @Query("SELECT * FROM credit_notes WHERE partyId = :partyId ORDER BY creditNoteDateMillis DESC")
    fun observeForParty(partyId: Long): Flow<List<CreditNoteEntity>>
}
