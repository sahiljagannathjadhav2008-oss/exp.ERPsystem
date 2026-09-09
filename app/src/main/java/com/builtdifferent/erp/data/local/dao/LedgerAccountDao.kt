package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.LedgerAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerAccountDao {

    @Insert
    suspend fun insert(account: LedgerAccountEntity): Long

    @Update
    suspend fun update(account: LedgerAccountEntity)

    @Query("SELECT * FROM ledger_accounts WHERE ledgerAccountId = :id")
    suspend fun getById(id: Long): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE accountName = :name LIMIT 1")
    suspend fun findByName(name: String): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE linkedPartyId = :partyId LIMIT 1")
    suspend fun findByPartyId(partyId: Long): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE isActive = 1 ORDER BY accountName ASC")
    fun observeAll(): Flow<List<LedgerAccountEntity>>

    @Query("SELECT COUNT(*) FROM ledger_accounts")
    suspend fun count(): Int
}
