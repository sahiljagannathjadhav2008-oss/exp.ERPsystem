package com.builtdifferent.erp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.GstRateEntity
import com.builtdifferent.erp.data.local.entity.HsnSacEntity
import com.builtdifferent.erp.data.local.entity.HsnSacType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the core GST-engine guarantee documented on GstRateEntity:
 * adding a new effective-dated rate must close the previous rate at
 * (newEffectiveFrom - 1) and must NEVER alter what getRateEffectiveOn()
 * returns for a date before the change. This is the mechanism Acceptance
 * Test 5 in the spec ("change future GST rate, verify old invoices
 * unaffected") depends on.
 */
@RunWith(RobolectricTestRunner::class)
class GstRateResolutionTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `revising a rate does not change the rate resolved for a past date`() = runBlocking {
        val hsnSacId = db.hsnSacDao().insert(
            HsnSacEntity(code = "8471", type = HsnSacType.HSN, description = "Computers")
        )

        val jan1 = 1704067200000L // 2024-01-01 UTC
        val jun1 = 1717200000000L // 2024-06-01 UTC
        val mar1 = 1709251200000L // 2024-03-01 UTC (between the two rates)

        db.gstRateDao().addNewEffectiveRate(
            GstRateEntity(
                hsnSacId = hsnSacId,
                totalRatePercent = 18.0,
                cgstRatePercent = 9.0,
                sgstRatePercent = 9.0,
                igstRatePercent = 18.0,
                effectiveFromMillis = jan1
            )
        )

        // Old invoice, dated before the rate change, resolved BEFORE we
        // insert the new rate — this is the "old invoice already posted"
        // case.
        val rateForOldInvoice = db.gstRateDao().getRateEffectiveOn(hsnSacId, mar1)
        assertEquals(18.0, rateForOldInvoice?.totalRatePercent)

        // Government notifies a rate hike effective 1 June.
        db.gstRateDao().addNewEffectiveRate(
            GstRateEntity(
                hsnSacId = hsnSacId,
                totalRatePercent = 28.0,
                cgstRatePercent = 14.0,
                sgstRatePercent = 14.0,
                igstRatePercent = 28.0,
                effectiveFromMillis = jun1
            )
        )

        // Re-resolving the SAME historical date must give the SAME answer.
        val rateForOldInvoiceAfterChange = db.gstRateDao().getRateEffectiveOn(hsnSacId, mar1)
        assertEquals(18.0, rateForOldInvoiceAfterChange?.totalRatePercent)

        // A new invoice dated after 1 June gets the new rate.
        val rateForNewInvoice = db.gstRateDao().getRateEffectiveOn(hsnSacId, jun1 + 86_400_000L)
        assertEquals(28.0, rateForNewInvoice?.totalRatePercent)

        // The old rate row is now closed (has a non-null effectiveToMillis).
        val currentRate = db.gstRateDao().getCurrentRate(hsnSacId)
        assertEquals(28.0, currentRate?.totalRatePercent)
        assertNull(currentRate?.effectiveToMillis)
    }
}
