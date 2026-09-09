package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.AllocationTargetType
import com.builtdifferent.erp.data.local.entity.BankTransactionEntity
import com.builtdifferent.erp.data.local.entity.BankTransactionType
import com.builtdifferent.erp.data.local.entity.CashTransactionEntity
import com.builtdifferent.erp.data.local.entity.CashTransactionType
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.PaymentAllocationEntity
import com.builtdifferent.erp.data.local.entity.PaymentMadeEntity
import com.builtdifferent.erp.data.local.entity.PaymentMode
import com.builtdifferent.erp.data.local.entity.PaymentReceivedEntity
import com.builtdifferent.erp.util.DateUtils

/** One allocation the caller wants to make: how much of this payment goes
 * against which invoice. The UI is responsible for not allocating more than
 * an invoice's outstanding balance — this repository enforces the payment
 * total matches the sum of allocations, but does not second-guess which
 * invoices were chosen (an on-account payment with zero allocations is
 * valid too, for money received before it's known which invoice it covers). */
data class PaymentAllocationInput(val targetType: AllocationTargetType, val targetId: Long, val amountPaise: Long)

/**
 * Recording a payment atomically:
 *  1. Inserts the PaymentReceivedEntity/PaymentMadeEntity row
 *  2. Inserts one PaymentAllocationEntity row per allocation given
 *  3. Posts a balanced journal entry:
 *       Payment received: Dr Cash/Bank, Cr Party (receivable decreases)
 *       Payment made:     Dr Party (payable decreases), Cr Cash/Bank
 *
 * Outstanding balance for any invoice is never a stored/maintained field —
 * it is always (grandTotal - SUM(allocations)), computed on demand via
 * PaymentAllocationDao.getTotalAllocated, so it can never drift from the
 * ledger the way a manually-updated "amount paid" column could.
 */
