// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
// L'Allenatore da PC: allievi con la loro scheda, agenda delle lezioni, corsi col registro, riepilogo del mese e
// ricevute. Usa gli strumenti della Segreteria da PC (app.js): h, put, api, toast, eur, dateText, hhmm, openPdf.
'use strict';

let coachView = 'students';
let coachStudent = null;
let coachCourseId = null;
let coachMonth = null;
/** Agenda: 'month', 'week' o 'list'; e il giorno a cui si guarda. */
let agendaView = 'month';
let agendaDate = null;

const ASPECTS = ['serve', 'forehand', 'backhand', 'volley', 'movement', 'tactics', 'mental'];
const aspectName = (k) => ({ serve: T.asServe, forehand: T.asForehand, backhand: T.asBackhand, volley: T.asVolley, movement: T.asMovement, tactics: T.asTactics, mental: T.asMental })[k] || k;
const testName = (k) => ({ sprint: T.ftSprint, spider: T.ftSpider, jump: T.ftJump, yoyo: T.ftYoyo })[k] || k;
const testValue = (k, v) => (k === 'sprint' || k === 'spider') ? v.toFixed(2) : (Number.isInteger(v) ? String(v) : v.toFixed(1));
const coachHours = (min) => (min % 60 === 0 ? `${min / 60} h` : `${Math.floor(min / 60)} h ${min % 60}′`);

async function coachStart() {
  document.title = T.ccDeskTitle + (info.coach ? ' · ' + info.coach : '');
  await coachRender();
}

const COACH_VIEWS = () => [['students', T.studentsTitle], ['agenda', T.coAgenda], ['courses', T.ccTitle], ['month', T.cmMonth]];

async function coachRender() {
  put(root, h('div.shell',
    h('nav.side', { 'aria-label': T.ccDeskTitle },
      h('div.brand', info.coach || T.ccDeskTitle, h('small', T.ccDeskTitle)),
      COACH_VIEWS().map(([v, l]) => h('button' + (v === coachView ? '.on' : ''), { 'aria-current': v === coachView ? 'page' : null, onclick: () => { coachView = v; coachStudent = null; coachCourseId = null; coachRender(); } }, l)),
      h('div.spacer'),
      h('div.note', T.ccDeskNote),
      h('button', { onclick: logout }, T.logout)),
    h('main')));
  try { await ({ students: showStudents, agenda: showAgenda, courses: showCourses, month: showMonth })[coachView](); }
  catch (e) { if (e instanceof ApiError && token) put(main(), h('div.empty', errText(e))); else if (!(e instanceof ApiError)) throw e; }
}

/** Scarica un file del telefono (Excel) col suo nome. */
async function downloadFile(path, name) {
  try {
    const blob = await api('GET', path, undefined, true);
    const url = URL.createObjectURL(blob);
    const a = h('a', { href: url, download: name }); document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) { toast(errText(e)); }
}

const printBtn = () => h('button', { onclick: () => window.print() }, T.ccDeskPrint);
const balanceText = (b) => (b.bought || b.used) ? `${b.bought} / ${b.used} / ${b.left}` : '';

// --- Allievi ---

async function showStudents() {
  if (coachStudent) return showStudent(coachStudent);
  const list = await api('GET', '/coach/students');
  const q = h('input', { type: 'search', placeholder: T.coSearch });
  const body = h('div');
  const draw = () => {
    const t = q.value.trim().toLowerCase();
    const rows = list.filter((s) => !t || s.name.toLowerCase().includes(t));
    put(body, rows.length ? h('table',
      h('thead', h('tr', h('th', T.studentsTitle), h('th', T.rkTitle), h('th', T.evTitle), h('th', T.coPacks), h('th', T.coUpcoming), h('th', ''))),
      h('tbody', rows.map((s) => h('tr', { onclick: () => { coachStudent = s.id; showStudents(); }, style: 'cursor:pointer' },
        h('td', h('b', s.name), h('div.dim', s.lastSeen ? T.studentLast(dateText(s.lastSeen)) : T.coNever)),
        h('td', s.ranking || ''),
        h('td', s.trend == null ? '' : (s.trend >= 0 ? '▲ ' + T.evTrendUp : '▼ ' + T.evTrendDown)),
        h('td', balanceText(s.balance), s.balance.dueCents ? h('div', { style: 'color:var(--warn)' }, T.coDue(eur(s.balance.dueCents))) : null),
        h('td', s.next ? dateText(s.next) + ' ' + hhmm(s.nextMin) : ''),
        h('td', s.guardian ? T.coGuardian + ': ' + s.guardian : s.phone))))) : h('div.empty', T.coNever));
  };
  q.addEventListener('input', draw);
  put(main(), h('div.head', h('h1', T.studentsTitle), printBtn()), h('div.panel', q, body));
  draw();
}

