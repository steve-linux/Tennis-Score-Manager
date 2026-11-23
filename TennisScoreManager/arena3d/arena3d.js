// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// TSM Arena in 3D (three.js): stesso gioco di assets/arena.html, disegnato in WebGL. Due stili:
//   "low"  = low-poly costruito in codice (leggero, va anche su TV economiche);
//   "real" = più realistico: corpo umano con scheletro (male_base_mesh.glb, CC0), luci, ombre morbide, pubblico.
// Le animazioni da tennis (pronto, corsa, preparazione, impatto, chiusura sopra la spalla, servizio) sono
// procedurali: direzioni dei segmenti del corpo per fotogrammi chiave, uguali per i due stili.
// Si compila con deliver/make_arena3d.sh in assets/arena/arena3d.js (un file solo, niente internet sulla TV).

import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { makeRealistic } from './character.js';

const V = (x, y, z) => new THREE.Vector3(x, y, z).normalize();
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const smooth = k => k * k * (3 - 2 * k);
const UP = new THREE.Vector3(0, 1, 0);

let A = null;              // stato del gioco (dalla pagina)
let renderer, scene, camera, style = 'low';
let G = {};                // oggetti della scena
// Il gioco usa x verso la destra di chi guarda dal fondo (come la pagina 2D); three.js, guardando verso +z, ha la
// destra su -x. Tutto ciò che sta in coordinate di gioco va in ROOT, specchiato su x.
let ROOT = null;
let male = null;           // modello glTF caricato (stile "real")
const tmpV = new THREE.Vector3(), tmpV2 = new THREE.Vector3(), tmpQ = new THREE.Quaternion(), tmpQ2 = new THREE.Quaternion();

/* =====================================================================
   Pose: direzioni dei segmenti nello spazio del giocatore (y su, z avanti, x = sinistra del giocatore)
   ===================================================================== */
