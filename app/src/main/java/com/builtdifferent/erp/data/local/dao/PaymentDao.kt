package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.AllocationTargetType
import com.builtdifferent.erp.data.local.entity.PaymentAllocationEntity
import com.builtdifferent.erp.data.local.entity.PaymentMadeEntity
import com.builtdifferent.erp.data.local.entity.PaymentReceivedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentReceivedDao {
    @Insert
    suspend fun insert(payment: PaymentReceivedEntity): Long

    @Query("SELECT * FROM payments_received WHERE partyId = :partyId ORDER BY paymentDateMillis DESC")
    fun observeForParty(partyId: Long): Flow<List<PaymentReceivedEntity>>

    @Query("SELECT * FROM payments_received WHERE financialYearId = :financialYearId ORDER BY paymentDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<PaymentReceivedEntity>>
}

@Dao
interface PaymentMadeDao {
    @Insert
    suspend fun insert(payment: PaymentMadeEntity): Long

    @Query("SELECT * FROM payments_made WHERE partyId = :partyId ORDER BY paymentDateMillis DESC")
    fun observeForParty(partyId: Long): Flow<List<PaymentMadeEntity>>

    @Query("SELECT * FROM payments_made WHERE financialYearId = :financialYearId ORDER BY paymentDateMillis DESC")
    fun observeForFinancialYear(financialYearId: Long): Flow<List<PaymentMadeEntity>>
}

@Dao
interface PaymentAllocationDao {
    @Insert
    suspend fun insertAll(allocations: List<PaymentAllocationEntity>): List<Long>

    @Query(
        """SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations
           WHERE targetType = :targetType AND targetId = :targetId"""
    )
    suspend fun getTotalAllocated(targetType: AllocationTargetType, targetId: Long): Long

    @Query("SELECT * FROM payment_allocations WHERE targetType = :targetType AND targetId = :targetId")
    fun observeForTarget(targetType: AllocationTargetType, targetId: Long): Flow<List<PaymentAllocationEntity>>
}