async function showStudent(id) {
  const d = await api('GET', '/coach/students/' + encodeURIComponent(id));
  const s = d.student;
  const back = h('button', { onclick: () => { coachStudent = null; showStudents(); }, 'aria-label': T.studentsTitle }, '←');
  const card = (title, ...kids) => h('div.card', h('h2', title), ...kids);
  const diaryIn = h('textarea', { rows: 3, style: 'width:100%' });
  const noteIn = h('textarea', { rows: 4, style: 'width:100%' }); noteIn.value = d.note || '';
  const last = d.evaluations[d.evaluations.length - 1];
  const prev = d.evaluations[d.evaluations.length - 2];
  put(main(),
    h('div.head', back, h('h1', s.name),
      h('button', { onclick: () => openPdf('/coach/students/' + encodeURIComponent(id) + '/report.pdf?days=30', T.reportTitle + ' ' + s.name) }, T.ccDeskReport30),
      h('button', { onclick: () => openPdf('/coach/students/' + encodeURIComponent(id) + '/report.pdf', T.reportTitle + ' ' + s.name) }, T.ccDeskReportAll),
      printBtn()),
    h('div.printonly.phead', h('b', s.name), ' · ', dateText(info.today)),
    h('p.dim', [s.phone, s.email, s.guardian ? T.coGuardian + ': ' + s.guardian + ' ' + s.guardianPhone : '', d.consent ? T.coConsentGiven(dateText(d.consent)) : T.coConsentMissing].filter(Boolean).join(' · ')),
    h('div.cards',
      card(T.coDiary,
        diaryIn,
        h('div.actions', h('button.primary', { onclick: async () => {
          if (!diaryIn.value.trim()) return;
          try { await api('POST', '/coach/students/' + encodeURIComponent(id) + '/diary', { text: diaryIn.value }); toast(T.saved); showStudent(id); } catch (e) { toast(errText(e)); }
        } }, T.coDiaryAdd)),
        d.diary.length ? d.diary.map((x) => h('p', h('b', dateText(x.date) + ' '), x.text)) : h('p.dim', T.coDiaryNone)),
      card(T.studentNotes, noteIn, h('div.actions', h('button.primary', { onclick: async () => {
        try { await api('PUT', '/coach/students/' + encodeURIComponent(id) + '/note', { text: noteIn.value }); toast(T.saved); } catch (e) { toast(errText(e)); }
      } }, T.save))),
      card(T.evTitle, last ? [h('p.dim', T.evLast(dateText(last.date))), h('table', h('tbody', ASPECTS.filter((k) => last.scores[k]).map((k) => {
        const v = last.scores[k]; const p = prev && prev.scores[k]; const diff = p ? v - p : 0;
        return h('tr', h('td', aspectName(k)), h('td', '●'.repeat(v) + '○'.repeat(5 - v)), h('td', diff ? (diff > 0 ? '+' + diff : String(diff)) : ''));
      }))), last.note ? h('p', last.note) : null] : h('p.dim', T.evNone)),
      card(T.ftTitle, d.tests.length ? [h('table', h('tbody', d.tests.map((x) => h('tr', h('td', testName(x.key)), h('td', h('b', testValue(x.key, x.value))),
        h('td.dim', x.previous != null && x.previous !== x.value ? '(' + testValue(x.key, x.previous) + ')' : ''))))), d.testDate ? h('p.dim', dateText(d.testDate)) : null] : h('p.dim', T.ftNone)),
      card(T.seTitle,
        h('p', d.rankings.length ? T.rkNow(d.rankings[d.rankings.length - 1].split(' ').slice(1).join(' ')) : T.rkNone),
        d.rankings.length > 1 ? h('p.dim', d.rankings.map((r) => r.split(' ').slice(1).join(' ')).join(' → ')) : null,
        d.events.length ? d.events.map((e) => h('p', h('b', dateText(e.date) + ' '), e.name, e.place ? ' · ' + e.place : '')) : h('p.dim', T.seNone)),
      card(T.mmTitle, d.results.length ? h('table', h('tbody', d.results.map((r) => h('tr', h('td', dateText(r.date)), h('td', r.event), h('td', r.opponent + (r.opponentRanking ? ' (' + r.opponentRanking + ')' : '')),
        h('td', r.score), h('td', { style: 'color:' + (r.won ? 'var(--ok)' : 'var(--warn)') }, r.won ? T.studentWon : T.studentLost))))) : h('p.dim', '—')),
      card(T.coPacks,
        h('p.dim', [balanceText(s.balance), s.balance.absences ? T.evAbsences(s.balance.absences) : '', s.balance.toRecover ? T.laToRecover(s.balance.toRecover) : ''].filter(Boolean).join(' · ')),
        d.packs.length ? h('table', h('tbody', d.packs.map((p) => h('tr',
          h('td', T.coPackLine(p.lessons, eur(p.cents))), h('td.dim', dateText(p.created)),
          h('td', p.paid ? h('button', { onclick: () => openPdf('/coach/packs/' + encodeURIComponent(p.id) + '/receipt.pdf', T.receiptTitle + ' ' + (p.receipt || '')) }, T.rcReceipt + (p.receipt ? ' ' + p.receipt : ''))
            : h('button', { onclick: async () => { try { await api('POST', '/coach/packs/' + encodeURIComponent(p.id) + '/paid', {}); toast(T.saved); showStudent(id); } catch (e) { toast(errText(e)); } } }, T.coMarkPaid)))))) : null),
      d.courses.length ? card(T.ccTitle, d.courses.map((c) => h('p', c.name + ' · ' + T.scAttendance(c.present, c.total)))) : null));
}

