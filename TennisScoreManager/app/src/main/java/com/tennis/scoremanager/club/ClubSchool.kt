// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import com.tennis.scoremanager.Role
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * La scuola tennis del circolo: corsi, allievi, quote dei corsi, lezioni annullate o spostate e i loro avvisi.
 * I periodi dei corsi finiscono nel planning dei campi ([courtPlan]). Il resto che serve è in [ClubHost].
 */
class ClubSchool(
    host: ClubHost,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {

    val courses = MutableStateFlow(storage.loadCourses())
    val openCourseId = MutableStateFlow<String?>(null)

    fun openSchool() {
        refreshPlayers()
        nav.push(Screen.SCHOOL)
    }

    fun openCourse(id: String) {
        openCourseId.value = id
        nav.push(Screen.COURSE)
    }

    fun course(id: String?): com.tennis.scoremanager.data.Course? = id?.let { i -> courses.value.firstOrNull { it.id == i } }

    /** Salva il corso e rifà i suoi periodi non prenotabili nel planning (uno per orario). */
    fun saveCourse(c: com.tennis.scoremanager.data.Course) {
        val list = courses.value.filter { it.id != c.id } + c
        courses.value = list
        scope.launch(io) { storage.saveCourses(list) }
        val p = courtPlan.value
        val np = p.copy(blocks = p.blocks.filter { it.course != c.id } + com.tennis.scoremanager.data.Courses.blocks(c))
        if (np != p) {
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    /** Toglie il corso e i suoi periodi non prenotabili; le quote già addebitate restano nel registro. */
    fun deleteCourse(id: String) {
        val list = courses.value.filter { it.id != id }
        courses.value = list
        scope.launch(io) { storage.saveCourses(list) }
        val p = courtPlan.value.let { it.copy(blocks = it.blocks.filter { b -> b.course != id }) }
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /** I corsi di un giocatore (come allievo). */
    fun coursesOf(p: com.tennis.scoremanager.data.PlayerProfile): List<com.tennis.scoremanager.data.Course> {
        val keys = setOf(p.id) + p.aliases
        return courses.value.filter { c -> c.students.any { it in keys } }
    }

    /** "SAT Under 10 · novembre 2026" o "SAT Under 10 · stagione": la voce della quota nel registro. */
    fun courseFeeLabel(c: com.tennis.scoremanager.data.Course, month: Pair<Int, Int>?): String {
        if (month == null) return "${c.name} · ${strings.scSeason}"
        val cal = java.util.Calendar.getInstance().apply { set(month.first, month.second, 1) }
        return "${c.name} · " + java.text.SimpleDateFormat("LLLL yyyy", options.value.lang.locale).format(cal.time)
    }

    /** Addebita la quota del mese (o della stagione) a chi non l'ha ancora; restituisce quanti addebiti nuovi. */
    fun chargeCourse(c: com.tennis.scoremanager.data.Course, month: Pair<Int, Int>?): Int {
        val due = month?.let { (y, m) -> java.util.Calendar.getInstance().apply { set(y, m, 10, 12, 0, 0) }.timeInMillis } ?: c.from
        val (book, n) = com.tennis.scoremanager.data.Courses.charge(clubBook.value, c, players.value, courseFeeLabel(c, month), System.currentTimeMillis(), due)
        if (n > 0) saveClubBook(book)
        return n
    }

    /** Avvisa gli allievi del corso: lezione annullata ([lesson]) o un messaggio libero. Ai minorenni scrive al genitore. */
    /** Le lezioni future (non annullate) dei corsi che cadono nella chiusura [b] del circolo; vuoto se [b] non chiude il circolo. */
    fun courseLessonsClosedBy(b: com.tennis.scoremanager.data.CourtBlock): List<Pair<com.tennis.scoremanager.data.Course, com.tennis.scoremanager.data.Lesson>> {
        val only = courtPlan.value.copy(blocks = listOf(b))
        val now = System.currentTimeMillis()
        return courses.value.flatMap { co ->
            com.tennis.scoremanager.data.Courses.lessons(co).filter { it.start >= now && it.key !in co.cancelled && com.tennis.scoremanager.data.CourtPlans.closedOn(only, it.start) != null }.map { co to it }
        }.sortedBy { it.second.start }
    }

    /** Annulla quelle lezioni (si rimettono dall'appello o si spostano). */
    fun cancelCourseLessons(list: List<Pair<com.tennis.scoremanager.data.Course, com.tennis.scoremanager.data.Lesson>>) {
        for ((id, ls) in list.groupBy { it.first.id }) {
            val co = course(id) ?: continue
            saveCourse(co.copy(cancelled = co.cancelled + ls.map { it.second.key }))
        }
    }

    /** Agli iscritti: la lezione [key] del corso [c] (già spostata) da quando a quando. */
    fun noticeCourseMoved(c: com.tennis.scoremanager.data.Course, key: String) {
        val l = com.tennis.scoremanager.data.Courses.lessons(c).firstOrNull { it.key == key } ?: return
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        fun at(t: Long) = com.tennis.scoremanager.data.Notices.day(t, lang) + ", " + com.tennis.scoremanager.data.Reports.time(t, lang)
        val text = (listOfNotNull(s.scMovedNotice(c.name), l.movedFrom?.let { at(it) + " →" }, at(l.start) + listOf(l.court).filter { it.isNotBlank() }.joinToString { " · $it" })
            + listOfNotNull(clubSign.ifBlank { null }.takeIf { activeRole.value == Role.CLUB })).joinToString("\n")
        val rec = c.students.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.scMovedTitle, c.name, text, rec))
    }

    fun noticeCourse(c: com.tennis.scoremanager.data.Course, lesson: com.tennis.scoremanager.data.Lesson?) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val people = c.students.mapNotNull { playerById(it) }
        val text = if (lesson != null) (listOf(s.scCancelledNotice(c.name), com.tennis.scoremanager.data.Notices.day(lesson.start, lang) + ", " + com.tennis.scoremanager.data.Reports.time(lesson.start, lang) + listOf(lesson.court).filter { it.isNotBlank() }.joinToString { " · $it" })
            + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n") else "${c.name}\n\n"
        val rec = people.map { com.tennis.scoremanager.data.Notices.recipient(it, if (lesson != null) text else "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(if (lesson != null) s.scCancelledTitle else s.ntKindFree, c.name, text, rec, editable = lesson == null))
    }
}
