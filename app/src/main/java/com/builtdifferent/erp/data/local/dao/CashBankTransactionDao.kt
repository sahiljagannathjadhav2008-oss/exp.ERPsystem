package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.BankTransactionEntity
import com.builtdifferent.erp.data.local.entity.CashTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashTransactionDao {
    @Insert
    suspend fun insert(transaction: CashTransactionEntity): Long

    @Query("SELECT * FROM cash_transactions WHERE transactionDateMillis BETWEEN :fromMillis AND :toMillis ORDER BY transactionDateMillis ASC")
    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<CashTransactionEntity>>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN type = 'RECEIPT' THEN amountPaise ELSE -amountPaise END), 0)
           FROM cash_transactions WHERE transactionDateMillis <= :asOfMillis"""
    )
    suspend fun getBalanceAsOf(asOfMillis: Long): Long
}

@Dao
interface BankTransactionDao {
    @Insert
    suspend fun insert(transaction: BankTransactionEntity): Long

    @Query("SELECT * FROM bank_transactions WHERE bankAccountId = :bankAccountId ORDER BY transactionDateMillis ASC")
    fun observeForAccount(bankAccountId: Long): Flow<List<BankTransactionEntity>>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN type = 'DEPOSIT' THEN amountPaise ELSE -amountPaise END), 0)
           FROM bank_transactions WHERE bankAccountId = :bankAccountId AND transactionDateMillis <= :asOfMillis"""
    )
    suspend fun getBalanceAsOf(bankAccountId: Long, asOfMillis: Long): Long

    @Query("UPDATE bank_transactions SET isReconciled = 1, reconciledDateMillis = :reconciledAt WHERE bankTransactionId = :id")
    suspend fun markReconciled(id: Long, reconciledAt: Long)
}
