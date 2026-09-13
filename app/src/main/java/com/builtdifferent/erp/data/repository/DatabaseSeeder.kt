package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.dao.LedgerAccountDao
import com.builtdifferent.erp.data.local.dao.UnitDao
import com.builtdifferent.erp.data.local.entity.AccountGroup
import com.builtdifferent.erp.data.local.entity.AccountSubGroup
import com.builtdifferent.erp.data.local.entity.LedgerAccountEntity
import com.builtdifferent.erp.data.local.entity.UnitEntity

/**
 * Seeds the fixed system ledger accounts (Sales, Purchases, the six GST
 * input/output accounts, Round Off, Cash) and a starter set of units of
 * measure, exactly once, the first time a company is created. These
 * accounts are marked isSystemAccount = true so later screens can prevent
 * the user from deleting the accounts the accounting/GST engine depends on
 * by name.
 */
class DatabaseSeeder(
    private val ledgerAccountDao: LedgerAccountDao,
    private val unitDao: UnitDao
) {
    suspend fun seedIfNeeded() {
        if (ledgerAccountDao.count() == 0) {
            seedSystemAccounts()
        }
        if (unitDao.count() == 0) {
            seedDefaultUnits()
        }
    }

    private suspend fun seedSystemAccounts() {
        val accounts = listOf(
            LedgerAccountEntity(accountName = "Cash", group = AccountGroup.ASSET, subGroup = AccountSubGroup.CASH, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "Sales Account", group = AccountGroup.INCOME, subGroup = AccountSubGroup.DIRECT_INCOME, isSystemAccount = true, normalBalanceIsDebit = false),
            LedgerAccountEntity(accountName = "Purchase Account", group = AccountGroup.EXPENSE, subGroup = AccountSubGroup.DIRECT_EXPENSE, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "CGST Output", group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = false),
            LedgerAccountEntity(accountName = "SGST Output", group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = false),
            LedgerAccountEntity(accountName = "IGST Output", group = AccountGroup.LIABILITY, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = false),
            LedgerAccountEntity(accountName = "CGST Input", group = AccountGroup.ASSET, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "SGST Input", group = AccountGroup.ASSET, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "IGST Input", group = AccountGroup.ASSET, subGroup = AccountSubGroup.DUTIES_AND_TAXES, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "Round Off", group = AccountGroup.INCOME, subGroup = AccountSubGroup.INDIRECT_INCOME, isSystemAccount = true, normalBalanceIsDebit = false),
            LedgerAccountEntity(accountName = "Discount Allowed", group = AccountGroup.EXPENSE, subGroup = AccountSubGroup.INDIRECT_EXPENSE, isSystemAccount = true, normalBalanceIsDebit = true),
            LedgerAccountEntity(accountName = "Discount Received", group = AccountGroup.INCOME, subGroup = AccountSubGroup.INDIRECT_INCOME, isSystemAccount = true, normalBalanceIsDebit = false)
        )
        accounts.forEach { ledgerAccountDao.insert(it) }
    }

    private suspend fun seedDefaultUnits() {
        val units = listOf(
            UnitEntity(unitCode = "NOS", displayLabel = "Pieces (Nos)", allowDecimalQuantity = false),
            UnitEntity(unitCode = "KGS", displayLabel = "Kilograms", allowDecimalQuantity = true),
            UnitEntity(unitCode = "GMS", displayLabel = "Grams", allowDecimalQuantity = true),
            UnitEntity(unitCode = "LTR", displayLabel = "Litres", allowDecimalQuantity = true),
            UnitEntity(unitCode = "MTR", displayLabel = "Metres", allowDecimalQuantity = true),
            UnitEntity(unitCode = "BOX", displayLabel = "Box", allowDecimalQuantity = false),
            UnitEntity(unitCode = "SET", displayLabel = "Set", allowDecimalQuantity = false),
            UnitEntity(unitCode = "PAC", displayLabel = "Packs", allowDecimalQuantity = false)
        )
        units.forEach { unitDao.insert(it) }
    }
}
