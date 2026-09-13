package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.ExpenseEntity
import com.builtdifferent.erp.data.local.entity.IncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE financialYearId = :financialYearId ORDER BY expenseDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<ExpenseEntity>>

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM expenses
           WHERE financialYearId = :financialYearId AND expenseDateMillis BETWEEN :fromMillis AND :toMillis"""
    )
    suspend fun sumInRange(financialYearId: Long, fromMillis: Long, toMillis: Long): Long
}

@Dao
interface IncomeDao {
    @Insert
    suspend fun insert(income: IncomeEntity): Long

    @Query("SELECT * FROM income_entries WHERE financialYearId = :financialYearId ORDER BY incomeDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<IncomeEntity>>

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM income_entries
           WHERE financialYearId = :financialYearId AND incomeDateMillis BETWEEN :fromMillis AND :toMillis"""
    )
    suspend fun sumInRange(financialYearId: Long, fromMillis: Long, toMillis: Long): Long
}
