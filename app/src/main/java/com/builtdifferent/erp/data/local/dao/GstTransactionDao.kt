package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.GstDirection
import com.builtdifferent.erp.data.local.entity.GstTransactionEntity

@Dao
interface GstTransactionDao {

    @Insert
    suspend fun insert(transaction: GstTransactionEntity): Long

    @Query(
        """SELECT * FROM gst_transactions
           WHERE financialYearId = :financialYearId AND direction = :direction
           AND transactionDateMillis BETWEEN :fromMillis AND :toMillis
           ORDER BY transactionDateMillis ASC"""
    )
    suspend fun getForPeriod(financialYearId: Long, direction: GstDirection, fromMillis: Long, toMillis: Long): List<GstTransactionEntity>

    @Query(
        """SELECT COALESCE(SUM(taxableValuePaise),0) as taxable, COALESCE(SUM(cgstPaise),0) as cgst,
           COALESCE(SUM(sgstPaise),0) as sgst, COALESCE(SUM(igstPaise),0) as igst, COALESCE(SUM(cessPaise),0) as cess
           FROM gst_transactions
           WHERE financialYearId = :financialYearId AND direction = :direction
           AND transactionDateMillis BETWEEN :fromMillis AND :toMillis"""
    )
    suspend fun getSummaryForPeriod(financialYearId: Long, direction: GstDirection, fromMillis: Long, toMillis: Long): GstSummaryRow
}

data class GstSummaryRow(
    val taxable: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val cess: Long
)