const POSES = {
  ready: { yaw: 0, crouch: 0.1, uaR: V(-0.25, -0.85, 0.45), faR: V(0.15, -0.1, 0.98), rk: V(0.35, 0.55, 0.75), uaL: V(0.25, -0.85, 0.45), faL: V(-0.25, -0.1, 0.96) },
  // dritto
  fPrep: { yaw: -0.9, crouch: 0.16, uaR: V(-0.7, -0.25, -0.6), faR: V(-0.45, 0.3, -0.85), rk: V(-0.2, 0.75, -0.65), uaL: V(0.0, -0.25, 0.97), faL: V(0.25, 0.0, 0.97) },
  fDrop: { yaw: -0.8, crouch: 0.2, uaR: V(-0.75, -0.45, -0.45), faR: V(-0.6, -0.4, -0.7), rk: V(-0.5, -0.6, -0.6), uaL: V(0.15, -0.3, 0.94), faL: V(0.35, 0.0, 0.94) },
  fHit: { yaw: 0.0, crouch: 0.16, uaR: V(-0.55, -0.45, 0.7), faR: V(-0.35, -0.15, 0.92), rk: V(-0.95, 0.08, 0.3), uaL: V(0.45, -0.6, 0.6), faL: V(0.5, -0.2, 0.84) },
  fThru: { yaw: 0.6, crouch: 0.12, uaR: V(0.1, -0.1, 0.99), faR: V(0.6, 0.45, 0.66), rk: V(0.6, 0.6, 0.5), uaL: V(0.5, -0.7, 0.2), faL: V(0.4, -0.2, 0.9) },
  fEnd: { yaw: 0.9, crouch: 0.1, uaR: V(0.6, 0.35, 0.7), faR: V(0.4, 0.85, -0.35), rk: V(0.2, 0.3, -0.93), uaL: V(0.3, -0.6, 0.4), faL: V(0.3, 0.4, 0.86) },
  // rovescio a una mano
  bPrep: { yaw: 1.0, crouch: 0.16, uaR: V(0.55, -0.4, -0.3), faR: V(0.6, -0.1, -0.75), rk: V(0.4, 0.6, -0.7), uaL: V(0.35, -0.6, -0.1), faL: V(0.1, 0.1, -0.95) },
  bDrop: { yaw: 0.95, crouch: 0.2, uaR: V(0.6, -0.55, -0.25), faR: V(0.6, -0.45, -0.6), rk: V(0.5, -0.6, -0.55), uaL: V(0.35, -0.6, -0.1), faL: V(0.2, -0.2, -0.95) },
  bHit: { yaw: 0.25, crouch: 0.16, uaR: V(0.55, -0.45, 0.65), faR: V(0.55, -0.25, 0.8), rk: V(0.95, 0.1, 0.25), uaL: V(0.6, -0.5, -0.5), faL: V(0.5, -0.3, -0.8) },
  bThru: { yaw: -0.1, crouch: 0.12, uaR: V(0.0, 0.1, 0.99), faR: V(-0.3, 0.6, 0.7), rk: V(-0.4, 0.75, 0.4), uaL: V(0.7, -0.4, -0.5), faL: V(0.6, -0.2, -0.75) },
  bEnd: { yaw: -0.4, crouch: 0.1, uaR: V(-0.3, 0.45, 0.83), faR: V(-0.4, 0.85, 0.3), rk: V(-0.3, 0.85, -0.35), uaL: V(0.7, -0.4, -0.5), faL: V(0.6, -0.1, -0.75) },
  // servizio
  sWait: { yaw: 0.9, crouch: 0.05, uaR: V(-0.3, -0.9, 0.2), faR: V(0.2, -0.3, 0.92), rk: V(0.4, 0.3, 0.86), uaL: V(0.1, -0.6, 0.8), faL: V(0.0, -0.05, 0.99) },
  sTrophy: { yaw: 0.9, crouch: 0.18, uaR: V(-0.9, 0.35, -0.2), faR: V(-0.1, 0.95, -0.3), rk: V(0.1, -0.75, -0.65), uaL: V(0.1, 0.97, 0.2), faL: V(0.05, 0.99, 0.1) },
  sDrop: { yaw: 0.6, crouch: 0.22, uaR: V(-0.8, 0.5, -0.3), faR: V(-0.2, 0.9, -0.4), rk: V(0.0, -0.95, -0.3), uaL: V(0.1, 0.6, 0.8), faL: V(0.1, 0.3, 0.95) },
  sHit: { yaw: 0.0, crouch: 0.0, uaR: V(-0.2, 0.95, 0.2), faR: V(-0.1, 0.97, 0.25), rk: V(-0.05, 0.95, 0.3), uaL: V(0.3, -0.5, 0.8), faL: V(0.2, -0.3, 0.93) },
  sThru: { yaw: 0.3, crouch: 0.1, uaR: V(0.1, 0.3, 0.95), faR: V(0.3, -0.3, 0.9), rk: V(0.2, -0.2, 0.95), uaL: V(0.3, -0.8, 0.5), faL: V(0.3, -0.4, 0.86) },
  sEnd: { yaw: 0.5, crouch: 0.12, uaR: V(0.5, -0.7, 0.5), faR: V(0.6, -0.6, 0.5), rk: V(0.6, -0.7, 0.3), uaL: V(0.2, -0.8, 0.5), faL: V(0.2, -0.5, 0.84) },
};
const KEYS = ['uaR', 'faR', 'rk', 'uaL', 'faL'];
function mix(a, b, k) {
  const o = { yaw: a.yaw + (b.yaw - a.yaw) * k, crouch: a.crouch + (b.crouch - a.crouch) * k };
  for (const n of KEYS) o[n] = a[n].clone().lerp(b[n], k).normalize();
  return o;
}
function seq(keys, u) {
  const n = keys.length - 1, f = clamp(u, 0, 1) * n, i = Math.min(n - 1, Math.floor(f));
  return mix(POSES[keys[i]], POSES[keys[i + 1]], smooth(f - i));
}
/** La posa del giocatore [i] adesso, più gambe e bacino. */
function poseFor(p, i, t) {
  const S = A.S;
  const u = p.swing >= 0 ? clamp(p.swing / 0.36, 0, 1) : -1;
  let pose;
  const serving = p.serveAnim && (S.phase === 'toss' || S.phase === 'rally');
  if (serving && S.phase === 'toss' && S.tossBy === i) pose = mix(POSES.sWait, POSES.sTrophy, smooth(clamp(S.timer / 0.32, 0, 1)));
  else if (serving && u >= 0) pose = seq(['sTrophy', 'sDrop', 'sHit', 'sThru', 'sEnd'], u);
  else if (serving) pose = POSES.sTrophy;
  else if (u >= 0) pose = seq(p.fore ? ['fPrep', 'fDrop', 'fHit', 'fThru', 'fEnd'] : ['bPrep', 'bDrop', 'bHit', 'bThru', 'bEnd'], u);
  else if (S.phase === 'serve' && S.server === i) pose = POSES.sWait;
  else {
    const c = S.contact, wait = c && !c.done && c.who === i && S.phase === 'rally';
    const prep = wait ? smooth(clamp(1 - (c.t - S.shotT) / 0.9, 0, 1)) : 0;
    pose = prep > 0 ? mix(POSES.ready, p.fore ? POSES.fPrep : POSES.bPrep, prep) : mix(POSES.ready, POSES.ready, 0);
  }
  // gambe: corsa o attesa (piccoli saltelli)
  const speed = Math.hypot(p.tx - p.x, p.tz - p.z);
  const run = clamp(speed / 0.6, 0, 1) * (A.S.paused ? 0 : 1);
  const ph = t * 11 + i;
  const legs = {};
  for (const [side, sg] of [['R', -1], ['L', 1]]) {
    const sw = Math.sin(ph + (sg > 0 ? Math.PI : 0)) * run;
    legs['th' + side] = V(sg * 0.12, -0.95, 0.12 + 0.55 * sw + pose.crouch * 1.2);
    legs['sh' + side] = V(sg * 0.04, -0.95, -0.1 - 0.5 * Math.max(0, -sw) - pose.crouch * 1.1);
  }
  pose.legs = legs;
  pose.bob = run * Math.abs(Math.sin(ph)) * 0.05;
  return pose;
}

/* =====================================================================
   Scheletro semplice (per lo stile low-poly, il braccio in prima persona e la racchetta)
   ===================================================================== */
const SEG = { ua: 0.29, fa: 0.27, th: 0.46, sh: 0.45, grip: 0.07, rk: 0.68 };
/** Giunti nello spazio del giocatore dalla posa. */
function joints(pose) {
  const hipH = 0.94 - pose.crouch * 0.55 + pose.bob;
  const yawQ = new THREE.Quaternion().setFromAxisAngle(UP, pose.yaw);
  const pelvis = new THREE.Vector3(0, hipH, 0);
  const chest = pelvis.clone().add(new THREE.Vector3(0, 0.5, 0.04 + pose.crouch * 0.25));
  const head = chest.clone().add(new THREE.Vector3(0, 0.27, 0.02));
  const side = x => new THREE.Vector3(x, 0, 0).applyQuaternion(yawQ);
  const shR = chest.clone().add(side(-0.2)).add(new THREE.Vector3(0, -0.04, 0));
  const shL = chest.clone().add(side(0.2)).add(new THREE.Vector3(0, -0.04, 0));
  const elR = shR.clone().addScaledVector(pose.uaR, SEG.ua), wrR = elR.clone().addScaledVector(pose.faR, SEG.fa);
  const elL = shL.clone().addScaledVector(pose.uaL, SEG.ua), wrL = elL.clone().addScaledVector(pose.faL, SEG.fa);
  const hipR = pelvis.clone().add(side(-0.1)), hipL = pelvis.clone().add(side(0.1));
  const knR = hipR.clone().addScaledVector(pose.legs.thR, SEG.th), anR = knR.clone().addScaledVector(pose.legs.shR, SEG.sh);
  const knL = hipL.clone().addScaledVector(pose.legs.thL, SEG.th), anL = knL.clone().addScaledVector(pose.legs.shL, SEG.sh);
  const grip = wrR.clone().addScaledVector(pose.rk, SEG.grip);
  return { pelvis, chest, head, shR, shL, elR, elL, wrR, wrL, hipR, hipL, knR, knL, anR, anL, grip, yawQ, yaw: pose.yaw };
}

