package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountGroup {
    ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
}

enum class AccountSubGroup {
    CASH, BANK, RECEIVABLE, PAYABLE, INVENTORY, FIXED_ASSET,
    DUTIES_AND_TAXES, CAPITAL, DIRECT_INCOME, INDIRECT_INCOME,
    DIRECT_EXPENSE, INDIRECT_EXPENSE, OTHER
}

/**
 * The full chart of accounts. Every party (customer/supplier) gets exactly
 * one auto-created ledger account (subGroup RECEIVABLE or PAYABLE) so party
 * balances and the accounting ledger are always the same numbers viewed two
 * ways — there is no separate "party balance" computed independently of the
 * ledger; PartyEntity.ledgerAccountId points here and every posting against
 * the party goes through JournalEntryLineEntity like any other account.
 *
 * System accounts (Sales, Purchases, CGST Output, SGST Output, IGST Output,
 * CGST Input, SGST Input, IGST Input, Round Off, Cash, discounts) are seeded
 * once on first company setup (see DatabaseSeeder) and are marked
 * isSystemAccount = true so the UI prevents deleting them.
 */
@Entity(
    tableName = "ledger_accounts",
    indices = [Index(value = ["accountName"], unique = true), Index(value = ["group"])]
)
data class LedgerAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val ledgerAccountId: Long = 0,
    val accountName: String,
    val group: AccountGroup,
    val subGroup: AccountSubGroup,
    val isSystemAccount: Boolean = false,
    /** Set when this account represents a specific party, else null for
     * general ledger accounts (Sales, Purchases, tax accounts, etc). */
    val linkedPartyId: Long? = null,
    val openingBalancePaise: Long = 0,
    /** true = normal debit balance (Asset/Expense), false = normal credit
     * balance (Liability/Equity/Income). Used to sign running-balance
     * displays consistently. */
    val normalBalanceIsDebit: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class JournalSourceType {
    MANUAL, SALES_INVOICE, SALES_RETURN, PURCHASE_INVOICE, PURCHASE_RETURN,
    PAYMENT_RECEIVED, PAYMENT_MADE, EXPENSE, INCOME, OPENING_BALANCE,
    STOCK_ADJUSTMENT
}

/**
 * The header of a double-entry posting. A JournalEntryEntity is only ever
 * created together with >= 2 JournalEntryLineEntity rows whose debit/credit
 * totals are equal — enforced in AccountingRepository.postJournalEntry(),
 * which runs inside a single Room @Transaction and throws rather than
 * commits an unbalanced entry. This is what "double-entry balance" (the
 * required unit test, Section 63) verifies.
 *
 * Entries are never edited or deleted once posted (accounting immutability)
 * — a correction is posted as a new reversing entry, keeping a full audit
 * trail, which is also what makes AuditLogEntity meaningful.
 */
@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = FinancialYearEntity::class,
            parentColumns = ["financialYearId"],
            childColumns = ["financialYearId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["financialYearId"]),
        Index(value = ["entryDateMillis"]),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val journalEntryId: Long = 0,
    val financialYearId: Long,
    val entryDateMillis: Long,
    val narration: String = "",
    val sourceType: JournalSourceType,
    /** Id of the source document (invoice/payment/etc) that generated this
     * entry, or 0 for a manual journal entry. */
    val sourceId: Long = 0,
    val isReversed: Boolean = false,
    /** If this entry reverses another one, points at it. */
    val reversalOfJournalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_entry_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["journalEntryId"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LedgerAccountEntity::class,
            parentColumns = ["ledgerAccountId"],
            childColumns = ["ledgerAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["journalEntryId"]),
        Index(value = ["ledgerAccountId"])
    ]
)
data class JournalEntryLineEntity(
    @PrimaryKey(autoGenerate = true)
    val journalEntryLineId: Long = 0,
    val journalEntryId: Long,
    val ledgerAccountId: Long,
    /** Exactly one of debitPaise/creditPaise is non-zero per line; both
     * present as fields (rather than one signed column) so ledger/report
     * queries can SUM(debitPaise) and SUM(creditPaise) directly. */
    val debitPaise: Long = 0,
    val creditPaise: Long = 0,
    val note: String = ""
)
