package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.DebitNoteEntity
import com.builtdifferent.erp.data.local.entity.PurchaseReturnEntity
import com.builtdifferent.erp.data.local.entity.PurchaseReturnItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseReturnDao {
    @Insert
    suspend fun insert(purchaseReturn: PurchaseReturnEntity): Long

    @Query("SELECT * FROM purchase_returns WHERE originalPurchaseInvoiceId = :invoiceId")
    suspend fun getForInvoice(invoiceId: Long): List<PurchaseReturnEntity>

    @Query("SELECT * FROM purchase_returns WHERE financialYearId = :financialYearId ORDER BY returnDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<PurchaseReturnEntity>>

    @Query(
        """SELECT COALESCE(SUM(quantity), 0) FROM purchase_return_items
           WHERE originalPurchaseInvoiceItemId = :originalItemId"""
    )
    suspend fun getReturnedQuantity(originalItemId: Long): Double
}

@Dao
interface PurchaseReturnItemDao {
    @Insert
    suspend fun insertAll(items: List<PurchaseReturnItemEntity>): List<Long>

    @Query("SELECT * FROM purchase_return_items WHERE purchaseReturnId = :returnId")
    suspend fun getForReturn(returnId: Long): List<PurchaseReturnItemEntity>
}

@Dao
interface DebitNoteDao {
    @Insert
    suspend fun insert(debitNote: DebitNoteEntity): Long

    @Query("SELECT * FROM debit_notes WHERE partyId = :partyId ORDER BY debitNoteDateMillis DESC")
    fun observeForParty(partyId: Long): Flow<List<DebitNoteEntity>>
}