/* =====================================================================
   Materiali e oggetti
   ===================================================================== */
function canvasTex(w, h, draw, repeat) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = renderer.capabilities.getMaxAnisotropy();
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); }
  return t;
}
function mat(color, opts = {}) {
  if (style === 'low') return new THREE.MeshLambertMaterial({ color, flatShading: true, ...opts });
  return new THREE.MeshStandardMaterial({ color, roughness: 0.75, metalness: 0, ...opts });
}
function noiseTex(base, amp, size = 256, repeat = [8, 8]) {
  return canvasTex(size, size, (g, w, h) => {
    g.fillStyle = base; g.fillRect(0, 0, w, h);
    const img = g.getImageData(0, 0, w, h), d = img.data;
    for (let i = 0; i < d.length; i += 4) { const n = (Math.random() - 0.5) * amp; d[i] += n; d[i + 1] += n; d[i + 2] += n; }
    g.putImageData(img, 0, 0);
  }, repeat);
}

/** Racchetta: telaio ellittico, corde, cuore e manico (origine al manico, testa verso +y). */
function makeRacket(frameColor) {
  const g = new THREE.Group();
  const frameMat = style === 'low' ? mat(frameColor) : new THREE.MeshStandardMaterial({ color: frameColor, roughness: 0.35, metalness: 0.3 });
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.13, 0.011, style === 'low' ? 4 : 8, style === 'low' ? 14 : 40), frameMat);
  ring.scale.set(0.85, 1.25, 1); ring.position.y = 0.5; g.add(ring);
  const strings = new THREE.Mesh(new THREE.CircleGeometry(0.128, 24), new THREE.MeshBasicMaterial({
    map: canvasTex(128, 128, (c, w, h) => {
      c.clearRect(0, 0, w, h); c.strokeStyle = 'rgba(240,240,240,.85)'; c.lineWidth = 2;
      for (let x = 4; x < w; x += 9) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, h); c.stroke(); }
      for (let y = 4; y < h; y += 8) { c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke(); }
    }), transparent: true, side: THREE.DoubleSide, depthWrite: false,
  }));
  strings.scale.set(0.85, 1.25, 1); strings.position.y = 0.5; g.add(strings);
  for (const sg of [-1, 1]) {
    const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.009, 0.009, 0.2, 5), frameMat);
    arm.position.set(sg * 0.045, 0.25, 0); arm.rotation.z = -sg * 0.42; g.add(arm);
  }
  const handle = new THREE.Mesh(new THREE.CylinderGeometry(0.016, 0.018, 0.2, 8), mat('#202329'));
  handle.position.y = 0.06; g.add(handle);
  g.traverse(o => { o.castShadow = true; });
  return g;
}

/** Corpo low-poly: cilindri tra i giunti, testa, fascia, scarpe. */
function makeLowBody(shirt, shorts, hair) {
  const g = new THREE.Group(), skin = mat('#e0a27c'), cl = mat(shirt), sh = mat(shorts), white = mat('#f2f2f2'), hairM = mat(hair);
  const limb = (r0, r1, m) => { const o = new THREE.Mesh(new THREE.CylinderGeometry(r1, r0, 1, style === 'low' ? 6 : 12), m); o.castShadow = true; g.add(o); return o; };
  const parts = {
    torso: limb(0.15, 0.19, cl), hips: limb(0.17, 0.16, sh),
    uaR: limb(0.05, 0.06, cl), faR: limb(0.04, 0.05, skin), uaL: limb(0.05, 0.06, cl), faL: limb(0.04, 0.05, skin),
    thR: limb(0.07, 0.085, sh), shR: limb(0.045, 0.06, skin), thL: limb(0.07, 0.085, sh), shL: limb(0.045, 0.06, skin),
    neck: limb(0.05, 0.05, skin),
  };
  const head = new THREE.Mesh(style === 'low' ? new THREE.IcosahedronGeometry(0.12, 1) : new THREE.SphereGeometry(0.12, 20, 16), skin);
  head.castShadow = true; g.add(head);
  const hairM_ = new THREE.Mesh(new THREE.SphereGeometry(0.125, style === 'low' ? 6 : 18, 8, 0, Math.PI * 2, 0, Math.PI * 0.5), hairM);
  head.add(hairM_); hairM_.position.y = 0.01;
  const band = new THREE.Mesh(new THREE.CylinderGeometry(0.124, 0.124, 0.03, style === 'low' ? 8 : 24, 1, true), white);
  head.add(band); band.position.y = 0.04;
  const shoes = [0, 1].map(() => { const s = new THREE.Mesh(new THREE.BoxGeometry(0.11, 0.08, 0.27), white); s.castShadow = true; g.add(s); return s; });
  const hands = [0, 1].map(() => { const s = new THREE.Mesh(new THREE.SphereGeometry(0.045, 8, 6), skin); g.add(s); return s; });
  return { group: g, parts, head, shoes, hands };
}
const _dir = new THREE.Vector3();
function placeLimb(o, a, b) {
  _dir.subVectors(b, a); const len = _dir.length();
  o.position.copy(a).addScaledVector(_dir, 0.5);
  o.quaternion.setFromUnitVectors(UP, _dir.normalize());
  o.scale.set(1, len, 1);
}
function updateLowBody(body, j) {
  const P = body.parts;
  placeLimb(P.torso, j.pelvis.clone().add(new THREE.Vector3(0, 0.08, 0)), j.chest.clone().add(new THREE.Vector3(0, 0.02, 0)));
  P.torso.quaternion.multiply(tmpQ.setFromAxisAngle(UP, j.yaw));  // spalle girate col busto
  P.torso.scale.x = 1.25; P.torso.scale.z = 0.8;
  placeLimb(P.hips, j.pelvis.clone().add(new THREE.Vector3(0, -0.1, 0)), j.pelvis.clone().add(new THREE.Vector3(0, 0.1, 0)));
  P.hips.scale.x = 1.15; P.hips.scale.z = 0.8;
  placeLimb(P.neck, j.chest, j.head);
  placeLimb(P.uaR, j.shR, j.elR); placeLimb(P.faR, j.elR, j.wrR);
  placeLimb(P.uaL, j.shL, j.elL); placeLimb(P.faL, j.elL, j.wrL);
  placeLimb(P.thR, j.hipR, j.knR); placeLimb(P.shR, j.knR, j.anR);
  placeLimb(P.thL, j.hipL, j.knL); placeLimb(P.shL, j.knL, j.anL);
  body.head.position.copy(j.head).add(new THREE.Vector3(0, 0.05, 0));
  body.head.quaternion.copy(j.yawQ);
  body.shoes[0].position.copy(j.anR).add(new THREE.Vector3(0, -0.02, 0.06)); body.shoes[0].quaternion.copy(j.yawQ);
  body.shoes[1].position.copy(j.anL).add(new THREE.Vector3(0, -0.02, 0.06)); body.shoes[1].quaternion.copy(j.yawQ);
  body.hands[0].position.copy(j.wrR); body.hands[1].position.copy(j.wrL);
}

