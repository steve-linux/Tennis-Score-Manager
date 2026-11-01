// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import com.tennis.scoremanager.data.CTMatch
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.ClubTournaments

/** "Finale", "Semifinale", "Quarti di finale", "Ottavi di finale", "1° turno", "Girone A". */
fun ctRoundName(s: Strings, t: ClubTournament, m: CTMatch): String {
    if (m.round == 0) return s.ctGroup(groupLetter(m.group ?: 0))
    return when (ClubTournaments.playersInRound(t, m.round)) {
        2 -> s.ctFinal
        4 -> s.ctSemi
        8 -> s.ctQuarter
        16 -> s.ctLast16
        32 -> s.ctLast32
        else -> s.ctRoundN(m.round)
    }
}

fun groupLetter(g: Int): String = ('A' + g).toString()

fun ctProblemText(s: Strings, p: ClubTournaments.Problem): String = when (p) {
    ClubTournaments.Problem.NOT_MEMBER -> s.ctNotMember
    ClubTournaments.Problem.NO_MEDICAL -> s.ctNoMedical
    ClubTournaments.Problem.MEDICAL_NOT_AGONISTIC -> s.ctMedicalNotAgonistic
    ClubTournaments.Problem.NO_FITP_CARD -> s.ctNoFitpCard
    ClubTournaments.Problem.NOT_AGONIST_CARD -> s.ctNotAgonistCard
    ClubTournaments.Problem.RANK_TOO_HIGH -> s.ctRankTooHigh
    ClubTournaments.Problem.UNPAID -> s.ctUnpaid
}