// --- Agenda ---

/** Il colore di una cosa del calendario: lezione privata, lezione di corso, torneo; grigia se annullata. */
/** I corsi dell'allenatore in azzurro, quelli della segreteria nel verde della pagina. */
const OWN = '#1e88c8';
const calColor = (x) => (x.cancelled ? 'var(--dim)' : x.kind === 'course' ? 'var(--accent)' : x.kind === 'own' ? OWN : x.kind === 'event' ? 'var(--warn)' : 'var(--text)');
const calLine = (x) => (x.startMin != null ? hhmm(x.startMin) + ' ' : '🏆 ') + x.title;

/** Un tocco su una cosa del calendario: la lezione privata passata si segna fatta, il corso apre il registro, il torneo l'allievo. */
async function calOpen(x) {
  if (x.kind === 'course' || x.kind === 'own') { coachView = 'courses'; coachCourseId = x.ref; return coachRender(); }
  if (x.kind === 'event') { coachView = 'students'; coachStudent = x.ref; return coachRender(); }
  if (x.past && !x.done && !x.cancelled && confirm(T.coDone + '?')) {
    try { await api('PUT', '/coach/lessons/' + encodeURIComponent(x.ref) + '/done', { done: true }); toast(T.saved); } catch (e) { toast(errText(e)); }
    showAgenda();
  }
}

