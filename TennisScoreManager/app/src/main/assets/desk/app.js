// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
// La Segreteria da PC. Tutti i testi entrano come testo (mai come HTML) e la chiave resta in questa scheda:
// chiudendola, o dopo mezz'ora ferma, si riabbina.
'use strict';

const root = document.getElementById('app');
const KEY = 'tsm-desk';
let token = read();
let info = null;
let T = pick((navigator.language || 'it').slice(0, 2));
let view = 'today';
let people = [];

function read() { try { return sessionStorage.getItem(KEY); } catch (_) { return null; } }
function write(t) { try { if (t) sessionStorage.setItem(KEY, t); else sessionStorage.removeItem(KEY); } catch (_) { /* resta in memoria */ } }
function pick(l) { return DESK_I18N[l] || DESK_I18N.en; }

/** Crea un elemento: h('td.num', {onclick}, 'testo', figlio…). */
function h(spec, attrs, ...kids) {
  const [tag, ...cls] = spec.split('.');
  const e = document.createElement(tag || 'div');
  if (cls.length) e.className = cls.join(' ');
  if (attrs instanceof Node || typeof attrs !== 'object' || attrs === null || Array.isArray(attrs)) { kids.unshift(attrs); attrs = {}; }
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'value') e.value = v;
    else if (k === 'checked' || k === 'selected' || k === 'disabled' || k === 'required') e[k] = !!v;
    else e.setAttribute(k, v);
  }
  for (const c of kids.flat(3)) if (c !== null && c !== undefined && c !== false) e.append(c instanceof Node ? c : String(c));
  return e;
}

class ApiError extends Error {}

async function api(method, path, body, raw) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let r;
  try {
    r = await fetch('/api' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store' });
  } catch (_) { throw new ApiError('NET'); }
  if (r.ok) return raw ? r.blob() : r.json();
  const code = (await r.json().catch(() => ({}))).error || 'SERVER';
  if (r.status === 401 && token) { token = null; write(null); info = null; showLogin(T.expired); }
  throw new ApiError(code);
}

const errText = (e) => (T.err && T.err[e.message]) || T.err.SERVER;
function toast(text) {
  document.querySelectorAll('.toast').forEach((x) => x.remove());
  const t = h('div.toast', { role: 'status' }, text);
  document.body.append(t);
  setTimeout(() => t.remove(), 3500);
}

