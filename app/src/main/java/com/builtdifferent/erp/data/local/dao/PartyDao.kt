package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {

    @Insert
    suspend fun insert(party: PartyEntity): Long

    @Update
    suspend fun update(party: PartyEntity)

    @Query("UPDATE parties SET ledgerAccountId = :ledgerAccountId WHERE partyId = :partyId")
    suspend fun attachLedgerAccount(partyId: Long, ledgerAccountId: Long)

    @Query("SELECT * FROM parties WHERE partyId = :partyId")
    suspend fun getById(partyId: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE partyId = :partyId")
    fun observeById(partyId: Long): Flow<PartyEntity?>

    @Query(
        """SELECT * FROM parties
           WHERE isActive = 1
           AND (:type IS NULL OR type = :type OR type = 'BOTH')
           AND (partyName LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%' OR gstin LIKE '%' || :search || '%')
           ORDER BY partyName ASC"""
    )
    fun observeParties(type: PartyType?, search: String = ""): Flow<List<PartyEntity>>

    @Query("SELECT COUNT(*) FROM parties WHERE isActive = 1")
    fun observeActivePartyCount(): Flow<Int>

    @Query("UPDATE parties SET isActive = 0 WHERE partyId = :partyId")
    suspend fun softDelete(partyId: Long)
}
