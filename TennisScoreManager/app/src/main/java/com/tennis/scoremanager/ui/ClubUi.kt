// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import com.tennis.scoremanager.data.FeeKind
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.PayMethod

/** I nomi delle cose del registro del circolo, nella lingua dell'app. */
fun payMethodName(s: Strings, m: PayMethod): String = when (m) {
    PayMethod.CASH -> s.payCash
    PayMethod.TRANSFER -> s.payTransfer
    PayMethod.POS -> s.payPos
    PayMethod.SATISPAY -> s.paySatispay
    PayMethod.OTHER -> s.payOther
}

fun feeKindName(s: Strings, k: FeeKind): String = when (k) {
    FeeKind.MEMBERSHIP -> s.feeKindMembership
    FeeKind.FITP -> s.feeKindFitp
    FeeKind.COURSE -> s.feeKindCourse
    FeeKind.TOURNAMENT -> s.feeKindTournament
    FeeKind.OTHER -> s.feeKindOther
}

fun chargeStatusName(s: Strings, st: Fees.Status, left: String): String = when (st) {
    Fees.Status.PAID -> s.chargePaid
    Fees.Status.PARTIAL -> s.chargePartial(left)
    Fees.Status.UNPAID -> s.chargeUnpaid
    Fees.Status.LATE -> s.chargeLate(left)
    Fees.Status.CANCELLED -> s.chargeCancelled
}

fun chargeStatusColor(st: Fees.Status) = when (st) {
    Fees.Status.PAID -> TsmColors.Ok
    Fees.Status.PARTIAL, Fees.Status.UNPAID -> TsmColors.Orange
    Fees.Status.LATE -> TsmColors.Danger
    Fees.Status.CANCELLED -> TsmColors.TextDim
}
