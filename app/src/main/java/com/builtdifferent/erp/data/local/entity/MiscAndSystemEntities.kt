package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ValueType { PERCENT, FLAT }

/** Reusable named discounts (e.g. "Festival Offer 10%") selectable on
 * invoice lines/headers, rather than free-typed every time. */
@Entity(tableName = "discounts")
data class DiscountEntity(
    @PrimaryKey(autoGenerate = true) val discountId: Long = 0,
    val name: String,
    val valueType: ValueType,
    val value: Double,
    val isActive: Boolean = true
)

/** Reusable named additional charges (freight, packing, installation) that
 * can be added to an invoice as an extra taxable or non-taxable line. */
@Entity(tableName = "charges")
data class ChargeEntity(
    @PrimaryKey(autoGenerate = true) val chargeId: Long = 0,
    val name: String,
    val isTaxable: Boolean = true,
    val defaultHsnSacId: Long? = null,
    val isActive: Boolean = true
)

enum class DocumentType {
    SALES_QUOTATION, SALES_ORDER, DELIVERY_CHALLAN, SALES_INVOICE, SALES_RETURN,
    CREDIT_NOTE, PURCHASE_ORDER, PURCHASE_INVOICE, PURCHASE_RETURN, DEBIT_NOTE,
    PAYMENT_RECEIPT, PAYMENT_VOUCHER
}

/**
 * One row per (documentType, financialYearId): holds the next number to
 * issue plus prefix/padding, so numbering is atomic (increment happens
 * inside the same transaction that inserts the document, preventing two
 * concurrent saves from ever getting the same number) and resets cleanly
 * per financial year, matching Section 8/61's invoice-numbering
 * requirement without hard-coding format assumptions per document type.
 */
@Entity(
    tableName = "invoice_sequences",
    indices = [Index(value = ["documentType", "financialYearId"], unique = true)]
)
data class InvoiceSequenceEntity(
    @PrimaryKey(autoGenerate = true) val invoiceSequenceId: Long = 0,
    val documentType: DocumentType,
    val financialYearId: Long,
    val prefix: String,
    val nextNumber: Long = 1,
    val padding: Int = 4
)

/**
 * Denormalized per-line GST ledger purely for fast GST report queries
 * (GSTR-1 style outward summary, GSTR-3B style tax liability) without
 * re-joining every invoice item table. Populated in the same transaction as
 * the sales/purchase invoice item it summarizes — never computed
 * independently, so it can never disagree with the invoice.
 */
enum class GstDirection { OUTWARD, INWARD }

@Entity(
    tableName = "gst_transactions",
    indices = [
        Index(value = ["financialYearId"]),
        Index(value = ["direction"]),
        Index(value = ["transactionDateMillis"]),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class GstTransactionEntity(
    @PrimaryKey(autoGenerate = true) val gstTransactionId: Long = 0,
    val financialYearId: Long,
    val direction: GstDirection,
    val transactionDateMillis: Long,
    val partyId: Long,
    val partyGstin: String? = null,
    val hsnSacCode: String,
    val taxableValuePaise: Long,
    val cgstPaise: Long = 0,
    val sgstPaise: Long = 0,
    val igstPaise: Long = 0,
    val cessPaise: Long = 0,
    val sourceType: JournalSourceType,
    val sourceId: Long
)

enum class BackupType { MANUAL, AUTO }

/** Metadata for every backup created, whether or not it has since been
 * restored — restoring never deletes prior backup metadata rows, since
 * knowing what backups exist and when is itself part of the audit trail. */
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey(autoGenerate = true) val backupMetadataId: Long = 0,
    val filePath: String,
    val fileSizeBytes: Long,
    val type: BackupType,
    val appVersionName: String,
    val databaseVersion: Int,
    val checksumSha256: String,
    val createdAt: Long = System.currentTimeMillis(),
    val restoredAt: Long? = null
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AuditAction { CREATE, UPDATE, DELETE, POST, CANCEL, RESTORE }

/** Immutable, append-only log of every mutating action on financially
 * significant tables (invoices, payments, journal entries, master data
 * edits) — required so "who changed what and when" is always answerable,
 * and so a restored backup's history isn't indistinguishable from live
 * data (restoredAt on BackupMetadataEntity plus a RESTORE audit row
 * together mark the boundary). */
@Entity(
    tableName = "audit_log",
    indices = [Index(value = ["entityName", "entityId"]), Index(value = ["createdAt"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val auditLogId: Long = 0,
    val entityName: String,
    val entityId: Long,
    val action: AuditAction,
    /** JSON diff or snapshot of changed fields, kept minimal (not a full
     * row dump) to bound table growth. */
    val detailsJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