const eur = (c) => new Intl.NumberFormat(locale(), { style: 'currency', currency: 'EUR' }).format((c || 0) / 100);
const locale = () => (info ? info.lang : navigator.language) || 'it';
const dateText = (d) => (d ? new Date(d + 'T12:00:00').toLocaleDateString(locale(), { day: '2-digit', month: '2-digit', year: 'numeric' }) : '');
const hhmm = (m) => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
function addDay(d, n) { const t = new Date(d + 'T12:00:00'); t.setDate(t.getDate() + n); return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0'); }

/** "150", "150,50", "1.234,56" → centesimi; null se non è un importo. */
function cents(text) {
  let t = String(text).replace(/[€\s]/g, '');
  const c = t.lastIndexOf(','), d = t.lastIndexOf('.');
  if (c >= 0 && d >= 0) t = c > d ? t.replace(/\./g, '').replace(',', '.') : t.replace(/,/g, '');
  else if (c >= 0) t = t.replace(',', '.');
  if (!/^\d{1,6}(\.\d{1,2})?$/.test(t)) return null;
  return Math.round(Number(t) * 100);
}

/** Sostituisce il contenuto di [el]; i pezzi vuoti (null, false) non si vedono. */
function put(el, ...kids) { el.replaceChildren(...kids.flat(3).filter((k) => k !== null && k !== undefined && k !== false)); }

function field(label, input, cls) { return h('label' + (cls ? '.' + cls : ''), label, input); }
function select(options, value) { return h('select', {}, options.map(([v, l]) => h('option', { value: v, selected: v === value }, l))); }

/** Una finestra con modulo: [save] riceve i valori; se dà errore resta aperta e lo mostra. */
function dialog(title, body, save, opts = {}) {
  const err = h('div.err', { role: 'alert' });
  const d = h('dialog' + (opts.wide ? '.wide' : ''));
  const close = () => { d.close(); d.remove(); };
  const ok = save && h('button.primary', { type: 'submit' }, opts.saveText || T.save);
  const form = h('form', {
    onsubmit: async (ev) => {
      ev.preventDefault();
      if (!save) return;
      err.textContent = '';
      ok.disabled = true;
      try { if (await save(err) !== false) close(); }
      catch (e) { if (d.open) err.textContent = e instanceof ApiError ? errText(e) : String(e.message || e); }
      ok.disabled = false;
    },
  }, h('h2', title), body, err, h('div.actions', opts.left, h('button', { type: 'button', onclick: close }, save ? T.cancel : T.close), ok));
  d.append(form);
  d.addEventListener('close', () => d.remove());
  document.body.append(d);
  d.showModal();
  return { close };
}

// --- Entrare ---

let loginRun = 0;
const sleep = (ms) => new Promise((ok) => setTimeout(ok, ms));

/** Il QR su sfondo bianco anche col tema scuro, con il margine che serve ai lettori. */
function drawQr(canvas, rows) {
  const n = rows.length, cell = Math.max(3, Math.floor(280 / (n + 8)));
  canvas.width = canvas.height = (n + 8) * cell;
  const g = canvas.getContext('2d');
  g.fillStyle = '#fff'; g.fillRect(0, 0, canvas.width, canvas.height);
  g.fillStyle = '#000';
  rows.forEach((row, y) => [...row].forEach((c, x) => { if (c === '1') g.fillRect((x + 4) * cell, (y + 4) * cell, cell, cell); }));
}

/** Mostra il QR, lo rinnova prima che scada e aspetta che il telefono lo inquadri. */
async function qrLoop(canvas, status) {
  const run = ++loginRun;
  while (run === loginRun) {
    try {
      const q = await api('POST', '/pair/qr', {});
      if (run !== loginRun) return;
      drawQr(canvas, q.rows);
      canvas.hidden = false;
      status.textContent = '';
      const until = Date.now() + 100000;
      let confirm = false;
      while (run === loginRun && (confirm || Date.now() < until)) {
        const r = await api('GET', '/pair/' + encodeURIComponent(q.id));
        if (run !== loginRun) return;
        if (r.state === 'OK') { loginRun++; token = r.token; write(token); await start(); return; }
        if (r.state === 'CONFIRM') { confirm = true; canvas.hidden = true; status.textContent = T.waitPhone; }
        await sleep(1000);
      }
    } catch (e) {
      if (run !== loginRun) return;
      canvas.hidden = true;
      status.textContent = errText(e);
      await sleep(4000);
    }
  }
}

function showLogin(message) {
  document.title = T.title;
  document.documentElement.lang = T.lang;
  const code = h('input', { autocomplete: 'off', spellcheck: 'false', maxlength: 12, 'aria-label': T.code, required: true });
  const msg = h('div.err', { role: 'alert' }, message || '');
  const btn = h('button.primary', { type: 'submit' }, T.enter);
  const form = h('form', {
    onsubmit: async (ev) => {
      ev.preventDefault();
      msg.textContent = '';
      btn.disabled = true;
      loginRun++;
      try {
        const r = await api('POST', '/pair', { code: code.value });
        btn.textContent = T.waitPhone;
        msg.textContent = '';
        await waitApproval(r.id);
        await start();
      } catch (e) {
        msg.textContent = errText(e);
        btn.disabled = false;
        btn.textContent = T.enter;
        qrLoop(canvas, status);
      }
    },
  }, code, btn, msg);
  const canvas = h('canvas.qr', { role: 'img', 'aria-label': T.qrAlt });
  canvas.hidden = true;
  const status = h('div.status', { role: 'status' }, message || '');
  put(root, h('div.login', h('div.box', h('h1', T.title), h('p', T.qrHint), canvas, status,
    h('details', h('summary', T.useCode), h('p', T.loginHint), form, h('ol', h('li', T.step1))))));
  qrLoop(canvas, status);
}

async function waitApproval(id) {
  for (let i = 0; i < 130; i++) {
    const r = await api('GET', '/pair/' + encodeURIComponent(id));
    if (r.state === 'OK') { token = r.token; write(token); return; }
    await new Promise((ok) => setTimeout(ok, 1000));
  }
  throw new ApiError('REFUSED');
}

async function start() {
  info = await api('GET', '/info');
  T = pick(info.lang);
  document.documentElement.lang = T.lang;
  document.title = T.title + ' · ' + info.club;
  planDate = info.today;
  await render();
}

// --- Telaio ---

/** Le pagine, solo per le funzioni del circolo accese (Oggi c'è sempre). */
const VIEWS = () => {
  const on = (f) => !info || !info.features || !info.features.length || info.features.includes(f);
  return [['today', T.tdTitle, true], ['people', T.members, on('MEMBERS')], ['money', T.money, on('PAYMENTS')], ['plan', T.plan, on('PLANNING')], ['cups', T.cups, on('TOURNAMENTS')],
    ['school', T.scTitle, on('SCHOOL')], ['stats', T.stTitle, on('STATS')]]
    .filter((v) => v[2]).map(([v, l]) => [v, l]);
};
const main = () => document.querySelector('main');

async function render() {
  if (!VIEWS().some(([v]) => v === view)) view = 'today';
  put(root, h('div.shell',
    h('nav.side', { 'aria-label': T.title },
      h('div.brand', info.club || T.title, h('small', T.title)),
      VIEWS().map(([v, l]) => h('button' + (v === view ? '.on' : ''), { 'aria-current': v === view ? 'page' : null, onclick: () => { view = v; if (v === 'cups') cupId = null; if (v === 'school') courseId = null; render(); } }, l)),
      h('div.spacer'),
      h('div.note', T.phoneNote),
      h('button', { onclick: logout }, T.logout)),
    h('main')));
  try { await ({ today: showToday, people: showPeople, money: showMoney, plan: showPlan, cups: showCups, school: showSchool, stats: showStats })[view](); }
  catch (e) { if (e instanceof ApiError && token) put(main(), h('div.empty', errText(e))); else if (!(e instanceof ApiError)) throw e; }
}

async function logout() {
  try { await api('POST', '/logout'); } catch (_) { /* si esce comunque */ }
  token = null; write(null); info = null;
  showLogin(T.loggedOut);
}

// --- Oggi ---

/** Va a una pagina (con [prep] per sistemarla prima). */
function go(v, prep) { view = v; if (prep) prep(); render(); }

async function showToday() {
  const d = await api('GET', '/today');
  const on = (f) => !info.features || !info.features.length || info.features.includes(f);
  const at = (l) => [l.startMin != null ? hhmm(l.startMin) : '', l.court, l.text].filter(Boolean).join(' · ');
  const card = (title, lines, onclick, alert) => h(onclick ? 'button.card' : 'div.card', onclick ? { type: 'button', onclick } : {},
    h('h2' + (alert ? '.alert' : ''), title), lines.filter(Boolean).map((l) => h('p.dim', l)));
  const cards = [];
  if (on('PLANNING')) cards.push(card(d.bookings ? T.tdCourts(d.bookings) : T.tdCourtsNone,
    d.next.map(at).concat(d.online ? [T.tdOnline(d.online)] : []), () => go('plan', () => { planDate = info.today; })));
  if (on('PAYMENTS') && d.collectCents > 0) cards.push(card(T.tdToCollect(eur(d.collectCents)), [T.tdPeople(d.collectPeople)],
    () => go('money', () => { moneyFilter = { type: '', show: 'open', text: '' }; })));
  if (on('MEMBERS') && d.members > 0) cards.push(card(T.tdMembers(d.members), [d.memberNames.join(', '), d.blocking ? T.tdBlocking(d.blocking) : ''],
    () => go('people', () => { filter.kind = 'todo'; filter.text = ''; }), d.blocking > 0));
  if (on('TOURNAMENTS') && d.matches.length) {
    const cups = [...new Set(d.matches.map((m) => m.cup))];
    cards.push(card(T.tdMatches(d.matches.length), d.matches.slice(0, 3).map(at),
      () => go('cups', () => { cupId = cups.length === 1 ? cups[0] : null; cupTab = 'matches'; })));
  }
  if (on('SCHOOL') && d.lessons.length) cards.push(card(T.tdLessons(d.lessons.length), d.lessons.slice(0, 3).map(at), () => go('school', () => { courseId = null; })));
  if (on('TEAMS') && d.fixtures.length) cards.push(card(T.tdFixtures(d.fixtures.length), d.fixtures.slice(0, 3).map((l) => shortDay(l.date) + ' · ' + l.text)));
  if (on('LADDER') && d.challenges) cards.push(card(T.tdChallenges(d.challenges), [d.late ? T.tdLate(d.late) : ''], null, d.late > 0));
  const label = new Date(info.today + 'T12:00:00').toLocaleDateString(locale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  put(main(), h('div.head', h('h1', T.tdTitle), h('span.dim', label)),
    h('div.cards', cards), cards.length === 1 && !d.bookings ? h('p.dim', T.tdNothing) : null);
}

/** Scarica [text] come file CSV (col BOM: il foglio di calcolo riconosce le lettere accentate). */
function saveCsv(name, rows) {
  const cell = (v) => { const t = String(v ?? ''); return /[";\n\r]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t; };
  const text = '\uFEFF' + rows.map((r) => r.map(cell).join(';')).join('\r\n') + '\r\n';
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const a = h('a', { href: url, download: name + '.csv' });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

/** L'intestazione che compare solo sulla carta: circolo, cosa e quando. */
const printHead = (what) => h('div.printonly.phead', h('b', info.club), ' · ', h('span.what', what), ' · ', dateText(info.today));

/** Apre un PDF del telefono in una scheda nuova (o lo scarica, se il browser blocca la scheda). */
async function openPdf(path, name) {
  const w = window.open('', '_blank');
  try {
    const blob = await api('GET', path, undefined, true);
    const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
    if (w) w.location.href = url;
    else { const a = h('a', { href: url, download: name + '.pdf' }); document.body.append(a); a.click(); a.remove(); }
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) { if (w) w.close(); toast(errText(e)); }
}

// --- Soci ---

let filter = { text: '', kind: 'members' };

function issueTag(i) {
  const bad = ['NO_MEDICAL', 'MEDICAL_EXPIRED', 'NO_FITP'].includes(i);
  return h('span.tag.' + (bad ? 'bad' : 'warn'), T.issue[i] || i);
}

const MEDICAL = ['NO_MEDICAL', 'MEDICAL_EXPIRED', 'MEDICAL_SOON'];
let shownPeople = [];

/** L'elenco che si vede, per il foglio di calcolo: le colonne si reimportano con Importa da CSV. */
function exportPeople() {
  const head = [T.csvLastName || T.lastName, T.firstName, T.nickname, T.phone, T.email, T.birth, T.isMember, T.since, T.number, T.fitp, T.fitpYear, T.ranking, T.medical, T.owed];
  const money = (c) => (c ? (c / 100).toLocaleString(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '');
  saveCsv(T.members + ' ' + info.today, [head].concat(shownPeople.map((p) => [p.lastName, p.firstName, p.nickname, p.phone, p.email, dateText(p.birth),
    p.member ? T.yes : T.no, dateText(p.since), p.number, p.fitpNumber, p.fitpYear || '', p.ranking, dateText(p.medicalUntil), money(p.owedCents)])));
}

async function showPeople() {
  people = await api('GET', '/people');
  const body = h('div');
  const search = h('input', { type: 'search', placeholder: T.search, 'aria-label': T.search, value: filter.text, oninput: () => { filter.text = search.value; list(); } });
  const kind = select([['members', T.onlyMembers], ['todo', T.todo], ['medical', T.medicalTodo], ['owing', T.owing], ['all', T.everyone]], filter.kind);
  kind.addEventListener('change', () => { filter.kind = kind.value; list(); });
  const ph = printHead('');
  function list() {
    const q = filter.text.trim().toLowerCase();
    const rows = people.filter((p) => (filter.kind === 'all' || (filter.kind === 'owing' ? p.owedCents > 0
      : filter.kind === 'medical' ? p.member && p.issues.some((i) => MEDICAL.includes(i)) : p.member && (filter.kind === 'members' || p.issues.length)))
      && (!q || [p.name, p.firstName, p.lastName, p.phone, p.email, p.fitpNumber, p.number].some((x) => (x || '').toLowerCase().includes(q))));
    shownPeople = rows;
    ph.querySelector('.what').textContent = T.members + ' · ' + kind.selectedOptions[0].textContent;
    put(body, rows.length ? h('div.panel', h('table',
      h('thead', h('tr', [T.name, T.phone, T.email, T.fitp, T.medical, T.status].map((x) => h('th', { scope: 'col' }, x)), h('th.num', { scope: 'col' }, T.owed))),
      h('tbody', rows.map((p) => h('tr.row', { tabindex: 0, onclick: () => editPerson(p), onkeydown: (e) => { if (e.key === 'Enter') editPerson(p); } },
        h('td', p.name), h('td.nowrap', p.phone), h('td', p.email), h('td', p.fitpNumber, p.ranking ? h('span.dim', ' · ' + p.ranking) : null), h('td', dateText(p.medicalUntil)),
        h('td', p.member ? (p.issues.length ? p.issues.map(issueTag) : h('span.tag.ok', T.inOrder)) : h('span.dim', T.notMember)),
        h('td.num', p.owedCents ? eur(p.owedCents) : '')))))) : h('div.panel', h('div.empty', people.length ? T.noResults : T.noPeople)));
  }
  put(main(), 
    h('div.head', h('h1', T.members), h('button', { onclick: () => window.print() }, T.print), h('button', { onclick: exportPeople }, T.exportCsv),
      h('button', { onclick: importDialog }, T.importCsv), h('button.primary', { onclick: () => editPerson(null) }, T.newMember)),
    ph,
    h('div.tools', search, kind), body);
  list();
}

function editPerson(p) {
  const v = p || { member: true };
  const inp = (k, type, extra) => h('input', { type: type || 'text', value: v[k] || '', ...extra });
  const f = {
    firstName: inp('firstName', 'text', { required: true, autocomplete: 'off' }), lastName: inp('lastName', 'text', { required: true, autocomplete: 'off' }), nickname: inp('nickname'),
    phone: inp('phone', 'tel'), email: inp('email', 'email'), birth: inp('birth', 'date'),
    since: inp('since', 'date'), number: inp('number'), fitpNumber: inp('fitpNumber'), fitpYear: h('input', { type: 'number', min: 2000, max: 2100, value: v.fitpYear || '' }), ranking: inp('ranking'),
    medicalUntil: inp('medicalUntil', 'date'), privacy: inp('privacy', 'date'), guardian: inp('guardian'), guardianPhone: inp('guardianPhone', 'tel'),
    note: h('textarea', { rows: 2, maxlength: 2000 }, v.note || ''),
  };
  const member = h('input', { type: 'checkbox', checked: v.member });
  const photos = h('input', { type: 'checkbox', checked: v.photosOk });
  const card = select([['', '—'], ['AGONIST', T.cardAgonist], ['NON_AGONIST', T.cardNon], ['OTHER', T.cardOther]], v.fitpCard || '');
  const med = select([['', '—'], ['AGONISTIC', T.medAgonistic], ['NON_AGONISTIC', T.medNon]], v.medical || '');
  const body = h('div.form',
    field(T.firstName, f.firstName), field(T.lastName, f.lastName), field(T.nickname, f.nickname), field(T.birth, f.birth), field(T.phone, f.phone), field(T.email, f.email),
    h('label.check.full', member, T.isMember),
    h('h3', T.memberData), field(T.since, f.since), field(T.number, f.number), field(T.fitp, f.fitpNumber), field(T.fitpCard, card), field(T.fitpYear, f.fitpYear), field(T.ranking, f.ranking),
    field(T.medicalKind, med), field(T.medical, f.medicalUntil), field(T.privacy, f.privacy), h('label.check', photos, T.photos),
    h('h3', T.guardianTitle), field(T.guardian, f.guardian), field(T.guardianPhone, f.guardianPhone),
    field(T.note, f.note, 'full'));
  dialog(p ? p.name : T.newMember, body, async () => {
    const out = { firstName: f.firstName.value, lastName: f.lastName.value, nickname: f.nickname.value, phone: f.phone.value, email: f.email.value, birth: f.birth.value || null, note: f.note.value,
      member: member.checked, since: f.since.value || null, number: f.number.value, fitpNumber: f.fitpNumber.value, fitpCard: card.value || null, fitpYear: f.fitpYear.value ? Number(f.fitpYear.value) : null,
      ranking: f.ranking.value, medical: med.value || null, medicalUntil: f.medicalUntil.value || null, privacy: f.privacy.value || null, photosOk: photos.checked, guardian: f.guardian.value, guardianPhone: f.guardianPhone.value };
    if (p) await api('PUT', '/people/' + encodeURIComponent(p.id), out); else await api('POST', '/people', out);
    toast(T.saved);
    await showPeople();
  });
}

/** Un CSV di Excel in italiano è spesso in Windows-1252, non in UTF-8. */
async function readFile(file) {
  const bytes = await file.arrayBuffer();
  try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); } catch (_) { return new TextDecoder('windows-1252').decode(bytes); }
}

function importDialog() {
  const input = h('input', { type: 'file', accept: '.csv,.txt,text/csv', 'aria-label': T.importCsv });
  const area = h('div');
  let text = null;
  input.addEventListener('change', async () => {
    text = null;
    put(area, );
    if (!input.files[0]) return;
    if (input.files[0].size > 900 * 1024) { put(area, h('div.err', T.err.TOO_BIG)); return; }
    try {
      const body = { text: await readFile(input.files[0]) };
      const r = await api('POST', '/people/import/preview', body);
      text = body.text;
      const good = r.rows.filter((x) => !x.error);
      put(area, 
        h('p', T.importSummary(good.filter((x) => !x.update).length, good.filter((x) => x.update).length, r.rows.length - good.length)),
        h('div.panel', h('table', h('thead', h('tr', [T.line, T.name, T.phone, T.email, T.birth, T.fitp, ''].map((x) => h('th', x)))),
          h('tbody', r.rows.slice(0, 200).map((x) => h('tr', h('td.dim', x.line), h('td', x.firstName + ' ' + x.lastName), h('td', x.phone), h('td', x.email), h('td', dateText(x.birth)), h('td', x.fitp),
            h('td', x.error ? h('span.tag.bad', T.rowErr[x.error]) : h('span.tag.' + (x.update ? 'warn' : 'ok'), x.update ? T.willUpdate : T.willAdd))))))));
    } catch (e) { put(area, h('div.err', e instanceof ApiError ? errText(e) : String(e))); }
  });
  dialog(T.importCsv, h('div', h('p.dim', T.importHint), input, area), async (err) => {
    if (text === null) { err.textContent = T.chooseFile; return false; }
    const r = await api('POST', '/people/import', { text });
    toast(T.imported(r.added, r.updated));
    await showPeople();
  }, { wide: true, saveText: T.importNow });
}

// --- Quote e pagamenti ---

let moneyYear = null;
let moneyFilter = { type: '', show: 'open', text: '' };

const STATUS = { PAID: 'ok', PARTIAL: 'warn', UNPAID: 'warn', LATE: 'bad', CANCELLED: 'dim' };

let lastMoney = null;
let shownCharges = [];

/**
 * Sollecito a chi deve qualcosa, solo per gli addebiti che si vedono (coi filtri): il PC prepara i messaggi,
 * il telefono li manda col suo WhatsApp, uno per uno.
 */
async function duesDialog(charges) {
  const ids = charges.filter((c) => c.leftCents > 0 && c.status !== 'CANCELLED').map((c) => c.id);
  let list = [];
  try { if (ids.length) list = await api('POST', '/dues', { ids }); } catch (e) { toast(errText(e)); return; }
  if (!list.length) { toast(T.duesNone); return; }
  const picks = list.map((d) => [d, h('input', { type: 'checkbox', checked: true })]);
  const dlg = dialog(T.duesButton + ' · ' + T.duesCount(list.length), h('div',
    h('p.dim', T.duesHint),
    h('div.dues', picks.map(([d, x]) => h('div.due',
      h('label.check', x, h('b', d.name), ' ', d.phone || d.email ? h('span.dim', [d.phone, d.email].filter(Boolean).join(' · ')) : h('span.tag.warn', T.noContact)),
      h('pre', d.text))))), async () => {
    const keep = new Set(picks.filter(([, x]) => x.checked).map(([d]) => d.id));
    const send = charges.filter((c) => keep.has(c.player) && ids.includes(c.id)).map((c) => c.id);
    if (!send.length) throw new Error(T.duesNone);
    await api('POST', '/dues/send', { ids: send });
    alert(T.duesSent);
  }, { saveText: T.duesSend, wide: true });
}

async function showMoney() {
  const m = await api('GET', '/money' + (moneyYear ? '?year=' + moneyYear : ''));
  lastMoney = m;
  moneyYear = m.year;
  const year = h('input', { type: 'number', min: 2000, max: 2100, value: m.year, 'aria-label': T.year, onchange: () => { moneyYear = Number(year.value) || m.year; showMoney(); } });
  year.classList.add('year');
  // Tipologia: i tipi di quota presenti, gli addebiti singoli, poi le quote una per una.
  const KINDS = [['COURT', T.kindCourt]].concat(FEE_KINDS());
  const types = [['', T.allTypes]].concat(KINDS.filter(([k]) => m.charges.some((c) => c.type === k)).map(([k, l]) => ['k:' + k, l]));
  m.fees.forEach((f) => types.push(['f:' + f.id, '› ' + f.name]));
  if (!types.some(([v]) => v === moneyFilter.type)) moneyFilter.type = '';
  const type = select(types, moneyFilter.type);
  type.setAttribute('aria-label', T.type);
  type.addEventListener('change', () => { moneyFilter.type = type.value; list(); });
  const show = select([['open', T.toCollect], ['late', T.chargeStatus.LATE], ['paid', T.paidOnes], ['all', T.allCharges]], moneyFilter.show);
  show.setAttribute('aria-label', T.status);
  show.addEventListener('change', () => { moneyFilter.show = show.value; list(); });
  const search = h('input', { type: 'search', placeholder: T.searchCharge, 'aria-label': T.searchCharge, value: moneyFilter.text, oninput: () => { moneyFilter.text = search.value; list(); } });
  const body = h('div');
  const pick = (f) => { moneyFilter.type = 'f:' + f.id; moneyFilter.show = 'all'; type.value = moneyFilter.type; show.value = 'all'; list(); window.scrollTo(0, 0); };
  function list() {
    const t = moneyFilter.type, q = moneyFilter.text.trim().toLowerCase();
    const rows = m.charges.filter((c) => (!t || (t.startsWith('k:') ? c.type === t.slice(2) : c.fee === t.slice(2)))
      && ({ open: c.leftCents > 0 && c.status !== 'CANCELLED', late: c.status === 'LATE', paid: c.status === 'PAID', all: true })[moneyFilter.show]
      && (!q || [c.name, c.label].some((x) => (x || '').toLowerCase().includes(q))));
    const left = rows.reduce((a, c) => a + (c.status === 'CANCELLED' ? 0 : c.leftCents), 0);
    shownCharges = rows;
    const filtered = t || q || moneyFilter.show !== 'open';
    put(body, 
      rows.length && filtered ? h('p.dim', T.shown(rows.length, eur(left))) : null,
      rows.length ? h('div.panel', h('table',
        h('thead', h('tr', [T.name, T.what, T.due, T.status].map((x) => h('th', { scope: 'col' }, x)), h('th.num', T.amount), h('th.num', T.left))),
        h('tbody', rows.map((c) => h('tr.row', { tabindex: 0, onclick: () => openCharge(c, m), onkeydown: (e) => { if (e.key === 'Enter') openCharge(c, m); } },
          h('td', c.name), h('td', c.label), h('td', dateText(c.due)), h('td', h('span.tag.' + STATUS[c.status], T.chargeStatus[c.status])), h('td.num', eur(c.cents)), h('td.num', eur(c.leftCents))))))) : h('div.panel', h('div.empty', filtered && m.charges.length ? T.noResults : T.noCharges)));
  }
  put(main(), 
    h('div.head', h('h1', T.money), h('button', { onclick: () => duesDialog(shownCharges) }, T.duesButton), h('button', { onclick: () => window.print() }, T.print), h('button', { onclick: () => exportCsv(m.year) }, T.exportCsv), h('button', { onclick: () => newCharge() }, T.newCharge), h('button.primary', { onclick: () => newFee() }, T.newFee)),
    h('div.kpis',
      h('div.kpi', h('b', eur(m.incomeCents)), h('span', T.income(m.year))),
      h('div.kpi', h('b', eur(m.openCents)), h('span', T.open)),
      h('div.kpi', h('b', m.openPeople), h('span', T.openPeople))),
    printHead(T.money),
    h('div.tools', search, type, show, h('label', T.year + ' ', year)),
    body,
    m.fees.length ? h('div.section', h('h2', T.fees), h('p.dim', T.feesHint), h('div.panel', h('table', h('thead', h('tr', h('th', T.name), h('th', T.due), h('th.num', T.amount), h('th.num', T.paidOf))),
      h('tbody', m.fees.map((f) => h('tr.row', { tabindex: 0, onclick: () => pick(f), onkeydown: (e) => { if (e.key === 'Enter') pick(f); } },
        h('td', f.name), h('td', dateText(f.due)), h('td.num', eur(f.cents)), h('td.num', f.paid + ' / ' + f.total))))))) : null);
  list();
}

function openCharge(c, m) {
  const amount = h('input', { value: (c.leftCents / 100).toFixed(2).replace('.', ','), inputmode: 'decimal', required: true });
  const date = h('input', { type: 'date', value: info.today, required: true });
  const type = select(m.payTypes.map((t) => [t.id, t.name]));
  const ref = h('input', { maxlength: 120 });
  const history = c.payments.length ? h('div.full', h('h2', T.payments), h('div.panel', h('table', h('tbody', c.payments.map((p) => h('tr',
    h('td', dateText(p.date)), h('td', p.method), h('td.dim', p.receipt), h('td.dim', p.ref), h('td.num', eur(p.cents)),
    h('td.num', h('button', { type: 'button', onclick: () => openPdf('/charges/' + encodeURIComponent(c.id) + '/payments/' + encodeURIComponent(p.id) + '/receipt',
      T.receipt + ' ' + p.receipt.replace('/', '-') + ' ' + c.name) }, T.receipt)),
    h('td.num', h('button.danger', { type: 'button', onclick: async () => {
      if (!confirm(T.removePaymentAsk)) return;
      try { await api('DELETE', '/charges/' + encodeURIComponent(c.id) + '/payments/' + encodeURIComponent(p.id)); d.close(); toast(T.saved); await showMoney(); } catch (e) { toast(errText(e)); }
    } }, T.remove)))))))) : null;
  const canPay = c.leftCents > 0;
  const form = h('div.form',
    h('p.full.dim', c.label + ' · ' + eur(c.cents)),
    canPay ? [field(T.amount, amount), field(T.date, date), field(T.payType, type), field(T.ref, ref)] : null,
    history);
  const cancel = c.status !== 'CANCELLED' && h('button.danger.left', { type: 'button', onclick: async () => {
    if (!confirm(T.cancelChargeAsk)) return;
    try { await api('POST', '/charges/' + encodeURIComponent(c.id) + '/cancel'); d.close(); toast(T.saved); await showMoney(); } catch (e) { toast(errText(e)); }
  } }, T.cancelCharge);
  const d = dialog(c.name, form, canPay ? async () => {
    const v = cents(amount.value);
    if (v === null) throw new Error(T.err.BAD_AMOUNT);
    await api('POST', '/charges/' + encodeURIComponent(c.id) + '/pay', { cents: v, date: date.value, type: type.value, ref: ref.value });
    toast(T.saved);
    await showMoney();
    // Riaperta subito: c'è il pagamento nuovo col tasto Ricevuta.
    const again = lastMoney && lastMoney.charges.find((x) => x.id === c.id);
    if (again) setTimeout(() => openCharge(again, lastMoney), 0);
  } : null, { saveText: T.register, left: cancel });
}

const FEE_KINDS = () => [['MEMBERSHIP', T.kindMembership], ['FITP', T.kindFitp], ['COURSE', T.kindCourse], ['TOURNAMENT', T.kindTournament], ['OTHER', T.kindOther]];

function newFee() {
  const name = h('input', { required: true, maxlength: 120 });
  const amount = h('input', { required: true, inputmode: 'decimal' });
  const due = h('input', { type: 'date' });
  const kind = select(FEE_KINDS(), 'MEMBERSHIP');
  const all = h('input', { type: 'checkbox', checked: true });
  dialog(T.newFee, h('div.form', field(T.name, name, 'full'), field(T.amount, amount), field(T.due, due), field(T.kind, kind), h('label.check.full', all, T.chargeAll)), async () => {
    const v = cents(amount.value);
    if (v === null) throw new Error(T.err.BAD_AMOUNT);
    const r = await api('POST', '/fees', { name: name.value, cents: v, kind: kind.value, due: due.value || null, chargeMembers: all.checked });
    toast(T.charged(r.count));
    await showMoney();
  });
}

async function newCharge() {
  people = await api('GET', '/people');
  const who = select(people.map((p) => [p.id, p.name]));
  const label = h('input', { required: true, maxlength: 120 });
  const amount = h('input', { required: true, inputmode: 'decimal' });
  const due = h('input', { type: 'date' });
  const kind = select(FEE_KINDS(), 'OTHER');
  dialog(T.newCharge, h('div.form', field(T.name, who, 'full'), field(T.what, label, 'full'), field(T.amount, amount), field(T.due, due), field(T.kind, kind)), async () => {
    const v = cents(amount.value);
    if (v === null) throw new Error(T.err.BAD_AMOUNT);
    await api('POST', '/charges', { player: who.value, label: label.value, cents: v, due: due.value || null, kind: kind.value });
    toast(T.saved);
    await showMoney();
  });
}

async function exportCsv(year) {
  try {
    const blob = await api('GET', '/payments.csv?year=' + year, undefined, true);
    const url = URL.createObjectURL(blob);
    const a = h('a', { href: url, download: T.csvName + ' ' + year + '.csv' });
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  } catch (e) { toast(errText(e)); }
}

// --- Planning ---

let planDate = null;
const ROW = 22;

let planView = 'day';
let planCourt = null;

/** Il lunedì della settimana di [d] e il primo del mese, come date "aaaa-mm-gg". */
function monday(d) { const t = new Date(d + 'T12:00:00'); return addDay(d, -((t.getDay() + 6) % 7)); }
function addMonth(d, n) { const t = new Date(d + 'T12:00:00'); t.setDate(1); t.setMonth(t.getMonth() + n); return addDay(t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-01', 0); }

async function showPlan() {
  if (!people.length) people = await api('GET', '/people');
  const step = { day: (n) => addDay(planDate, n), week: (n) => addDay(planDate, 7 * n), month: (n) => addMonth(planDate, n) }[planView];
  const date = h('input', { type: 'date', value: planDate, 'aria-label': T.date, onchange: () => { if (date.value) { planDate = date.value; showPlan(); } } });
  date.classList.add('year');
  const views = h('div.seg', { role: 'group', 'aria-label': T.planView }, [['day', T.viewDay], ['week', T.viewWeek], ['month', T.viewMonth]].map(([v, l]) =>
    h('button' + (v === planView ? '.on' : ''), { type: 'button', 'aria-pressed': v === planView ? 'true' : 'false', onclick: () => { planView = v; showPlan(); } }, l)));
  const head = (title, d, extra) => h('div.head', h('h1', title),
    h('button', { onclick: () => { planDate = step(-1); showPlan(); }, 'aria-label': T.prev, title: T.prev }, '←'),
    h('button', { onclick: () => { planDate = info.today; showPlan(); } }, T.today),
    h('button', { onclick: () => { planDate = step(1); showPlan(); }, 'aria-label': T.next, title: T.next }, '→'), date, views, extra,
    h('button', { onclick: () => blocksDialog(d) }, T.blkShort), h('button', { onclick: () => window.print() }, T.print));
  const long = (d, o) => new Date(d + 'T12:00:00').toLocaleDateString(locale(), o);

  if (planView === 'month') {
    const m = await api('GET', '/month?date=' + planDate);
    const title = long(m.first, { month: 'long', year: 'numeric' });
    const lead = (new Date(m.first + 'T12:00:00').getDay() + 6) % 7;
    const cells = [];
    for (let i = 0; i < lead; i++) cells.push(h('div.mday.out'));
    for (const x of m.days) {
      const n = Number(x.date.slice(8));
      cells.push(h('button.mday' + (x.date === info.today ? '.now' : '') + (x.blocked >= 100 ? '.closed' : ''), {
        type: 'button', title: long(x.date, { weekday: 'long', day: 'numeric', month: 'long' }), onclick: () => { planDate = x.date; planView = 'day'; showPlan(); },
      }, h('b', n),
      x.bookings ? h('span', T.nBookings(x.bookings)) : null,
      x.matches ? h('span', '🏆 ' + x.matches) : null,
      x.blocked >= 100 ? h('span.dim', T.blkShort) : x.blocked ? h('span.dim', T.blkShort + ' ' + x.blocked + '%') : null,
      h('i.bar', h('i', { style: 'width:' + x.busy + '%' }))));
    }
    const names = [1, 2, 3, 4, 5, 6, 7].map((k) => h('div.mhead', weekdayName(k)));
    put(main(), head(title, { courts: m.courts }), printHead(T.plan + ' · ' + title),
      h('div.panel', h('div.month', names, cells)), h('p.dim.noprint', T.monthHint));
    return;
  }

  // Giorno e settimana: la stessa griglia, una colonna per campo (e per giorno).
  let d, days, title;
  if (planView === 'week') {
    const w = await api('GET', '/week?date=' + planDate);
    d = w; days = w.days;
    title = long(w.from, { day: 'numeric', month: 'short' }) + ' – ' + long(addDay(w.from, 6), { day: 'numeric', month: 'short', year: 'numeric' });
  } else {
    d = await api('GET', '/day?date=' + planDate);
    days = [d];
    title = long(planDate, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }
  // In settimana con tanti campi se ne guarda uno alla volta, se no non si legge.
  let courts = d.courts;
  let pickCourt = null;
  if (planView === 'week' && d.courts.length > 3) {
    if (!d.courts.includes(planCourt)) planCourt = d.courts[0];
    courts = [planCourt];
    pickCourt = select(d.courts.map((c) => [c, c]), planCourt);
    pickCourt.setAttribute('aria-label', T.court);
    pickCourt.addEventListener('change', () => { planCourt = pickCourt.value; showPlan(); });
  }
  if (!d.courts.length) { put(main(), head(title, d), h('div.panel', h('div.empty', T.noCourts))); return; }
  const cols = days.flatMap((x) => courts.map((c) => ({ date: x.date, court: c })));
  const week = planView === 'week';
  const two = week && courts.length > 1;
  const heads = two ? 2 : 1;
  const slot = d.slotMinutes;
  const open = d.openHour * 60, close = d.closeHour * 60;
  const rows = Math.ceil((close - open) / slot);
  const grid = h('div.grid' + (week ? '.week' : ''));
  grid.style.gridTemplateColumns = '56px repeat(' + cols.length + ', minmax(' + (week ? (courts.length > 1 ? 54 : 96) : 110) + 'px, 1fr))';
  grid.style.gridTemplateRows = 'repeat(' + heads + ', auto) repeat(' + rows + ', ' + Math.max(ROW, Math.round(ROW * slot / 30)) + 'px)';
  const place = (e, row, col, span, colSpan) => { e.style.gridRow = row + ' / span ' + span; e.style.gridColumn = col + ' / span ' + (colSpan || 1); grid.append(e); };
  place(h('div'), 1, 1, heads);
  if (week) days.forEach((x, i) => place(h('button.gcourt.gday' + (x.date === info.today ? '.now' : ''), {
    type: 'button', title: T.viewDay, onclick: () => { planDate = x.date; planView = 'day'; showPlan(); },
  }, long(x.date, { weekday: 'short', day: 'numeric' })), 1, 2 + i * courts.length, 1, courts.length));
  if (!week || two) cols.forEach((c, i) => place(h('div.gcourt' + (two ? '.small' : ''), c.court), heads, i + 2, 1));
  for (let r = 0; r < rows; r++) {
    const start = open + r * slot;
    place(h('div.ghour', hhmm(start)), r + 1 + heads, 1, 1);
    cols.forEach((c, i) => place(h('button.gcell' + (week && i % courts.length === 0 ? '.dayline' : ''), {
      type: 'button', 'aria-label': c.date + ' ' + c.court + ' ' + hhmm(start), title: c.court + ' ' + hhmm(start),
      onclick: () => { planDate = c.date; bookingDialog(d, c.court, start, null); },
    }), r + 1 + heads, i + 2, 1));
  }
  for (const x of days) {
    const first = cols.findIndex((c) => c.date === x.date);
    for (const it of x.items) {
      const from = Math.max(0, Math.floor((it.startMin - open) / slot));
      const span = Math.max(1, Math.ceil((Math.min(it.endMin, close) - open) / slot) - from);
      if (from >= rows) continue;
      const all = it.court === '';
      const ci = all ? first : cols.findIndex((c) => c.date === x.date && c.court === it.court);
      if (ci < 0) continue;
      const what = it.type === 'BLOCK' ? it.label || blockReason(it.reason) : it.type === 'TOURNAMENT' ? it.label + ' · ' + it.note : it.label || (it.kind ? T.bookingKind[it.kind] : '');
      const text = [hhmm(it.startMin) + '–' + hhmm(it.endMin), what, it.series ? '↻' : ''].filter(Boolean).join(' ');
      const click = it.type === 'BOOKING' ? () => { planDate = x.date; bookingDialog(d, it.court, it.startMin, it); }
        : it.type === 'TOURNAMENT' ? () => go('cups', () => { cupId = it.cup; cupTab = 'matches'; cupOpenMatch = it.id; })
          : it.editable ? () => editBlock(d, it.id) : () => toast(T.blkManaged);
      const cls = 'button.gitem.' + it.type + (it.online ? '.online' : '');
      place(h(cls, { type: 'button', title: text, onclick: click }, week && courts.length > 1 ? (what || text) : text), from + 1 + heads, ci + 2, span, all ? courts.length : 1);
    }
  }
  put(main(), head(title, d, pickCourt), printHead(T.plan + ' · ' + title + (pickCourt ? ' · ' + planCourt : '')), h('div.panel', grid));
}

/** Una prenotazione: nuova (anche ogni settimana) o, con [it], da cambiare o cancellare. */
function bookingDialog(d, court, start, it) {
  const courtS = select(d.courts.map((c) => [c, c]), court);
  const times = [];
  for (let m = d.openHour * 60; m < d.closeHour * 60; m += d.slotMinutes) times.push([String(m), hhmm(m)]);
  if (!times.some(([v]) => v === String(start))) times.push([String(start), hhmm(start)]), times.sort((a, b) => a[0] - b[0]);
  const startS = select(times, String(start));
  const lengths = [];
  for (let m = d.slotMinutes; m <= 240; m += d.slotMinutes) lengths.push([String(m), m + ' min']);
  const was = it ? it.endMin - it.startMin : 0;
  if (was && !lengths.some(([v]) => v === String(was))) lengths.push([String(was), was + ' min']), lengths.sort((a, b) => a[0] - b[0]);
  const dur = select(lengths, String(was || (lengths.some(([v]) => v === '60') ? 60 : d.slotMinutes)));
  const kind = select(Object.entries(T.bookingKind), it ? it.kind : 'PLAY');
  const price = h('span');
  const who = h('input', { list: 'desk-people', placeholder: T.addPlayer });
  const list = h('datalist', { id: 'desk-people' }, people.map((p) => h('option', { value: p.name })));
  const chips = h('div.chips full');
  const picked = it ? it.players.map((id) => ({ id, name: (people.find((p) => p.id === id) || {}).name || id })).concat(it.names.map((n) => ({ name: n }))) : [];
  function add() {
    const n = who.value.trim();
    if (!n) return;
    picked.push({ name: n, id: (people.find((p) => p.name === n) || {}).id });
    who.value = '';
    drawChips();
  }
  function drawChips() { put(chips, ...picked.map((p, i) => h('button.chip', { type: 'button', title: T.remove, onclick: () => { picked.splice(i, 1); drawChips(); } }, p.name + ' ×'))); }
  drawChips();
  who.addEventListener('change', add);
  const charge = h('input', { type: 'checkbox', checked: it && it.charged });
  const note = h('input', { maxlength: 500, value: it ? it.note : '' });
  const weeks = select([['1', T.onlyOnce]].concat([2, 4, 8, 12, 16, 26, 52].map((n) => [String(n), T.weeksN(n)])), '1');
  async function quote() {
    try {
      const q = await api('GET', '/quote?date=' + planDate + '&court=' + encodeURIComponent(courtS.value) + '&startMin=' + startS.value + '&minutes=' + dur.value + '&kind=' + kind.value);
      price.textContent = eur(q.cents) + (q.lights ? ' · ' + T.lights : '');
    } catch (_) { price.textContent = ''; }
  }
  [courtS, startS, dur, kind].forEach((e) => e.addEventListener('change', quote));
  quote();
  const remove = it && h('button.danger.left', { type: 'button', onclick: () => deleteBooking(it, dlg) }, T.deleteBooking);
  const dlg = dialog((it ? it.court + ' · ' + hhmm(it.startMin) + '–' + hhmm(it.endMin) : T.newBooking) + ' · ' + dateText(planDate), h('div.form', list,
    field(T.court, courtS), field(T.start, startS), field(T.duration, dur), field(T.kind, kind),
    h('label.full', T.players, who), chips, field(T.note, note, 'full'),
    it ? (it.series ? h('p.full.dim', T.seriesEditHint) : null) : field(T.repeat, weeks, 'full'),
    it && it.online ? h('p.full.dim', T.onlineEditHint) : null,
    h('label.check.full', charge, T.chargeSplit), h('div.full', T.price + ': ', price)), async () => {
    add();
    const body = { court: courtS.value, date: planDate, startMin: Number(startS.value), minutes: Number(dur.value), kind: kind.value,
      players: picked.filter((p) => p.id).map((p) => p.id), names: picked.filter((p) => !p.id).map((p) => p.name), note: note.value, charge: charge.checked,
      weeks: Number(weeks.value) };
    const out = it ? await api('PUT', '/bookings/' + encodeURIComponent(it.id), body) : await api('POST', '/bookings', body);
    toast(T.saved);
    await showPlan();
    if (out.skipped && out.skipped.length) alert(T.skippedDays(out.skipped.map(dateText).join(', ')));
  }, { left: remove });
}

/** Cancella: da sola o, se è ripetuta ogni settimana, anche con quelle dopo. */
function deleteBooking(it, parent) {
  const run = async (series) => {
    try {
      await api('DELETE', '/bookings/' + encodeURIComponent(it.id) + (series ? '?series=1' : ''));
      ask.close(); parent.close(); toast(T.saved); await showPlan();
    } catch (e) { toast(errText(e)); }
  };
  const ask = dialog(T.deleteBooking, h('p', it.series ? T.deleteSeriesAsk : T.deleteBookingAsk), null, {
    left: it.series ? [h('button.danger.left', { type: 'button', onclick: () => run(false) }, T.onlyThis), h('button.danger', { type: 'button', onclick: () => run(true) }, T.thisAndNext)]
      : h('button.danger.left', { type: 'button', onclick: () => run(false) }, T.deleteBooking),
  });
}

// --- Campi non prenotabili ---

const blockReason = (r) => ({ COURSE: T.blkCourse, TOURNAMENT: T.blkTournament, MAINTENANCE: T.blkMaintenance, OTHER: T.bkOther })[r] || '';
/** Lunedì = 1, come nell'app. */
const weekdayName = (n) => new Date(2024, 0, n, 12).toLocaleDateString(locale(), { weekday: 'short' });
const blockLine = (b) => [blockReason(b.reason), b.weekdays.length ? b.weekdays.map(weekdayName).join(' ') : T.blkEveryDay,
  b.startMin === 0 && b.endMin === 1440 ? T.blkAllDay : hhmm(b.startMin) + '–' + hhmm(b.endMin), b.courts.length ? b.courts.join(', ') : T.blkAllCourts,
  b.from === b.to ? dateText(b.from) : dateText(b.from) + ' – ' + dateText(b.to)].join(' · ');

async function blocksDialog(d) {
  let list;
  try { list = await api('GET', '/blocks'); } catch (e) { toast(errText(e)); return; }
  const dlg = dialog(T.blkTitle, h('div',
    h('p.dim', T.blkHint),
    list.length ? h('div.panel', h('table', h('tbody', list.map((b) => h('tr' + (b.editable ? '.row' : ''), b.editable ? { tabindex: 0, onclick: () => { dlg.close(); blockDialog(d, b); } } : {},
      h('td', h('b', b.label || blockReason(b.reason)), h('div.dim', blockLine(b)), b.editable ? null : h('div.dim', T.blkManaged))))))) : h('p.dim', T.blkNone)),
  null, { left: h('button.primary.left', { type: 'button', onclick: () => { dlg.close(); blockDialog(d, null); } }, T.blkNew), wide: true });
}

async function editBlock(d, id) {
  try { const b = (await api('GET', '/blocks')).find((x) => x.id === id); if (b) blockDialog(d, b); } catch (e) { toast(errText(e)); }
}

function blockDialog(d, b) {
  const v = b || { label: '', reason: 'MAINTENANCE', courts: [], from: planDate, to: planDate, weekdays: [], startMin: 0, endMin: 1440 };
  const label = h('input', { maxlength: 40, value: v.label });
  const reason = select([['MAINTENANCE', T.blkMaintenance], ['TOURNAMENT', T.blkTournament], ['OTHER', T.bkOther]], v.reason);
  const all = h('input', { type: 'checkbox', checked: !v.courts.length });
  const courts = d.courts.map((c) => [c, h('input', { type: 'checkbox', checked: v.courts.includes(c) })]);
  const courtBox = h('div.full.checks', courts.map(([c, x]) => h('label.check', x, c)));
  const from = h('input', { type: 'date', value: v.from, required: true }), to = h('input', { type: 'date', value: v.to, required: true });
  const days = [1, 2, 3, 4, 5, 6, 7].map((n) => [n, h('input', { type: 'checkbox', checked: v.weekdays.includes(n) })]);
  const allDay = h('input', { type: 'checkbox', checked: v.startMin === 0 && v.endMin === 1440 });
  const t1 = h('input', { type: 'time', value: hhmm(v.startMin === 0 && v.endMin === 1440 ? 15 * 60 : v.startMin), step: 300 });
  const t2 = h('input', { type: 'time', value: hhmm(v.startMin === 0 && v.endMin === 1440 ? 18 * 60 : Math.min(v.endMin, 1439)), step: 300 });
  const f1 = field(T.blkStart, t1), f2 = field(T.blkEnd, t2);
  function update() { courtBox.hidden = all.checked; f1.hidden = f2.hidden = allDay.checked; }
  all.addEventListener('change', update); allDay.addEventListener('change', update); update();
  const del = b && h('button.danger.left', { type: 'button', onclick: async () => {
    try { await api('DELETE', '/blocks/' + encodeURIComponent(b.id)); dlg.close(); toast(T.saved); await showPlan(); } catch (e) { toast(errText(e)); }
  } }, T.blkDelete);
  const dlg = dialog(b ? b.label || T.blkTitle : T.blkNew, h('div.form',
    field(T.blkLabel, label, 'full'), field(T.kind, reason), h('span'),
    h('label.check.full', all, T.blkAllCourts), courtBox,
    field(T.blkFrom, from), field(T.blkTo, to),
    h('h3', T.blkDays), h('div.full.checks', days.map(([n, x]) => h('label.check', x, weekdayName(n)))), h('p.full.dim', T.blkDaysHint),
    h('label.check.full', allDay, T.blkAllDay), f1, f2), async () => {
    const pick = courts.filter(([, x]) => x.checked).map(([c]) => c);
    const body = {
      id: b ? b.id : '', label: label.value, reason: reason.value, courts: all.checked ? [] : pick, from: from.value, to: to.value,
      weekdays: days.filter(([, x]) => x.checked).map(([n]) => n),
      startMin: allDay.checked ? 0 : toMin(t1.value || '00:00'), endMin: allDay.checked ? 1440 : toMin(t2.value || '00:00'),
    };
    if (!all.checked && !pick.length) throw new Error(T.blkPickCourt);
    await api('POST', '/blocks', body);
    toast(T.saved);
    await showPlan();
  }, { left: del });
}

// --- Tornei ---

// --- Tornei ---

let cupId = null;
let cupTab = 'entries';
let cupShow = 'todo';
let cupRun = 0;
/** La partita da aprire appena si apre il torneo (dal Planning). */
let cupOpenMatch = null;

const PROBLEM = () => ({
  NOT_MEMBER: T.ctNotMember, NO_MEDICAL: T.ctNoMedical, MEDICAL_NOT_AGONISTIC: T.ctMedicalNotAgonistic, NO_FITP_CARD: T.ctNoFitpCard,
  NOT_AGONIST_CARD: T.ctNotAgonistCard, RANK_TOO_HIGH: T.ctRankTooHigh, UNPAID: T.ctUnpaid, NO_PLAYER: T.cupNoPlayer,
});
const groupLetter = (g) => String.fromCharCode(65 + g);
const cupDates = (t) => (t.start ? T.ctDates(dateText(t.start), dateText(t.end || t.start)) : '');
const cupLine = (t) => [t.category, t.kind === 'FITP' ? 'FITP' : T.ctSocial, t.doubles ? T.ctDoubles : T.ctSingles,
  t.format === 'GROUPS' ? T.ctFormatGroups : T.ctFormatKnockout, cupDates(t)].filter(Boolean).join(' · ');
const toMin = (v) => { const [a, b] = v.split(':').map(Number); return a * 60 + (b || 0); };
const shortDay = (d) => new Date(d + 'T12:00:00').toLocaleDateString(locale(), { weekday: 'short', day: '2-digit', month: '2-digit' });

/** Il nome del turno, come nell'app: dal numero di giocatori rimasti. */
function roundName(t, m) {
  if (m.round === 0) return T.ctGroup(groupLetter(m.group || 0));
  const n = t.matches.filter((x) => x.round === m.round).length * 2;
  return ({ 2: T.ctFinal, 4: T.ctSemi, 8: T.ctQuarter, 16: T.ctLast16, 32: T.ctLast32 })[n] || T.ctRoundN(m.round);
}

/** Chi ha vinto secondo il punteggio, come nell'app: più set, a pari set più game. */
function winnerOf(score) {
  const sets = [...score.replace(/\(\d+\)/g, '').matchAll(/(\d{1,2})\s*[-/]\s*(\d{1,2})/g)].map((x) => [Number(x[1]), Number(x[2])]);
  if (!sets.length) return null;
  const a = sets.filter(([x, y]) => x > y).length, b = sets.filter(([x, y]) => y > x).length;
  if (a !== b) return a > b ? 'a' : 'b';
  const ga = sets.reduce((n, [x]) => n + x, 0), gb = sets.reduce((n, [, y]) => n + y, 0);
  return ga === gb ? null : ga > gb ? 'a' : 'b';
}

const scoreText = (m) => [m.score, m.walkover ? T.ctWalkover : '', m.retired ? T.ctRetired : ''].filter(Boolean).join(' ');
const whenText = (m) => (m.date ? [shortDay(m.date), m.startMin != null ? hhmm(m.startMin) : '', m.court].filter(Boolean).join(' · ') : '');

async function showCups() {
  if (cupId) return showCup(cupId);
  const list = await api('GET', '/tournaments');
  put(main(), h('div.head', h('h1', T.cups), h('button.primary', { onclick: () => editCup(null) }, T.ctNew)), h('p.dim', T.cupsNote),
    list.length ? h('div.cards', list.map((t) => h('button.card', { type: 'button', onclick: () => { cupId = t.id; cupTab = t.drawn ? 'draw' : 'entries'; showCup(t.id); } },
      h('h2', t.name), h('p.dim', cupLine(t)),
      h('p', T.ctEntriesCount(t.entries.length) + (t.matches ? ' · ' + T.cupPlayed(t.played, t.matches) : '')),
      h('span.tag.' + (t.champion ? 'ok' : t.drawn ? 'warn' : 'dim'), t.champion ? T.ctChampion(t.champion) : t.drawn ? T.ctRunning : T.ctOpen)))) : h('div.panel', h('div.empty', T.ctNone)));
}

/** Una chiamata sul torneo aperto, poi la scheda si ridisegna; [onOk] solo se è andata. */
async function cupDo(method, path, body, onOk) {
  let ok = true;
  try { await api(method, '/tournaments/' + encodeURIComponent(cupId) + path, body); toast(T.saved); if (onOk) onOk(); }
  catch (e) { ok = false; toast(e instanceof ApiError ? errText(e) : String(e.message || e)); }
  await showCup(cupId);
  return ok;
}

async function showCup(id) {
  // Se nel frattempo è partito un altro aggiornamento, vince quello.
  const run = ++cupRun;
  let t;
  try { t = await api('GET', '/tournaments/' + encodeURIComponent(id)); }
  catch (e) { if (e instanceof ApiError && e.message === 'NOT_FOUND') { cupId = null; return showCups(); } throw e; }
  if (cupTab === 'entries') people = await api('GET', '/people');
  if (run !== cupRun || view !== 'cups') return;
  cupId = t.id;
  const names = Object.fromEntries(t.entries.map((e) => [e.id, e.names.join(' / ')]));
  const who = (x) => (x ? names[x] || '?' : '');
  const tabs = [['entries', T.ctTabEntries + ' (' + t.entries.length + ')'], ['draw', T.ctTabDraw], ['matches', T.ctTabMatches]];
  const extra = [t.feeCents ? T.ctFeeLine(eur(t.feeCents)) + (t.requirePaid ? ' (' + T.ctPaidOnlyShort + ')' : '') : '', t.rankLimit ? T.ctLimitShort(t.rankLimit) : '',
    t.fitpCode ? T.ctFitpCodeLine(t.fitpCode) : '', t.referee ? T.ctRefereeLine(t.referee) : ''].filter(Boolean).join(' · ');
  const body = h('div');
  put(main(), 
    h('div.head', h('button', { onclick: () => { cupId = null; showCups(); }, 'aria-label': T.cupsBack, title: T.cupsBack }, '←'), h('h1', t.name),
      h('button', { onclick: () => window.print() }, T.print), h('button', { onclick: () => editCup(t) }, T.edit)),
    h('p.dim', cupLine(t), extra ? h('br') : null, extra),
    t.champion ? h('p', h('span.tag.ok', T.ctChampion(who(t.champion)))) : null,
    h('h2.printonly', Object.fromEntries(tabs)[cupTab]),
    h('div.tabs', { role: 'tablist' }, tabs.map(([v, l]) => h('button' + (v === cupTab ? '.on' : ''), {
      type: 'button', role: 'tab', 'aria-selected': v === cupTab ? 'true' : 'false', onclick: () => { cupTab = v; showCup(t.id); },
    }, l))),
    body);
  ({ entries: cupEntries, draw: cupDraw, matches: cupMatches })[cupTab](t, body, who);
  const m = cupOpenMatch && t.matches.find((x) => x.id === cupOpenMatch);
  cupOpenMatch = null;
  if (m && m.a && m.b) openMatch(t, m, who);
}

/** Iscritti: si cerca il giocatore e lo si iscrive; i problemi si vedono subito; poi il sorteggio. */
function cupEntries(t, body) {
  const need = t.doubles ? 2 : 1;
  const picked = [];
  const inside = new Set(t.entries.flatMap((e) => e.players));
  const inDraw = new Set(t.matches.flatMap((m) => [m.a, m.b]));
  const input = h('input', { type: 'search', placeholder: t.doubles ? T.cupPickPair : T.cupPick, 'aria-label': T.ctAddEntry, autocomplete: 'off' });
  const hits = h('div.hits');
  const chosen = h('div.chips');
  function refresh() {
    const q = input.value.trim().toLowerCase();
    put(chosen, ...picked.map((p) => h('span.chip', p.name)));
    const list = q ? people.filter((p) => !inside.has(p.id) && !picked.includes(p) && p.name.toLowerCase().includes(q)).slice(0, 8) : [];
    put(hits, ...list.map((p) => h('button', { type: 'button', onclick: () => choose(p) },
      p.name, p.ranking ? h('span.dim', ' · ' + p.ranking) : null, p.member ? null : h('span.dim', ' · ' + T.notMember))));
    if (q && !list.length) hits.append(h('div.dim.pad', T.noResults));
  }
  async function choose(p) {
    picked.push(p);
    input.value = '';
    if (picked.length < need) { input.placeholder = T.ctPickPartner(picked[0].name); refresh(); input.focus(); return; }
    const players = picked.splice(0).map((x) => x.id);
    input.disabled = true;
    await cupDo('POST', '/entries', { players });
    const again = document.querySelector('.picker input');
    if (again) again.focus();
  }
  input.addEventListener('input', refresh);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); const b = hits.querySelector('button'); if (b) b.click(); } });

  const rows = t.entries.map((e, i) => {
    const seed = h('input.seed', { type: 'number', min: 1, max: 64, value: e.seed || '', placeholder: T.ctSeedAuto, 'aria-label': T.cupSeed, disabled: t.drawn });
    seed.addEventListener('change', () => cupDo('PUT', '/entries/' + encodeURIComponent(e.id) + '/seed', { seed: Number(seed.value) || null }));
    const name = e.names.join(' / ');
    return h('tr', h('td.num', i + 1), h('td', name), h('td', seed),
      h('td', e.problems.length ? e.problems.map((p) => h('span.tag.' + (p.blocking ? 'bad' : 'warn'), (t.doubles ? p.name + ': ' : '') + (PROBLEM()[p.code] || p.code))) : h('span.tag.ok', T.ctInOrder)),
      h('td.num.noprint', inDraw.has(e.id) ? null : h('button.danger', { type: 'button', onclick: async () => {
        if (confirm(T.cupRemoveAsk(name))) await cupDo('DELETE', '/entries/' + encodeURIComponent(e.id));
      } }, T.ctRemoveEntry)));
  });

  const excluded = t.entries.length - t.eligible;
  const draw = !t.drawn
    ? [h('p', T.ctDrawAsk(t.eligible)), excluded > 0 ? h('p.dim', T.ctDrawExcluded(excluded)) : null,
      t.eligible < 2 ? h('p.dim', T.ctDrawTooFew) : h('button.primary', { type: 'button', onclick: () => cupDo('POST', '/draw', undefined, () => { cupTab = 'draw'; }) }, T.ctDraw)]
    : t.hasResults ? h('p.dim', T.cupDrawnLocked)
      : [h('p.dim', T.cupRedrawHint), h('button', { type: 'button', onclick: async () => { if (confirm(T.cupRedrawAsk)) await cupDo('POST', '/undraw'); } }, T.ctUndoDraw)];

  put(body, 
    h('div.picker.noprint', input, chosen, hits),
    t.entries.length ? h('div.panel', h('table',
      h('thead', h('tr', h('th.num', '#'), h('th', T.name), h('th', T.cupSeed), h('th', T.status), h('th.noprint'))),
      h('tbody', rows))) : h('div.panel', h('div.empty', T.ctNoEntries)),
    t.entries.length && !t.drawn ? h('p.dim.noprint', T.ctSeedHint) : null,
    h('div.section.noprint', h('h2', T.ctDraw), h('div.panel', h('div.pad', draw))));
  refresh();
}

/** Una partita del tabellone: chi gioca, chi ha vinto, punteggio, e dove e quando. */
function matchBox(t, m, who) {
  const pl = (x) => h('div.pl' + (m.winner && m.winner === x ? '.win' : ''), x ? who(x) : (m.round === 1 ? T.ctBye : '…'));
  const meta = [scoreText(m), m.winner ? '' : whenText(m)].filter(Boolean).join(' · ');
  const kids = [pl(m.a), pl(m.b), meta ? h('div.meta', meta) : null];
  return m.a && m.b ? h('button.match', { type: 'button', onclick: () => openMatch(t, m, who) }, kids) : h('div.match', kids);
}

function cupDraw(t, body, who) {
  if (!t.drawn) { put(body, h('div.panel', h('div.empty', T.ctNotDrawn))); return; }
  const out = [];
  const ko = t.matches.filter((m) => m.round > 0);
  if (t.format === 'GROUPS') {
    const groups = t.matches.filter((m) => m.round === 0);
    const done = groups.length > 0 && groups.every((m) => m.winner);
    out.push(h('div.cards', t.standings.map((st, g) => h('div.panel', h('div.pad',
      h('h2', T.ctGroup(groupLetter(g))),
      h('table', h('thead', h('tr', h('th', T.name), h('th.num', T.ctColWon), h('th.num', T.ctColSets), h('th.num', T.ctColGames))),
        h('tbody', st.map((r, i) => h('tr' + (i < t.qualify ? '.pass' : ''), h('td', who(r.entry)), h('td.num', r.won + '/' + r.played),
          h('td.num', r.setsWon + '-' + r.setsLost), h('td.num', r.gamesWon + '-' + r.gamesLost))))),
      h('div.gmatches', groups.filter((m) => m.group === g).map((m) => matchBox(t, m, who))))))));
    if (!ko.length) out.push(done
      ? h('div.section.noprint', h('button.primary', { type: 'button', onclick: () => cupDo('POST', '/knockout') }, T.ctMakeKnockout))
      : h('p.dim', T.ctGroupsFirst));
  }
  if (ko.length) {
    const rounds = [...new Set(ko.map((m) => m.round))].sort((a, b) => a - b);
    out.push(h('div.section.panel', h('div.bracket', rounds.map((r) => h('div.round',
      h('div.rname', roundName(t, ko.find((m) => m.round === r))),
      h('div.rmatches', ko.filter((m) => m.round === r).sort((a, b) => a.slot - b.slot).map((m) => matchBox(t, m, who))))))));
  }
  put(body, ...out);
}

/** Partite: il programma per giorno e ora, poi quelle ancora da programmare. */
function cupMatches(t, body, who) {
  const show = select([['todo', T.ctToPlay], ['played', T.ctPlayed], ['all', T.cupAll]], cupShow);
  show.setAttribute('aria-label', T.status);
  show.addEventListener('change', () => { cupShow = show.value; cupMatches(t, body, who); });
  const real = t.matches.filter((m) => !m.bye && (m.a || m.b));
  const rows = real.filter((m) => ({ todo: !m.winner, played: !!m.winner, all: true })[cupShow])
    .sort((x, y) => (x.date ? 0 : 1) - (y.date ? 0 : 1) || (x.date || '').localeCompare(y.date || '') || (x.startMin ?? 0) - (y.startMin ?? 0) || x.round - y.round || x.slot - y.slot);
  put(body, 
    h('div.tools.noprint', show, h('span.dim', T.cupMatchHint)),
    rows.length ? h('div.panel', h('table',
      h('thead', h('tr', [T.cupWhen, T.ctTime, T.ctCourt, T.cupRound, T.cupMatch, T.cupResult].map((x) => h('th', { scope: 'col' }, x)))),
      h('tbody', rows.map((m) => {
        const can = m.a && m.b;
        return h('tr' + (can ? '.row' : ''), can ? { tabindex: 0, onclick: () => openMatch(t, m, who), onkeydown: (e) => { if (e.key === 'Enter') openMatch(t, m, who); } } : {},
          h('td', m.date ? shortDay(m.date) : h('span.dim', T.cupUnscheduled)), h('td', m.startMin != null && m.date ? hhmm(m.startMin) : ''), h('td', m.court),
          h('td', roundName(t, m)),
          h('td', h('span' + (m.winner === m.a ? '.b' : ''), who(m.a) || '…'), ' – ', h('span' + (m.winner === m.b ? '.b' : ''), who(m.b) || '…')),
          h('td', scoreText(m)));
      })))) : h('div.panel', h('div.empty', real.length ? T.noResults : T.ctNotDrawn)));
}

function openMatch(t, m, who) {
  const wa = h('input', { type: 'radio', name: 'winner', checked: m.winner === m.a });
  const wb = h('input', { type: 'radio', name: 'winner', checked: m.winner === m.b });
  const score = h('input', { value: m.score, placeholder: '6-4 3-6 10-8', autocomplete: 'off', maxlength: 60 });
  score.addEventListener('input', () => { const w = winnerOf(score.value); if (w) (w === 'a' ? wa : wb).checked = true; });
  const wo = h('input', { type: 'checkbox', checked: m.walkover });
  const rit = h('input', { type: 'checkbox', checked: m.retired });
  const court = select([['', '—']].concat(t.courts.map((c) => [c, c])), m.court);
  const date = h('input', { type: 'date', value: m.date || '' });
  const time = h('input', { type: 'time', value: m.date && m.startMin != null ? hhmm(m.startMin) : '', step: 300 });
  const base = '/tournaments/' + encodeURIComponent(t.id) + '/matches/' + encodeURIComponent(m.id);
  const clear = m.winner && h('button.danger.left', { type: 'button', onclick: async () => {
    try { await api('POST', base + '/result', {}); d.close(); toast(T.saved); await showCup(t.id); } catch (e) { toast(errText(e)); }
  } }, T.ctClearResult);
  const d = dialog(roundName(t, m) + ': ' + who(m.a) + ' – ' + who(m.b), h('div.form',
    h('h3', T.ctWinner), h('label.check.full', wa, who(m.a)), h('label.check.full', wb, who(m.b)),
    field(T.ctScore, score, 'full'), h('label.check', wo, T.ctWalkoverLong), h('label.check', rit, T.ctRetiredLong),
    h('h3', T.ctWhere), field(T.date, date), field(T.ctTime, time), field(T.ctCourt, court),
    h('p.full.dim', T.cupWhereHint(t.matchMinutes))), async () => {
    const w = wa.checked ? m.a : wb.checked ? m.b : null;
    const sched = { court: court.value, date: date.value || null, startMin: time.value ? toMin(time.value) : null };
    if (sched.date && sched.startMin === null) throw new Error(T.err.BAD_TIME);
    if (sched.court !== m.court || sched.date !== (m.date || null) || (sched.date && sched.startMin !== m.startMin)) {
      try { await api('POST', base + '/schedule', sched); }
      catch (e) {
        if (!(e instanceof ApiError && e.message === 'CONFLICT') || !confirm(T.cupBusyAsk)) throw e;
        await api('POST', base + '/schedule', { ...sched, force: true });
      }
    }
    const changed = w !== (m.winner || null) || score.value.trim() !== m.score || wo.checked !== m.walkover || rit.checked !== m.retired;
    if (changed && (w || score.value.trim())) await api('POST', base + '/result', { winner: w, score: score.value, walkover: wo.checked, retired: rit.checked });
    toast(T.saved);
    await showCup(t.id);
  }, { left: clear });
}

function editCup(t) {
  const v = t || { kind: 'SOCIAL', format: 'KNOCKOUT', doubles: false, groups: 2, qualify: 2, requirePaid: true, feeCents: 0 };
  const locked = !!(t && t.drawn);
  const inp = (k, extra) => h('input', { value: v[k] || '', ...extra });
  const name = inp('name', { required: true, maxlength: 120 });
  const category = inp('category', { maxlength: 80 });
  const kind = select([['SOCIAL', T.ctSocial], ['FITP', 'FITP']], v.kind);
  const format = select([['KNOCKOUT', T.ctFormatKnockout], ['GROUPS', T.ctFormatGroups]], v.format);
  const doubles = select([['0', T.ctSingles], ['1', T.ctDoubles]], v.doubles ? '1' : '0');
  format.disabled = locked; doubles.disabled = locked;
  const start = inp('start', { type: 'date' }), end = inp('end', { type: 'date' });
  const fee = h('input', { inputmode: 'decimal', value: v.feeCents ? (v.feeCents / 100).toFixed(2).replace('.', ',') : '' });
  const paid = h('input', { type: 'checkbox', checked: v.requirePaid });
  const limit = inp('rankLimit', { maxlength: 10 });
  const code = inp('fitpCode', { maxlength: 40 }), referee = inp('referee', { maxlength: 80 });
  const groups = h('input', { type: 'number', min: 1, max: 16, value: v.groups, disabled: locked });
  const qualify = h('input', { type: 'number', min: 1, max: 8, value: v.qualify });
  const hint = h('p.full.dim');
  const fCode = field(T.ctFitpCode, code), fRef = field(T.ctReferee, referee), fGroups = field(T.ctGroupsCount, groups), fQualify = field(T.ctQualify, qualify);
  const paidRow = h('label.check.full', paid, T.ctPaidOnly);
  function update() {
    hint.textContent = kind.value === 'FITP' ? T.ctFitpHint : T.ctSocialHint;
    fCode.hidden = fRef.hidden = kind.value !== 'FITP';
    fGroups.hidden = fQualify.hidden = format.value !== 'GROUPS';
  }
  kind.addEventListener('change', update);
  format.addEventListener('change', update);
  update();
  const del = t && h('button.danger.left', { type: 'button', onclick: async () => {
    if (!confirm(T.ctDeleteAsk(t.name))) return;
    try { await api('DELETE', '/tournaments/' + encodeURIComponent(t.id)); d.close(); cupId = null; toast(T.saved); await showCups(); } catch (e) { toast(errText(e)); }
  } }, T.ctDelete);
  const d = dialog(t ? t.name : T.ctNew, h('div.form',
    field(T.ctName, name, 'full'), field(T.ctCategory, category, 'full'),
    field(T.kind, kind), field(T.cupFormat, format), hint, field(T.cupPlay, doubles), h('span'),
    fGroups, fQualify, field(T.ctStart, start), field(T.ctEnd, end),
    field(T.ctFee, fee), field(T.ctLimit, limit), paidRow, h('p.full.dim', T.ctPaidOnlyHint), fCode, fRef,
    locked ? h('p.full.dim', T.cupLocked) : null), async () => {
    const fc = fee.value.trim() ? cents(fee.value) : 0;
    if (fc === null) throw new Error(T.err.BAD_AMOUNT);
    const body = {
      name: name.value, kind: kind.value, format: format.value, doubles: doubles.value === '1', category: category.value, start: start.value || null, end: end.value || null,
      feeCents: fc, requirePaid: paid.checked, rankLimit: limit.value, fitpCode: code.value, referee: referee.value, groups: Number(groups.value) || 2, qualify: Number(qualify.value) || 2,
    };
    if (t) await api('PUT', '/tournaments/' + encodeURIComponent(t.id), body);
    else { const r = await api('POST', '/tournaments', body); cupId = r.id; cupTab = 'entries'; }
    toast(T.saved);
    await showCup(cupId);
  }, { left: del });
}

// --- Statistiche dei campi ---

let statsPeriod = 'w4';
let statsFrom = null, statsTo = null;

/** Il periodo scelto: le ultime 4 settimane, questo mese, il mese scorso, quest'anno, o dal–al. */
function statsRange() {
  const t = info.today, first = t.slice(0, 8) + '01';
  if (statsPeriod === 'month') return [first, t];
  if (statsPeriod === 'last') { const f = addMonth(t, -1); return [f, addDay(first, -1)]; }
  if (statsPeriod === 'year') return [t.slice(0, 4) + '-01-01', t];
  if (statsPeriod === 'custom') return [statsFrom || first, statsTo || t];
  return [addDay(t, -27), t];
}

async function showStats() {
  const [a, b] = statsRange();
  const month = (d) => new Date(d + 'T12:00:00').toLocaleDateString(locale(), { month: 'long' });
  const period = select([['w4', T.stLast4w], ['month', month(info.today)], ['last', month(addMonth(info.today, -1))], ['year', T.thisYear], ['custom', T.otherPeriod]], statsPeriod);
  period.setAttribute('aria-label', T.periodLabel);
  period.addEventListener('change', () => { statsPeriod = period.value; if (statsPeriod === 'custom') { statsFrom = a; statsTo = b; } showStats(); });
  // Le date si scrivono con calma e valgono con Mostra (o Invio): niente ricarica a metà, mentre si scrive l'anno.
  const from = h('input', { type: 'date', value: a, 'aria-label': T.blkFrom });
  const to = h('input', { type: 'date', value: b, 'aria-label': T.blkTo });
  from.classList.add('year'); to.classList.add('year');
  const apply = () => {
    const f = from.value, t = to.value;
    if (!f || !t || f > t || (new Date(t) - new Date(f)) / 86400000 > 366) { toast(T.badPeriod); return; }
    statsFrom = f; statsTo = t; showStats();
  };
  [from, to].forEach((x) => x.addEventListener('keydown', (e) => { if (e.key === 'Enter') apply(); }));
  const tools = h('div.tools', period, statsPeriod === 'custom'
    ? [h('label', T.blkFrom + ' ', from), h('label', T.blkTo + ' ', to), h('button.primary', { type: 'button', onclick: apply }, T.showPeriod)] : null);
  let st;
  try { st = await api('GET', '/stats?from=' + a + '&to=' + b); }
  catch (e) {
    if (!(e instanceof ApiError) || e.message === 'UNAUTHORIZED') throw e;
    // Il periodo non va: restano le scelte per cambiarlo.
    put(main(), h('div.head', h('h1', T.stTitle)), tools, h('div.panel', h('div.empty', T.badPeriod)));
    return;
  }
  const pct = (x) => Math.round(x * 100) + '%';
  const hrs = (x) => (Math.round(x * 10) / 10).toLocaleString(locale());
  const money0 = (c) => new Intl.NumberFormat(locale(), { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(c / 100);
  const label = dateText(a) + ' – ' + dateText(b);
  const heat = st.heat[0] ? h('div.panel', h('table.heat',
    h('thead', h('tr', h('th'), st.heat[0].map((_, i) => h('th.num', String(st.firstHour + i))))),
    h('tbody', st.heat.map((row, d) => h('tr', h('th', weekdayName(d + 1)), row.map((v, i) => h('td', {
      title: weekdayName(d + 1) + ' ' + hhmm((st.firstHour + i) * 60) + ': ' + pct(v), style: 'background: rgba(127, 196, 143, ' + (0.08 + v * 0.85).toFixed(2) + ')',
    }, v >= 0.5 ? pct(v) : ''))))))) : null;
  put(main(),
    h('div.head', h('h1', T.stTitle), h('span.dim', label), h('button', { onclick: () => window.print() }, T.print)),
    printHead(T.stTitle + ' · ' + label),
    tools,
    h('div.kpis',
      h('div.kpi', h('b', pct(st.occupancy)), h('span', T.stOccupancy)),
      h('div.kpi', h('b', money0(st.cents)), h('span', T.stRevenue)),
      h('div.kpi', h('b', st.bookings), h('span', T.stBookings + (st.online ? ' · ' + T.stOnline(st.online) : '')))),
    st.tournamentHours ? h('p.dim', T.stTournaments(hrs(st.tournamentHours))) : null,
    !st.bookings ? h('p.dim', T.stNone) : null,
    h('div.section', h('h2', T.stByCourt), h('div.panel', h('table',
      h('thead', h('tr', h('th', T.court), h('th', T.stOccupancy), h('th.num', T.hoursCol), h('th.num', T.stBookings), h('th.num', T.stRevenue))),
      h('tbody', st.courts.map((c) => h('tr', h('td', c.court),
        h('td', h('span.pbar', h('i', { style: 'width:' + pct(c.occupancy) })), ' ', pct(c.occupancy)),
        h('td.num', T.stHoursOf(hrs(c.bookedHours), hrs(Math.max(0, c.openHours - c.blockedHours))), c.blockedHours ? h('div.dim', T.stBlocked(hrs(c.blockedHours))) : null),
        h('td.num', c.bookings + (c.online ? ' (' + c.online + ' online)' : '')), h('td.num', money0(c.cents)))))))),
    h('p.dim', T.stRevenueHint),
    heat ? h('div.section', h('h2', T.stHeat), h('p.dim', T.stHeatHint), heat) : null,
    st.top.length ? h('div.section', h('h2', T.stTop), h('div.panel', h('table', h('tbody', st.top.map((x, i) => h('tr', h('td.num', i + 1), h('td', x.name), h('td.num', T.stCount(x.count)))))))) : null);
}

// --- Scuola tennis ---

let courseId = null;
let courseTab = 'students';
let lessonShow = 'next';

const slotText = (x) => [weekdayName(x.weekday), hhmm(x.startMin), x.court].filter(Boolean).join(' ');
const feeText = (c) => (c.feeCents ? eur(c.feeCents) + ' ' + (c.feeKind === 'SEASON' ? T.scSeasonFee : T.scMonthly) : '');
const coachOf = (c) => (c.coach ? (people.find((p) => p.id === c.coach) || {}).name || '' : c.coachName);

async function showSchool() {
  people = await api('GET', '/people');
  const list = await api('GET', '/courses');
  const c = courseId && list.find((x) => x.id === courseId);
  if (c) return showCourse(c);
  courseId = null;
  put(main(), h('div.head', h('h1', T.scTitle), h('button.primary', { onclick: () => editCourse(null) }, T.scNew)), h('p.dim', T.scHint),
    list.length ? h('div.cards', list.map((x) => {
      const next = x.lessons.find((l) => !l.cancelled && l.date >= info.today);
      return h('button.card', { type: 'button', onclick: () => { courseId = x.id; courseTab = 'students'; showSchool(); } },
        h('h2', x.name), h('p.dim', [x.level, coachOf(x) ? T.scCoachLine(coachOf(x)) : ''].filter(Boolean).join(' · ')),
        h('p.dim', x.slots.map(slotText).join(' · ')),
        h('p', T.scStudentsCount(x.students.length, x.maxStudents) + (feeText(x) ? ' · ' + feeText(x) : '')),
        next ? h('p.dim', T.scNext(shortDay(next.date) + ' ' + hhmm(next.startMin))) : null);
    })) : h('div.panel', h('div.empty', T.scNone)));
}

/** Una chiamata sul corso aperto, poi la scheda si ridisegna. */
async function courseDo(method, path, body) {
  let out = null;
  try { out = await api(method, '/courses/' + encodeURIComponent(courseId) + path, body); toast(T.saved); }
  catch (e) { toast(errText(e)); }
  await showSchool();
  return out;
}

function showCourse(c) {
  const tabs = [['students', T.scTabStudents + ' (' + c.students.length + ')'], ['lessons', T.scTabLessons], ['fees', T.scTabFees]];
  const body = h('div');
  put(main(),
    h('div.head', h('button', { onclick: () => { courseId = null; showSchool(); }, 'aria-label': T.scTitle, title: T.scTitle }, '←'), h('h1', c.name),
      h('button', { onclick: () => window.print() }, T.print), h('button', { onclick: () => editCourse(c) }, T.edit)),
    h('p.dim', [c.level, coachOf(c) ? T.scCoachLine(coachOf(c)) : '', dateText(c.from) + ' – ' + dateText(c.to), feeText(c)].filter(Boolean).join(' · '),
      h('br'), c.slots.length ? c.slots.map(slotText).join(' · ') : T.scNoLessons),
    printHead(T.scTitle + ' · ' + c.name + ' · ' + Object.fromEntries(tabs)[courseTab]),
    h('div.tabs', { role: 'tablist' }, tabs.map(([v, l]) => h('button' + (v === courseTab ? '.on' : ''), {
      type: 'button', role: 'tab', 'aria-selected': v === courseTab ? 'true' : 'false', onclick: () => { courseTab = v; showCourse(c); },
    }, l))),
    body);
  ({ students: courseStudents, lessons: courseLessons, fees: courseFees })[courseTab](c, body);
}

function courseStudents(c, body) {
  const inside = new Set(c.students.map((x) => x.id));
  const full = c.maxStudents > 0 && c.students.length >= c.maxStudents;
  const input = h('input', { type: 'search', placeholder: T.scAddStudent, 'aria-label': T.scAddStudent, autocomplete: 'off', disabled: full });
  const hits = h('div.hits');
  function refresh() {
    const q = input.value.trim().toLowerCase();
    const list = q ? people.filter((p) => !inside.has(p.id) && p.name.toLowerCase().includes(q)).slice(0, 8) : [];
    put(hits, list.map((p) => h('button', { type: 'button', onclick: async () => {
      input.disabled = true;
      await courseDo('POST', '/students', { ids: [p.id] });
      const again = document.querySelector('.picker input');
      if (again && !again.disabled) again.focus();
    } }, p.name, p.member ? null : h('span.dim', ' · ' + T.notMember))), q && !list.length ? h('div.dim.pad', T.noResults) : null);
  }
  input.addEventListener('input', refresh);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); const b = hits.querySelector('button'); if (b) b.click(); } });
  put(body,
    h('div.picker.noprint', input, hits, c.maxStudents ? h('p.dim', T.scMax(c.maxStudents)) : null),
    c.students.length ? h('div.panel', h('table',
      h('thead', h('tr', h('th.num', '#'), h('th', T.name), h('th', T.scTabLessons), h('th.num', T.owed), h('th.noprint'))),
      h('tbody', c.students.map((x, i) => h('tr', h('td.num', i + 1), h('td', x.name), h('td', x.marked ? T.scPresent(x.present, x.marked) : h('span.dim', '—')),
        h('td.num', x.owedCents ? eur(x.owedCents) : ''),
        h('td.num.noprint', h('button.danger', { type: 'button', onclick: async () => { if (confirm(T.scRemoveStudent + ': ' + x.name + '?')) await courseDo('DELETE', '/students/' + encodeURIComponent(x.id)); } }, T.scRemoveStudent))))))) 
      : h('div.panel', h('div.empty', T.noResults)));
}

function courseLessons(c, body) {
  const show = select([['next', T.scUpcoming], ['past', T.scPast], ['all', T.cupAll]], lessonShow);
  show.setAttribute('aria-label', T.status);
  show.addEventListener('change', () => { lessonShow = show.value; courseLessons(c, body); });
  const rows = c.lessons.filter((l) => ({ next: l.date >= info.today, past: l.date < info.today, all: true })[lessonShow]);
  if (lessonShow === 'past') rows.reverse();
  const names = Object.fromEntries(c.students.map((x) => [x.id, x.name]));
  put(body,
    h('div.tools.noprint', show, h('span.dim', T.lessonHint)),
    rows.length ? h('div.panel', h('table',
      h('thead', h('tr', [T.cupWhen, T.ctTime, T.ctCourt, T.status].map((x) => h('th', { scope: 'col' }, x)))),
      h('tbody', rows.map((l) => h('tr.row', { tabindex: 0, onclick: () => openLesson(c, l), onkeydown: (e) => { if (e.key === 'Enter') openLesson(c, l); } },
        h('td', shortDay(l.date)), h('td', hhmm(l.startMin) + '–' + hhmm(l.startMin + l.minutes)), h('td', l.court),
        h('td', l.cancelled ? h('span.tag.bad', T.scCancelled) : l.marked ? h('span', { title: l.present.map((id) => names[id] || '').join(', ') }, T.scPresent(l.present.filter((id) => names[id]).length, c.students.length))
          : l.date < info.today ? h('span.tag.warn', T.scToMark) : ''))))))
      : h('div.panel', h('div.empty', c.slots.length ? T.noResults : T.scNoLessons)));
}

function openLesson(c, l) {
  const boxes = c.students.map((x) => [x.id, h('input', { type: 'checkbox', checked: l.marked ? l.present.includes(x.id) : false })]);
  const act = async (body) => {
    try { await api('POST', '/courses/' + encodeURIComponent(c.id) + '/lesson', body); dlg.close(); toast(T.saved); await showSchool(); } catch (e) { toast(errText(e)); }
  };
  const left = h('button.danger.left', { type: 'button', onclick: () => act({ key: l.key, cancelled: !l.cancelled }) }, l.cancelled ? T.restoreLesson : T.cancelLessonBtn);
  const all = h('button', { type: 'button', onclick: () => boxes.forEach(([, x]) => { x.checked = true; }) }, T.allPresent);
  const dlg = dialog(c.name + ' · ' + shortDay(l.date) + ' ' + hhmm(l.startMin), h('div.form',
    l.cancelled ? h('p.full', h('span.tag.bad', T.scCancelled)) : null,
    h('h3', T.scWhoWasThere), c.students.length ? h('div.full.checks', boxes.map(([id, x]) => h('label.check', x, c.students.find((s) => s.id === id).name)), all) : h('p.full.dim', T.noResults),
    h('p.full.dim', T.cancelLessonHint)), l.cancelled ? null : async () => {
    await api('POST', '/courses/' + encodeURIComponent(c.id) + '/lesson', { key: l.key, present: boxes.filter(([, x]) => x.checked).map(([id]) => id) });
    toast(T.saved);
    await showSchool();
  }, { left });
}

function courseFees(c, body) {
  if (!c.feeCents) { put(body, h('div.panel', h('div.empty', T.scNoFee))); return; }
  put(body,
    h('p.dim', feeText(c) + ' · ' + T.feesCourseHint),
    h('div.panel', h('table',
      h('thead', h('tr', h('th', T.what), h('th.num', T.chargedCol), h('th.num', T.paidOnes), h('th.noprint'))),
      h('tbody', c.fees.map((f) => h('tr', h('td', f.label), h('td.num', f.charged + ' / ' + c.students.length), h('td.num', f.paid),
        h('td.num.noprint', f.charged < c.students.length ? h('button', { type: 'button', onclick: async () => {
          const out = await courseDo('POST', '/charge', { month: f.month });
          if (out) toast(T.scCharged(out.count));
        } }, T.scChargeStudents) : null)))))));
}

function editCourse(c) {
  const v = c || { name: '', level: '', coach: null, coachName: '', from: info.today, to: addMonth(info.today, 9), maxStudents: 0, feeCents: 0, feeKind: 'MONTHLY', note: '', slots: [] };
  const name = h('input', { required: true, maxlength: 80, value: v.name });
  const level = h('input', { maxlength: 60, value: v.level });
  const coach = select([['', '—']].concat(people.map((p) => [p.id, p.name])), v.coach || '');
  const coachName = h('input', { maxlength: 80, value: v.coachName, placeholder: T.scCoach });
  const from = h('input', { type: 'date', required: true, value: v.from }), to = h('input', { type: 'date', required: true, value: v.to });
  const fee = h('input', { inputmode: 'decimal', value: v.feeCents ? (v.feeCents / 100).toFixed(2).replace('.', ',') : '' });
  const kind = select([['MONTHLY', T.scMonthly], ['SEASON', T.scSeasonFee]], v.feeKind);
  const max = h('input', { type: 'number', min: 0, max: 200, value: v.maxStudents || '', placeholder: T.scNoMax });
  const note = h('input', { maxlength: 500, value: v.note });
  const slots = v.slots.map((x) => ({ ...x }));
  const box = h('div.full.slots');
  const minutes = [30, 45, 60, 75, 90, 120, 150, 180];
  function draw() {
    put(box, slots.map((x, i) => {
      const wd = select([1, 2, 3, 4, 5, 6, 7].map((n) => [String(n), weekdayName(n)]), String(x.weekday));
      wd.addEventListener('change', () => { x.weekday = Number(wd.value); });
      const at = h('input', { type: 'time', value: hhmm(x.startMin), step: 300, 'aria-label': T.ctTime, onchange: () => { if (at.value) x.startMin = toMin(at.value); } });
      const len = select((minutes.includes(x.minutes) ? minutes : minutes.concat([x.minutes]).sort((a, b) => a - b)).map((m) => [String(m), m + ' min']), String(x.minutes));
      len.addEventListener('change', () => { x.minutes = Number(len.value); });
      const ct = select([['', '—']].concat((info.courts || []).map((k) => [k, k])), x.court);
      ct.addEventListener('change', () => { x.court = ct.value; });
      return h('div.slot', wd, at, len, ct, h('button.danger', { type: 'button', title: T.remove, onclick: () => { slots.splice(i, 1); draw(); } }, '×'));
    }), h('button', { type: 'button', onclick: () => { slots.push({ weekday: 1, startMin: 17 * 60, minutes: 60, court: '' }); draw(); } }, T.scAddSlot));
  }
  draw();
  const del = c && h('button.danger.left', { type: 'button', onclick: async () => {
    if (!confirm(T.scDeleteAsk(c.name))) return;
    try { await api('DELETE', '/courses/' + encodeURIComponent(c.id)); dlg.close(); courseId = null; toast(T.saved); await showSchool(); } catch (e) { toast(errText(e)); }
  } }, T.scDelete);
  const dlg = dialog(c ? c.name : T.scNew, h('div.form',
    field(T.scName, name, 'full'), field(T.scLevel, level, 'full'),
    field(T.scCoach, coach), field(T.scCoachFree, coachName),
    field(T.blkFrom, from), field(T.blkTo, to),
    field(T.scFee, fee), field(T.kind, kind), field(T.scMaxField, max), field(T.note, note),
    h('h3', T.scSchedule), box, h('p.full.dim', T.scScheduleHint)), async () => {
    const fc = fee.value.trim() ? cents(fee.value) : 0;
    if (fc === null) throw new Error(T.err.BAD_AMOUNT);
    const body = { name: name.value, level: level.value, coach: coach.value || null, coachName: coach.value ? '' : coachName.value, from: from.value, to: to.value,
      maxStudents: Number(max.value) || 0, feeCents: fc, feeKind: kind.value, note: note.value, slots };
    if (c) await api('PUT', '/courses/' + encodeURIComponent(c.id), body);
    else { const out = await api('POST', '/courses', body); courseId = out.id; courseTab = 'students'; }
    toast(T.saved);
    await showSchool();
  }, { left: del, wide: true });
}

// --- Via ---

(async () => {
  if (!token) { showLogin(); return; }
  try { await start(); } catch (_) { if (!info) showLogin(); }
})();
