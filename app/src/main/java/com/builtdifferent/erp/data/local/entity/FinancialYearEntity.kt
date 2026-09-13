package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every transactional table (invoices, journal entries, payments, ...)
 * carries a financialYearId foreign key. This is what makes "prevent
 * accidental mixing of transactions between financial years" (Section 7)
 * enforceable at the database level rather than only in UI logic: a
 * transaction can never be inserted without pointing at a real FY row, and
 * reports filter by financialYearId rather than by re-deriving date ranges
 * every time.
 */
@Entity(
    tableName = "financial_years",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["companyId"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["startDateMillis", "endDateMillis"]),
        Index(value = ["companyId", "isCurrent"])
    ]
)
data class FinancialYearEntity(
    @PrimaryKey(autoGenerate = true)
    val financialYearId: Long = 0,

    val companyId: Long,

    val label: String,
    val startDateMillis: Long,
    val endDateMillis: Long,

    /** Exactly one FY per company should have isCurrent = true; enforced in
     * the repository layer via a transaction that flips the previous
     * current FY off before setting a new one on. */
    val isCurrent: Boolean = false,

    /** Once a financial year is closed, no new transactions may be posted
     * into it and opening balances for the next FY are locked in from its
     * closing balances. */
    val isClosed: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
)
