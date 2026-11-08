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
    // Lo stile passa da JavaScript: la pagina non permette gli attributi style (Content-Security-Policy).
    else if (k === 'style') for (const d of String(v).split(';')) { const i = d.indexOf(':'); if (i > 0) e.style.setProperty(d.slice(0, i).trim(), d.slice(i + 1).trim()); }
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
    loginCard(),
    installTip());
  input.focus();
}

/** Chi ha già scelto un PIN rientra da solo, con il numero di telefono: senza un nuovo invito. */
function loginCard() {
  const phone = el('input', { type: 'tel', id: 'lphone', autocomplete: 'tel', inputmode: 'tel' });
  const pin = el('input', { type: 'password', id: 'lpin', inputmode: 'numeric', autocomplete: 'current-password', maxlength: '8' });
  const msg = el('p', { class: 'error' }, '');
  const go = async () => {
    if (!phone.value.trim() || !pin.value.trim()) return;
    try {
      const r = await api('POST', '/login', { phone: phone.value, pin: pin.value });
      state.token = r.token; save(KEY, r.token);
      await refresh();
    } catch (e) { msg.textContent = errText(e); pin.value = ''; }
  };
  pin.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
  return el('div', { class: 'card' },
    el('h3', null, T.loginTitle),
    el('p', { class: 'dim' }, T.loginText),
    el('label', { for: 'lphone' }, T.pinPhone), phone,
    el('label', { for: 'lpin' }, T.pinPin), pin, msg,
    el('div', { class: 'actions' }, el('button', { onclick: go }, T.loginGo)));
}

/**
 * Il primo ingresso con l'invito: il PIN è obbligatorio. È un vero modulo con numero ("username") e PIN
 * ("new-password"), così il browser propone di salvarlo nel suo gestore delle password.
 */
function showPinRequired() {
  const phone = el('input', { type: 'tel', id: 'rphone', name: 'username', autocomplete: 'username', inputmode: 'tel', required: 'required' });
  const pin = el('input', { type: 'password', id: 'rpin', name: 'password', autocomplete: 'new-password', inputmode: 'numeric', maxlength: '8', required: 'required' });
  const pin2 = el('input', { type: 'password', id: 'rpin2', autocomplete: 'new-password', inputmode: 'numeric', maxlength: '8', required: 'required' });
  const msg = el('p', { class: 'error' }, '');
  const form = el('form', { id: 'pinreq', class: 'card', method: 'post', action: '#' },
    el('h3', null, T.pinRequiredTitle),
    el('p', null, T.pinRequiredText),
    el('label', { for: 'rphone' }, T.pinPhone), phone,
    el('label', { for: 'rpin' }, T.pinPin), pin,
    el('label', { for: 'rpin2' }, T.pinPin2), pin2,
    el('p', { class: 'dim' }, '🔑 ', T.pinSaveTip),
    msg,
    el('div', { class: 'actions' }, el('button', { type: 'submit' }, T.pinGo)));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.textContent = '';
    if (pin.value !== pin2.value) { msg.textContent = T.pinMismatch; return; }
    try {
      await api('POST', '/pin', { phone: phone.value, pin: pin.value });
      // Un cambio di indirizzo dopo l'invio: è il segnale che fa proporre al browser di salvare numero e PIN.
      history.pushState(null, '', location.pathname + '#ok');
      // Si rileggono i dati dal server: intanto la pagina può averli ricaricati (ogni minuto).
      await refresh();
    } catch (err) { msg.textContent = errText(err); }
  });
  $('app').replaceChildren(form);
  phone.focus();
}