/** Agenda: il mese (o la settimana) con lezioni private, lezioni dei corsi e tornei degli allievi; o l'elenco. */
async function showAgenda() {
  if (!agendaDate) agendaDate = info.today;
  const views = h('div.seg', { role: 'group' }, [['month', T.viewMonth], ['week', T.viewWeek], ['list', T.coUpcoming]].map(([v, l]) =>
    h('button' + (v === agendaView ? '.on' : ''), { type: 'button', 'aria-pressed': v === agendaView ? 'true' : 'false', onclick: () => { agendaView = v; showAgenda(); } }, l)));
  if (agendaView === 'list') return showAgendaList(views);
  const shift = (n) => {
    const d = new Date(agendaDate + 'T12:00:00');
    if (agendaView === 'month') { d.setDate(1); d.setMonth(d.getMonth() + n); } else d.setDate(d.getDate() + 7 * n);
    agendaDate = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    showAgenda();
  };
  const long = (d, o) => new Date(d + 'T12:00:00').toLocaleDateString(locale(), o);
  const nav = (title) => h('div.head', h('button', { onclick: () => shift(-1) }, '‹'), h('h1', title), h('button', { onclick: () => shift(1) }, '›'),
    h('button', { onclick: () => { agendaDate = info.today; showAgenda(); } }, '•'), views, printBtn());
  const legend = h('p.dim', h('span', { style: 'color:var(--text)' }, '■ ' + T.coLessons), '  ', h('span', { style: 'color:var(--accent)' }, '■ ' + T.ccClubCourses), '  ', h('span', { style: 'color:' + OWN }, '■ ' + T.ccOwnTag), '  ',
    h('span', { style: 'color:var(--warn)' }, '■ ' + T.seTitle));
  const item = (x) => h('button', { type: 'button', style: 'display:block;width:100%;text-align:left;border:0;padding:1px 2px;background:none;font-size:12px;color:' + calColor(x) +
    (x.cancelled ? ';text-decoration:line-through' : '') + (x.done ? ';font-weight:600' : ''), title: [calLine(x), x.place].filter(Boolean).join(' · '),
    onclick: (ev) => { ev.stopPropagation(); calOpen(x); } }, calLine(x) + (x.past && !x.done && !x.cancelled && x.kind !== 'event' ? ' •' : ''));
  if (agendaView === 'month') {
    const m = await api('GET', '/coach/calendar?date=' + agendaDate);
    const title = long(m.first, { month: 'long', year: 'numeric' });
    const lead = (new Date(m.first + 'T12:00:00').getDay() + 6) % 7;
    const cells = [];
    for (let i = 0; i < lead; i++) cells.push(h('div.mday.out'));
    for (const d of m.days) {
      cells.push(h('div.mday' + (d.date === info.today ? '.now' : ''), { title: long(d.date, { weekday: 'long', day: 'numeric', month: 'long' }) },
        h('b', Number(d.date.slice(8))), d.items.slice(0, 4).map(item), d.items.length > 4 ? h('span.dim', '+' + (d.items.length - 4)) : null));
    }
    put(main(), nav(title), h('div.printonly.phead', h('b', info.coach || T.ccDeskTitle), ' · ', T.coAgenda + ' · ' + title),
      h('div.panel', h('div.month', [1, 2, 3, 4, 5, 6, 7].map((k) => h('div.mhead', weekdayName(k))), cells)), legend);
    return;
  }
  // La settimana (da lunedì): una colonna per giorno, con tutto in ordine d'ora.
  const d0 = new Date(agendaDate + 'T12:00:00');
  d0.setDate(d0.getDate() - ((d0.getDay() + 6) % 7));
  const iso = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  const days = [...Array(7)].map((_, i) => { const d = new Date(d0); d.setDate(d0.getDate() + i); return iso(d); });
  const months = [...new Set(days.map((x) => x.slice(0, 8) + '01'))];
  const cals = await Promise.all(months.map((x) => api('GET', '/coach/calendar?date=' + x)));
  const byDay = Object.fromEntries(cals.flatMap((c) => c.days).map((d) => [d.date, d.items]));
  const title = long(days[0], { day: 'numeric', month: 'short' }) + ' – ' + long(days[6], { day: 'numeric', month: 'short', year: 'numeric' });
  put(main(), nav(title), h('div.printonly.phead', h('b', info.coach || T.ccDeskTitle), ' · ', T.coAgenda + ' · ' + title),
    h('div.panel', h('div.month', days.map((d) => h('div.mhead', long(d, { weekday: 'short', day: 'numeric' }))),
      days.map((d) => h('div.mday' + (d === info.today ? '.now' : ''), { style: 'min-height:240px' }, (byDay[d] || []).map(item))))), legend);
}

