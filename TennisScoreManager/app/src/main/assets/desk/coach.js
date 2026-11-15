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
  put(main(), h('div.head', h('h1', T.studentsTitle), h('button.primary', { onclick: () => studentForm(null) }, T.studentNew), printBtn()), h('div.panel', q, body));
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
      h('button', { onclick: () => studentForm(d) }, T.edit),
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

/**
 * Un allievo nuovo ([d] null) o da cambiare: nome, cognome, soprannome, telefono, email, genitore (per i minorenni)
 * e consenso a riprese e dati. Con lo stesso nome e cognome di un giocatore che c'è già, diventa allievo lui.
 */
function studentForm(d) {
  const s = d ? d.student : {};
  const inp = (v, max, type) => h('input', { value: v || '', maxlength: max, type: type || 'text' });
  const first = inp(d && d.firstName, 40), last = inp(d && d.lastName, 40), nick = inp(d && d.nickname, 40);
  const phone = inp(s.phone, 30, 'tel'), email = inp(s.email, 80, 'email');
  const guardian = inp(s.guardian, 60), gphone = inp(s.guardianPhone, 30, 'tel');
  const consent = h('input', { type: 'checkbox' }); consent.checked = !!(d && d.consent);
  dialog(d ? s.name : T.studentNew, h('div.form',
    field(T.playerFirstName, first), field(T.playerLastName, last), field(T.playerNickname, nick, 'full'),
    field(T.memberPhone, phone), field(T.memberEmail, email),
    field(T.coGuardian, guardian), field(T.coGuardianPhone, gphone),
    h('label.check.full', consent, T.coConsent), h('p.dim.full', T.coConsentHint)), async () => {
    if (!first.value.trim() || !last.value.trim()) throw new Error(T.err.BAD_REQUEST);
    const body = { firstName: first.value, lastName: last.value, nickname: nick.value, phone: phone.value, email: email.value, guardian: guardian.value, guardianPhone: gphone.value, consent: consent.checked };
    const out = d ? await api('PUT', '/coach/students/' + encodeURIComponent(s.id), body) : await api('POST', '/coach/students', body);
    toast(T.saved);
    coachView = 'students'; coachStudent = out.student.id;
    await coachRender();
  });
}

// --- Agenda ---

/** Il colore di una cosa del calendario: lezione privata, lezione di corso, torneo; grigia se annullata. */
/** I corsi dell'allenatore in azzurro, quelli della segreteria nel verde della pagina. */
const OWN = '#1e88c8';
// Le lezioni dei corsi col colore scelto per il corso (di partenza: verde segreteria, azzurro allenatore).
const calColor = (x) => (x.cancelled ? 'var(--dim)' : (x.kind === 'course' || x.kind === 'own') ? (x.color || (x.kind === 'own' ? OWN : 'var(--accent)')) : x.kind === 'event' ? 'var(--warn)' : 'var(--text)');
/** Un pallino del colore dato, con o senza bordo (scelto). */
const dot = (color, on, onclick) => h('button', { type: 'button', title: color, onclick, style: `width:${on ? 26 : 22}px;height:${on ? 26 : 22}px;border-radius:50%;padding:0;background:${color};border:${on ? '3px solid var(--text)' : '1px solid var(--line)'};cursor:pointer` });
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

// --- Impegni (giorni occupati) ---

const BUSY_KINDS = ['BUSY', 'VACATION', 'TOURNAMENT', 'SICK', 'OTHER'];
const busyKindName = (k) => ({ BUSY: T.bzBusy, VACATION: T.bzVacation, TOURNAMENT: T.bzTournament, SICK: T.bzSick, OTHER: T.bzOther })[k] || k;
const busyLabel = (b) => [busyKindName(b.kind), b.note].filter(Boolean).join(' · ');
const busyDays = (b) => (b.from === b.to ? dateText(b.from) : dateText(b.from) + ' – ' + dateText(b.to));