/** In Le mie: scegliere (o cambiare) numero e PIN, per rientrare da solo su un altro telefono. */
function pinCard() {
  const me = state.me;
  const box = el('div', { class: 'card' });
  const form = () => {
    const phone = el('input', { type: 'tel', id: 'pphone', autocomplete: 'tel', inputmode: 'tel' });
    const pin = el('input', { type: 'password', id: 'ppin', inputmode: 'numeric', autocomplete: 'new-password', maxlength: '8' });
    const pin2 = el('input', { type: 'password', id: 'ppin2', inputmode: 'numeric', autocomplete: 'new-password', maxlength: '8' });
    const msg = el('p', { class: 'error' }, '');
    const save = async () => {
      if (pin.value !== pin2.value) { msg.textContent = T.pinMismatch; return; }
      try {
        await api('POST', '/pin', { phone: phone.value, pin: pin.value });
        state.me.hasPin = true; state.me.phone = phone.value.replace(/\D/g, '').slice(-3);
        box.replaceChildren(el('b', null, T.pinTitle), el('p', null, T.pinSaved));
      } catch (e) { msg.textContent = errText(e); }
    };
    box.replaceChildren(el('b', null, T.pinTitle), el('p', { class: 'dim' }, T.pinHint),
      el('label', { for: 'pphone' }, T.pinPhone), phone,
      el('label', { for: 'ppin' }, T.pinPin), pin,
      el('label', { for: 'ppin2' }, T.pinPin2), pin2, msg,
      el('div', { class: 'actions' }, el('button', { onclick: save }, T.pinSave)));
  };
  if (me.hasPin) box.replaceChildren(el('b', null, T.pinTitle), el('p', { class: 'dim' }, T.pinActive(me.phone)),
    el('div', { class: 'actions' }, el('button', { class: 'ghost small', onclick: form }, T.pinChange)));
  else form();
  return box;
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
  try { state.rooms = await api('GET', '/chats'); } catch (_) { state.rooms = []; }
  if (!state.date || !state.me.days.includes(state.date)) state.date = state.me.days[0];
  setHeader();
  await loadDay();
  if (state.openRoom) { const r = state.openRoom; state.openRoom = null; openRoom(r); }
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
  // Entrato con l'invito ma senza PIN: prima di tutto il PIN (la schermata resta finché non è salvato).
  if (!me.hasPin) { if (!document.getElementById('pinreq')) showPinRequired(); return; }
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
  if (state.tab === 2) markSeeksSeen();
  const fresh = state.tab === 2 ? [] : unreadSeeks();
  const chatNew = (state.rooms || []).some(x => chatUnread(x.room, x.last));
  const tabs = [T.tabBook, T.tabMine((me.bookings.length + (me.waits || []).length)) + (freed ? ' •' : ''), T.tabSeek + (fresh.length ? ' (' + fresh.length + ')' : ''), T.tabChat + (chatNew ? ' •' : '')];
  const bar = el('div', { class: 'tabs' }, tabs.map((t, i) => el('button', { class: i === state.tab ? 'on' : '', onclick: () => { state.tab = i; save(TAB, String(i)); render(dayError); } }, t)));
  const parts = [boardNews(), ...chatNews(), bar].filter(Boolean);
  if (state.tab === 1) {
    parts.push(...mine, ...waits, el('div', { id: 'push' }), pinCard());
  } else if (state.tab === 2) {
    parts.push(...seekSection().filter(Boolean));
  } else if (state.tab === 3) {
    parts.push(...chatSection());
  } else {
    // Senza PIN: un promemoria per sceglierlo, così chi sbaglia o cambia telefono rientra da solo.
    if (!me.hasPin) parts.push(el('p', { class: 'dim' }, el('a', { href: '#', onclick: (e) => { e.preventDefault(); state.tab = 1; save(TAB, '1'); render(dayError); } }, T.pinRemind)));
    // La bacheca del circolo: i messaggi della segreteria (col pallino se ce ne sono di nuovi).
    // Già letta resta qui, piccola; quella nuova sta in evidenza sopra le schede (boardNews).
    if (me.boardLast > 0 && !chatUnread('board', me.boardLast)) parts.push(el('div', { class: 'card' },
      el('b', null, T.boardTitle),
      el('div', { class: 'actions' }, el('button', { class: 'small', onclick: () => openChat('board', T.boardTitle, false) }, T.boardOpen))));
    // Gli annunci nuovi degli altri, finché non si apre Compagni: altrimenti nessuno li vede.
    if (fresh.length) parts.push(el('div', { class: 'card freed' },
      el('b', null, T.seekNewTitle(fresh.length)),
      ...fresh.slice(0, 5).map(k => el('div', null, el('a', { href: '#', onclick: (e) => { e.preventDefault(); state.tab = 2; save(TAB, '2'); render(dayError); } }, seekLine(k)))),
      el('div', { class: 'actions' }, el('button', { class: 'small', onclick: () => { state.tab = 2; save(TAB, '2'); render(dayError); } }, T.seekOpen))));
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

// --- Annunci nuovi degli altri ---
// Quelli a cui si può rispondere e non ancora visti in questo browser: in cima a Prenota e col numero su Compagni.
const SEEN = 'tsm-seek-seen';
function seekSeen() { try { return new Set(JSON.parse(localStorage.getItem(SEEN) || '[]')); } catch (_) { return new Set(); } }
function othersOpen() { return (state.seeks || []).filter(k => !k.mine && !k.joined && k.need > 0 && !k.bookedCourt && !k.noCourt); }
function unreadSeeks() { const seen = seekSeen(); return othersOpen().filter(k => !seen.has(k.id)); }
function markSeeksSeen() {
  // Si tengono solo quelli ancora aperti: la lista non cresce.
  try { localStorage.setItem(SEEN, JSON.stringify(othersOpen().map(k => k.id))); } catch (_) { /* resta da vedere */ }
}
function seekLine(k) {
  const [wd, dm] = fmtDate(k.date);
  return [k.owner, wd + ' ' + dm + ' ' + hhmm(k.fromMin) + '–' + hhmm(k.toMin), k.doubles ? T.seekDoubles : T.seekSingles, k.level, T.seekNeed(k.need)].filter(Boolean).join(' · ');
}

// --- Chat: quella di un annuncio (chi c'è scrive) e la bacheca del circolo (scrive la segreteria) ---
const READ = 'tsm-chat-read';
function chatRead() { try { return JSON.parse(localStorage.getItem(READ) || '{}'); } catch (_) { return {}; } }
function markChatRead(room, at) { try { const r = chatRead(); r[room] = Math.max(r[room] || 0, at); localStorage.setItem(READ, JSON.stringify(r)); } catch (_) { /* resta da leggere */ } }
function chatUnread(room, last) { return last > 0 && last > (chatRead()[room] || 0); }

/** La chat in un riquadro che sale dal basso: i messaggi si aggiornano ogni pochi secondi finché è aperta. */
/** Gli avvisi nuovi della segreteria: una striscia in cima, col numero; toccandola si apre la bacheca. */
function boardNews() {
  const me = state.me;
  if (!me || !(me.boardLast > 0) || !chatUnread('board', me.boardLast)) return null;
  if (state.boardFor !== me.boardLast) {
    state.boardFor = me.boardLast;
    api('GET', '/chat/board').then(list => { state.board = list; render(); }).catch(() => { /* resta il numero minimo */ });
  }
  const n = Math.max(1, (state.board || []).filter(m => chatUnread('board', m.at)).length);
  return el('button', { class: 'boardbar', onclick: () => openChat('board', T.boardTitle, false) }, '📣 ' + T.boardUnread(n), el('span', null, '›'));
}

/**
 * Le strisce dei messaggi nuovi tra soci, in cima come quella della bacheca: la chat del circolo (col numero)
 * e una per ogni privata; toccandole si apre la chat. Il numero arriva dopo (una chiamata in più).
 */
function chatNews() {
  const rooms = state.rooms || [];
  const out = [];
  const club = rooms.find(x => x.room === 'club');
  if (club && chatUnread('club', club.last)) {
    if (state.clubFor !== club.last) {
      state.clubFor = club.last;
      api('GET', '/chat/club?after=' + (chatRead().club || 0)).then(l => { state.clubNew = l.filter(m => !m.mine).length; render(); }).catch(() => {});
    }
    out.push(el('button', { class: 'boardbar', onclick: () => { state.tab = 3; save(TAB, '3'); openRoom('club'); } }, '💬 ' + T.clubUnread(Math.max(1, state.clubNew || 0)), el('span', null, '›')));
  }
  for (const d of rooms.filter(x => x.room.startsWith('d-') && chatUnread(x.room, x.last)))
    out.push(el('button', { class: 'boardbar', onclick: () => { state.tab = 3; save(TAB, '3'); openRoom(d.room, d.title); } }, '✉️ ' + T.dmFrom(d.title), el('span', null, '›')));
  return out;
}

/** Il titolo di una chat: quella del circolo, o il nome dell'altro in una privata. */
function roomTitle(room) {
  if (room === 'club') return T.clubChat;
  const r = (state.rooms || []).find(x => x.room === room);
  return r ? r.title : T.chatOpen;
}

/** Apre una chat tra soci: la chat del circolo o una privata (con Blocca). */
function openRoom(room, name) {
  const other = room.startsWith('d-') ? room.slice(2).split('~').find(id => id !== state.me.id) : null;
  openChat(room, name || roomTitle(room), true, {
    hint: room === 'club' ? T.clubChatHint : T.dmHint, report: true,
    block: other ? { id: other, name: name || roomTitle(room) } : null,
  });
}

/** La scheda Chat: la chat del circolo, i messaggi privati (a chi l'ha acceso), chi si è bloccato. */
function chatSection() {
  const me = state.me;
  const toggle = (key, label, hint) => {
    const box = el('input', { type: 'checkbox' });
    box.checked = !!me[key];
    box.addEventListener('change', async () => {
      try { await api('POST', '/prefs', { [key]: box.checked }); me[key] = box.checked; toast(T.saved || '✓'); }
      catch (e) { box.checked = !box.checked; toast(errText(e)); }
    });
    return [el('label', { class: 'check' }, box, ' ', label), el('p', { class: 'dim' }, hint)];
  };
  const dms = (state.rooms || []).filter(x => x.room.startsWith('d-'));
  const club = (state.rooms || []).find(x => x.room === 'club');
  const preview = r => (r.previewMine ? T.chatYou + ': ' : '') + (r.preview || '');
  const write = async () => {
    let list;
    try { list = await api('GET', '/people'); } catch (e) { toast(errText(e)); return; }
    sheet(el('h3', null, T.dmWrite),
      list.length ? el('div', { class: 'list' }, ...list.map(p => el('button', { class: 'ghost', onclick: () => { closeSheet(); openRoom(p.room, p.name); } }, p.name)))
        : el('p', { class: 'dim' }, T.dmNobody),
      el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: closeSheet }, T.close)));
  };
  return [
    // L'elenco come nelle app di messaggi: in cima la chat del circolo, poi le private, le più recenti prima.
    chatRow(T.clubChat, club && club.count ? preview(club) : T.clubEntryHint, club ? club.last : 0,
      club && chatUnread('club', club.last) ? Math.max(1, state.clubNew || 0) : 0, '💬', () => openRoom('club')),
    ...dms.map(d => chatRow(d.title, preview(d), d.last, chatUnread(d.room, d.last) && !d.previewMine ? 1 : 0, null, () => openRoom(d.room, d.title))),
    dms.length ? null : el('p', { class: 'dim' }, T.dmNone),
    el('button', { class: 'ghost', onclick: write }, '+ ' + T.dmWrite),
    ...toggle('clubChat', T.clubChatAlerts, T.clubChatAlertsHint),
    ...toggle('dmOpen', T.dmOpen, T.dmOpenHint),
    (me.blocked || []).length ? el('div', null, el('h2', null, T.blockedList), ...me.blocked.map(b => el('button', { class: 'small ghost', onclick: async () => {
      try { await api('POST', '/block', { player: b.id, on: false }); await refresh(); } catch (e) { toast(errText(e)); }
    } }, T.unblock(b.name)))) : null,
  ].filter(Boolean);
}

