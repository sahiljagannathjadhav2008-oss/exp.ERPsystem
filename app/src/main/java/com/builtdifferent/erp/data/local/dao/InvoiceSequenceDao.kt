package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.InvoiceSequenceEntity

@Dao
interface InvoiceSequenceDao {

    @Insert
    suspend fun insert(sequence: InvoiceSequenceEntity): Long

    @Query("SELECT * FROM invoice_sequences WHERE documentType = :type AND financialYearId = :financialYearId LIMIT 1")
    suspend fun find(type: DocumentType, financialYearId: Long): InvoiceSequenceEntity?

    @Query(
        """UPDATE invoice_sequences SET nextNumber = nextNumber + 1
           WHERE documentType = :type AND financialYearId = :financialYearId"""
    )
    suspend fun increment(type: DocumentType, financialYearId: Long)

    /**
     * Returns the next formatted document number and atomically increments
     * the counter, creating the sequence row (starting at 1) the first time
     * a document type is issued for a financial year. Because this runs as
     * a single Room @Transaction, two concurrent invoice creations can
     * never be handed the same number — the second caller's read of
     * nextNumber always happens after the first caller's increment commits.
     */
    @Transaction
    suspend fun nextNumber(type: DocumentType, financialYearId: Long, defaultPrefix: String): String {
        var sequence = find(type, financialYearId)
        if (sequence == null) {
            insert(InvoiceSequenceEntity(documentType = type, financialYearId = financialYearId, prefix = defaultPrefix))
            sequence = find(type, financialYearId)!!
        }
        increment(type, financialYearId)
        return sequence.prefix + sequence.nextNumber.toString().padStart(sequence.padding, '0')
    }
}