/* ---------------- Corpo realistico (male_base_mesh.glb, CC0) ---------------- */
const bone = (body, n) => body.bones[n] || body.bones[n.replace(/\./g, '')];
const BONES = {
  uaR: ['upper_arm.R', 'forearm.R'], faR: ['forearm.R', 'hand.R'], hR: ['hand.R', 'f_middle.01.R'],
  uaL: ['upper_arm.L', 'forearm.L'], faL: ['forearm.L', 'hand.L'],
  thR: ['thigh.R', 'shin.R'], shR: ['shin.R', 'foot.R'], thL: ['thigh.L', 'shin.L'], shL: ['shin.L', 'foot.L'],
};
function makeRealBody(shirt, shorts, hair) {
  return makeRealistic(male, { shirt, shorts, hair });
}
/** Gira un osso perché punti verso [dirW] (direzione nel mondo). */
function aim(bone, child, dirW) {
  if (!bone || !child) return;
  bone.updateWorldMatrix(true, true);
  const a = bone.getWorldPosition(tmpV), b = child.getWorldPosition(tmpV2);
  const cur = b.sub(a).normalize();
  const q = tmpQ.setFromUnitVectors(cur, dirW);
  const wq = bone.getWorldQuaternion(tmpQ2).premultiply(q);
  const pq = bone.parent.getWorldQuaternion(new THREE.Quaternion()).invert();
  bone.quaternion.copy(pq.multiply(wq));
  bone.updateWorldMatrix(false, true);
}
function updateRealBody(body, pose, look) {
  for (const [k, q] of Object.entries(body.rest)) body.bones[k].quaternion.copy(q);
  for (const [k, q] of Object.entries(body.curl || {})) body.bones[k].quaternion.multiply(q);
  body.root.position.y = body.yOff - pose.crouch * 0.5 + pose.bob;
  body.group.updateWorldMatrix(true, true);
  // busto girato (spalle) e un po' piegato in avanti
  const spine = bone(body, 'spine.002') || bone(body, 'spine.001');
  if (spine) {
    const wq = spine.getWorldQuaternion(new THREE.Quaternion());
    const turn = new THREE.Quaternion().setFromAxisAngle(UP, pose.yaw * 0.8);
    const lean = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0).transformDirection(body.group.matrixWorld), pose.crouch * 0.6);
    wq.premultiply(turn).premultiply(lean);
    spine.quaternion.copy(spine.parent.getWorldQuaternion(new THREE.Quaternion()).invert().multiply(wq));
    spine.updateWorldMatrix(false, true);
  }
  const w = d => d.clone().transformDirection(body.group.matrixWorld);
  const dirs = { uaR: pose.uaR, faR: pose.faR, hR: pose.rk, uaL: pose.uaL, faL: pose.faL, thR: pose.legs.thR, shR: pose.legs.shR, thL: pose.legs.thL, shL: pose.legs.shL };
  for (const k of ['thR', 'shR', 'thL', 'shL', 'uaR', 'faR', 'hR', 'uaL', 'faL']) aim(bone(body, BONES[k][0]), bone(body, BONES[k][1]), w(dirs[k]));
  // la testa segue la palla (girandosi al massimo di ~60°)
  const neck = bone(body, 'spine.004');
  if (neck && look) {
    const hp = neck.getWorldPosition(new THREE.Vector3());
    const fw = new THREE.Vector3(0, 0, 1).transformDirection(body.group.matrixWorld).applyAxisAngle(UP, pose.yaw * 0.8);
    const to = look.clone().sub(hp); to.y = 0;
    if (to.lengthSq() > 0.01) {
      fw.y = 0; fw.normalize(); to.normalize();
      const ang = clamp(Math.atan2(fw.clone().cross(to).y, fw.dot(to)), -1.05, 1.05);
      const wq = neck.getWorldQuaternion(new THREE.Quaternion()).premultiply(new THREE.Quaternion().setFromAxisAngle(UP, ang * 0.75));
      neck.quaternion.copy(neck.parent.getWorldQuaternion(new THREE.Quaternion()).invert().multiply(wq));
      neck.updateWorldMatrix(false, true);
    }
  }
}