/** Un impegno: dal… al…, il motivo e una nota ([b] null = nuovo). */
function busyDialog(b, day) {
  const from = h('input', { type: 'date', value: b ? b.from : (day || info.today), required: true });
  const to = h('input', { type: 'date', value: b ? b.to : (day || info.today), required: true });
  from.onchange = () => { if (to.value < from.value) to.value = from.value; };
  const kind = select(BUSY_KINDS.map((k) => [k, busyKindName(k)]), b ? b.kind : 'BUSY');
  const note = h('input', { maxlength: 200, value: b ? b.note : '' });
  const del = b ? h('button.danger', { type: 'button', onclick: async () => {
    try { await api('DELETE', '/coach/busy/' + encodeURIComponent(b.id)); dlg.close(); toast(T.saved); showAgenda(); } catch (e) { toast(errText(e)); }
  } }, T.remove) : null;
  const dlg = dialog(T.bzNew, h('div.form', h('p.dim.full', T.bzHint), field(T.bzFrom, from), field(T.bzTo, to), field('', kind, 'full'), field(T.bzNote, note, 'full')), async () => {
    const body = { from: from.value, to: to.value, kind: kind.value, note: note.value };
    if (b) await api('PUT', '/coach/busy/' + encodeURIComponent(b.id), body); else await api('POST', '/coach/busy', body);
    toast(T.saved);
    showAgenda();
  }, { left: del });
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
    h('button', { onclick: () => { agendaDate = info.today; showAgenda(); } }, '•'), views, h('button', { onclick: () => busyDialog(null) }, T.bzNew), printBtn());
  // L'impegno del giorno, in arancione: un clic per cambiarlo.
  const busyTag = (b) => (b ? h('button.busyname', { type: 'button', title: busyDays(b), onclick: (ev) => { ev.stopPropagation(); busyDialog(b); } }, busyLabel(b)) : null);
  // La legenda: lezioni private, tornei e ogni corso col suo colore.
  const legendOf = (items) => {
    const courses = [...new Map(items.filter((x) => x.kind === 'course' || x.kind === 'own').map((x) => [x.ref, [x.title, calColor({ ...x, cancelled: false })]])).values()];
    return h('p.dim', h('span', { style: 'color:var(--text)' }, '■ ' + T.coLessons), '  ', h('span', { style: 'color:var(--warn)' }, '■ ' + T.seTitle),
      courses.map(([n, c]) => ['  ', h('span', { style: 'border-left:12px solid ' + c + ';padding-left:4px;color:var(--text)' }, n)]));
  };
  const item = (x) => h('button', { type: 'button', style: 'display:block;width:100%;text-align:left;border:0;border-left:4px solid ' + calColor(x) + ';padding:1px 4px;margin:1px 0;background:none;font-size:12px;color:' + (x.cancelled ? 'var(--dim)' : 'var(--text)') +
    (x.cancelled ? ';text-decoration:line-through' : '') + (x.done ? ';font-weight:600' : ''), title: [calLine(x), x.place].filter(Boolean).join(' · '),
    onclick: (ev) => { ev.stopPropagation(); calOpen(x); } }, calLine(x) + (x.past && !x.done && !x.cancelled && x.kind !== 'event' ? ' •' : ''));
  if (agendaView === 'month') {
    const m = await api('GET', '/coach/calendar?date=' + agendaDate);
    const title = long(m.first, { month: 'long', year: 'numeric' });
    const lead = (new Date(m.first + 'T12:00:00').getDay() + 6) % 7;
    const cells = [];
    for (let i = 0; i < lead; i++) cells.push(h('div.mday.out'));
    for (const d of m.days) {
      cells.push(h('div.mday' + (d.date === info.today ? '.now' : '') + (d.holiday ? '.hol' : '') + (d.busy ? '.busy' : ''), { title: long(d.date, { weekday: 'long', day: 'numeric', month: 'long' }) + (d.holiday ? ' · ' + d.holiday : '') },
        h('b', Number(d.date.slice(8))), d.holiday ? h('span.holname', d.holiday) : null, busyTag(d.busy), d.items.slice(0, 4).map(item), d.items.length > 4 ? h('span.dim', '+' + (d.items.length - 4)) : null));
    }
    put(main(), nav(title), h('div.printonly.phead', h('b', info.coach || T.ccDeskTitle), ' · ', T.coAgenda + ' · ' + title),
      h('div.panel', h('div.month', [1, 2, 3, 4, 5, 6, 7].map((k) => h('div.mhead', weekdayName(k))), cells)), legendOf(m.days.flatMap((d) => d.items)));
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
  const hol = Object.fromEntries(cals.flatMap((c) => c.days).filter((d) => d.holiday).map((d) => [d.date, d.holiday]));
  const busyOf = Object.fromEntries(cals.flatMap((c) => c.days).filter((d) => d.busy).map((d) => [d.date, d.busy]));
  const title = long(days[0], { day: 'numeric', month: 'short' }) + ' – ' + long(days[6], { day: 'numeric', month: 'short', year: 'numeric' });
  put(main(), nav(title), h('div.printonly.phead', h('b', info.coach || T.ccDeskTitle), ' · ', T.coAgenda + ' · ' + title),
    h('div.panel', h('div.month', days.map((d) => h('div.mhead' + (hol[d] ? '.hol' : ''), { title: hol[d] || '' }, long(d, { weekday: 'short', day: 'numeric' }))),
      days.map((d) => h('div.mday' + (d === info.today ? '.now' : '') + (hol[d] ? '.hol' : '') + (busyOf[d] ? '.busy' : ''), { style: 'min-height:240px' }, hol[d] ? h('span.holname', hol[d]) : null, busyTag(busyOf[d]), (byDay[d] || []).map(item))))), legendOf(days.flatMap((d) => byDay[d] || [])));
}

