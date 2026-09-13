package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.AccountGroup
import com.builtdifferent.erp.data.local.entity.AccountSubGroup
import com.builtdifferent.erp.data.local.entity.LedgerAccountEntity
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * Every party gets exactly one ledger account, created in the SAME database
 * transaction as the party row itself (via AppDatabase.withTransaction), so
 * it is impossible to end up with a party that has no ledger account or a
 * ledger account that outlives its party — see PartyEntity/LedgerAccountEntity
 * doc comments for why the two must always exist together.
 */
class PartyRepository(private val db: AppDatabase) {

    private val partyDao = db.partyDao()
    private val ledgerAccountDao = db.ledgerAccountDao()

    fun observeParties(type: PartyType?, search: String = ""): Flow<List<PartyEntity>> =
        partyDao.observeParties(type, search)

    fun observeActivePartyCount(): Flow<Int> = partyDao.observeActivePartyCount()

    suspend fun getById(partyId: Long): PartyEntity? = partyDao.getById(partyId)

    suspend fun createParty(party: PartyEntity): Long = db.withTransaction {
        val subGroup = when (party.type) {
            PartyType.CUSTOMER -> AccountSubGroup.RECEIVABLE
            PartyType.SUPPLIER -> AccountSubGroup.PAYABLE
            PartyType.BOTH -> AccountSubGroup.RECEIVABLE
        }
        val group = if (subGroup == AccountSubGroup.RECEIVABLE) AccountGroup.ASSET else AccountGroup.LIABILITY

        val ledgerAccountId = ledgerAccountDao.insert(
            LedgerAccountEntity(
                accountName = party.partyName,
                group = group,
                subGroup = subGroup,
                linkedPartyId = null,
                openingBalancePaise = party.openingBalancePaise,
                normalBalanceIsDebit = subGroup == AccountSubGroup.RECEIVABLE
            )
        )
        val partyId = partyDao.insert(party.copy(ledgerAccountId = ledgerAccountId))
        val insertedLedger = ledgerAccountDao.getById(ledgerAccountId)
        if (insertedLedger != null) {
            ledgerAccountDao.update(insertedLedger.copy(linkedPartyId = partyId))
        }
        partyId
    }

    suspend fun updateParty(party: PartyEntity) {
        partyDao.update(party.copy(updatedAt = DateUtils.nowMillis()))
        val ledgerAccountId = party.ledgerAccountId ?: return
        val ledger = ledgerAccountDao.getById(ledgerAccountId) ?: return
        if (ledger.accountName != party.partyName) {
            ledgerAccountDao.update(ledger.copy(accountName = party.partyName))
        }
    }

    suspend fun deactivateParty(partyId: Long) = partyDao.softDelete(partyId)
}
