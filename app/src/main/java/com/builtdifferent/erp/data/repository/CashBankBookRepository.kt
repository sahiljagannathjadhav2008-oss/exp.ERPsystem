package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.BankTransactionEntity
import com.builtdifferent.erp.data.local.entity.BankTransactionType
import com.builtdifferent.erp.data.local.entity.CashTransactionEntity
import com.builtdifferent.erp.data.local.entity.CashTransactionType
import kotlinx.coroutines.flow.first

data class CashBookRow(val transaction: CashTransactionEntity, val runningBalancePaise: Long)
data class BankBookRow(val transaction: BankTransactionEntity, val runningBalancePaise: Long)

/**
 * Cash Book / Bank Book compute a running balance by walking the
 * CashTransactionEntity/BankTransactionEntity rows in date order and
 * accumulating — the running balance is never stored, only derived at read
 * time, so it can't fall out of sync if a back-dated transaction is ever
 * inserted later.
 */
class CashBookRepository(private val db: AppDatabase) {
    suspend fun getRows(fromMillis: Long, toMillis: Long): List<CashBookRow> {
        val transactions = db.cashTransactionDao().observeInRange(fromMillis, toMillis).first()
        var balance = 0L
        return transactions.sortedBy { it.transactionDateMillis }.map { tx ->
            balance += if (tx.type == CashTransactionType.RECEIPT) tx.amountPaise else -tx.amountPaise
            CashBookRow(tx, balance)
        }
    }
}

class BankBookRepository(private val db: AppDatabase) {
    suspend fun getRows(bankAccountId: Long): List<BankBookRow> {
        val transactions = db.bankTransactionDao().observeForAccount(bankAccountId).first()
        var balance = 0L
        return transactions.sortedBy { it.transactionDateMillis }.map { tx ->
            balance += if (tx.type == BankTransactionType.DEPOSIT) tx.amountPaise else -tx.amountPaise
            BankBookRow(tx, balance)
        }
    }

    suspend fun markReconciled(bankTransactionId: Long, reconciledAtMillis: Long) {
        db.bankTransactionDao().markReconciled(bankTransactionId, reconciledAtMillis)
    }
}