/** L'elenco: le prossime lezioni private e quelle passate da segnare come fatte. */
async function showAgendaList(views) {
  const [list, busy] = await Promise.all([api('GET', '/coach/lessons'), api('GET', '/coach/busy')]);
  const now = info.today;
  const next = list.filter((l) => !l.cancelled && l.date >= now);
  const past = list.filter((l) => !l.cancelled && !l.done && l.date < now).reverse();
  const row = (l, mark) => h('tr',
    h('td', dateText(l.date) + ' ' + hhmm(l.startMin)), h('td', T.coMinutes(l.minutes)), h('td', l.students.join(', ')), h('td', l.place),
    h('td', mark ? h('button', { onclick: async () => { try { await api('PUT', '/coach/lessons/' + encodeURIComponent(l.id) + '/done', { done: true }); toast(T.saved); showAgenda(); } catch (e) { toast(errText(e)); } } }, T.coDone) : (l.done ? T.coDone : '')));
  const byId = Object.fromEntries(busy.items.map((b) => [b.id, b]));
  put(main(), h('div.head', h('h1', T.coAgenda), views, h('button', { onclick: () => busyDialog(null) }, T.bzNew), printBtn()),
    busy.clashes.length ? h('div.panel', h('h2', { style: 'color:var(--warn)' }, T.bzClash(busy.clashes.length)),
      h('table', h('tbody', busy.clashes.map((x) => h('tr', h('td', dateText(x.date) + ' ' + hhmm(x.startMin)), h('td', x.title), h('td', { style: 'color:var(--warn)' }, byId[x.busyId] ? busyLabel(byId[x.busyId]) : ''))))),
      h('p.dim', T.bzClashHint)) : null,
    h('div.panel', h('h2', T.bzTitle), busy.items.length ? h('table', h('tbody', busy.items.map((b) => h('tr', { style: 'cursor:pointer', onclick: () => busyDialog(b) },
      h('td', { style: 'color:var(--warn);font-weight:600' }, busyDays(b)), h('td', busyLabel(b)))))) : h('p.dim', T.bzNone)),
    h('div.panel', h('h2', T.coUpcoming), next.length ? h('table', h('tbody', next.map((l) => row(l, false)))) : h('p.dim', T.coNoLessons)),
    past.length ? h('div.panel', h('h2', T.coToMark), h('table', h('tbody', past.map((l) => row(l, true))))) : null);
}

// --- Corsi ---