class PaymentRepository(
    private val db: AppDatabase,
    private val accountingRepository: AccountingRepository
) {
    private val paymentReceivedDao = db.paymentReceivedDao()
    private val paymentMadeDao = db.paymentMadeDao()
    private val allocationDao = db.paymentAllocationDao()
    private val ledgerAccountDao = db.ledgerAccountDao()

    suspend fun getOutstanding(targetType: AllocationTargetType, targetId: Long, grandTotalPaise: Long): Long {
        val allocated = allocationDao.getTotalAllocated(targetType, targetId)
        return grandTotalPaise - allocated
    }

    suspend fun recordPaymentReceived(
        partyId: Long,
        partyLedgerAccountId: Long,
        financialYearId: Long,
        receiptNumber: String,
        paymentDateMillis: Long,
        amountPaise: Long,
        mode: PaymentMode,
        bankAccountId: Long?,
        referenceNumber: String,
        note: String,
        allocations: List<PaymentAllocationInput>
    ): Long {
        val allocatedTotal = allocations.sumOf { it.amountPaise }
        if (allocatedTotal > amountPaise) {
            throw IllegalArgumentException("Allocated amount ($allocatedTotal) exceeds payment amount ($amountPaise)")
        }

        return db.withTransaction {
            val paymentId = paymentReceivedDao.insert(
                PaymentReceivedEntity(
                    receiptNumber = receiptNumber,
                    paymentDateMillis = paymentDateMillis,
                    financialYearId = financialYearId,
                    partyId = partyId,
                    mode = mode,
                    amountPaise = amountPaise,
                    bankAccountId = bankAccountId,
                    referenceNumber = referenceNumber,
                    note = note
                )
            )
            if (allocations.isNotEmpty()) {
                allocationDao.insertAll(
                    allocations.map {
                        PaymentAllocationEntity(
                            paymentReceivedId = paymentId,
                            targetType = it.targetType,
                            targetId = it.targetId,
                            allocatedAmountPaise = it.amountPaise
                        )
                    }
                )
            }

            val cashOrBankAccountId = resolveCashOrBankAccount(mode, bankAccountId)
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = paymentDateMillis,
                narration = "Payment received: $receiptNumber",
                sourceType = JournalSourceType.PAYMENT_RECEIVED,
                sourceId = paymentId,
                lines = listOf(
                    JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashOrBankAccountId, debitPaise = amountPaise),
                    JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = partyLedgerAccountId, creditPaise = amountPaise)
                )
            )
            logCashOrBankMovement(
                mode = mode, bankAccountId = bankAccountId, amountPaise = amountPaise, dateMillis = paymentDateMillis,
                isInflow = true, sourceType = JournalSourceType.PAYMENT_RECEIVED, sourceId = paymentId
            )
            paymentId
        }
    }

    suspend fun recordPaymentMade(
        partyId: Long,
        partyLedgerAccountId: Long,
        financialYearId: Long,
        paymentNumber: String,
        paymentDateMillis: Long,
        amountPaise: Long,
        mode: PaymentMode,
        bankAccountId: Long?,
        referenceNumber: String,
        note: String,
        allocations: List<PaymentAllocationInput>
    ): Long {
        val allocatedTotal = allocations.sumOf { it.amountPaise }
        if (allocatedTotal > amountPaise) {
            throw IllegalArgumentException("Allocated amount ($allocatedTotal) exceeds payment amount ($amountPaise)")
        }

        return db.withTransaction {
            val paymentId = paymentMadeDao.insert(
                PaymentMadeEntity(
                    paymentNumber = paymentNumber,
                    paymentDateMillis = paymentDateMillis,
                    financialYearId = financialYearId,
                    partyId = partyId,
                    mode = mode,
                    amountPaise = amountPaise,
                    bankAccountId = bankAccountId,
                    referenceNumber = referenceNumber,
                    note = note
                )
            )
            if (allocations.isNotEmpty()) {
                allocationDao.insertAll(
                    allocations.map {
                        PaymentAllocationEntity(
                            paymentMadeId = paymentId,
                            targetType = it.targetType,
                            targetId = it.targetId,
                            allocatedAmountPaise = it.amountPaise
                        )
                    }
                )
            }

            val cashOrBankAccountId = resolveCashOrBankAccount(mode, bankAccountId)
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = paymentDateMillis,
                narration = "Payment made: $paymentNumber",
                sourceType = JournalSourceType.PAYMENT_MADE,
                sourceId = paymentId,
                lines = listOf(
                    JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = partyLedgerAccountId, debitPaise = amountPaise),
                    JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cashOrBankAccountId, creditPaise = amountPaise)
                )
            )
            logCashOrBankMovement(
                mode = mode, bankAccountId = bankAccountId, amountPaise = amountPaise, dateMillis = paymentDateMillis,
                isInflow = false, sourceType = JournalSourceType.PAYMENT_MADE, sourceId = paymentId
            )
            paymentId
        }
    }

    private suspend fun resolveCashOrBankAccount(mode: PaymentMode, bankAccountId: Long?): Long {
        if (mode == PaymentMode.CASH) {
            return ledgerAccountDao.findByName("Cash")!!.ledgerAccountId
        }
        if (bankAccountId != null) {
            val bank = db.bankAccountDao().getById(bankAccountId)
            if (bank != null) return bank.ledgerAccountId
        }
        // Fall back to Cash if no specific bank account was resolved, so a
        // payment is never lost/unposted for lack of bank-account setup —
        // the user can re-classify it once banking is configured.
        return ledgerAccountDao.findByName("Cash")!!.ledgerAccountId
    }

    /** Writes the Cash Book / Bank Book read-model row (see
     * CashTransactionEntity/BankTransactionEntity doc comments) alongside
     * the journal entry — this is a secondary, denormalized view for the
     * day-book screens, never the source of truth (the ledger is). */
    private suspend fun logCashOrBankMovement(
        mode: PaymentMode,
        bankAccountId: Long?,
        amountPaise: Long,
        dateMillis: Long,
        isInflow: Boolean,
        sourceType: JournalSourceType,
        sourceId: Long
    ) {
        if (mode == PaymentMode.CASH || bankAccountId == null) {
            db.cashTransactionDao().insert(
                CashTransactionEntity(
                    type = if (isInflow) CashTransactionType.RECEIPT else CashTransactionType.PAYMENT,
                    amountPaise = amountPaise,
                    transactionDateMillis = dateMillis,
                    sourceType = sourceType,
                    sourceId = sourceId
                )
            )
        } else {
            db.bankTransactionDao().insert(
                BankTransactionEntity(
                    bankAccountId = bankAccountId,
                    type = if (isInflow) BankTransactionType.DEPOSIT else BankTransactionType.WITHDRAWAL,
                    amountPaise = amountPaise,
                    transactionDateMillis = dateMillis,
                    sourceType = sourceType,
                    sourceId = sourceId
                )
            )
        }
    }
}
