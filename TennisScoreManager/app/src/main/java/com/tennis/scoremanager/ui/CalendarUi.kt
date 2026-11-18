// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast

/**
 * Un evento nel calendario del telefono (lo crea l'app del calendario: niente permessi). [allDay] per i tornei;
 * [weeklyUntil] lo ripete ogni settimana fino a quel giorno (le lezioni di un corso).
 */
fun calendarIntent(title: String, start: Long, minutes: Int, place: String = "", text: String = "", allDay: Boolean = false, weeklyUntil: Long? = null): Intent =
    Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + minutes * 60_000L)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        putExtra(CalendarContract.Events.TITLE, title)
        if (place.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, place)
        if (text.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, text)
        weeklyUntil?.let {
            val until = java.text.SimpleDateFormat("yyyyMMdd'T'235959'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(it))
            putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY;UNTIL=$until")
        }
    }

/** Apre il calendario; senza un'app del calendario lo dice. */
fun addToCalendar(context: Context, intent: Intent, s: Strings) {
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, s.calNoApp, Toast.LENGTH_LONG).show() }
}