async function showCourses() {
  if (coachCourseId) return showCourseRegister(coachCourseId);
  const list = await api('GET', '/coach/courses');
  put(main(), h('div.head', h('h1', T.ccTitle), h('button.primary', { onclick: () => editOwnCourse(null) }, T.ccNew)),
    list.length ? h('div.cards', list.map((c) => h('button.card', { type: 'button', style: 'border-left:8px solid ' + (c.color || (c.personal ? OWN : 'var(--accent)')), onclick: () => { coachCourseId = c.id; showCourses(); } },
      h('h2', c.name), c.personal ? h('p.dim', T.ccOwnTag) : null, h('p.dim', [c.level, c.club, dateText(c.from) + ' – ' + dateText(c.to)].filter(Boolean).join(' · ')),
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
    h('div.head', back, h('h1', h('span', { style: 'display:inline-block;width:14px;height:14px;border-radius:50%;margin-right:8px;background:' + c.color }), c.name),
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
    // L'etichetta colorata del corso: un clic la cambia (anche per i corsi della segreteria, solo per te).
    h('div.noprint', { style: 'display:flex;gap:8px;align-items:center;margin:6px 0' }, h('span.dim', T.ccColor), c.colors.map((col) => dot(col, col === c.color, async () => {
      try { await api('PUT', '/coach/courses/' + encodeURIComponent(id) + '/color', { text: col }); showCourseRegister(id); } catch (e) { toast(errText(e)); }
    }))),
    h('div.printonly.phead', h('b', T.rgTitle + ' · ' + c.name), ' · ', dateText(info.today)),
    // Con tanti allievi: un filtro per nome sulle righe del registro.
    r.rows.length > 12 ? h('input.noprint', { type: 'search', placeholder: T.coSearch, style: 'max-width:320px;margin:6px 0', oninput: (ev) => {
      const q = ev.target.value.trim().toLowerCase();
      document.querySelectorAll('tr[data-name]').forEach((tr) => { tr.hidden = !!q && !tr.dataset.name.includes(q); });
    } }) : null,
    h('div.panel', { style: 'overflow-x:auto' }, h('table',
      h('thead', h('tr', h('th', T.studentsTitle), r.lessons.map((l) => h('th', { title: dateText(l.date) + ' ' + hhmm(l.startMin) + (l.holiday ? ' · ' + T.hdHoliday + ': ' + l.holiday : '') + (l.busy ? ' · ' + busyLabel(l.busy) : ''), style: 'cursor:' + (l.past || c.personal ? 'pointer' : 'default') + ';text-align:center' + (l.holiday ? ';color:var(--bad)' : l.busy ? ';color:var(--warn)' : l.movedFrom ? ';color:var(--warn)' : ''), onclick: () => { if (l.past || c.personal) attendanceDialog(r, l); } },
        shortD(l.date), l.past && !l.marked && !l.cancelled ? h('div', { style: 'color:var(--warn)' }, '•') : null)), h('th', T.rgTotal))),
      h('tbody', r.rows.map((row) => h('tr', { 'data-name': row.name.toLowerCase() }, h('td', row.name), row.marks.map((m) => h('td', { style: 'text-align:center;font-weight:bold;color:' + color(m) }, m)), h('td', h('b', row.present + '/' + row.total))))))),
    h('p.dim', T.rgLegend), c.toSend ? h('p', { style: 'color:var(--warn)' }, T.ccDeskSent) : null);
}

/**
 * Scegliere gli allievi anche tra centinaia: si cerca per nome (il menu sotto mostra i primi dieci che non ci sono
 * ancora; Invio aggiunge il primo), si aggiunge un gruppo intero, si toglie con la ✕. [chosen] è l'insieme degli id.
 */
function studentPicker(students, groups, chosen) {
  const byId = Object.fromEntries(students.map((s) => [s.id, s]));
  const input = h('input', { type: 'search', placeholder: T.coSearch, 'aria-label': T.coSearch, autocomplete: 'off' });
  const hits = h('div.hits');
  const count = h('p.dim');
  const tags = h('div', { style: 'display:flex;flex-wrap:wrap;gap:6px;max-height:220px;overflow:auto' });
  const group = select([['', T.pkAddGroup]].concat(groups.map((g) => [g.id, g.name + ' (' + g.students.length + ')'])), '');
  const draw = () => {
    const q = input.value.trim().toLowerCase();
    const list = q ? students.filter((s) => !chosen.has(s.id) && s.name.toLowerCase().includes(q)).slice(0, 10) : [];
    put(hits, list.map((s) => h('button', { type: 'button', onclick: () => { chosen.add(s.id); input.value = ''; draw(); input.focus(); } }, s.name)),
      q && !list.length ? h('div.dim.pad', T.noResults) : null);
    count.textContent = T.pkChosen(chosen.size);
    put(tags, [...chosen].map((id) => byId[id]).filter(Boolean).sort((a, b) => a.name.localeCompare(b.name)).map((s) =>
      h('span', { style: 'display:inline-flex;align-items:center;gap:4px;padding:2px 4px 2px 10px;border-radius:14px;background:var(--hover)' }, s.name,
        h('button', { type: 'button', 'aria-label': T.remove, style: 'border:0;background:none;padding:0 4px', onclick: () => { chosen.delete(s.id); draw(); } }, '✕'))));
  };
  input.addEventListener('input', draw);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); const b = hits.querySelector('button'); if (b) b.click(); } });
  group.addEventListener('change', () => { const g = groups.find((x) => x.id === group.value); if (g) g.students.forEach((id) => { if (byId[id]) chosen.add(id); }); group.value = ''; draw(); });
  draw();
  return h('div.picker', { style: 'max-width:none' }, input, hits, h('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' }, groups.length ? group : null,
    h('button', { type: 'button', onclick: () => { chosen.clear(); draw(); } }, T.pkClear), count), tags);
}

