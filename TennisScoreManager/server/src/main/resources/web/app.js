// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

// La pagina per prenotare i campi dal telefono (anche iPhone): invito, giorni, caselle libere, prenota e disdici.
// Parla solo col server del circolo che la serve (/api/v1); la chiave del telefono resta nel telefono.

const KEY = 'tsm.token';
const state = { token: null, me: null, date: null, day: null };

function load(k) { try { return localStorage.getItem(k); } catch (_) { return null; } }
function save(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch (_) { /* senza memoria resta per questa visita */ } }

/** Un elemento: el('div', {class: 'x', onclick: f}, 'testo', figlio…). I testi non diventano mai HTML. */
function el(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'class') e.className = v;
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat()) if (c != null && c !== false) e.append(c instanceof Node ? c : String(c));
  return e;
}
const $ = id => document.getElementById(id);

class ApiErr extends Error { constructor(code, status) { super(code); this.code = code; this.status = status; } }

async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) headers.Authorization = 'Bearer ' + state.token;
  let r;
  try {
    r = await fetch('/api/v1' + path, { method, headers, body: body ? JSON.stringify(body) : undefined, cache: 'no-store' });
  } catch (_) { throw new ApiErr('OFFLINE', 0); }
  let data = null;
  try { data = await r.json(); } catch (_) { /* nessun corpo */ }
  if (!r.ok) throw new ApiErr((data && data.error) || 'generic', r.status);
  return data;
}

function errText(e) {
  if (e && e.code === 'OFFLINE') return T.offline;
  return (e && T.err[e.code]) || T.err.generic;
}

let toastTimer = null;
function toast(msg) {
  const t = $('toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; }, 3500);
}

// --- Date e soldi, sempre nel fuso del circolo ---

function tz() { return (state.me && state.me.timeZone) || 'Europe/Rome'; }
function fmtTime(ms) { return new Intl.DateTimeFormat(LANG, { hour: '2-digit', minute: '2-digit', timeZone: tz() }).format(new Date(ms)); }
function fmtWhen(ms) { return new Intl.DateTimeFormat(LANG, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: tz() }).format(new Date(ms)); }
/** "2026-11-09" → [lun, 9 nov]: la data è già quella del circolo, la si mostra così com'è. */
function fmtDate(d) {
  const [y, m, dd] = d.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, dd, 12));
  return [new Intl.DateTimeFormat(LANG, { weekday: 'short', timeZone: 'UTC' }).format(dt), new Intl.DateTimeFormat(LANG, { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(dt)];
}
function money(c) { return c > 0 ? new Intl.NumberFormat(LANG, { style: 'currency', currency: 'EUR' }).format(c / 100) : T.free; }

// --- Schermate ---

function setHeader() {
  document.title = T.title;
  $('club').textContent = (state.me && state.me.club) || T.title;
  $('who').textContent = state.me ? state.me.name : '';
  const lo = $('logout'); lo.textContent = T.logout; lo.hidden = !state.token;
}

function showInvite(error) {
  state.me = null; setHeader();
  const input = el('input', { class: 'code', id: 'code', autocomplete: 'one-time-code', autocapitalize: 'characters', spellcheck: 'false', maxlength: '16' });
  const msg = el('p', { class: 'error' }, error || '');
  const go = async () => {
    const code = input.value.trim();
    if (!code) return;
    try { await claim(code); } catch (e) { msg.textContent = errText(e); }
  };
  input.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
  $('app').replaceChildren(
    el('div', { class: 'card' },
      el('h3', null, T.welcome),
      el('p', { class: 'dim' }, T.inviteText),
      el('label', { for: 'code' }, T.code), input, msg,
      el('div', { class: 'actions' }, el('button', { onclick: go }, T.enter))),
    el('p', { class: 'tip' }, T.install));
  input.focus();
}

async function claim(code) {
  const r = await api('POST', '/claim', { code });
  state.token = r.token; save(KEY, r.token);
  await refresh();
}

async function refresh() {
  try {
    state.me = await api('GET', '/me');
  } catch (e) {
    if (e.status === 401) { state.token = null; save(KEY, null); showInvite(errText(e)); return; }
    $('app').replaceChildren(el('p', { class: 'error center' }, errText(e)), el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: refresh }, '↻')));
    return;
  }
  if (!state.date || !state.me.days.includes(state.date)) state.date = state.me.days[0];
  setHeader();
  await loadDay();
}