/* =====================================================================
   Scena
   ===================================================================== */
function buildScene() {
  const S = A.consts;
  scene = new THREE.Scene();
  ROOT = new THREE.Group(); ROOT.scale.x = -1; scene.add(ROOT);
  scene.background = canvasTex(4, 256, (g, w, h) => {
    const gr = g.createLinearGradient(0, 0, 0, h);
    gr.addColorStop(0, '#02040b'); gr.addColorStop(0.55, '#0b1430'); gr.addColorStop(1, '#1b2b55');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
  });
  scene.fog = new THREE.Fog('#0b1430', 70, 170);
  ROOT.add(new THREE.HemisphereLight('#9fb4ff', '#1d3a26', style === 'low' ? 1.3 : 0.9));
  const sun = new THREE.DirectionalLight('#fff6e8', style === 'low' ? 2.2 : 2.6);
  sun.position.set(-14, 30, -8); sun.target.position.set(0, 0, 0);
  sun.castShadow = true;
  const sm = style === 'low' ? 1024 : 2048;
  sun.shadow.mapSize.set(sm, sm);
  Object.assign(sun.shadow.camera, { left: -20, right: 20, top: 24, bottom: -24, near: 1, far: 80 });
  sun.shadow.bias = -0.0005; sun.shadow.normalBias = 0.02;
  ROOT.add(sun, sun.target);
  if (style === 'real') {
    // riflessi morbidi dell'ambiente su pelle, tessuti e racchetta
    const pm = new THREE.PMREMGenerator(renderer);
    scene.environment = pm.fromScene(new RoomEnvironment(), 0.04).texture;
    scene.environmentIntensity = 0.35;
    pm.dispose();
    const fill = new THREE.DirectionalLight('#cfe0ff', 0.8); fill.position.set(16, 20, 14); ROOT.add(fill);
  }

  const { HL, HW, DW, SVC } = S;
  const plane = (w, d, m, y = 0) => { const o = new THREE.Mesh(new THREE.PlaneGeometry(w, d), m); o.rotation.x = -Math.PI / 2; o.position.y = y; o.receiveShadow = true; ROOT.add(o); return o; };
  const real = style === 'real';
  plane(160, 160, real ? new THREE.MeshStandardMaterial({ map: noiseTex('#173f2b', 18, 256, [40, 40]), roughness: 1 }) : mat('#1b4a32'));
  plane((DW + 3.6) * 2, (HL + 6.4) * 2, real ? new THREE.MeshStandardMaterial({ map: noiseTex('#2c6e9e', 14, 256, [6, 12]), roughness: 0.92 }) : mat('#2c6e9e'), 0.002);
  plane(DW * 2, HL * 2, real ? new THREE.MeshStandardMaterial({ map: noiseTex('#24589c', 14, 256, [4, 10]), roughness: 0.88 }) : mat('#25589a'), 0.004);
  const lineM = real ? new THREE.MeshStandardMaterial({ color: '#f5f7f3', roughness: 0.6 }) : new THREE.MeshBasicMaterial({ color: '#f5f7f3' });
  const line = (x1, z1, x2, z2, w = 0.05) => {
    const o = new THREE.Mesh(new THREE.PlaneGeometry(Math.abs(x2 - x1) + w, Math.abs(z2 - z1) + w), lineM);
    o.rotation.x = -Math.PI / 2; o.position.set((x1 + x2) / 2, 0.008, (z1 + z2) / 2); o.receiveShadow = true; ROOT.add(o);
  };
  line(-DW, -HL, DW, -HL, 0.1); line(-DW, HL, DW, HL, 0.1);
  [-DW, -HW, HW, DW].forEach(x => line(x, -HL, x, HL));
  line(-HW, -SVC, HW, -SVC); line(-HW, SVC, HW, SVC); line(0, -SVC, 0, SVC);
  line(0, -HL, 0, -HL + 0.2); line(0, HL - 0.2, 0, HL);

  // rete
  const x0 = -DW - 0.914, x1 = DW + 0.914;
  const netGeo = new THREE.PlaneGeometry(x1 - x0, 1, 40, 1), np = netGeo.attributes.position;
  for (let i = 0; i < np.count; i++) { const x = np.getX(i); np.setY(i, np.getY(i) > 0 ? A.netH(x) : 0); }
  netGeo.computeVertexNormals();
  const net = new THREE.Mesh(netGeo, new THREE.MeshBasicMaterial({
    map: canvasTex(64, 64, (g, w, h) => {
      g.clearRect(0, 0, w, h); g.strokeStyle = 'rgba(20,24,30,.9)'; g.lineWidth = 3;
      for (let k = 0; k <= w; k += 16) { g.beginPath(); g.moveTo(k, 0); g.lineTo(k, h); g.stroke(); g.beginPath(); g.moveTo(0, k); g.lineTo(w, k); g.stroke(); }
    }, [90, 22]), transparent: true, side: THREE.DoubleSide, depthWrite: false, opacity: 0.9,
  }));
  ROOT.add(net);
  const tape = new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([new THREE.Vector3(x0, 1.07, 0), new THREE.Vector3(0, 0.914, 0), new THREE.Vector3(x1, 1.07, 0)]), 30, 0.025, 6), mat('#ffffff'));
  tape.castShadow = true; ROOT.add(tape);
  const strap = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.914, 0.01), mat('#ffffff')); strap.position.set(0, 0.457, 0); ROOT.add(strap);
  for (const x of [x0, x1]) {
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 1.12, 10), real ? new THREE.MeshStandardMaterial({ color: '#b9c0cc', metalness: 0.7, roughness: 0.3 }) : mat('#b9c0cc'));
    post.position.set(x, 0.56, 0); post.castShadow = true; ROOT.add(post);
  }

  // muri con le scritte
  const zb = HL + 7.5, xs = DW + 8;
  const wallTex = canvasTex(2048, 64, (g, w, h) => {
    g.fillStyle = '#0d5c38'; g.fillRect(0, 0, w, h);
    g.fillStyle = '#ffffff'; g.font = '800 40px system-ui'; g.textAlign = 'center'; g.textBaseline = 'middle';
    ['TENNIS SCORE MANAGER', 'TSM ARENA', 'TENNIS SCORE MANAGER', 'TSM ARENA'].forEach((t, i) => g.fillText(t, w * (i + 0.5) / 4, h / 2 + 2));
  });
  const wallM = real ? new THREE.MeshStandardMaterial({ map: wallTex, roughness: 0.6, emissive: '#ffffff', emissiveMap: wallTex, emissiveIntensity: 0.25 }) : new THREE.MeshLambertMaterial({ map: wallTex });
  const wall = new THREE.Mesh(new THREE.PlaneGeometry(80, 1.3), wallM); wall.position.set(0, 0.65, zb); wall.rotation.y = Math.PI; wall.scale.x = -1; ROOT.add(wall);  // scritte non specchiate
  const sideWallM = mat('#0b5233');
  for (const sg of [-1, 1]) { const o = new THREE.Mesh(new THREE.PlaneGeometry(HL * 2 + 20, 1.3), sideWallM); o.position.set(sg * xs, 0.65, -2); o.rotation.y = -sg * Math.PI / 2; ROOT.add(o); }

  // tribune a gradoni con il pubblico
  const standM = mat('#1b2236');
  const rows = real ? 16 : 12, stepH = 0.62, stepD = 0.9;
  const fans = [];
  const addStand = (along, cx, cz, len, sgOut, axisX) => {
    for (let r = 0; r < rows; r++) {
      const y = 1.3 + r * stepH, d = 1.2 + r * stepD;
      const geo = new THREE.BoxGeometry(axisX ? len : stepD, stepH, axisX ? stepD : len);
      const o = new THREE.Mesh(geo, standM);
      if (axisX) o.position.set(cx, y - stepH / 2, cz + sgOut * d); else o.position.set(cx + sgOut * d, y - stepH / 2, cz);
      o.receiveShadow = real; ROOT.add(o);
      for (let s = -len / 2 + 0.35; s < len / 2; s += 0.62) {
        if (Math.random() < 0.07) continue;  // qualche posto vuoto
        const p = axisX ? new THREE.Vector3(cx + s + (Math.random() - 0.5) * 0.15, y, cz + sgOut * d) : new THREE.Vector3(cx + sgOut * d, y, cz + s + (Math.random() - 0.5) * 0.15);
        fans.push(p);
      }
    }
  };
  addStand(true, 0, zb, 78, 1, true);
  addStand(false, xs, -2, HL * 2 + 18, 1, false);
  addStand(false, -xs, -2, HL * 2 + 18, -1, false);
  const shirts = ['#e53935', '#ffd600', '#f5f5f5', '#42a5f5', '#66bb6a', '#ab47bc', '#ff8a65', '#26c6da', '#37474f', '#5c6bc0', '#ffffff', '#ef6c00'];
  const skins = ['#f1c7a5', '#d9a07a', '#a86f4c', '#6d4530', '#f5d3b8'];
  const bodyGeo = real ? new THREE.CapsuleGeometry(0.2, 0.3, 3, 8) : new THREE.BoxGeometry(0.38, 0.6, 0.3);
  const headGeo = real ? new THREE.SphereGeometry(0.11, 10, 8) : new THREE.IcosahedronGeometry(0.12, 0);
  const bodies = new THREE.InstancedMesh(bodyGeo, mat('#ffffff'), fans.length);
  const heads = new THREE.InstancedMesh(headGeo, mat('#ffffff'), fans.length);
  const m4 = new THREE.Matrix4(), col = new THREE.Color();
  fans.forEach((p, i) => {
    m4.makeTranslation(p.x, p.y + 0.35, p.z); bodies.setMatrixAt(i, m4);
    bodies.setColorAt(i, col.set(shirts[(Math.random() * shirts.length) | 0]));
    m4.makeTranslation(p.x, p.y + 0.78, p.z); heads.setMatrixAt(i, m4);
    heads.setColorAt(i, col.set(skins[(Math.random() * skins.length) | 0]));
  });
  ROOT.add(bodies, heads);
  G.fans = { bodies, heads, pos: fans };

  // torri dei riflettori
  const glow = canvasTex(128, 128, (g, w, h) => {
    const rg = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    rg.addColorStop(0, 'rgba(255,255,240,1)'); rg.addColorStop(0.2, 'rgba(255,250,220,.5)'); rg.addColorStop(1, 'rgba(255,240,200,0)');
    g.fillStyle = rg; g.fillRect(0, 0, w, h);
  });
  G.glowTex = glow;
  for (const [x, z] of [[-(DW + 22), HL + 16], [DW + 22, HL + 16], [-(DW + 22), -HL - 8], [DW + 22, -HL - 8]]) {
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.4, 26, 8), mat('#2a3550')); pole.position.set(x, 13, z); ROOT.add(pole);
    const panel = new THREE.Mesh(new THREE.BoxGeometry(4, 2, 0.4), new THREE.MeshBasicMaterial({ color: '#fffbe8' }));
    panel.position.set(x, 26.5, z); panel.lookAt(0, 0, 0); ROOT.add(panel);
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glow, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
    sp.scale.set(22, 22, 1); sp.position.copy(panel.position); ROOT.add(sp);
  }

  // palla, ombra, scia, bersaglio, scintille
  const ballTex = canvasTex(256, 128, (g, w, h) => {
    g.fillStyle = '#d6f23a'; g.fillRect(0, 0, w, h);
    g.strokeStyle = '#f7fbe8'; g.lineWidth = 7;
    g.beginPath(); for (let x = 0; x <= w; x += 2) { const y = h / 2 + Math.sin(x / w * Math.PI * 4) * h * 0.28; x ? g.lineTo(x, y) : g.moveTo(x, y); } g.stroke();
  });
  G.ball = new THREE.Mesh(new THREE.SphereGeometry(0.045, 20, 14), new THREE.MeshStandardMaterial({ map: ballTex, roughness: 0.8, emissive: '#3a4a00', emissiveIntensity: 0.6 }));
  G.ball.castShadow = true; ROOT.add(G.ball);
  G.ballGlow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glow, color: '#e8ff90', blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.55 }));
  ROOT.add(G.ballGlow);
  G.blob = new THREE.Mesh(new THREE.CircleGeometry(0.06, 16), new THREE.MeshBasicMaterial({ color: '#000', transparent: true, opacity: 0.35, depthWrite: false }));
  G.blob.rotation.x = -Math.PI / 2; ROOT.add(G.blob);
  G.trail = new THREE.InstancedMesh(new THREE.SphereGeometry(0.035, 8, 6), new THREE.MeshBasicMaterial({ color: '#e8ff90', transparent: true, opacity: 0.25, depthWrite: false }), 9);
  ROOT.add(G.trail);
  G.ring = new THREE.Mesh(new THREE.RingGeometry(0.92, 1, 48), new THREE.MeshBasicMaterial({ color: '#ffd36b', transparent: true, depthWrite: false, depthTest: false, side: THREE.DoubleSide }));
  G.ring2 = new THREE.Mesh(new THREE.RingGeometry(0.95, 1, 48), G.ring.material.clone());
  scene.add(G.ring, G.ring2);
  G.sparks = new THREE.Points(new THREE.BufferGeometry(), new THREE.PointsMaterial({ color: '#f6ffc0', map: glow, size: 0.09, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false }));
  G.sparks.geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(300), 3));
  ROOT.add(G.sparks);

  // giocatori, racchette e braccio in prima persona
  G.players = A.P.map((p, i) => {
    let body = style === 'real' && male ? makeRealBody(p.shirt, p.shorts, p.hair) : null;
    const kind = body ? 'real' : 'low';
    if (!body) body = makeLowBody(p.shirt, p.shorts, p.hair);
    ROOT.add(body.group);
    const racket = makeRacket(i === 0 ? '#d4a900' : '#c62828'); ROOT.add(racket);
    return { body, kind, racket };
  });
  const arm = makeLowBody(A.P[0].shirt, A.P[0].shorts, A.P[0].hair);
  for (const [k, o] of Object.entries(arm.parts)) if (!['uaR', 'faR'].includes(k)) o.visible = false;
  arm.head.visible = false; arm.shoes.forEach(o => (o.visible = false)); arm.hands[1].visible = false;
  const band = new THREE.Mesh(new THREE.BoxGeometry(0.075, 0.03, 0.1), mat('#1b1e26'));
  const screenM = new THREE.Mesh(new THREE.PlaneGeometry(0.05, 0.07), new THREE.MeshBasicMaterial({ color: '#c6f432' }));
  screenM.rotation.x = -Math.PI / 2; screenM.position.y = 0.016; band.add(screenM);
  arm.group.add(band);
  ROOT.add(arm.group);
  G.fpArm = { body: arm, band };
}