/**
 * Un corso dell'allenatore, nuovo ([c] null) o da cambiare: nome, livello, dal… al…, orari settimanali (giorno, ora,
 * durata, dove) e allievi. Salvato, si apre il suo registro.
 */
async function editOwnCourse(c) {
  let students = [], groups = [], busy = { items: [] };
  try { [students, groups, busy] = await Promise.all([api('GET', '/coach/students'), api('GET', '/coach/groups'), api('GET', '/coach/busy')]); } catch (e) { toast(errText(e)); return; }
  const name = h('input', { value: c ? c.name : '', maxlength: 60, required: true });
  const level = h('input', { value: c ? c.level : '', maxlength: 40 });
  const plus = (d, n) => { const x = new Date(d + 'T12:00:00'); x.setMonth(x.getMonth() + n); return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0'); };
  const from = h('input', { type: 'date', value: c ? c.from : info.today });
  const to = h('input', { type: 'date', value: c ? c.to : plus(info.today, 6) });
  // Quante lezioni: la fine la calcola il telefono da inizio e orari (saltando le feste, se richiesto).
  const count = h('input', { type: 'number', min: 1, max: 200, value: c ? '' : '10', placeholder: '—' });
  const skipHol = h('input', { type: 'checkbox' }); skipHol.checked = true;
  const skipBusy = h('input', { type: 'checkbox' }); skipBusy.checked = busy.items.length > 0;
  const endBox = h('div.full');
  let endSeq = 0;
  const drawEnd = async () => {
    const n = Number(count.value);
    if (!count.value || !(n > 0)) { put(endBox, field(T.ctEnd, to)); return; }
    const my = ++endSeq;
    let out = null;
    try { out = await api('POST', '/coach/courses/end', { name: 'x', from: from.value, slots, lessons: n, skipHolidays: skipHol.checked, skipBusy: skipBusy.checked }); } catch (e) { /* senza orari non c'è fine */ }
    if (my !== endSeq) return;
    put(endBox, h('p', out && out.to ? T.ccEndAuto(dateText(out.to)) : h('span.dim', T.ccSlots + ' —')));
  };
  count.oninput = drawEnd; from.onchange = drawEnd; skipHol.onchange = drawEnd; skipBusy.onchange = drawEnd;
  let slots = c ? c.slots.slice() : [];
  const slotList = h('div');
  const drawSlots = () => put(slotList, slots.length ? slots.map((x, i) => h('div', { style: 'display:flex;gap:8px;align-items:center' },
    h('span', weekdayName(x.weekday) + ' ' + hhmm(x.startMin) + ' · ' + T.coMinutes(x.minutes) + (x.court ? ' · ' + x.court : '')),
    h('button', { type: 'button', onclick: () => { slots.splice(i, 1); drawSlots(); drawEnd(); } }, '✕'))) : h('p.dim', '—'));
  const wd = select([1, 2, 3, 4, 5, 6, 7].map((k) => [String(k), weekdayName(k)]), '1');
  const time = h('input', { type: 'time', value: '17:00', step: 300 });
  const mins = select([45, 60, 90, 120].map((k) => [String(k), T.coMinutes(k)]), '60');
  const place = h('input', { maxlength: 30, placeholder: T.coPlace });
  const add = h('button', { type: 'button', onclick: () => {
    const [hh, mm] = time.value.split(':').map(Number);
    if (Number.isNaN(hh)) return;
    slots.push({ weekday: Number(wd.value), startMin: hh * 60 + (mm || 0), minutes: Number(mins.value), court: place.value.trim() });
    slots.sort((a, b) => a.weekday - b.weekday || a.startMin - b.startMin);
    drawSlots(); drawEnd();
  } }, T.ccAddSlot);
  let color = c ? c.color : '#29B6F6';
  const colors = c ? c.colors : ['#C6F432', '#29B6F6', '#FF9800', '#E53935', '#AB47BC', '#26A69A', '#FFD600', '#EC407A'];
  const colorRow = h('div.full', { style: 'display:flex;gap:8px;align-items:center' });
  const drawColors = () => put(colorRow, h('span.dim', T.ccColor), colors.map((col) => dot(col, col === color, () => { color = col; drawColors(); })));
  drawColors();
  const chosen = new Set(c ? c.studentIds : []);
  const picker = studentPicker(students, groups, chosen);
  drawSlots(); drawEnd();
  dialog(c ? c.name : T.ccNew, h('div.form',
    field(T.ccName, name, 'full'), field(T.ccLevel, level, 'full'), colorRow,
    h('h3.full', T.ccSlots), h('div.full', slotList),
    field('', wd), field('', time), field('', mins), field('', place), h('div.full', add),
    field(T.ctStart, from), field(T.ccLessonsCount, count),
    h('label.full', { style: 'display:flex;gap:8px;align-items:center', title: T.ccSkipHolidaysHint }, skipHol, T.ccSkipHolidays),
    busy.items.length ? h('label.full', { style: 'display:flex;gap:8px;align-items:center', title: T.ccSkipBusyHint }, skipBusy, T.ccSkipBusy) : null, endBox,
    h('h3.full', T.ccPickStudents), h('div.full', picker)), async () => {
    if (!name.value.trim() || !slots.length) throw new Error(T.err.BAD_REQUEST || '');
    const n = Number(count.value);
    const body = { name: name.value, level: level.value, from: from.value, to: to.value, slots, students: [...chosen], color,
      lessons: count.value && n > 0 ? n : null, skipHolidays: skipHol.checked, skipBusy: skipBusy.checked };
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
    return [row.id, cb, h('label', { style: 'display:flex;gap:8px;align-items:center' }, cb, row.name), row.name.toLowerCase()];
  });
  const cancelled = h('input', { type: 'checkbox' }); cancelled.checked = l.cancelled;
  const note = h('textarea', { rows: 3, style: 'width:100%' }); note.value = r.notes[l.key] || '';
  const dlg = h('dialog', h('form', { method: 'dialog', style: 'min-width:320px' },
    h('h2', dateText(l.date) + ' ' + hhmm(l.startMin)),
    h('label', { style: 'display:flex;gap:8px;align-items:center' }, cancelled, T.scCancelLesson),
    // Le presenze solo per le lezioni già fatte; le future si annullano o (nei corsi suoi) si spostano.
    l.past ? [h('p.dim', T.scWhoWasThere),
    // Con tanti iscritti: cerca, tutti presenti o nessuno (poi si tolgono o aggiungono gli altri).
    h('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' },
      boxes.length > 12 ? h('input', { type: 'search', placeholder: T.coSearch, oninput: (ev) => { const q = ev.target.value.trim().toLowerCase(); boxes.forEach((b) => { b[2].hidden = !!q && !b[3].includes(q); }); } }) : null,
      h('button', { type: 'button', onclick: () => boxes.forEach((b) => { if (!b[2].hidden) b[1].checked = true; }) }, T.pkAll),
      h('button', { type: 'button', onclick: () => boxes.forEach((b) => { if (!b[2].hidden) b[1].checked = false; }) }, T.pkNone)),
    h('div', { style: 'max-height:50vh;overflow:auto' }, boxes.map((b) => b[2])),
    h('label', T.ccLessonNote, note)] : null,
    l.movedFrom ? h('p', { style: 'color:var(--warn)' }, T.scMovedFrom(dateText(l.movedFrom) + ' ' + hhmm(l.movedFromMin))) : null,
    // Solo nei corsi suoi: un altro giorno o ora, il recupero in fondo al corso, o com'era.
    r.course.personal ? h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap;align-items:center' },
      h('span.dim', T.scMove + ':'), (() => { const d = h('input', { type: 'date', value: l.date }); const t = h('input', { type: 'time', step: 300, value: hhmm(l.startMin) });
        const go = async (body) => { try { await api('POST', '/coach/courses/' + encodeURIComponent(r.course.id) + '/move', { key: l.key, ...body }); dlg.close(); dlg.remove(); toast(T.saved); showCourseRegister(r.course.id); } catch (e) { toast(errText(e)); } };
        return [d, t, h('button', { type: 'button', onclick: () => { const [hh, mm] = t.value.split(':').map(Number); go({ date: d.value, startMin: hh * 60 + (mm || 0) }); } }, T.scMove),
          h('button', { type: 'button', onclick: () => go({ makeup: true }) }, T.scMakeup), l.movedFrom ? h('button', { type: 'button', onclick: () => go({ reset: true }) }, T.scMoveBack) : null]; })()) : null,
    h('div.actions',
      h('button', { type: 'button', onclick: () => dlg.close() }, '✕'),
      h('button.primary', { type: 'button', onclick: async () => {
        if (!l.past && !cancelled.checked && !l.cancelled) { dlg.close(); dlg.remove(); return; }
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
