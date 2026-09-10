package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row-per-business table. Most small businesses using this ERP are a
 * single legal entity, but the schema allows multiple rows (isActive picks
 * the current one) so a future multi-company mode does not require a
 * destructive migration.
 *
 * Editing company info (address, GSTIN, logo, etc.) must NEVER rewrite
 * historical invoices — invoices snapshot the company details that were
 * true at the time of issue into SalesInvoiceEntity/PurchaseInvoiceEntity
 * (see companySnapshotJson), so this table only reflects "current" info.
 */
@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true)
    val companyId: Long = 0,

    val legalName: String,
    val tradeName: String? = null,

    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val stateCode: String = "",
    val pinCode: String = "",
    val country: String = "India",

    val phone: String = "",
    val email: String = "",
    val website: String = "",

    val gstin: String? = null,
    val pan: String? = null,

    val bankAccountName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val bankName: String = "",
    val bankBranch: String = "",

    val invoicePrefix: String = "INV-",
    val nextInvoiceNumber: Long = 1,
    val invoiceNumberPadding: Int = 4,

    val termsAndConditions: String = "",
    val authorizedSignatory: String = "",

    val logoPath: String? = null,

    /** Default financial-year start expressed as MM-dd, e.g. "04-01" for
     * 1 April. Used only when creating a new FinancialYearEntity. */
    val financialYearStartMonthDay: String = "04-01",

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
