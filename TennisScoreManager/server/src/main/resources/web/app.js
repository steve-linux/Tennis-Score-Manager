// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

// La pagina per prenotare i campi dal telefono (anche iPhone): invito, giorni, caselle libere, prenota e disdici.
// Parla solo col server del circolo che la serve (/api/v1); la chiave del telefono resta nel telefono.

const KEY = 'tsm.token';
const TAB = 'tsm.tab';
const state = { token: null, me: null, date: null, day: null, seeks: [], tab: Number(load(TAB) || 0) || 0 };

// Su iPhone (e iPad) Safari e l'app messa sulla schermata Home non condividono i dati: l'invito va usato
// nell'app installata, altrimenti la chiave resta in Safari e l'app chiede un codice già usato.
const IOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const STANDALONE = navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;

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
  document.title = state.me && state.me.club ? 'TSM · ' + state.me.club : 'TSM · ' + T.title;
  $('club').textContent = (state.me && state.me.club) || 'Tennis Score Manager';
  $('who').textContent = state.me ? state.me.name : T.title;
  const lo = $('logout'); lo.textContent = T.logout; lo.hidden = !state.token;
  const rf = $('refresh'); rf.title = T.refresh; rf.setAttribute('aria-label', T.refresh); rf.hidden = !state.me;
}

/** Il suggerimento per averla come un'app, solo se non lo è già. */
function installTip() {
  if (STANDALONE) return null;
  return el('p', { class: 'tip' }, IOS ? T.install : T.installOther);
}

/** Copia anche senza HTTPS (rete del circolo in http), dove Safari non dà navigator.clipboard. */
function copyOld(text) {
  const t = el('textarea', { readonly: true, style: 'position:fixed;top:0;left:0;opacity:0' });
  t.value = text;
  document.body.append(t);
  t.focus(); t.setSelectionRange(0, text.length);
  const ok = document.execCommand('copy');
  t.remove();
  if (!ok) throw new Error('copy');
}

/**
 * Su iPhone, dal link dell'invito in Safari: prima si installa, poi il codice si usa nell'app installata
 * (copiato negli appunti, che Safari e l'app condividono). Chi preferisce resta nel browser.
 */
