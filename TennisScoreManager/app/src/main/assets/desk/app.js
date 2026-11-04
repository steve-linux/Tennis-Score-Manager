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
let view = 'people';
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
  root.replaceChildren(h('div.login', h('div.box', h('h1', T.title), h('p', T.qrHint), canvas, status,
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

const VIEWS = () => [['people', T.members], ['money', T.money], ['plan', T.plan], ['cups', T.cups]];
const main = () => document.querySelector('main');

async function render() {
  root.replaceChildren(h('div.shell',
    h('nav.side', { 'aria-label': T.title },
      h('div.brand', info.club || T.title, h('small', T.title)),
      VIEWS().map(([v, l]) => h('button' + (v === view ? '.on' : ''), { 'aria-current': v === view ? 'page' : null, onclick: () => { view = v; render(); } }, l)),
      h('div.spacer'),
      h('div.note', T.phoneNote),
      h('button', { onclick: logout }, T.logout)),
    h('main')));
  try { await ({ people: showPeople, money: showMoney, plan: showPlan, cups: showCups })[view](); }
  catch (e) { if (e instanceof ApiError && token) main().replaceChildren(h('div.empty', errText(e))); else if (!(e instanceof ApiError)) throw e; }
}

async function logout() {
  try { await api('POST', '/logout'); } catch (_) { /* si esce comunque */ }
  token = null; write(null); info = null;
  showLogin(T.loggedOut);
}

// --- Soci ---

let filter = { text: '', kind: 'members' };

function issueTag(i) {
  const bad = ['NO_MEDICAL', 'MEDICAL_EXPIRED', 'NO_FITP'].includes(i);
  return h('span.tag.' + (bad ? 'bad' : 'warn'), T.issue[i] || i);
}

async function showPeople() {
  people = await api('GET', '/people');
  const body = h('div');
  const search = h('input', { type: 'search', placeholder: T.search, 'aria-label': T.search, value: filter.text, oninput: () => { filter.text = search.value; list(); } });
  const kind = select([['members', T.onlyMembers], ['todo', T.todo], ['all', T.everyone]], filter.kind);
  kind.addEventListener('change', () => { filter.kind = kind.value; list(); });
  function list() {
    const q = filter.text.trim().toLowerCase();
    const rows = people.filter((p) => (filter.kind === 'all' || (p.member && (filter.kind === 'members' || p.issues.length)))
      && (!q || [p.name, p.firstName, p.lastName, p.phone, p.email, p.fitpNumber, p.number].some((x) => (x || '').toLowerCase().includes(q))));
    body.replaceChildren(rows.length ? h('div.panel', h('table',
      h('thead', h('tr', [T.name, T.phone, T.email, T.fitp, T.medical, T.status].map((x) => h('th', { scope: 'col' }, x)), h('th.num', { scope: 'col' }, T.owed))),
      h('tbody', rows.map((p) => h('tr.row', { tabindex: 0, onclick: () => editPerson(p), onkeydown: (e) => { if (e.key === 'Enter') editPerson(p); } },
        h('td', p.name), h('td.nowrap', p.phone), h('td', p.email), h('td', p.fitpNumber, p.ranking ? h('span.dim', ' · ' + p.ranking) : null), h('td', dateText(p.medicalUntil)),
        h('td', p.member ? (p.issues.length ? p.issues.map(issueTag) : h('span.tag.ok', T.inOrder)) : h('span.dim', T.notMember)),
        h('td.num', p.owedCents ? eur(p.owedCents) : '')))))) : h('div.panel', h('div.empty', people.length ? T.noResults : T.noPeople)));
  }
  main().replaceChildren(
    h('div.head', h('h1', T.members), h('button', { onclick: importDialog }, T.importCsv), h('button.primary', { onclick: () => editPerson(null) }, T.newMember)),
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
    area.replaceChildren();
    if (!input.files[0]) return;
    if (input.files[0].size > 900 * 1024) { area.replaceChildren(h('div.err', T.err.TOO_BIG)); return; }
    try {
      const body = { text: await readFile(input.files[0]) };
      const r = await api('POST', '/people/import/preview', body);
      text = body.text;
      const good = r.rows.filter((x) => !x.error);
      area.replaceChildren(
        h('p', T.importSummary(good.filter((x) => !x.update).length, good.filter((x) => x.update).length, r.rows.length - good.length)),
        h('div.panel', h('table', h('thead', h('tr', [T.line, T.name, T.phone, T.email, T.birth, T.fitp, ''].map((x) => h('th', x)))),
          h('tbody', r.rows.slice(0, 200).map((x) => h('tr', h('td.dim', x.line), h('td', x.firstName + ' ' + x.lastName), h('td', x.phone), h('td', x.email), h('td', dateText(x.birth)), h('td', x.fitp),
            h('td', x.error ? h('span.tag.bad', T.rowErr[x.error]) : h('span.tag.' + (x.update ? 'warn' : 'ok'), x.update ? T.willUpdate : T.willAdd))))))));
    } catch (e) { area.replaceChildren(h('div.err', e instanceof ApiError ? errText(e) : String(e))); }
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
let moneyFilter = 'open';

const STATUS = { PAID: 'ok', PARTIAL: 'warn', UNPAID: 'warn', LATE: 'bad', CANCELLED: 'dim' };

async function showMoney() {
  const m = await api('GET', '/money' + (moneyYear ? '?year=' + moneyYear : ''));
  moneyYear = m.year;
  const year = h('input', { type: 'number', min: 2000, max: 2100, value: m.year, 'aria-label': T.year, onchange: () => { moneyYear = Number(year.value) || m.year; showMoney(); } });
  year.classList.add('year');
  const show = select([['open', T.toCollect], ['all', T.allCharges]], moneyFilter);
  show.addEventListener('change', () => { moneyFilter = show.value; showMoney(); });
  const rows = m.charges.filter((c) => moneyFilter === 'all' || c.leftCents > 0);
  main().replaceChildren(
    h('div.head', h('h1', T.money), h('button', { onclick: () => exportCsv(m.year) }, T.exportCsv), h('button', { onclick: () => newCharge() }, T.newCharge), h('button.primary', { onclick: () => newFee() }, T.newFee)),
    h('div.kpis',
      h('div.kpi', h('b', eur(m.incomeCents)), h('span', T.income(m.year))),
      h('div.kpi', h('b', eur(m.openCents)), h('span', T.open)),
      h('div.kpi', h('b', m.openPeople), h('span', T.openPeople))),
    h('div.tools', show, h('label', T.year + ' ', year)),
    rows.length ? h('div.panel', h('table',
      h('thead', h('tr', [T.name, T.what, T.due, T.status].map((x) => h('th', { scope: 'col' }, x)), h('th.num', T.amount), h('th.num', T.left))),
      h('tbody', rows.map((c) => h('tr.row', { tabindex: 0, onclick: () => openCharge(c, m), onkeydown: (e) => { if (e.key === 'Enter') openCharge(c, m); } },
        h('td', c.name), h('td', c.label), h('td', dateText(c.due)), h('td', h('span.tag.' + STATUS[c.status], T.chargeStatus[c.status])), h('td.num', eur(c.cents)), h('td.num', eur(c.leftCents))))))) : h('div.panel', h('div.empty', T.noCharges)),
    m.fees.length ? h('div.section', h('h2', T.fees), h('div.panel', h('table', h('thead', h('tr', h('th', T.name), h('th', T.due), h('th.num', T.amount), h('th.num', T.paidOf))),
      h('tbody', m.fees.map((f) => h('tr', h('td', f.name), h('td', dateText(f.due)), h('td.num', eur(f.cents)), h('td.num', f.paid + ' / ' + f.total))))))) : null);
}

function openCharge(c, m) {
  const amount = h('input', { value: (c.leftCents / 100).toFixed(2).replace('.', ','), inputmode: 'decimal', required: true });
  const date = h('input', { type: 'date', value: info.today, required: true });
  const type = select(m.payTypes.map((t) => [t.id, t.name]));
  const ref = h('input', { maxlength: 120 });
  const history = c.payments.length ? h('div.full', h('h2', T.payments), h('div.panel', h('table', h('tbody', c.payments.map((p) => h('tr',
    h('td', dateText(p.date)), h('td', p.method), h('td.dim', p.receipt), h('td.dim', p.ref), h('td.num', eur(p.cents)),
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
  } : null, { saveText: T.register, left: cancel });
}

function newFee() {
  const name = h('input', { required: true, maxlength: 120 });
  const amount = h('input', { required: true, inputmode: 'decimal' });
  const due = h('input', { type: 'date' });
  const kind = select([['MEMBERSHIP', T.kindMembership], ['FITP', T.kindFitp], ['COURSE', T.kindCourse], ['TOURNAMENT', T.kindTournament], ['OTHER', T.kindOther]], 'MEMBERSHIP');
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
  dialog(T.newCharge, h('div.form', field(T.name, who, 'full'), field(T.what, label, 'full'), field(T.amount, amount), field(T.due, due)), async () => {
    const v = cents(amount.value);
    if (v === null) throw new Error(T.err.BAD_AMOUNT);
    await api('POST', '/charges', { player: who.value, label: label.value, cents: v, due: due.value || null });
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

async function showPlan() {
  const d = await api('GET', '/day?date=' + planDate);
  if (!people.length) people = await api('GET', '/people');
  const label = new Date(planDate + 'T12:00:00').toLocaleDateString(locale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const date = h('input', { type: 'date', value: planDate, 'aria-label': T.date, onchange: () => { if (date.value) { planDate = date.value; showPlan(); } } });
  date.classList.add('year');
  const head = h('div.head', h('h1', label),
    h('button', { onclick: () => { planDate = addDay(planDate, -1); showPlan(); }, 'aria-label': T.prevDay }, '←'),
    h('button', { onclick: () => { planDate = info.today; showPlan(); } }, T.today),
    h('button', { onclick: () => { planDate = addDay(planDate, 1); showPlan(); }, 'aria-label': T.nextDay }, '→'), date);
  if (!d.courts.length) { main().replaceChildren(head, h('div.panel', h('div.empty', T.noCourts))); return; }
  const slot = d.slotMinutes;
  const open = d.openHour * 60, close = d.closeHour * 60;
  const rows = Math.ceil((close - open) / slot);
  const grid = h('div.grid');
  grid.style.gridTemplateColumns = '56px repeat(' + d.courts.length + ', minmax(110px, 1fr))';
  grid.style.gridTemplateRows = 'auto repeat(' + rows + ', ' + Math.max(ROW, Math.round(ROW * slot / 30)) + 'px)';
  const put = (e, row, col, span, cols) => { e.style.gridRow = row + ' / span ' + span; e.style.gridColumn = col + ' / span ' + (cols || 1); grid.append(e); };
  put(h('div'), 1, 1, 1);
  d.courts.forEach((c, i) => put(h('div.gcourt', c), 1, i + 2, 1));
  for (let r = 0; r < rows; r++) {
    const start = open + r * slot;
    put(h('div.ghour', hhmm(start)), r + 2, 1, 1);
    d.courts.forEach((c, i) => put(h('button.gcell', { type: 'button', 'aria-label': c + ' ' + hhmm(start), title: hhmm(start), onclick: () => newBooking(d, c, start) }), r + 2, i + 2, 1));
  }
  for (const it of d.items) {
    const from = Math.max(0, Math.floor((it.startMin - open) / slot));
    const span = Math.max(1, Math.ceil((Math.min(it.endMin, close) - open) / slot) - from);
    if (from >= rows) continue;
    const text = [hhmm(it.startMin) + '–' + hhmm(it.endMin), it.label || (it.kind ? T.bookingKind[it.kind] : '')].filter(Boolean).join(' ');
    const all = it.court === '';
    const col = all ? 2 : d.courts.indexOf(it.court) + 2;
    if (col < 2) continue;
    const cls = 'button.gitem.' + it.type + (it.online ? '.online' : '');
    put(h(cls, { type: 'button', title: text, onclick: it.type === 'BOOKING' ? () => openBooking(it) : null }, text), from + 2, col, span, all ? d.courts.length : 1);
  }
  main().replaceChildren(head, h('div.panel', grid));
}

function newBooking(d, court, start) {
  const courtS = select(d.courts.map((c) => [c, c]), court);
  const times = [];
  for (let m = d.openHour * 60; m < d.closeHour * 60; m += d.slotMinutes) times.push([String(m), hhmm(m)]);
  const startS = select(times, String(start));
  const lengths = [];
  for (let m = d.slotMinutes; m <= 240; m += d.slotMinutes) lengths.push([String(m), m + ' min']);
  const dur = select(lengths, String(lengths.some(([v]) => v === '60') ? 60 : d.slotMinutes));
  const kind = select(Object.entries(T.bookingKind));
  const price = h('span');
  const who = h('input', { list: 'desk-people', placeholder: T.addPlayer });
  const list = h('datalist', { id: 'desk-people' }, people.map((p) => h('option', { value: p.name })));
  const chips = h('div.chips full');
  const picked = [];
  function add() {
    const n = who.value.trim();
    if (!n) return;
    picked.push({ name: n, id: (people.find((p) => p.name === n) || {}).id });
    who.value = '';
    drawChips();
  }
  function drawChips() { chips.replaceChildren(...picked.map((p, i) => h('button.chip', { type: 'button', title: T.remove, onclick: () => { picked.splice(i, 1); drawChips(); } }, p.name + ' ×'))); }
  who.addEventListener('change', add);
  const charge = h('input', { type: 'checkbox' });
  const note = h('input', { maxlength: 500 });
  async function quote() {
    try {
      const q = await api('GET', '/quote?date=' + planDate + '&court=' + encodeURIComponent(courtS.value) + '&startMin=' + startS.value + '&minutes=' + dur.value + '&kind=' + kind.value);
      price.textContent = eur(q.cents) + (q.lights ? ' · ' + T.lights : '');
    } catch (_) { price.textContent = ''; }
  }
  [courtS, startS, dur, kind].forEach((e) => e.addEventListener('change', quote));
  quote();
  dialog(T.newBooking + ' · ' + dateText(planDate), h('div.form', list, field(T.court, courtS), field(T.start, startS), field(T.duration, dur), field(T.kind, kind),
    h('label.full', T.players, who), chips, field(T.note, note, 'full'), h('label.check.full', charge, T.chargeSplit), h('div.full', T.price + ': ', price)), async () => {
    add();
    await api('POST', '/bookings', { court: courtS.value, date: planDate, startMin: Number(startS.value), minutes: Number(dur.value), kind: kind.value,
      players: picked.filter((p) => p.id).map((p) => p.id), names: picked.filter((p) => !p.id).map((p) => p.name), note: note.value, charge: charge.checked });
    toast(T.saved);
    await showPlan();
  });
}

function openBooking(it) {
  const remove = h('button.danger.left', { type: 'button', onclick: async () => {
    if (!confirm(T.deleteBookingAsk)) return;
    try { await api('DELETE', '/bookings/' + encodeURIComponent(it.id)); d.close(); toast(T.saved); await showPlan(); } catch (e) { toast(errText(e)); }
  } }, T.deleteBooking);
  const d = dialog(it.court + ' · ' + hhmm(it.startMin) + '–' + hhmm(it.endMin), h('div',
    h('p', it.label || T.bookingKind[it.kind]), it.note ? h('p.dim', it.note) : null, h('p.dim', T.price + ': ' + eur(it.cents) + (it.online ? ' · online' : ''))), null, { left: remove });
}

// --- Tornei ---

async function showCups() {
  const list = await api('GET', '/tournaments');
  main().replaceChildren(h('div.head', h('h1', T.cups)), h('p.dim', T.cupsNote),
    list.length ? h('div.cards', list.map((t) => h('div.panel', h('div.pad',
      h('h2', t.name), h('p.dim', [t.category, t.start ? dateText(t.start) + (t.end ? ' – ' + dateText(t.end) : '') : ''].filter(Boolean).join(' · ')),
      h('p', T.cupEntries(t.entries.length) + (t.matches ? ' · ' + T.cupPlayed(t.played, t.matches) : '')),
      t.entries.length ? h('details', h('summary', T.entriesList), h('ul', t.entries.map((e) => h('li', e)))) : null)))) : h('div.panel', h('div.empty', T.noCups)));
}

// --- Via ---

(async () => {
  if (!token) { showLogin(); return; }
  try { await start(); } catch (_) { if (!info) showLogin(); }
})();