/** I colori dei nomi nelle chat di gruppo: ognuno sempre il suo, come nell'app (stessa impronta del nome). */
const NAME_COLORS = ['#29B6F6', '#FF9800', '#AB47BC', '#66BB6A', '#EF5350', '#26C6DA', '#FFCA28', '#EC407A'];
function nameColor(name) { let h = 0; for (const c of name) h = (31 * h + c.charCodeAt(0)) | 0; return NAME_COLORS[(h & 0x7fffffff) % NAME_COLORS.length]; }
/** Le iniziali in un cerchio colorato; [icon] (un'emoji) al posto delle iniziali, per il gruppo e la bacheca. */
function avatar(name, icon) {
  const ini = name.split(/[ -]/).filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('') || '?';
  return el('span', { class: 'avatar' + (icon ? ' icon' : ''), style: icon ? null : 'background:' + nameColor(name) }, icon || ini);
}
const dayKey = ms => new Intl.DateTimeFormat('en-CA', { timeZone: tz() }).format(new Date(ms));
/** L'ora se è di oggi, se no la data: come negli elenchi delle chat. */
const whenShort = ms => dayKey(ms) === dayKey(Date.now()) ? fmtTime(ms) : new Intl.DateTimeFormat(LANG, { day: '2-digit', month: '2-digit', timeZone: tz() }).format(new Date(ms));

