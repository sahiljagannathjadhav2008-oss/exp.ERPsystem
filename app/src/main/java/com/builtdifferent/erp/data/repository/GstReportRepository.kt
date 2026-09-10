package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.dao.GstSummaryRow
import com.builtdifferent.erp.data.local.entity.GstDirection

data class GstReport(
    val outward: GstSummaryRow,
    val inward: GstSummaryRow,
    val netCgstPayablePaise: Long,
    val netSgstPayablePaise: Long,
    val netIgstPayablePaise: Long,
    val netTaxPayablePaise: Long
)

/**
 * BUSINESS RULE (documented per project convention — GST reports):
 *
 * Every figure here is read from GstTransactionEntity, which is written by
 * the sales/purchase invoice and return repositories in the SAME
 * transaction as the invoice or return itself — never computed separately
 * after the fact — so this report can never disagree with the invoices it
 * summarises, and returns already net out correctly because they were
 * logged as negative rows against the same table.
 *
 * Net tax payable = output tax (sales) minus input tax credit (purchases),
 * the standard GSTR-3B-style computation, per tax head (CGST/SGST/IGST)
 * rather than pooled together, since Indian GST law does not allow
 * offsetting CGST liability with SGST credit.
 */
class GstReportRepository(private val db: AppDatabase) {

    suspend fun getReport(financialYearId: Long, fromMillis: Long, toMillis: Long): GstReport {
        val outward = db.gstTransactionDao().getSummaryForPeriod(financialYearId, GstDirection.OUTWARD, fromMillis, toMillis)
        val inward = db.gstTransactionDao().getSummaryForPeriod(financialYearId, GstDirection.INWARD, fromMillis, toMillis)

        return GstReport(
            outward = outward,
            inward = inward,
            netCgstPayablePaise = outward.cgst - inward.cgst,
            netSgstPayablePaise = outward.sgst - inward.sgst,
            netIgstPayablePaise = outward.igst - inward.igst,
            netTaxPayablePaise = (outward.cgst + outward.sgst + outward.igst) - (inward.cgst + inward.sgst + inward.igst)
        )
    }

    suspend fun getOutwardTransactions(financialYearId: Long, fromMillis: Long, toMillis: Long) =
        db.gstTransactionDao().getForPeriod(financialYearId, GstDirection.OUTWARD, fromMillis, toMillis)

    suspend fun getInwardTransactions(financialYearId: Long, fromMillis: Long, toMillis: Long) =
        db.gstTransactionDao().getForPeriod(financialYearId, GstDirection.INWARD, fromMillis, toMillis)
}
