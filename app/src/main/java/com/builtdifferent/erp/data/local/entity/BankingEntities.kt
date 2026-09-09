package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Each bank account maps 1:1 to a LedgerAccountEntity (subGroup BANK) so
 * its balance is always the ledger balance, same pattern as PartyEntity. */
@Entity(
    tableName = "bank_accounts",
    foreignKeys = [
        ForeignKey(LedgerAccountEntity::class, ["ledgerAccountId"], ["ledgerAccountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["ledgerAccountId"], unique = true)]
)
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true) val bankAccountId: Long = 0,
    val ledgerAccountId: Long,
    val accountName: String,
    val bankName: String,
    val accountNumber: String,
    val ifsc: String,
    val branch: String = "",
    val openingBalancePaise: Long = 0,
    val isActive: Boolean = true
)

enum class BankTransactionType { DEPOSIT, WITHDRAWAL }

/** Read-model of bank movements for the bank register/reconciliation
 * screen; every row here is also mirrored as JournalEntryLineEntity rows
 * against the account's ledgerAccountId (source of truth), same
 * dual-representation pattern as StockEntity/StockMovementEntity. */
@Entity(
    tableName = "bank_transactions",
    foreignKeys = [
        ForeignKey(BankAccountEntity::class, ["bankAccountId"], ["bankAccountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["bankAccountId"]), Index(value = ["transactionDateMillis"]), Index(value = ["sourceType", "sourceId"])]
)
data class BankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val bankTransactionId: Long = 0,
    val bankAccountId: Long,
    val type: BankTransactionType,
    val amountPaise: Long,
    val transactionDateMillis: Long,
    val sourceType: JournalSourceType,
    val sourceId: Long,
    val isReconciled: Boolean = false,
    val reconciledDateMillis: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class CashTransactionType { RECEIPT, PAYMENT }

/** Same read-model role as BankTransactionEntity but for the default Cash
 * ledger account, so a simple day-book/cash-in-hand view doesn't need to
 * join through journal_entry_lines. */
@Entity(
    tableName = "cash_transactions",
    indices = [Index(value = ["transactionDateMillis"]), Index(value = ["sourceType", "sourceId"])]
)
data class CashTransactionEntity(
    @PrimaryKey(autoGenerate = true) val cashTransactionId: Long = 0,
    val type: CashTransactionType,
    val amountPaise: Long,
    val transactionDateMillis: Long,
    val sourceType: JournalSourceType,
    val sourceId: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