/** Una riga dell'elenco delle chat: cerchio, nome, ultimo messaggio, ora e il tondino coi non letti. */
function chatRow(name, preview, at, unread, icon, onclick) {
  return el('button', { class: 'chatrow', onclick },
    avatar(name, icon),
    el('span', { class: 'body' },
      el('span', { class: 'top' }, el('b', null, name), el('small', { class: unread ? 'new' : '' }, at ? whenShort(at) : '')),
      el('span', { class: 'bottom' }, el('span', { class: 'preview' }, preview), unread ? el('span', { class: 'count' }, unread > 99 ? '99+' : String(unread)) : null)));
}

/**
 * Una chat a tutto schermo, come nelle app di messaggi: in alto il titolo, i messaggi a fumetti (i propri a destra;
 * nelle chat di gruppo il nome colorato di chi scrive) con le date in mezzo, in basso la barra per scrivere.
 * I messaggi si aggiornano ogni pochi secondi finché è aperta.
 */
async function openChat(room, title, canWrite, opts = {}) {
  const group = room === 'club' || room.startsWith('s-');
  const list = el('div', { class: 'chatlist' });
  const input = canWrite ? el('textarea', { id: 'chatin', rows: '1', maxlength: '500', placeholder: T.chatPlaceholder }) : null;
  const err = el('p', { class: 'error' });
  let last = 0, timer = 0;
  const today = dayKey(Date.now()), yesterday = dayKey(Date.now() - 86400000);
  // Com'è questa chat (chi la legge, come si segnala): un riquadro in cima.
  const hint = opts.hint || (canWrite ? null : T.boardHint);
  if (hint) list.append(el('div', { class: 'notice' }, hint));
  const none = el('p', { class: 'dim center' }, T.chatNone);
  list.append(none);
  const head = [...list.children];
  // Il proprio messaggio che si sta correggendo: il testo va nella barra, sopra c'è una riga con la X.
  let editing = null;
  const editBar = el('div', { class: 'editbar', hidden: true });
  const stopEdit = () => { editing = null; editBar.hidden = true; if (input) { input.value = ''; input.style.height = ''; } };
  const startEdit = (m) => {
    editing = m;
    editBar.replaceChildren(el('span', null, el('b', null, '✎ ' + T.msgEditing), el('small', null, m.text)), el('button', { class: 'x', 'aria-label': T.close, onclick: stopEdit }, '✕'));
    editBar.hidden = false;
    input.value = m.text; input.focus();
  };
  const ownMenu = (m) => {
    const canEdit = Date.now() - m.at <= 15 * 60000;
    sheet(el('p', null, m.text),
      el('div', { class: 'actions' },
        canEdit ? el('button', { onclick: () => { closeSheet(); startEdit(m); } }, T.msgEdit) : null,
        el('button', { class: 'danger', onclick: async () => {
          if (!confirm(T.msgDeleteAsk)) return;
          closeSheet();
          try { await api('DELETE', '/chat/msg/' + m.id); if (editing && editing.id === m.id) stopEdit(); await load(); } catch (e) { err.textContent = errText(e); }
        } }, T.msgDelete),
        el('button', { class: 'ghost', onclick: closeSheet }, T.close)));
  };
  let shown = '';
  // Ogni volta tutti i messaggi (al massimo 200): così si vedono anche quelli corretti o eliminati.
  const draw = (msgs) => {
    const sig = JSON.stringify(msgs.map(m => [m.id, m.text, m.edited, m.deleted]));
    if (sig === shown) return;
    const atBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 60;
    const grew = msgs.length && msgs[msgs.length - 1].at > last;
    shown = sig;
    let lastDay = '';
    const nodes = [];
    for (const m of msgs) {
      last = Math.max(last, m.at);
      const d = dayKey(m.at);
      if (d !== lastDay) {
        lastDay = d;
        nodes.push(el('div', { class: 'day' }, el('span', null, d === today ? T.today : d === yesterday ? T.yesterday
          : new Intl.DateTimeFormat(LANG, { weekday: 'long', day: 'numeric', month: 'long', timeZone: tz() }).format(new Date(m.at)))));
      }
      // Con la bandierina si segnala alla segreteria un messaggio ricevuto; coi puntini si corregge o si elimina il proprio.
      const flag = opts.report && !m.mine && !m.office && !m.deleted ? el('button', { class: 'flag', title: T.report, 'aria-label': T.report, onclick: async () => {
        if (!confirm(T.report + '?')) return;
        try { await api('POST', '/chat/msg/' + m.id + '/report'); toast(T.reported); } catch (e) { err.textContent = errText(e); }
      } }, '⚑') : null;
      const more = m.mine && !m.deleted && canWrite ? el('button', { class: 'flag', 'aria-label': T.msgEdit, onclick: () => ownMenu(m) }, '⋯') : null;
      nodes.push(el('div', { class: 'bubble' + (m.mine ? ' mine' : '') + (m.office ? ' office' : '') + (m.deleted ? ' deleted' : '') },
        !m.mine && (group || m.office) ? el('div', { class: 'name', style: m.office ? null : 'color:' + nameColor(m.name) }, m.name) : null,
        // Eliminato: "Hai eliminato…" o "Mario ha eliminato…".
        el('div', { class: 'text' }, m.deleted ? '🚫 ' + (m.mine ? T.msgDeletedMine : T.msgDeletedBy(m.name)) : m.text),
        el('div', { class: 'meta' }, flag, more, (m.edited && !m.deleted ? T.msgEdited + ' · ' : '') + fmtTime(m.at))));
    }
    list.replaceChildren(...head.filter(n => n !== none || !msgs.length), ...nodes);
    if (grew || atBottom) list.scrollTop = list.scrollHeight;
    if (msgs.length) markChatRead(room, last);
  };
  const load = async () => {
    try { draw(await api('GET', '/chat/' + room)); err.textContent = ''; } catch (e) { err.textContent = errText(e); }
  };
  const send = async () => {
    const text = input.value.trim();
    if (!text) return;
    try {
      if (editing) await api('PUT', '/chat/msg/' + editing.id, { text }); else await api('POST', '/chat/' + room, { text });
      stopEdit(); await load();
    } catch (e) { err.textContent = errText(e); }
  };
  if (input) {
    input.addEventListener('keydown', e => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
      if (e.key === 'Escape' && editing) stopEdit();
    });
    // Il campo cresce col testo, fino a qualche riga.
    input.addEventListener('input', () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 140) + 'px'; });
  }
  const view = el('div', { class: 'chatview', role: 'dialog', 'aria-label': title });
  // Chiudendo, l'elenco si aggiorna (l'ultimo messaggio, i non letti).
  const close = async () => {
    clearInterval(timer); view.remove(); document.body.classList.remove('nochrome');
    try { state.rooms = await api('GET', '/chats'); } catch (_) { /* resta quello di prima */ }
    render();
  };
  const block = opts.block ? el('button', { class: 'small ghost danger', onclick: async () => {
    if (!confirm(T.blockAsk(opts.block.name))) return;
    try { await api('POST', '/block', { player: opts.block.id, on: true }); clearInterval(timer); view.remove(); document.body.classList.remove('nochrome'); await refresh(); } catch (e) { err.textContent = errText(e); }
  } }, T.block) : null;
  view.append(
    el('div', { class: 'chathead' }, el('button', { class: 'back', 'aria-label': T.close, onclick: close }, '←'),
      avatar(title, group ? '💬' : canWrite ? null : '📣'), el('b', null, title), block),
    list, err, input ? editBar : null,
    input ? el('div', { class: 'chatbar' }, input, el('button', { class: 'send', 'aria-label': T.chatSend, onclick: send }, '➤')) : null);
  document.body.append(view);
  document.body.classList.add('nochrome');
  await load();
  timer = setInterval(() => { if (!view.isConnected) clearInterval(timer); else load(); }, 4000);
  if (input) input.focus();
}

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
    // La chat dell'annuncio: per chi l'ha messo e chi ha detto ci sto (col pallino se ci sono messaggi nuovi).
    if (k.mine || k.joined) buttons.push(el('button', { class: 'small', onclick: () => openChat('s-' + k.id, T.chatTitle(who + ' · ' + wd + ' ' + dm), true) },
      T.chatOpen + (chatUnread('s-' + k.id, k.chatLast) ? ' •' : '')));
    return el('div', { class: 'card' + (k.mine ? ' freed' : '') }, ...lines, buttons.length ? el('div', { class: 'actions' }, ...buttons) : null);
  });
  // Le notifiche dei nuovi annunci si possono spegnere: gli annunci restano qui e in cima a Prenota.
  const box = el('input', { type: 'checkbox', checked: state.me.seekAlerts !== false ? 'checked' : null });
  box.checked = state.me.seekAlerts !== false;
  box.addEventListener('change', async () => {
    try { await api('POST', '/prefs', { seekAlerts: box.checked }); state.me.seekAlerts = box.checked; toast(T.saved || '✓'); }
    catch (e) { box.checked = !box.checked; toast(errText(e)); }
  });
  // Arrivati da una notifica: se quell'annuncio non è più aperto lo si dice (ha già trovato il compagno o è stato tolto).
  let gone = null;
  if (state.lookFor) {
    const k = (state.seeks || []).find(x => x.id === state.lookFor);
    if (!k || (!k.mine && !k.joined && (k.need === 0 || k.bookedCourt || k.noCourt))) gone = el('div', { class: 'card' }, el('p', null, T.seekGone));
    state.lookFor = null;
  }
  return [
    gone,
    el('label', { class: 'check' }, box, ' ', T.seekAlerts), el('p', { class: 'dim small' }, T.seekAlertsHint),
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
// Ogni minuto il giorno e gli annunci (che si chiudono quando qualcuno accetta).
setInterval(async () => {
  if (document.hidden || !state.me || !$('sheet').hidden) return;
  try { state.seeks = await api('GET', '/seeks'); } catch (_) { /* restano quelli di prima */ }
  loadDay();
}, 60000);
// Dalla notifica di un annuncio, con la pagina già aperta: si va in Compagni.
if ('serviceWorker' in navigator) navigator.serviceWorker.addEventListener('message', (e) => {
  if (e.data && e.data.seek && state.me) { state.lookFor = e.data.seek; state.tab = 2; save(TAB, '2'); refresh(); }
  if (e.data && e.data.chat && state.me) { state.tab = 3; save(TAB, '3'); refresh().then(() => openRoom(e.data.chat)); }
});
if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('sw.js').catch(() => {});

(async () => {
  setHeader();
  state.token = load(KEY);
  // Il link del QR: https://circolo/#i=CODICE (dopo il # non arriva al server né nei log).
  // Dalla notifica di un annuncio: si apre Compagni (e si dice se nel frattempo si è chiuso).
  const sk = location.hash.match(/[#&]s=([A-Za-z0-9_-]+)/);
  if (sk) { state.lookFor = sk[1]; state.tab = 2; save(TAB, '2'); history.replaceState(null, '', location.pathname); }
  const ck = location.hash.match(/[#&]c=([^&]+)/);
  if (ck) { state.openRoom = decodeURIComponent(ck[1]); state.tab = 3; save(TAB, '3'); history.replaceState(null, '', location.pathname); }
  const m = location.hash.match(/[#&]i=([A-Za-z0-9-]+)/);
  if (m) {
    history.replaceState(null, '', location.pathname);
    if (IOS && !STANDALONE && !state.token) { showIosInstall(m[1]); return; }
    try { await claim(m[1]); return; } catch (e) { showInvite(errText(e)); return; }
  }
  if (state.token) await refresh(); else showInvite();
})();