function showIosInstall(code) {
  state.me = null; setHeader();
  const shown = code.replace(/[^A-Za-z0-9]/g, '').toUpperCase().replace(/(.{5})(?=.)/g, '$1-');
  const msg = el('p', { class: 'dim center' }, '');
  const copy = async () => {
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(shown);
      else copyOld(shown);
      msg.textContent = T.copied;
    } catch (_) { msg.textContent = shown; }
  };
  const here = async () => {
    try { await claim(code); } catch (e) { msg.className = 'error center'; msg.textContent = errText(e); }
  };
  $('app').replaceChildren(
    el('div', { class: 'card' },
      el('h3', null, T.iosTitle),
      el('ol', { class: 'steps' },
        el('li', null, T.iosStep1, ' ', el('span', { class: 'share', 'aria-hidden': 'true' }, '⬆︎')),
        el('li', null, T.iosStep2),
        el('li', null, T.iosStep3)),
      el('div', { class: 'bigcode' }, shown),
      el('div', { class: 'actions' }, el('button', { onclick: copy }, T.copy)), msg),
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: here }, T.useBrowser)));
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
    installTip());
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
  try { state.seeks = await api('GET', '/seeks'); } catch (_) { state.seeks = []; }
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

  // Le liste d'attesa: aperte, o liberate (si prenota subito, prima di un altro).
  const waits = (me.waits || []).map(w => {
    const where = (w.freedCourt || w.court || T.waitAnyCourt) + ' · ' + fmtWhen(w.start);
    const book = w.freedCourt ? el('button', { class: 'small', onclick: () => openBook(courtByName(w.freedCourt), w.start) }, T.bookNow) : null;
    return el('div', { class: 'card row' + (w.freedCourt ? ' freed' : '') },
      el('div', { class: 'grow' },
        el('div', null, el('b', null, w.freedCourt ? T.waitFreed : T.waitList)),
        el('div', { class: 'dim' }, where)),
      book, el('button', { class: 'ghost small', onclick: () => removeWait(w) }, T.waitRemove));
  });

  const days = el('div', { class: 'days' }, me.days.map((d, i) => {
    const [wd, dm] = fmtDate(d);
    return el('button', { class: d === state.date ? 'on' : '', onclick: () => { state.date = d; loadDay(); } }, i === 0 ? T.today : wd, el('small', null, dm));
  }));

  // Tre schede: prenotare, le proprie prenotazioni (con le attese), Cerco compagno.
  const freed = (me.waits || []).some(w => w.freedCourt);
  const tabs = [T.tabBook, T.tabMine((me.bookings.length + (me.waits || []).length)) + (freed ? ' •' : ''), T.tabSeek];
  const bar = el('div', { class: 'tabs' }, tabs.map((t, i) => el('button', { class: i === state.tab ? 'on' : '', onclick: () => { state.tab = i; save(TAB, String(i)); render(dayError); } }, t)));
  const parts = [bar];
  if (state.tab === 1) {
    parts.push(...mine, ...waits, el('div', { id: 'push' }));
  } else if (state.tab === 2) {
    parts.push(...seekSection());
  } else {
    parts.push(el('h2', null, T.day), days);
    if (!r.open) parts.push(el('p', { class: 'error' }, T.closedNow));
    if (dayError) parts.push(el('p', { class: 'error' }, dayError));
    else if (state.day) parts.push(grid(state.day));
    parts.push(
      el('div', { class: 'legend' }, ['FREE', 'MINE', 'BUSY', 'WAITING', 'BLOCKED'].map(s => el('span', { class: s }, T[s]))),
      el('p', { class: 'tip' }, T.rules(r.daysAhead, r.maxFuture), ' ', T.cancelRule(r.cancelHours), me.payInfo ? ' ' + T.pay + ': ' + me.payInfo : ''),
    );
  }
  const tip = installTip();
  if (tip) parts.push(tip);
  $('app').replaceChildren(...parts);
  updatePush();
}

// --- Promemoria (notifiche push) ---

const PUSH_OK = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window && window.isSecureContext;

function keyBytes(b64) {
  const s = atob(b64.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - b64.length % 4) % 4));
  return Uint8Array.from(s, c => c.charCodeAt(0));
}

async function currentSub() {
  if (!PUSH_OK) return null;
  const reg = await navigator.serviceWorker.getRegistration();
  return reg ? reg.pushManager.getSubscription() : null;
}

/** Il riquadro dei promemoria: attivarli, vederli attivi, spegnerli; o perché qui non si può. */
async function updatePush(message) {
  const box = $('push');
  if (!box) return;
  const card = (...kids) => box.replaceChildren(el('div', { class: 'card' }, el('b', null, T.pushTitle), ...kids));
  if (!PUSH_OK) {
    const why = IOS && !STANDALONE ? T.pushIos : !window.isSecureContext ? T.pushHttps : null;
    if (why) card(el('p', { class: 'dim' }, why)); else box.replaceChildren();
    return;
  }
  if (Notification.permission === 'denied') { card(el('p', { class: 'dim' }, T.pushDenied)); return; }
  let sub = null;
  try { sub = await currentSub(); } catch (_) { /* nessun abbonamento */ }
  const note = message ? el('p', { class: 'error' }, message) : null;
  if (sub) card(el('p', { class: 'dim' }, T.pushActive), note, el('div', { class: 'actions' }, el('button', { class: 'ghost small', onclick: pushOff }, T.pushOff)));
  else card(el('p', { class: 'dim' }, T.pushHint), note, el('div', { class: 'actions' }, el('button', { onclick: pushOn }, T.pushOn)));
}

async function pushOn() {
  try {
    if (await Notification.requestPermission() !== 'granted') { updatePush(); return; }
    const key = await api('GET', '/push/key');
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(key.key) });
    const j = sub.toJSON();
    await api('POST', '/push/subscribe', { endpoint: j.endpoint, p256dh: j.keys.p256dh, auth: j.keys.auth, lang: LANG });
    updatePush();
  } catch (e) { updatePush(T.pushFail); }
}

