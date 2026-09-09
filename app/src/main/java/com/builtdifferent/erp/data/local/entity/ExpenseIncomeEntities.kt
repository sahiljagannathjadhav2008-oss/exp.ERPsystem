package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Indirect expenses not tied to a purchase invoice (rent, salaries,
 * electricity, etc). Each posts a journal entry Dr <expense ledger>, Cr
 * Cash/Bank via ledgerAccountId, which must point at an account with
 * subGroup DIRECT_EXPENSE or INDIRECT_EXPENSE. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(LedgerAccountEntity::class, ["ledgerAccountId"], ["expenseLedgerAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["expenseLedgerAccountId"]), Index(value = ["financialYearId"]), Index(value = ["expenseDateMillis"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val expenseId: Long = 0,
    val expenseDateMillis: Long,
    val financialYearId: Long,
    val expenseLedgerAccountId: Long,
    val amountPaise: Long,
    val paymentMode: PaymentMode,
    val bankAccountId: Long? = null,
    val vendorPartyId: Long? = null,
    val description: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Indirect income not tied to a sales invoice (interest received, rent
 * income, misc income). */
@Entity(
    tableName = "income_entries",
    foreignKeys = [
        ForeignKey(LedgerAccountEntity::class, ["ledgerAccountId"], ["incomeLedgerAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["incomeLedgerAccountId"]), Index(value = ["financialYearId"]), Index(value = ["incomeDateMillis"])]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val incomeId: Long = 0,
    val incomeDateMillis: Long,
    val financialYearId: Long,
    val incomeLedgerAccountId: Long,
    val amountPaise: Long,
    val receiptMode: PaymentMode,
    val bankAccountId: Long? = null,
    val sourcePartyId: Long? = null,
    val description: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