/** L'elenco: le prossime lezioni private e quelle passate da segnare come fatte. */
async function showAgendaList(views) {
  const list = await api('GET', '/coach/lessons');
  const now = info.today;
  const next = list.filter((l) => !l.cancelled && l.date >= now);
  const past = list.filter((l) => !l.cancelled && !l.done && l.date < now).reverse();
  const row = (l, mark) => h('tr',
    h('td', dateText(l.date) + ' ' + hhmm(l.startMin)), h('td', T.coMinutes(l.minutes)), h('td', l.students.join(', ')), h('td', l.place),
    h('td', mark ? h('button', { onclick: async () => { try { await api('PUT', '/coach/lessons/' + encodeURIComponent(l.id) + '/done', { done: true }); toast(T.saved); showAgenda(); } catch (e) { toast(errText(e)); } } }, T.coDone) : (l.done ? T.coDone : '')));
  put(main(), h('div.head', h('h1', T.coAgenda), views, printBtn()),
    h('div.panel', h('h2', T.coUpcoming), next.length ? h('table', h('tbody', next.map((l) => row(l, false)))) : h('p.dim', T.coNoLessons)),
    past.length ? h('div.panel', h('h2', T.coToMark), h('table', h('tbody', past.map((l) => row(l, true))))) : null);
}

// --- Corsi ---

async function showCourses() {
  if (coachCourseId) return showCourseRegister(coachCourseId);
  const list = await api('GET', '/coach/courses');
  put(main(), h('div.head', h('h1', T.ccTitle), h('button.primary', { onclick: () => editOwnCourse(null) }, T.ccNew)),
    list.length ? h('div.cards', list.map((c) => h('button.card', { type: 'button', style: 'border-left:5px solid ' + (c.personal ? OWN : 'var(--accent)'), onclick: () => { coachCourseId = c.id; showCourses(); } },
      h('h2', c.name), c.personal ? h('p', { style: 'color:' + OWN }, T.ccOwnTag) : null, h('p.dim', [c.level, c.club, dateText(c.from) + ' – ' + dateText(c.to)].filter(Boolean).join(' · ')),
      h('p', T.coGroupCount(c.students)), c.toMark ? h('p', { style: 'color:var(--warn)' }, T.ccToMark(c.toMark)) : null, c.toSend ? h('p.dim', T.ccToSend) : null))) : h('div.panel', h('div.empty', T.ccNothingYet)));
}

/** Il registro del corso: una colonna per lezione; un clic su una lezione passata apre l'appello. */
async function showCourseRegister(id) {
  const r = await api('GET', '/coach/courses/' + encodeURIComponent(id));
  const c = r.course;
  const back = h('button', { onclick: () => { coachCourseId = null; showCourses(); }, 'aria-label': T.ccTitle }, '←');
  const color = (m) => (m === '✓' ? 'var(--ok)' : m === '✗' ? 'var(--warn)' : 'var(--dim)');
  const shortD = (d) => d.slice(8, 10) + '/' + d.slice(5, 7);
  put(main(),
    h('div.head', back, h('h1', c.name),
      // Un corso suo (azzurro): si cambia e si toglie anche dal PC; quelli della segreteria li cambia lei.
      c.personal ? h('button', { onclick: () => editOwnCourse(c) }, T.edit) : null,
      c.personal ? h('button.danger', { onclick: async () => {
        if (!confirm(T.ccDeleteAsk(c.name))) return;
        try { await api('DELETE', '/coach/courses/' + encodeURIComponent(id)); toast(T.saved); coachCourseId = null; showCourses(); } catch (e) { toast(errText(e)); }
      } }, T.remove) : null,
      h('button', { onclick: () => openPdf('/coach/courses/' + encodeURIComponent(id) + '/register.pdf', T.rgTitle + ' ' + c.name) }, T.rgPdf),
      h('button', { onclick: () => downloadFile('/coach/courses/' + encodeURIComponent(id) + '/register.xlsx', T.rgTitle + ' ' + c.name + '.xlsx') }, T.xlSave),
      printBtn()),
    h('p.dim', [c.level, c.club, dateText(c.from) + ' – ' + dateText(c.to)].filter(Boolean).join(' · ')),
    h('div.printonly.phead', h('b', T.rgTitle + ' · ' + c.name), ' · ', dateText(info.today)),
    h('div.panel', { style: 'overflow-x:auto' }, h('table',
      h('thead', h('tr', h('th', T.studentsTitle), r.lessons.map((l) => h('th', { title: dateText(l.date) + ' ' + hhmm(l.startMin), style: 'cursor:' + (l.past ? 'pointer' : 'default') + ';text-align:center', onclick: () => { if (l.past) attendanceDialog(r, l); } },
        shortD(l.date), l.past && !l.marked && !l.cancelled ? h('div', { style: 'color:var(--warn)' }, '•') : null)), h('th', T.rgTotal))),
      h('tbody', r.rows.map((row) => h('tr', h('td', row.name), row.marks.map((m) => h('td', { style: 'text-align:center;font-weight:bold;color:' + color(m) }, m)), h('td', h('b', row.present + '/' + row.total))))))),
    h('p.dim', T.rgLegend), c.toSend ? h('p', { style: 'color:var(--warn)' }, T.ccDeskSent) : null);
}