async function pushOff() {
  try {
    const sub = await currentSub();
    if (sub) { await api('POST', '/push/unsubscribe', { endpoint: sub.endpoint }).catch(() => {}); await sub.unsubscribe(); }
  } catch (_) { /* resta com'è */ }
  updatePush();
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
        : cell.state === 'MINE' ? () => { const b = state.me.bookings.find(x => x.id === cell.booking); if (b) askCancel(b); }
        : cell.state === 'BUSY' && day.open && !cell.waiting ? () => openWait(c, s) : null;
      const label = cell.waiting ? T.WAITING : T[cell.state];
      return el('td', null, el('button', { class: 'cell ' + cell.state + (cell.waiting ? ' waiting' : ''), onclick: click, disabled: !click, 'aria-label': c.name + ' ' + fmtTime(s) + ' ' + label }, label));
    })));
  return el('div', { class: 'grid-wrap' }, el('table', { class: 'grid' }, el('thead', null, head), el('tbody', null, rows)));
}

// --- Prenota e disdici ---

function sheet(...kids) { $('sheet-box').replaceChildren(...kids.filter(k => k != null && k !== false)); $('sheet').hidden = false; }
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

// --- Cerco compagno ---

/** Minuti dalla mezzanotte, nel fuso del circolo. */
function clubMinutes(ms) {
  const p = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: tz() }).formatToParts(new Date(ms));
  return Number(p.find(x => x.type === 'hour').value) * 60 + Number(p.find(x => x.type === 'minute').value);
}
function hhmm(min) { return String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0'); }

function seekSection() {
  const cards = (state.seeks || []).map(k => {
    const [wd, dm] = fmtDate(k.date);
    const who = k.mine ? T.seekYours : k.owner;
    const lines = [
      el('div', null, el('b', null, who), ' · ', wd + ' ' + dm, ' · ', hhmm(k.fromMin) + '–' + hhmm(k.toMin)),
      el('div', { class: 'dim' }, [k.doubles ? T.seekDoubles : T.seekSingles, k.level, k.note].filter(Boolean).join(' · ')),
      el('div', { class: 'dim' }, T.seekWho(k.players.join(', ')), k.need > 0 ? ' · ' + T.seekNeed(k.need) : ''),
    ];
    if (k.bookedCourt) lines.push(el('div', { class: 'ok' }, T.seekBooked(k.bookedCourt + ' · ' + fmtWhen(k.bookedStart))));
    else if (k.noCourt) lines.push(el('div', { class: 'error' }, T.seekNoCourt));
    const buttons = [];
    if (!k.mine && !k.joined && k.need > 0) buttons.push(el('button', { class: 'small', onclick: () => seekAction('POST', '/seeks/' + k.id + '/join', T.seekJoined) }, T.seekJoin));
    if (k.joined && !k.bookedCourt) buttons.push(el('button', { class: 'ghost small', onclick: () => seekAction('POST', '/seeks/' + k.id + '/leave') }, T.seekLeave));
    if (k.mine) buttons.push(el('button', { class: 'ghost small', onclick: () => seekAction('DELETE', '/seeks/' + k.id) }, T.seekRemove));
    return el('div', { class: 'card' + (k.mine ? ' freed' : '') }, ...lines, buttons.length ? el('div', { class: 'actions' }, ...buttons) : null);
  });
  return [
    ...(cards.length ? cards : [el('p', { class: 'dim' }, T.seekNone)]),
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: openSeek, disabled: !state.me.rules.open }, T.seekNew)),
  ];
}

async function seekAction(method, path, done) {
  try { await api(method, path); if (done) toast(done); } catch (e) { toast(errText(e)); }
  refresh();
}