async function loadDay() {
  try {
    state.day = await api('GET', '/day?date=' + encodeURIComponent(state.date));
  } catch (e) {
    state.day = null;
    render(errText(e));
    return;
  }
  render();
}

function render(dayError) {
  const me = state.me, r = me.rules;
  const mine = me.bookings.length === 0 ? [el('p', { class: 'dim' }, T.none)] : me.bookings.map(b =>
    el('div', { class: 'card row' },
      el('div', { class: 'grow' },
        el('div', null, el('b', null, b.court), ' · ', fmtWhen(b.start), '–', fmtTime(b.start + b.minutes * 60000)),
        el('div', { class: 'dim' }, money(b.cents), b.lights ? ' · ' + T.lights : '', b.heating ? ' · ' + T.heating : '')),
      el('button', { class: 'danger small', onclick: () => askCancel(b) }, T.cancel)));

  const days = el('div', { class: 'days' }, me.days.map((d, i) => {
    const [wd, dm] = fmtDate(d);
    return el('button', { class: d === state.date ? 'on' : '', onclick: () => { state.date = d; loadDay(); } }, i === 0 ? T.today : wd, el('small', null, dm));
  }));

  const parts = [
    el('h2', null, T.mine), ...mine,
    el('h2', null, T.day), days,
  ];
  if (!r.open) parts.push(el('p', { class: 'error' }, T.closedNow));
  if (dayError) parts.push(el('p', { class: 'error' }, dayError));
  else if (state.day) parts.push(grid(state.day));
  parts.push(
    el('div', { class: 'legend' }, ['FREE', 'MINE', 'BUSY', 'BLOCKED'].map(s => el('span', { class: s }, T[s]))),
    el('p', { class: 'tip' }, T.rules(r.daysAhead, r.maxFuture), ' ', T.cancelRule(r.cancelHours), me.payInfo ? ' ' + T.pay + ': ' + me.payInfo : ''),
  );
  $('app').replaceChildren(...parts);
}

function courtLine(c) {
  return [c.surface ? T[c.surface] : '', c.covered ? T.covered : T.outdoor].filter(Boolean).join(' · ');
}

function grid(day) {
  const starts = [...new Set(day.cells.map(c => c.start))].sort((a, b) => a - b);
  const byKey = new Map(day.cells.map(c => [c.court + '|' + c.start, c]));
  const head = el('tr', null, el('th', null, ''), day.courts.map(c => el('th', null, c.name, el('small', null, courtLine(c)))));
  const rows = starts.map(s => el('tr', null,
    el('td', { class: 'time' }, fmtTime(s)),
    day.courts.map(c => {
      const cell = byKey.get(c.name + '|' + s);
      if (!cell) return el('td');
      const click = cell.state === 'FREE' && day.open ? () => openBook(c, s)
        : cell.state === 'MINE' ? () => { const b = state.me.bookings.find(x => x.id === cell.booking); if (b) askCancel(b); } : null;
      return el('td', null, el('button', { class: 'cell ' + cell.state, onclick: click, disabled: !click, 'aria-label': c.name + ' ' + fmtTime(s) + ' ' + T[cell.state] }, T[cell.state]));
    })));
  return el('div', { class: 'grid-wrap' }, el('table', { class: 'grid' }, el('thead', null, head), el('tbody', null, rows)));
}

// --- Prenota e disdici ---

function sheet(...kids) { $('sheet-box').replaceChildren(...kids); $('sheet').hidden = false; }
function closeSheet() { $('sheet').hidden = true; }

