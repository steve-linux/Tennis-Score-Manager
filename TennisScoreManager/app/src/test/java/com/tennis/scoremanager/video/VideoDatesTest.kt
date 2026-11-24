// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class VideoDatesTest {
    private val now = 1_795_500_000_000L // 24 nov 2026

    private fun day(ms: Long?, tz: TimeZone = TimeZone.getDefault()) = ms?.let { Calendar.getInstance(tz).apply { timeInMillis = it }.let { c -> Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) } }

    @Test
    fun dateWrittenByTheCamera() {
        assertEquals(Triple(2026, 10, 24), day(VideoDates.fromMeta("20261024T211657.000Z", now), TimeZone.getTimeZone("UTC")))
        // Data vuota delle fotocamere (1904) o nel futuro: nessuna data.
        assertNull(VideoDates.fromMeta("19040101T000000.000Z", now))
        assertNull(VideoDates.fromMeta("20301024T211657.000Z", now))
        assertNull(VideoDates.fromMeta(null, now))
    }

    @Test
    fun dateInTheFileName() {
        assertEquals(Triple(2026, 10, 24), day(VideoDates.fromName("2026-10-24 23-16-57.mp4", now)))
        assertEquals(Triple(2026, 10, 24), day(VideoDates.fromName("VID_20261024_231657.mp4", now)))
        assertEquals(Triple(2025, 6, 1), day(VideoDates.fromName("PXL_20250601_101500123.mp4", now)))
        // Il video scaricato da YouTube: numeri che non sono una data.
        assertNull(VideoDates.fromName("Amateur Tennis - (Full) Match  - LK 16.0⧸NTRP 4.0 vs LK 15.1⧸NTRP 4.0 [4apx6gd5Uxs].mp4", now))
    }
}
