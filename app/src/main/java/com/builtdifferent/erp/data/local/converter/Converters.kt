package com.builtdifferent.erp.data.local.converter

import androidx.room.TypeConverter
import com.builtdifferent.erp.data.local.entity.*

/**
 * Room stores every enum as its plain .name() String rather than an
 * ordinal Int. Ordinal storage is a known migration trap: inserting a new
 * enum constant anywhere but the end silently corrupts every previously
 * stored row. Name-based storage is one extra byte or two on disk and is
 * immune to that class of bug entirely.
 */
class Converters {

    @TypeConverter fun partyTypeToString(v: PartyType?): String? = v?.name
    @TypeConverter fun stringToPartyType(v: String?): PartyType? = v?.let { PartyType.valueOf(it) }

    @TypeConverter fun addressTypeToString(v: AddressType?): String? = v?.name
    @TypeConverter fun stringToAddressType(v: String?): AddressType? = v?.let { AddressType.valueOf(it) }

    @TypeConverter fun hsnSacTypeToString(v: HsnSacType?): String? = v?.name
    @TypeConverter fun stringToHsnSacType(v: String?): HsnSacType? = v?.let { HsnSacType.valueOf(it) }

    @TypeConverter fun productTypeToString(v: ProductType?): String? = v?.name
    @TypeConverter fun stringToProductType(v: String?): ProductType? = v?.let { ProductType.valueOf(it) }

    @TypeConverter fun priceTypeToString(v: PriceType?): String? = v?.name
    @TypeConverter fun stringToPriceType(v: String?): PriceType? = v?.let { PriceType.valueOf(it) }

    @TypeConverter fun stockMovementTypeToString(v: StockMovementType?): String? = v?.name
    @TypeConverter fun stringToStockMovementType(v: String?): StockMovementType? = v?.let { StockMovementType.valueOf(it) }

    @TypeConverter fun stockRefTypeToString(v: StockRefType?): String? = v?.name
    @TypeConverter fun stringToStockRefType(v: String?): StockRefType? = v?.let { StockRefType.valueOf(it) }

    @TypeConverter fun stockAdjustmentReasonToString(v: StockAdjustmentReason?): String? = v?.name
    @TypeConverter fun stringToStockAdjustmentReason(v: String?): StockAdjustmentReason? = v?.let { StockAdjustmentReason.valueOf(it) }

    @TypeConverter fun accountGroupToString(v: AccountGroup?): String? = v?.name
    @TypeConverter fun stringToAccountGroup(v: String?): AccountGroup? = v?.let { AccountGroup.valueOf(it) }

    @TypeConverter fun accountSubGroupToString(v: AccountSubGroup?): String? = v?.name
    @TypeConverter fun stringToAccountSubGroup(v: String?): AccountSubGroup? = v?.let { AccountSubGroup.valueOf(it) }

    @TypeConverter fun journalSourceTypeToString(v: JournalSourceType?): String? = v?.name
    @TypeConverter fun stringToJournalSourceType(v: String?): JournalSourceType? = v?.let { JournalSourceType.valueOf(it) }

    @TypeConverter fun invoiceStatusToString(v: InvoiceStatus?): String? = v?.name
    @TypeConverter fun stringToInvoiceStatus(v: String?): InvoiceStatus? = v?.let { InvoiceStatus.valueOf(it) }

    @TypeConverter fun gstInvoiceCategoryToString(v: GstInvoiceCategory?): String? = v?.name
    @TypeConverter fun stringToGstInvoiceCategory(v: String?): GstInvoiceCategory? = v?.let { GstInvoiceCategory.valueOf(it) }

    @TypeConverter fun salesDocStatusToString(v: SalesDocStatus?): String? = v?.name
    @TypeConverter fun stringToSalesDocStatus(v: String?): SalesDocStatus? = v?.let { SalesDocStatus.valueOf(it) }

    @TypeConverter fun paymentModeToString(v: PaymentMode?): String? = v?.name
    @TypeConverter fun stringToPaymentMode(v: String?): PaymentMode? = v?.let { PaymentMode.valueOf(it) }

    @TypeConverter fun allocationTargetTypeToString(v: AllocationTargetType?): String? = v?.name
    @TypeConverter fun stringToAllocationTargetType(v: String?): AllocationTargetType? = v?.let { AllocationTargetType.valueOf(it) }

    @TypeConverter fun bankTransactionTypeToString(v: BankTransactionType?): String? = v?.name
    @TypeConverter fun stringToBankTransactionType(v: String?): BankTransactionType? = v?.let { BankTransactionType.valueOf(it) }

    @TypeConverter fun cashTransactionTypeToString(v: CashTransactionType?): String? = v?.name
    @TypeConverter fun stringToCashTransactionType(v: String?): CashTransactionType? = v?.let { CashTransactionType.valueOf(it) }

    @TypeConverter fun valueTypeToString(v: ValueType?): String? = v?.name
    @TypeConverter fun stringToValueType(v: String?): ValueType? = v?.let { ValueType.valueOf(it) }

    @TypeConverter fun documentTypeToString(v: DocumentType?): String? = v?.name
    @TypeConverter fun stringToDocumentType(v: String?): DocumentType? = v?.let { DocumentType.valueOf(it) }

    @TypeConverter fun gstDirectionToString(v: GstDirection?): String? = v?.name
    @TypeConverter fun stringToGstDirection(v: String?): GstDirection? = v?.let { GstDirection.valueOf(it) }

    @TypeConverter fun backupTypeToString(v: BackupType?): String? = v?.name
    @TypeConverter fun stringToBackupType(v: String?): BackupType? = v?.let { BackupType.valueOf(it) }

    @TypeConverter fun auditActionToString(v: AuditAction?): String? = v?.name
    @TypeConverter fun stringToAuditAction(v: String?): AuditAction? = v?.let { AuditAction.valueOf(it) }
}