/* =====================================================================
   Aggiornamento a ogni fotogramma
   ===================================================================== */
const charQ = i => new THREE.Quaternion().setFromAxisAngle(UP, i === 0 ? 0 : Math.PI);
/** Dallo spazio del giocatore (destra = -x) alle coordinate di gioco (destra di P1 = +x). */
const charDirToGame = (v, q) => new THREE.Vector3(-v.x, v.y, v.z).applyQuaternion(q);
function updatePlayers(t) {
  const fp = A.view() === 'fp';
  A.P.forEach((p, i) => {
    const pl = G.players[i], pose = poseFor(p, i, t), q = charQ(i);
    const toGame = v => charDirToGame(v, q).add(new THREE.Vector3(p.x, 0, p.z));
    const j = joints(pose);
    const hide = fp && i === 0;
    pl.body.group.visible = !hide;
    if (!hide) {
      pl.body.group.position.set(p.x, 0, p.z); pl.body.group.quaternion.copy(q); pl.body.group.scale.x = -1;
      if (pl.kind === 'real') updateRealBody(pl.body, pose, A.S.ball.show ? new THREE.Vector3(-A.S.ball.x, A.S.ball.y, A.S.ball.z) : null);
      else updateLowBody(pl.body, j);
    }
    // racchetta: dal polso, lungo la direzione della posa
    const rkG = charDirToGame(pose.rk, q);
    let gripG = toGame(j.grip);
    if (pl.kind === 'real' && !hide) {
      const hand = bone(pl.body, 'hand.R');
      if (hand) gripG = ROOT.worldToLocal(hand.getWorldPosition(new THREE.Vector3())).addScaledVector(rkG, 0.08);
    }
    pl.racket.position.copy(gripG);
    pl.racket.quaternion.setFromUnitVectors(UP, rkG);
    // in prima persona: solo braccio destro, braccialetto e racchetta
    if (i === 0) {
      const arm = G.fpArm.body;
      arm.group.visible = fp;
      if (fp) {
        arm.group.position.set(p.x, 0, p.z); arm.group.quaternion.copy(q); arm.group.scale.x = -1;
        updateLowBody(arm, j);
        G.fpArm.band.position.copy(j.wrR).addScaledVector(pose.faR, -0.07);
        G.fpArm.band.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), pose.faR);
      }
    }
  });
}
function updateBall(cam) {
  const b = A.S.ball, show = b.show && b.y > -0.5;
  G.ball.visible = G.blob.visible = G.ballGlow.visible = show;
  if (!show) { G.trail.count = 0; return; }
  G.ball.position.set(b.x, b.y, b.z);
  const d = cam.position.distanceTo(new THREE.Vector3(-b.x, b.y, b.z)), s = Math.max(1, d / 14);
  G.ball.scale.setScalar(s);
  G.ball.rotation.x += 0.25; G.ball.rotation.z += 0.1;
  G.ballGlow.position.copy(G.ball.position); G.ballGlow.scale.setScalar(0.45 * s);
  G.blob.position.set(b.x, 0.012, b.z); G.blob.scale.setScalar(s * clamp(1 - b.y / 8, 0.4, 1));
  const tr = A.S.trail, m4 = new THREE.Matrix4();
  const showTrail = ['rally', 'point', 'fault'].includes(A.S.phase);
  G.trail.count = showTrail ? tr.length : 0;
  tr.forEach((p, k) => { const sc = s * (0.3 + 0.7 * k / tr.length); m4.makeScale(sc, sc, sc).setPosition(p.x, p.y, p.z); G.trail.setMatrixAt(k, m4); });
  G.trail.instanceMatrix.needsUpdate = true;
}
function updateTarget(cam) {
  const S = A.S, c = S.contact;
  let on = S.phase === 'rally' && c && !c.done && A.human(c.who) && !(c.who === 1 && A.view() === 'fp');
  let tc = 0, d = 0;
  if (on) { tc = c.t - S.shotT; d = tc - A.idealTc(); on = tc <= 1.0 && d >= -A.WIN_BEFORE; }
  G.ring.visible = G.ring2.visible = !!on;
  if (!on) return;
  const p = new THREE.Vector3(-c.x, Math.max(0.35, c.y), c.z), good = Math.abs(d) < 0.07;
  const base = 0.16 * Math.max(1, cam.position.distanceTo(p) / 10);
  for (const [r, sc] of [[G.ring, base * (1 + clamp(d, 0, 1) * 2.2)], [G.ring2, base]]) {
    r.position.copy(p); r.quaternion.copy(cam.quaternion); r.scale.setScalar(sc);
    r.material.color.set(good ? '#7dff9c' : '#ffd36b'); r.material.opacity = good ? 0.95 : 0.4 + 0.5 * (1 - clamp(d, 0, 1));
  }
}
function updateSparks() {
  const parts = A.FX.parts, arr = G.sparks.geometry.attributes.position.array, n = Math.min(parts.length, 100);
  for (let k = 0; k < n; k++) { arr[k * 3] = parts[k].x; arr[k * 3 + 1] = parts[k].y; arr[k * 3 + 2] = parts[k].z; }
  G.sparks.geometry.setDrawRange(0, n);
  G.sparks.geometry.attributes.position.needsUpdate = true;
}
export const debug = { cam: null, G: () => G };
function updateCamera() {
  const C = A.CAM;
  if (debug.cam) { camera.position.set(...debug.cam[0]); camera.lookAt(...debug.cam[1]); return; }
  camera.position.set(-C.x, C.y, C.z);
  camera.lookAt(-C.x, C.y - Math.sin(C.pitch), C.z + Math.cos(C.pitch));
  const fov = 2 * Math.atan(0.5 * A.H() / A.FOC()) * 180 / Math.PI;
  if (Math.abs(camera.fov - fov) > 0.01) { camera.fov = fov; camera.updateProjectionMatrix(); }
  if (A.FX.shake > 0) { camera.position.x += (Math.random() - 0.5) * A.FX.shake * 0.12; camera.position.y += (Math.random() - 0.5) * A.FX.shake * 0.12; }
}