function openBook(court, start) {
  const step = state.day.slotMinutes, max = Math.max(state.me.rules.maxMinutes, step);
  const opts = []; for (let m = step; m <= max; m += step) opts.push(m);
  const dur = el('select', { id: 'dur' }, opts.map(m => el('option', { value: m }, m % 60 === 0 ? (m / 60) + ' h' : m + ' min')));
  const heat = court.heating ? el('input', { type: 'checkbox', id: 'heat' }) : null;
  const price = el('div', { class: 'price' }, '…');
  const info = el('p', { class: 'dim' });
  const err = el('p', { class: 'error' });
  const ok = el('button', { disabled: true }, T.confirm);
  let seq = 0;
  const quote = async () => {
    const my = ++seq; ok.disabled = true; err.textContent = '';
    const q = '/quote?court=' + encodeURIComponent(court.name) + '&start=' + start + '&minutes=' + dur.value + '&heating=' + (heat ? heat.checked : false);
    try {
      const r = await api('GET', q);
      if (my !== seq) return;
      price.textContent = money(r.cents);
      info.textContent = r.lights ? T.lights : '';
      if (r.problem) err.textContent = T.err[r.problem] || T.err.generic; else ok.disabled = false;
    } catch (e) { if (my === seq) err.textContent = errText(e); }
  };
  dur.addEventListener('change', quote);
  if (heat) heat.addEventListener('change', quote);
  ok.addEventListener('click', async () => {
    ok.disabled = true;
    try {
      await api('POST', '/bookings', { court: court.name, start, minutes: Number(dur.value), heating: heat ? heat.checked : false });
      closeSheet(); toast(T.booked); await refresh();
    } catch (e) { err.textContent = errText(e); if (e.code === 'TAKEN') loadDay(); }
  });
  sheet(
    el('h3', null, court.name), el('div', { class: 'dim' }, fmtWhen(start)),
    el('label', { for: 'dur' }, T.duration), dur,
    heat ? el('label', { class: 'check' }, heat, T.heating) : null,
    el('label', null, T.price), price, info, err,
    state.me.payInfo ? el('p', { class: 'dim' }, T.pay + ': ' + state.me.payInfo) : null,
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: closeSheet }, T.close), ok));
  quote();
}

function askCancel(b) {
  const err = el('p', { class: 'error' }, b.canCancel ? '' : T.callOffice);
  const yes = el('button', { class: 'danger', disabled: !b.canCancel }, T.cancel);
  yes.addEventListener('click', async () => {
    yes.disabled = true;
    try { await api('DELETE', '/bookings/' + encodeURIComponent(b.id)); closeSheet(); toast(T.cancelled); await refresh(); }
    catch (e) { err.textContent = errText(e); }
  });
  sheet(
    el('h3', null, T.cancelAsk), el('p', null, el('b', null, b.court), ' · ', fmtWhen(b.start)), err,
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: closeSheet }, T.close), yes));
}

// --- Avvio ---

$('sheet').addEventListener('click', e => { if (e.target.id === 'sheet') closeSheet(); });
$('logout').addEventListener('click', async () => {
  if (!confirm(T.logoutAsk)) return;
  try { await api('POST', '/logout'); } catch (_) { /* si esce comunque da qui */ }
  state.token = null; save(KEY, null); showInvite();
});
document.addEventListener('visibilitychange', () => { if (!document.hidden && state.me) refresh(); });
setInterval(() => { if (!document.hidden && state.me && $('sheet').hidden) loadDay(); }, 60000);
if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('sw.js').catch(() => {});

(async () => {
  setHeader();
  state.token = load(KEY);
  // Il link del QR: https://circolo/#i=CODICE (dopo il # non arriva al server né nei log).
  const m = location.hash.match(/[#&]i=([A-Za-z0-9-]+)/);
  if (m) {
    history.replaceState(null, '', location.pathname);
    try { await claim(m[1]); return; } catch (e) { showInvite(errText(e)); return; }
  }
  if (state.token) await refresh(); else showInvite();
})();
