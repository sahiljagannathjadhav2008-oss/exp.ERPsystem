package com.builtdifferent.erp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.builtdifferent.erp.data.local.converter.Converters
import com.builtdifferent.erp.data.local.dao.*
import com.builtdifferent.erp.data.local.entity.*

/**
 * BUSINESS RULE (documented per project convention — schema stability):
 *
 * Every table required by the full ERP specification is registered here
 * from version 1 onward, even though this initial implementation pass only
 * ships working DAOs/business logic for company/financial-year/party/
 * product/unit/HSN-SAC/GST-rate. Declaring the complete schema up front
 * (rather than adding tables module-by-module via later migrations) means
 * later passes only ever ADD columns/indices via proper Migration objects
 * (see AppDatabaseMigrations) — they never need a destructive table
 * creation that could conflict with data a user has already entered.
 *
 * Room's exportSchema is enabled (see room.schemaLocation in app/build.gradle.kts)
 * so every future schema change is diffable and migration tests can assert
 * against the exact previous schema.
 */
@Database(
    entities = [
        CompanyEntity::class,
        FinancialYearEntity::class,
        PartyEntity::class,
        PartyAddressEntity::class,
        UnitEntity::class,
        ProductCategoryEntity::class,
        HsnSacEntity::class,
        GstRateEntity::class,
        ProductEntity::class,
        ProductBarcodeEntity::class,
        ProductPriceEntity::class,
        WarehouseEntity::class,
        StockEntity::class,
        StockMovementEntity::class,
        StockAdjustmentEntity::class,
        LedgerAccountEntity::class,
        JournalEntryEntity::class,
        JournalEntryLineEntity::class,
        SalesQuotationEntity::class,
        SalesOrderEntity::class,
        SalesPreDocItemEntity::class,
        DeliveryChallanEntity::class,
        DeliveryChallanItemEntity::class,
        SalesInvoiceEntity::class,
        SalesInvoiceItemEntity::class,
        SalesReturnEntity::class,
        SalesReturnItemEntity::class,
        CreditNoteEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderItemEntity::class,
        PurchaseInvoiceEntity::class,
        PurchaseInvoiceItemEntity::class,
        PurchaseReturnEntity::class,
        PurchaseReturnItemEntity::class,
        DebitNoteEntity::class,
        PaymentReceivedEntity::class,
        PaymentMadeEntity::class,
        PaymentAllocationEntity::class,
        ExpenseEntity::class,
        IncomeEntity::class,
        BankAccountEntity::class,
        BankTransactionEntity::class,
        CashTransactionEntity::class,
        DiscountEntity::class,
        ChargeEntity::class,
        InvoiceSequenceEntity::class,
        GstTransactionEntity::class,
        BackupMetadataEntity::class,
        AppSettingEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun companyDao(): CompanyDao
    abstract fun financialYearDao(): FinancialYearDao
    abstract fun partyDao(): PartyDao
    abstract fun unitDao(): UnitDao
    abstract fun productCategoryDao(): ProductCategoryDao
    abstract fun hsnSacDao(): HsnSacDao
    abstract fun gstRateDao(): GstRateDao
    abstract fun productDao(): ProductDao
    abstract fun ledgerAccountDao(): LedgerAccountDao
    abstract fun productPriceDao(): ProductPriceDao
    abstract fun stockDao(): StockDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun salesInvoiceDao(): SalesInvoiceDao
    abstract fun salesInvoiceItemDao(): SalesInvoiceItemDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun invoiceSequenceDao(): InvoiceSequenceDao
    abstract fun gstTransactionDao(): GstTransactionDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun purchaseInvoiceItemDao(): PurchaseInvoiceItemDao
    abstract fun paymentReceivedDao(): PaymentReceivedDao
    abstract fun paymentMadeDao(): PaymentMadeDao
    abstract fun paymentAllocationDao(): PaymentAllocationDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun salesReturnDao(): SalesReturnDao
    abstract fun salesReturnItemDao(): SalesReturnItemDao
    abstract fun creditNoteDao(): CreditNoteDao
    abstract fun purchaseReturnDao(): PurchaseReturnDao
    abstract fun purchaseReturnItemDao(): PurchaseReturnItemDao
    abstract fun debitNoteDao(): DebitNoteDao
    abstract fun salesQuotationDao(): SalesQuotationDao
    abstract fun salesOrderDao(): SalesOrderDao
    abstract fun salesPreDocItemDao(): SalesPreDocItemDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun purchaseOrderItemDao(): PurchaseOrderItemDao
    abstract fun deliveryChallanDao(): DeliveryChallanDao
    abstract fun deliveryChallanItemDao(): DeliveryChallanItemDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun cashTransactionDao(): CashTransactionDao
    abstract fun bankTransactionDao(): BankTransactionDao
    abstract fun stockReportDao(): StockReportDao
    abstract fun backupMetadataDao(): BackupMetadataDao

    // Additional DAOs (inventory, sales, purchase, accounting, payments,
    // reports, backup) are added to this list as each module's business
    // logic is implemented in subsequent passes over this same file —
    // their entities already exist above so no table is ever recreated.

    companion object {
        const val DATABASE_NAME = "offline_erp.db"
        const val SCHEMA_VERSION = 1
    }
}
