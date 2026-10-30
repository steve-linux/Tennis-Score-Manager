// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentsTest {
    private fun sess(id: String, at: Long, stroke: TrainedStroke, student: String?, ins: Int, outs: Int) = ServeSession(
        id, at, stroke = stroke, student = student,
        serves = List(ins) { ServeAttempt(at, true, ServeSide.DEUCE, ServeKind.FIRST) } + List(outs) { ServeAttempt(at, false, ServeSide.DEUCE, ServeKind.FIRST) },
    )

    @Test
    fun sessionsAndStrokeLinesPerStudent() {
        val all = listOf(
            sess("a", 1, TrainedStroke.FOREHAND, "rossi", 2, 2),
            sess("b", 2, TrainedStroke.FOREHAND, "rossi", 3, 1),
            sess("c", 3, TrainedStroke.FOREHAND, "verdi", 1, 0),
            sess("d", 4, TrainedStroke.SERVE, null, 1, 1),
            sess("e", 5, TrainedStroke.SERVE, "rossi", 0, 0),
        )
        val rossi = Students.sessionsOf(all, "rossi")
        assertEquals(listOf("a", "b", "e"), rossi.map { it.id })
        assertEquals(listOf("d"), Students.sessionsOf(all, null).map { it.id })
        // Le sessioni vuote non contano; prima e ultima in ordine di tempo.
        val lines = Students.strokeLines(rossi)
        assertEquals(listOf(TrainedStroke.FOREHAND), lines.map { it.stroke })
        assertEquals(Students.StrokeLine(TrainedStroke.FOREHAND, 2, 8, 50, 75, 2), lines[0])
    }

    @Test
    fun folderNamesAreSafe() {
        assertEquals("Mario Rossi", Students.folderName("  Mario/Rossi "))
        assertEquals("?", Students.folderName("::"))
    }
}