/* =====================================================================
   Interfaccia verso la pagina
   ===================================================================== */
let slow = 0, lastT = 0, startT = 0;
export async function init(canvas, api, wanted) {
  A = api;
  style = wanted === 'real' ? 'real' : 'low';
  renderer = new THREE.WebGLRenderer({ canvas, antialias: style === 'real', powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, style === 'real' ? 1.5 : 1));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = style === 'real' ? THREE.PCFSoftShadowMap : THREE.PCFShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = style === 'real' ? 1.05 : 1.0;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  camera = new THREE.PerspectiveCamera(45, 16 / 9, 0.05, 400);
  startT = 0; slow = 0; lastT = 0;
  if (style === 'real' && !male) {
    try { male = await new GLTFLoader().loadAsync(api.asset('male.glb')); } catch (e) { male = null; }
  }
  buildScene();
  resize();
  return style;
}
export function resize() {
  if (!renderer) return;
  renderer.setSize(innerWidth, innerHeight, false);
  camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix();
}
/** Disegna un fotogramma; restituisce false se la TV è troppo lenta per questo stile. */
export function render(t) {
  updateCamera();
  updatePlayers(t);
  updateBall(camera);
  updateTarget(camera);
  updateSparks();
  renderer.render(scene, camera);
  // misura (dopo 1,5 s di avvio): 3 secondi in tutto sotto ~22 fotogrammi al secondo = troppo lento
  const now = performance.now();
  if (!startT) startT = now;
  if (lastT && now - startT > 1500) { const dt = Math.min(now - lastT, 1000); slow = dt > 45 ? slow + dt : Math.max(0, slow - dt * 0.5); }
  lastT = now;
  return slow < 3000;
}
export function dispose() {
  if (renderer) { renderer.dispose(); renderer.forceContextLoss(); }
  renderer = null; scene = null; G = {}; slow = 0; lastT = 0; startT = 0;
}