/** L'annuncio: giorno, fascia (orari del planning), singolo o doppio, livello e nota. */
function openSeek() {
  const day = el('select', { id: 'sday' }, state.me.days.map((d, i) => { const [wd, dm] = fmtDate(d); return el('option', { value: d }, i === 0 ? T.today + ' ' + dm : wd + ' ' + dm); }));
  day.value = state.date || state.me.days[0];
  const starts = state.day ? [...new Set(state.day.cells.map(c => clubMinutes(c.start)))].sort((a, b) => a - b) : [];
  const step = state.day ? state.day.slotMinutes : 60;
  const mins = starts.length ? starts : Array.from({ length: 14 }, (_, i) => (8 + i) * 60);
  const from = el('select', { id: 'sfrom' }, mins.map(m => el('option', { value: m }, hhmm(m))));
  const to = el('select', { id: 'sto' }, mins.map(m => m + step).map(m => el('option', { value: m }, hhmm(m))));
  from.value = String(mins[Math.min(1, mins.length - 1)]); to.value = String(Math.min(Number(from.value) + 3 * 60, mins[mins.length - 1] + step));
  const kind = el('select', { id: 'skind' }, el('option', { value: 's' }, T.seekSingles), el('option', { value: 'd' }, T.seekDoubles));
  const level = el('input', { id: 'slevel', maxlength: '30', placeholder: 'NC, 4.3…' });
  const note = el('input', { id: 'snote', maxlength: '140' });
  const err = el('p', { class: 'error' });
  const go = el('button', null, T.seekPublish);
  go.addEventListener('click', async () => {
    go.disabled = true;
    try {
      await api('POST', '/seeks', { date: day.value, fromMin: Number(from.value), toMin: Number(to.value), doubles: kind.value === 'd', level: level.value, note: note.value });
      closeSheet(); toast(T.seekPublished); refresh();
    } catch (e) { go.disabled = false; err.textContent = errText(e); }
  });
  sheet(
    el('h3', null, T.seekTitle), el('p', { class: 'dim' }, T.seekHint),
    el('label', { for: 'sday' }, T.seekDay), day,
    el('div', { class: 'row' }, el('div', { class: 'grow' }, el('label', { for: 'sfrom' }, T.seekFrom), from), el('div', { class: 'grow' }, el('label', { for: 'sto' }, T.seekTo), to)),
    el('label', { for: 'skind' }, T.seekKind), kind,
    el('label', { for: 'slevel' }, T.seekLevel), level,
    el('label', { for: 'snote' }, T.seekNote), note, err,
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: closeSheet }, T.close), go));
}

/** Il campo com'è fatto, se è nel giorno a schermo; altrimenti solo il nome. */
function courtByName(name) {
  return (state.day && state.day.courts.find(c => c.name === name)) || { name };
}

/** Casella occupata: avvisami se si libera (quel campo, o un campo qualsiasi alla stessa ora). */
function openWait(court, start) {
  const any = el('input', { type: 'checkbox', id: 'any' });
  const err = el('p', { class: 'error' });
  const go = el('button', null, T.waitAsk);
  go.addEventListener('click', async () => {
    go.disabled = true;
    try {
      await api('POST', '/waits', { court: any.checked ? null : court.name, start, minutes: state.day.slotMinutes });
      closeSheet(); toast(T.waitAdded); await refresh();
    } catch (e) {
      go.disabled = false;
      err.textContent = errText(e);
      if (e.code === 'FREE_NOW') { closeSheet(); toast(T.err.FREE_NOW); loadDay(); }
    }
  });
  sheet(
    el('h3', null, court.name), el('div', { class: 'dim' }, fmtWhen(start)),
    el('p', null, T.waitText),
    el('label', { class: 'check' }, any, T.waitAny),
    PUSH_OK ? null : el('p', { class: 'dim' }, T.waitNoPush), err,
    el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: closeSheet }, T.close), go));
}

async function removeWait(w) {
  try { await api('DELETE', '/waits/' + encodeURIComponent(w.id)); } catch (_) { /* si riprova aggiornando */ }
  refresh();
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
$('refresh').addEventListener('click', () => { if (state.me) refresh(); });
$('logout').addEventListener('click', async () => {
  if (!confirm(T.logoutAsk)) return;
  try { await pushOff(); await api('POST', '/logout'); } catch (_) { /* si esce comunque da qui */ }
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
    if (IOS && !STANDALONE && !state.token) { showIosInstall(m[1]); return; }
    try { await claim(m[1]); return; } catch (e) { showInvite(errText(e)); return; }
  }
  if (state.token) await refresh(); else showInvite();
})();
