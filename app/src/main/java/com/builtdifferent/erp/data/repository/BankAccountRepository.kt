package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.AccountGroup
import com.builtdifferent.erp.data.local.entity.AccountSubGroup
import com.builtdifferent.erp.data.local.entity.BankAccountEntity
import com.builtdifferent.erp.data.local.entity.LedgerAccountEntity
import kotlinx.coroutines.flow.Flow

/** Same "always create the ledger account in the same transaction" pattern
 * as PartyRepository — a bank account with no ledger account (or vice
 * versa) should be structurally impossible, not just a convention. */
class BankAccountRepository(private val db: AppDatabase) {
    private val bankAccountDao = db.bankAccountDao()
    private val ledgerAccountDao = db.ledgerAccountDao()

    fun observeAll(): Flow<List<BankAccountEntity>> = bankAccountDao.observeAll()
    suspend fun getById(id: Long) = bankAccountDao.getById(id)

    suspend fun createBankAccount(
        accountName: String,
        bankName: String,
        accountNumber: String,
        ifsc: String,
        branch: String,
        openingBalancePaise: Long
    ): Long = db.withTransaction {
        val ledgerAccountId = ledgerAccountDao.insert(
            LedgerAccountEntity(
                accountName = accountName,
                group = AccountGroup.ASSET,
                subGroup = AccountSubGroup.BANK,
                openingBalancePaise = openingBalancePaise,
                normalBalanceIsDebit = true
            )
        )
        bankAccountDao.insert(
            BankAccountEntity(
                ledgerAccountId = ledgerAccountId,
                accountName = accountName,
                bankName = bankName,
                accountNumber = accountNumber,
                ifsc = ifsc,
                branch = branch,
                openingBalancePaise = openingBalancePaise
            )
        )
    }
}