/**
 * Un corso dell'allenatore, nuovo ([c] null) o da cambiare: nome, livello, dal… al…, orari settimanali (giorno, ora,
 * durata, dove) e allievi. Salvato, si apre il suo registro.
 */
async function editOwnCourse(c) {
  let students = [];
  try { students = await api('GET', '/coach/students'); } catch (e) { toast(errText(e)); return; }
  const name = h('input', { value: c ? c.name : '', maxlength: 60, required: true });
  const level = h('input', { value: c ? c.level : '', maxlength: 40 });
  const plus = (d, n) => { const x = new Date(d + 'T12:00:00'); x.setMonth(x.getMonth() + n); return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0'); };
  const from = h('input', { type: 'date', value: c ? c.from : info.today });
  const to = h('input', { type: 'date', value: c ? c.to : plus(info.today, 6) });
  let slots = c ? c.slots.slice() : [];
  const slotList = h('div');
  const drawSlots = () => put(slotList, slots.length ? slots.map((x, i) => h('div', { style: 'display:flex;gap:8px;align-items:center' },
    h('span', weekdayName(x.weekday) + ' ' + hhmm(x.startMin) + ' · ' + T.coMinutes(x.minutes) + (x.court ? ' · ' + x.court : '')),
    h('button', { type: 'button', onclick: () => { slots.splice(i, 1); drawSlots(); } }, '✕'))) : h('p.dim', '—'));
  const wd = select([1, 2, 3, 4, 5, 6, 7].map((k) => [String(k), weekdayName(k)]), '1');
  const time = h('input', { type: 'time', value: '17:00', step: 300 });
  const mins = select([45, 60, 90, 120].map((k) => [String(k), T.coMinutes(k)]), '60');
  const place = h('input', { maxlength: 30, placeholder: T.coPlace });
  const add = h('button', { type: 'button', onclick: () => {
    const [hh, mm] = time.value.split(':').map(Number);
    if (Number.isNaN(hh)) return;
    slots.push({ weekday: Number(wd.value), startMin: hh * 60 + (mm || 0), minutes: Number(mins.value), court: place.value.trim() });
    slots.sort((a, b) => a.weekday - b.weekday || a.startMin - b.startMin);
    drawSlots();
  } }, T.ccAddSlot);
  const chosen = new Set(c ? c.studentIds : []);
  const boxes = students.map((s) => { const b = h('input', { type: 'checkbox' }); b.checked = chosen.has(s.id); return [s.id, h('label.check', b, s.name), b]; });
  drawSlots();
  dialog(c ? c.name : T.ccNew, h('div.form',
    field(T.ccName, name, 'full'), field(T.ccLevel, level, 'full'), field(T.ctStart, from), field(T.ctEnd, to),
    h('h3.full', T.ccSlots), h('div.full', slotList),
    field('', wd), field('', time), field('', mins), field('', place), h('div.full', add),
    h('h3.full', T.ccPickStudents), h('div.full', boxes.map((b) => b[1]))), async () => {
    if (!name.value.trim() || !slots.length) throw new Error(T.err.BAD_REQUEST || '');
    const body = { name: name.value, level: level.value, from: from.value, to: to.value, slots, students: boxes.filter((b) => b[2].checked).map((b) => b[0]) };
    const out = c ? await api('PUT', '/coach/courses/' + encodeURIComponent(c.id), body) : await api('POST', '/coach/courses', body);
    toast(T.saved);
    coachView = 'courses'; coachCourseId = out.id;
    await coachRender();
  }, { wide: true });
}

function attendanceDialog(r, l) {
  const boxes = r.rows.map((row, i) => {
    const idx = r.lessons.indexOf(l);
    const cb = h('input', { type: 'checkbox' });
    cb.checked = l.marked ? row.marks[idx] === '✓' : true;
    return [row.id, cb, h('label', { style: 'display:flex;gap:8px;align-items:center' }, cb, row.name)];
  });
  const cancelled = h('input', { type: 'checkbox' }); cancelled.checked = l.cancelled;
  const note = h('textarea', { rows: 3, style: 'width:100%' }); note.value = r.notes[l.key] || '';
  const dlg = h('dialog', h('form', { method: 'dialog', style: 'min-width:320px' },
    h('h2', dateText(l.date) + ' ' + hhmm(l.startMin)),
    h('label', { style: 'display:flex;gap:8px;align-items:center' }, cancelled, T.scCancelLesson),
    h('p.dim', T.scWhoWasThere), boxes.map((b) => b[2]),
    h('label', T.ccLessonNote, note),
    h('div.actions',
      h('button', { type: 'button', onclick: () => dlg.close() }, '✕'),
      h('button.primary', { type: 'button', onclick: async () => {
        try {
          await api('PUT', '/coach/courses/' + encodeURIComponent(r.course.id) + '/attendance',
            { key: l.key, present: boxes.filter((b) => b[1].checked).map((b) => b[0]), cancelled: cancelled.checked, note: note.value });
          dlg.close(); dlg.remove(); toast(T.saved); showCourseRegister(r.course.id);
        } catch (e) { toast(errText(e)); }
      } }, T.ccDeskSaveAttendance))));
  document.body.append(dlg);
  dlg.showModal();
}

// --- Riepilogo del mese e ricevute ---

async function showMonth() {
  if (!coachMonth) coachMonth = info.today.slice(0, 8) + '01';
  const m = await api('GET', '/coach/month?date=' + coachMonth);
  const shift = (n) => { const d = new Date(coachMonth + 'T12:00:00'); d.setMonth(d.getMonth() + n); coachMonth = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-01'; showMonth(); };
  const label = new Date(m.from + 'T12:00:00').toLocaleDateString(locale(), { month: 'long', year: 'numeric' });
  const year = m.from.slice(0, 4);
  const lines = [
    `${T.coLessons}: ${m.privateLessons} (${coachHours(m.privateMinutes)}) · ${T.coGroupCount(m.students)}`,
    T.cmCourses(m.courseLessons, coachHours(m.courseMinutes)) + (m.presencePct != null ? ' · ' + T.cmPresence(m.presencePct) : ''),
    m.paidCents ? T.cmPaid(eur(m.paidCents)) : '',
  ].filter(Boolean);
  put(main(),
    h('div.head', h('button', { onclick: () => shift(-1) }, '‹'), h('h1', T.cmMonthTitle(label)), h('button', { onclick: () => shift(1) }, '›'), printBtn()),
    h('div.printonly.phead', h('b', info.coach || T.ccDeskTitle), ' · ', T.cmMonthTitle(label)),
    h('div.panel', lines.map((l) => h('p', l)), (!m.privateLessons && !m.courseLessons && !m.paidCents) ? h('p.dim', T.cmEmpty) : null),
    h('div.panel',
      h('div.head', h('h2', T.xlReceipts + ' ' + year), h('button', { onclick: () => downloadFile('/coach/receipts.xlsx?year=' + year, T.xlReceipts + ' ' + year + '.xlsx') }, T.xlSave)),
      m.receipts.length ? h('table', h('tbody', m.receipts.map((x) => h('tr',
        h('td', x.number), h('td', dateText(x.date)), h('td', x.name), h('td', T.coPackLine(x.lessons, eur(x.cents))),
        h('td', h('button', { onclick: () => openPdf('/coach/packs/' + encodeURIComponent(x.packId) + '/receipt.pdf', T.receiptTitle + ' ' + x.number) }, 'PDF')))))) : h('p.dim', '—')));
}
