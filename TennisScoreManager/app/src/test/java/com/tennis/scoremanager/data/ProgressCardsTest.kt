// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCardsTest {
    @Test fun cardCarriesProgressButNotTheCoachDiary() {
        val now = 1_000_000_000L
        val book = CoachBook(
            diary = listOf(DiaryEntry("d", "s1", 1, "nota privata dell'allenatore")),
            evaluations = listOf(Evaluation("e", "s1", 5, mapOf("serve" to 3)), Evaluation("x", "s2", 5, mapOf("serve" to 1))),
            goals = listOf(StudentGoal("g", "s1", TrainedStroke.FOREHAND, 70)),
            rankings = listOf(RankingEntry("r", "s1", 3, "4.3")),
            lessons = listOf(CoachLesson("l", now + 86_400_000L, students = listOf("s1"))),
            packs = listOf(LessonPack("p", "s1", 10)),
        )
        val card = ProgressCards.build(book, emptyList(), "s1", "Anna", "Marco", "TC", now)
        assertEquals(1, card.evaluations.size)
        assertEquals(70, card.goals.single().target)
        assertNull(card.goals.single().now)
        assertEquals("4.3", card.rankings.single().ranking)
        assertEquals(listOf(now + 86_400_000L), card.nextLessons)
        assertEquals(10, card.lessonsLeft)
        val text = ProgressCards.encode(card)
        assertFalse("nota privata" in text)
        assertEquals(card, ProgressCards.decode(text))
        assertNull(MatchCards.decode(text))
        assertNull(ProgressCards.decode(MatchCards.encodeResult(MatchResult(code = "c", ref = "r", match = "m"))))
    }
}
