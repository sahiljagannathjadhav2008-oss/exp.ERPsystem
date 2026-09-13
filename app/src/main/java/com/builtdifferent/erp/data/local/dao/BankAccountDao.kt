package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.BankAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Insert
    suspend fun insert(account: BankAccountEntity): Long

    @Update
    suspend fun update(account: BankAccountEntity)

    @Query("SELECT * FROM bank_accounts WHERE bankAccountId = :id")
    suspend fun getById(id: Long): BankAccountEntity?

    @Query("SELECT * FROM bank_accounts WHERE isActive = 1 ORDER BY accountName ASC")
    fun observeAll(): Flow<List<BankAccountEntity>>
}
