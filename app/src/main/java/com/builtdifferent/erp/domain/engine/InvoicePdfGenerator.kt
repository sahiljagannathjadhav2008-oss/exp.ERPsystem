package com.builtdifferent.erp.domain.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.data.local.entity.SalesInvoiceItemEntity
import com.builtdifferent.erp.util.DateUtils
import com.builtdifferent.erp.util.Money
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * BUSINESS RULE (documented per project convention — invoice layout):
 *
 * Renders a traditional Indian tax-invoice layout — the boxed
 * header/item-table/totals/signature structure every GST billing book or
 * Tally-style print shares — NOT a modern card-style e-commerce receipt.
 * This deliberately reproduces only the generic structure GST rules
 * dictate (shared by every compliant invoice), not Tally's specific
 * proprietary box proportions or branding.
 *
 * Built on android.graphics.pdf.PdfDocument (part of the Android SDK) so
 * the app carries zero third-party PDF dependency.
 *
 * All amounts are read from the already-posted SalesInvoiceEntity /
 * SalesInvoiceItemEntity rows — this generator never recalculates tax, it
 * only lays out numbers TaxEngine/SalesInvoiceRepository already computed
 * and stored at posting time, so the printed PDF can never drift from the
 * historical snapshot.
 */
object InvoicePdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f

    fun generate(invoice: SalesInvoiceEntity, items: List<SalesInvoiceItemEntity>, outputDir: File): File {
        val company = JSONObject(invoice.companySnapshotJson)
        val party = JSONObject(invoice.partySnapshotJson)

        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val headingPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 8.5f }
        val smallPaint = Paint().apply { textSize = 7.5f }
        val boxPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        var y = MARGIN
        val outerRect = RectF(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN)

        y += 16f
        canvas.drawText("TAX INVOICE", PAGE_WIDTH / 2f, y, titlePaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        y += 14f
        canvas.drawText(company.optString("legalName"), MARGIN + 6f, y, headingPaint)
        y += 11f
        listOf(
            company.optString("addressLine1"),
            company.optString("addressLine2"),
            "${company.optString("city")}, ${company.optString("state")} - ${company.optString("pinCode")}",
            "GSTIN: ${company.optString("gstin")}   PAN: ${company.optString("pan")}",
            "Phone: ${company.optString("phone")}   Email: ${company.optString("email")}"
        ).forEach { line ->
            if (line.isNotBlank()) { canvas.drawText(line, MARGIN + 6f, y, smallPaint); y += 10f }
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        val midX = PAGE_WIDTH / 2f
        val metaTop = y
        y += 12f
        canvas.drawText("Invoice No: ${invoice.invoiceNumber}", MARGIN + 6f, y, bodyPaint)
        canvas.drawText("Invoice Date: ${DateUtils.formatDate(invoice.invoiceDateMillis)}", midX + 6f, y, bodyPaint)
        y += 11f
        canvas.drawText("Place of Supply: ${invoice.placeOfSupplyStateCode}", MARGIN + 6f, y, bodyPaint)
        canvas.drawText("Type: ${invoice.gstCategory}", midX + 6f, y, bodyPaint)
        y += 8f
        canvas.drawLine(midX, metaTop - 2f, midX, y, boxPaint)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        y += 12f
        canvas.drawText("Bill To:", MARGIN + 6f, y, headingPaint)
        y += 11f
        canvas.drawText(party.optString("partyName"), MARGIN + 6f, y, bodyPaint)
        y += 10f
        listOf(
            party.optString("billingAddressLine1"),
            party.optString("billingAddressLine2"),
            "${party.optString("billingCity")}, ${party.optString("billingState")} - ${party.optString("billingPinCode")}",
            "GSTIN: ${party.optString("gstin").ifBlank { "Unregistered" }}"
        ).forEach { line ->
            if (line.isNotBlank()) { canvas.drawText(line, MARGIN + 6f, y, smallPaint); y += 10f }
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        y += 4f
        val col = TableColumns(MARGIN, PAGE_WIDTH - MARGIN)
        drawTableHeader(canvas, col, y, headingPaint, boxPaint)
        y += 14f

        items.forEach { item ->
            val srNo = items.indexOf(item) + 1
            if (y <= PAGE_HEIGHT - 220f) {
                drawItemRow(canvas, col, y, srNo, item, smallPaint)
                y += 12f
            }
        }
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        y += 12f
        val labelX = PAGE_WIDTH - MARGIN - 160f
        val valueX = PAGE_WIDTH - MARGIN - 6f
        val totalsValuePaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }

        fun totalLine(label: String, amount: Long) {
            canvas.drawText(label, labelX, y, bodyPaint)
            canvas.drawText(Money(amount).formatIndian(), valueX, y, totalsValuePaint)
            y += 11f
        }
        totalLine("Taxable Value", invoice.totalTaxableValuePaise)
        if (invoice.totalCgstPaise > 0) totalLine("CGST", invoice.totalCgstPaise)
        if (invoice.totalSgstPaise > 0) totalLine("SGST", invoice.totalSgstPaise)
        if (invoice.totalIgstPaise > 0) totalLine("IGST", invoice.totalIgstPaise)
        if (invoice.roundOffPaise != 0L) totalLine("Round Off", invoice.roundOffPaise)
        canvas.drawLine(labelX, y, valueX, y, boxPaint)
        y += 12f
        val grandPaint = Paint(headingPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Grand Total", labelX, y, headingPaint)
        canvas.drawText(Money(invoice.grandTotalPaise).formatIndian(), valueX, y, grandPaint)
        y += 14f

        canvas.drawText("Amount in Words: ${invoice.amountInWords}", MARGIN + 6f, y, smallPaint)
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, boxPaint)

        y += 12f
        canvas.drawText("Bank Details:", MARGIN + 6f, y, headingPaint)
        y += 10f
        listOf(
            "A/c Name: ${company.optString("bankAccountName")}",
            "A/c No: ${company.optString("bankAccountNumber")}   IFSC: ${company.optString("bankIfsc")}",
            "Bank: ${company.optString("bankName")}, ${company.optString("bankBranch")}"
        ).forEach { line -> canvas.drawText(line, MARGIN + 6f, y, smallPaint); y += 10f }

        val signatureY = PAGE_HEIGHT - MARGIN - 30f
        canvas.drawText(
            "For ${company.optString("legalName")}",
            PAGE_WIDTH - MARGIN - 6f, signatureY, Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        )
        canvas.drawText(
            "Authorised Signatory",
            PAGE_WIDTH - MARGIN - 6f, PAGE_HEIGHT - MARGIN - 6f,
            Paint(smallPaint).apply { textAlign = Paint.Align.RIGHT }
        )

        canvas.drawRect(outerRect, boxPaint)
        document.finishPage(page)

        outputDir.mkdirs()
        val file = File(outputDir, "${invoice.invoiceNumber.replace("/", "-")}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private data class TableColumns(val left: Float, val right: Float) {
        val srNo = left + 4f
        val name = left + 24f
        val hsn = left + 210f
        val qty = left + 270f
        val rate = left + 320f
        val disc = left + 380f
        val taxable = left + 420f
        val tax = left + 470f
    }

    private fun drawTableHeader(canvas: Canvas, col: TableColumns, y: Float, paint: Paint, boxPaint: Paint) {
        canvas.drawText("#", col.srNo, y, paint)
        canvas.drawText("Item", col.name, y, paint)
        canvas.drawText("HSN", col.hsn, y, paint)
        canvas.drawText("Qty", col.qty, y, paint)
        canvas.drawText("Rate", col.rate, y, paint)
        canvas.drawText("Disc", col.disc, y, paint)
        canvas.drawText("Taxable", col.taxable, y, paint)
        canvas.drawText("Tax", col.tax, y, paint)
        canvas.drawLine(col.left, y + 3f, col.right, y + 3f, boxPaint)
    }

    private fun drawItemRow(canvas: Canvas, col: TableColumns, y: Float, srNo: Int, item: SalesInvoiceItemEntity, paint: Paint) {
        canvas.drawText(srNo.toString(), col.srNo, y, paint)
        canvas.drawText(item.productNameSnapshot.take(28), col.name, y, paint)
        canvas.drawText(item.hsnSacCodeSnapshot, col.hsn, y, paint)
        canvas.drawText(item.quantity.toString(), col.qty, y, paint)
        canvas.drawText(Money(item.unitPricePaise).toRupees().toPlainString(), col.rate, y, paint)
        canvas.drawText(if (item.discountPercent > 0) "${item.discountPercent}%" else "-", col.disc, y, paint)
        canvas.drawText(Money(item.taxableValuePaise).toRupees().toPlainString(), col.taxable, y, paint)
        val taxTotal = item.cgstAmountPaise + item.sgstAmountPaise + item.igstAmountPaise + item.cessAmountPaise
        canvas.drawText(Money(taxTotal).toRupees().toPlainString(), col.tax, y, paint)
    }
}
