// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Giocatore "3D realistica" di TSM Arena, costruito sul corpo male_base_mesh.glb (CC0, ~1200 vertici):
//   - il corpo si arrotonda con due passaggi di suddivisione di Loop (pesi dello scheletro compresi);
//   - maglietta, pantaloncini, scarpe e capelli sono gusci separati sopra la pelle, mossi dallo stesso scheletro;
//   - materiali fisici: pelle con riflesso morbido (sheen) e pori, tessuto a maglia, capelli a ciocche;
//   - occhi, dita chiuse sul manico, testa che segue la palla (in arena3d.js).
// Tutto è generato in codice: nessuna immagine o modello in più oltre al corpo CC0.

import * as THREE from 'three';
import * as SkeletonUtils from 'three/examples/jsm/utils/SkeletonUtils.js';

/* ---------------------------------------------------------------- suddivisione */

/** Pesi dello scheletro di un vertice come mappa osso → peso. */
function readWeights(si, sw, i) {
  const m = new Map();
  for (let k = 0; k < 4; k++) { const w = sw.getComponent(i, k); if (w > 0) m.set(si.getComponent(i, k), (m.get(si.getComponent(i, k)) || 0) + w); }
  return m;
}
function mixWeights(list) {
  const m = new Map();
  for (const [wm, f] of list) for (const [b, w] of wm) m.set(b, (m.get(b) || 0) + w * f);
  return m;
}
function top4(m) {
  const arr = [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4);
  const s = arr.reduce((t, e) => t + e[1], 0) || 1;
  while (arr.length < 4) arr.push([0, 0]);
  return arr.map(([b, w]) => [b, w / s]);
}

/**
 * Suddivisione di Loop di una geometria con scheletro. Le posizioni si arrotondano sulla topologia "saldata"
 * (vertici uguali uniti, anche dove le UV sono tagliate); UV e pesi si interpolano.
 */
export function subdivideSkinned(geo, levels) {
  const pos = geo.attributes.position, uv = geo.attributes.uv, si = geo.attributes.skinIndex, sw = geo.attributes.skinWeight;
  const idx = geo.index ? geo.index.array : [...Array(pos.count).keys()];
  // saldatura
  const key = i => `${pos.getX(i).toFixed(5)},${pos.getY(i).toFixed(5)},${pos.getZ(i).toFixed(5)}`;
  const weldOf = new Map(), vid = new Int32Array(pos.count);
  let P = [], W = [];
  for (let i = 0; i < pos.count; i++) {
    const k = key(i);
    if (!weldOf.has(k)) { weldOf.set(k, P.length); P.push(new THREE.Vector3().fromBufferAttribute(pos, i)); W.push(readWeights(si, sw, i)); }
    vid[i] = weldOf.get(k);
  }
  let T = [];
  for (let t = 0; t < idx.length; t += 3) {
    const c = [idx[t], idx[t + 1], idx[t + 2]];
    T.push({ v: c.map(i => vid[i]), uv: c.map(i => (uv ? new THREE.Vector2().fromBufferAttribute(uv, i) : new THREE.Vector2())) });
  }
  for (let l = 0; l < levels; l++) {
    const edges = new Map(), nb = P.map(() => new Set()), ek = (a, b) => (a < b ? a * 1e6 + b : b * 1e6 + a);
    for (const t of T) for (let e = 0; e < 3; e++) {
      const a = t.v[e], b = t.v[(e + 1) % 3], c = t.v[(e + 2) % 3], k = ek(a, b);
      if (!edges.has(k)) edges.set(k, { a, b, opp: [] });
      edges.get(k).opp.push(c);
      nb[a].add(b); nb[b].add(a);
    }
    const boundary = P.map(() => []);
    for (const e of edges.values()) if (e.opp.length === 1) { boundary[e.a].push(e.b); boundary[e.b].push(e.a); }
    // vertici pari (originali, spostati) e dispari (sugli spigoli)
    const NP = P.map((p, i) => {
      if (boundary[i].length >= 2) return p.clone().multiplyScalar(0.75).addScaledVector(P[boundary[i][0]], 0.125).addScaledVector(P[boundary[i][1]], 0.125);
      const n = nb[i].size, beta = n > 3 ? 3 / (8 * n) : 3 / 16, v = p.clone().multiplyScalar(1 - n * beta);
      for (const j of nb[i]) v.addScaledVector(P[j], beta);
      return v;
    });
    const NW = W.slice();
    for (const e of edges.values()) {
      const v = e.opp.length === 2
        ? P[e.a].clone().add(P[e.b]).multiplyScalar(0.375).addScaledVector(P[e.opp[0]], 0.125).addScaledVector(P[e.opp[1]], 0.125)
        : P[e.a].clone().add(P[e.b]).multiplyScalar(0.5);
      e.id = NP.length; NP.push(v); NW.push(mixWeights([[W[e.a], 0.5], [W[e.b], 0.5]]));
    }
    const NT = [];
    for (const t of T) {
      const [a, b, c] = t.v, [ua, ub, uc] = t.uv;
      const ab = edges.get(ek(a, b)).id, bc = edges.get(ek(b, c)).id, ca = edges.get(ek(c, a)).id;
      const uab = ua.clone().lerp(ub, 0.5), ubc = ub.clone().lerp(uc, 0.5), uca = uc.clone().lerp(ua, 0.5);
      NT.push({ v: [a, ab, ca], uv: [ua, uab, uca] }, { v: [ab, b, bc], uv: [uab, ub, ubc] }, { v: [ca, bc, c], uv: [uca, ubc, uc] }, { v: [ab, bc, ca], uv: [uab, ubc, uca] });
    }
    P = NP; W = NW; T = NT;
  }
  // normali morbide sulla topologia saldata
  const N = P.map(() => new THREE.Vector3()), e1 = new THREE.Vector3(), e2 = new THREE.Vector3();
  for (const t of T) {
    const [a, b, c] = t.v;
    e1.subVectors(P[b], P[a]); e2.subVectors(P[c], P[a]);
    const fn = new THREE.Vector3().crossVectors(e1, e2);
    N[a].add(fn); N[b].add(fn); N[c].add(fn);
  }
  N.forEach(n => n.normalize());
  return { P, N, W: W.map(top4), T };
}

/** Geometria da una parte dei triangoli suddivisi, gonfiata di [offset] lungo le normali. */
function shellGeometry(sub, triFilter, offset, colorFn, maskFn) {
  const tris = sub.T.filter(triFilter), n = tris.length * 3;
  const p = new Float32Array(n * 3), nr = new Float32Array(n * 3), u = new Float32Array(n * 2), sI = new Uint16Array(n * 4), sW = new Float32Array(n * 4);
  const col = colorFn ? new Float32Array(n * 3) : null, msk = maskFn ? new Float32Array(n) : null;
  let k = 0;
  for (const t of tris) for (let c = 0; c < 3; c++, k++) {
    const w = t.v[c], P = sub.P[w], N = sub.N[w];
    p[k * 3] = P.x + N.x * offset; p[k * 3 + 1] = P.y + N.y * offset; p[k * 3 + 2] = P.z + N.z * offset;
    nr[k * 3] = N.x; nr[k * 3 + 1] = N.y; nr[k * 3 + 2] = N.z;
    u[k * 2] = t.uv[c].x; u[k * 2 + 1] = t.uv[c].y;
    sub.W[w].forEach(([b, wt], j) => { sI[k * 4 + j] = b; sW[k * 4 + j] = wt; });
    if (col) { const cc = colorFn(w); col[k * 3] = cc.r; col[k * 3 + 1] = cc.g; col[k * 3 + 2] = cc.b; }
    if (msk) msk[k] = maskFn(w);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(p, 3));
  g.setAttribute('normal', new THREE.BufferAttribute(nr, 3));
  g.setAttribute('uv', new THREE.BufferAttribute(u, 2));
  g.setAttribute('skinIndex', new THREE.BufferAttribute(sI, 4));
  g.setAttribute('skinWeight', new THREE.BufferAttribute(sW, 4));
  if (col) g.setAttribute('color', new THREE.BufferAttribute(col, 3));
  if (msk) g.setAttribute('clothMask', new THREE.BufferAttribute(msk, 1));
  return g;
}

/* ---------------------------------------------------------------- texture generate in codice */

/** Mappa delle normali da un campo di altezze h(x, y) in [0, 1] (ripetibile). */
function normalTex(size, h, strength, repeat) {
  const c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d'), img = g.createImageData(size, size), H = new Float32Array(size * size);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) H[y * size + x] = h(x / size, y / size);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const at = (xx, yy) => H[((yy + size) % size) * size + ((xx + size) % size)];
    const dx = (at(x + 1, y) - at(x - 1, y)) * strength, dy = (at(x, y + 1) - at(x, y - 1)) * strength;
    const l = Math.hypot(dx, dy, 1), i = (y * size + x) * 4;
    img.data[i] = (-dx / l * 0.5 + 0.5) * 255; img.data[i + 1] = (-dy / l * 0.5 + 0.5) * 255; img.data[i + 2] = (1 / l * 0.5 + 0.5) * 255; img.data[i + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat, repeat);
  return t;
}
let TEX = null;
function textures() {
  if (TEX) return TEX;
  const rnd = new Float32Array(64 * 64).map(() => Math.random());
  const noise = (x, y) => {   // rumore di valore ripetibile
    const fx = x * 64, fy = y * 64, ix = Math.floor(fx), iy = Math.floor(fy), tx = fx - ix, ty = fy - iy;
    const v = (a, b) => rnd[((b & 63) * 64) + (a & 63)];
    const s = t => t * t * (3 - 2 * t);
    return (v(ix, iy) * (1 - s(tx)) + v(ix + 1, iy) * s(tx)) * (1 - s(ty)) + (v(ix, iy + 1) * (1 - s(tx)) + v(ix + 1, iy + 1) * s(tx)) * s(ty);
  };
  TEX = {
    // maglia: file di "v" fitte, con un po' di irregolarità
    knit: normalTex(128, (x, y) => Math.abs(Math.sin(x * Math.PI * 32 + Math.sin(y * Math.PI * 64) * 0.9)) * 0.7 + noise(x * 2, y * 2) * 0.3, 2.2, 6),
    // pelle: pori e piccole irregolarità
    skin: normalTex(128, (x, y) => noise(x * 4, y * 4) * 0.6 + noise(x * 1, y * 1) * 0.4, 1.2, 5),
    // capelli: ciocche allungate
    hair: normalTex(128, (x, y) => noise(x * 8, y * 0.5) * 0.7 + noise(x * 16, y * 1) * 0.3, 4, 3),
    // scarpe: tela a trama fine
    shoe: normalTex(64, (x, y) => (Math.sin(x * Math.PI * 24) * Math.sin(y * Math.PI * 24)) * 0.5 + 0.5, 1.5, 8),
  };
  return TEX;
}

/**
 * Il bordo di un indumento non segue i triangoli (sarebbe a zigzag): ogni vertice ha una "maschera" continua
 * 0..1 e il colore si scarta dove è sotto 0,5, così orli, maniche e colletto vengono dritti.
 */
function masked(mat) {
  mat.onBeforeCompile = sh => {
    sh.vertexShader = 'attribute float clothMask;\nvarying float vMask;\n' + sh.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\nvMask = clothMask;');
    sh.fragmentShader = 'varying float vMask;\n' + sh.fragmentShader.replace('void main() {', 'void main() {\n  if (vMask < 0.5) discard;');
  };
  mat.side = THREE.DoubleSide;   // dentro la manica si vede la stoffa, non il vuoto
  return mat;
}

/* ---------------------------------------------------------------- giocatore */

const clean = n => n.replace(/\./g, '');

/**
 * Giocatore realistico. [colors]: maglietta, pantaloncini, capelli. Restituisce il gruppo, le ossa (anche coi nomi
 * senza punti), le rotazioni di riposo e l'altezza di partenza, come si aspetta arena3d.js.
 */
export function makeRealistic(gltf, colors) {
  const root = SkeletonUtils.clone(gltf.scene);
  let base = null; const bones = {};
  root.traverse(o => { if (o.isSkinnedMesh) base = o; if (o.isBone) { bones[o.name] = o; bones[clean(o.name)] = o; } });
  if (!base) return null;
  // il modello deve guardare verso +z (avanti per arena3d.js): se le punte dei piedi stanno dietro, si gira
  root.updateMatrixWorld(true);
  const toe = bones['toe.L'] || bones.toeL, heel = bones['heel.02.L'] || bones.heel02L;
  if (toe && heel && toe.getWorldPosition(new THREE.Vector3()).z < heel.getWorldPosition(new THREE.Vector3()).z) root.rotation.y = Math.PI;
  root.updateMatrixWorld(true);
  const tex = textures();
  const names = base.skeleton.bones.map(b => clean(b.name));
  const sub = subdivideSkinned(base.geometry, 2);

  // misure del corpo a riposo
  const box = new THREE.Box3(); sub.P.forEach(p => box.expandByPoint(p));
  const h = box.max.y - box.min.y, y0 = box.min.y;
  const dom = sub.W.map(w => names[w[0][0]] || '');
  const ny = w => (sub.P[w].y - y0) / h;
  // avanti: le punte dei piedi stanno davanti ai talloni
  const zOf = re => { let s = 0, n = 0; sub.P.forEach((p, i) => { if (re.test(dom[i])) { s += p.z; n++; } }); return n ? s / n : 0; };
  const fwd = Math.sign(zOf(/^toe/) - zOf(/^heel/)) || 1;
  // posizione lungo un osso (0 all'attacco, 1 al figlio) a riposo, nello spazio della geometria
  root.updateMatrixWorld(true);
  const inv = new THREE.Matrix4().copy(base.matrixWorld).invert();
  const bpos = n => (bones[n] ? bones[n].getWorldPosition(new THREE.Vector3()).applyMatrix4(inv) : null);
  const along = (w, from, to) => {
    const a = bpos(from), b = bpos(to); if (!a || !b) return 0;
    const ab = b.clone().sub(a); return sub.P[w].clone().sub(a).dot(ab) / ab.lengthSq();
  };
  const headZ = (() => { let s = 0, n = 0; sub.P.forEach((p, i) => { if (dom[i] === 'spine005') { s += p.z; n++; } }); return n ? s / n : 0; })();

  // maschere continue: per ogni osso un valore 0..1, mescolato coi pesi dello scheletro del vertice
  const sstep = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const blend = f => w => sub.W[w].reduce((acc, [b, wt]) => acc + wt * f(names[b] || '', w), 0);
  const shirtMask = blend((d, w) => {
    if (/^(spine002|spine003)$/.test(d) || /^shoulder/.test(d)) return 1 - sstep(0.83, 0.845, ny(w));
    if (d === 'spine004') return 1 - sstep(0.825, 0.84, ny(w));
    if (d === 'spine001') return sstep(0.52, 0.535, ny(w));
    if (/^upper_arm/.test(d)) { const sd = d.slice(-1); return 1 - sstep(0.42, 0.5, along(w, 'upper_arm.' + sd, 'forearm.' + sd)); }
    return 0;
  });
  const shortsMask = blend((d, w) => {
    if (/^(spine|spine001)$/.test(d) || /^pelvis/.test(d)) return 1 - sstep(0.56, 0.575, ny(w));
    if (/^thigh/.test(d)) { const sd = d.slice(-1); return 1 - sstep(0.5, 0.56, along(w, 'thigh.' + sd, 'shin.' + sd)); }
    return 0;
  });
  const shoeMask = blend((d, w) => (/^(foot|toe|heel)/.test(d) ? 1 : (/^shin/.test(d) ? 1 - sstep(0.06, 0.075, ny(w)) : 0)));
  const hairMask = blend((d, w) => (d === 'spine005'
    ? Math.max(sstep(0.945, 0.955, ny(w)), sstep(0.9, 0.91, ny(w)) * (1 - sstep(-0.005 * h, 0.012 * h, (sub.P[w].z - headZ) * fwd))) : 0));
  const anyOver = m => t => t.v.some(w => m(w) > 0.45);
  const isShirt = w => {
    const d = dom[w];
    if (/^(spine002|spine003)$/.test(d) || /^shoulder/.test(d)) return true;
    if (d === 'spine004') return ny(w) < 0.835;                           // colletto basso
    if (d === 'spine001') return ny(w) > 0.535;                            // la maglietta copre l'elastico
    if (/^upper_arm/.test(d)) { const s = d.slice(-1); return along(w, 'upper_arm.' + s, 'forearm.' + s) < 0.5; }  // manica corta
    return false;
  };
  const isShorts = w => {
    const d = dom[w];
    if (/^(spine|spine001)$/.test(d) || /^pelvis/.test(d)) return true;
    if (/^thigh/.test(d)) { const s = d.slice(-1); return along(w, 'thigh.' + s, 'shin.' + s) < 0.55; }
    return false;
  };
  const isShoe = w => /^(foot|toe|heel)/.test(dom[w]) || (/^shin/.test(dom[w]) && ny(w) < 0.07);
  const isHair = w => dom[w] === 'spine005' && (ny(w) > 0.952 || (ny(w) > 0.905 && (sub.P[w].z - headZ) * fwd < 0.012 * h));
  const all3 = f => t => t.v.every(f);
  const most = f => t => t.v.filter(f).length >= 2;

  // pelle: calze bianche sulle caviglie (colore per vertice), il resto pelle con piccole variazioni
  const skinC = new THREE.Color('#c98a69'), sockC = new THREE.Color('#f2f2f2');
  const bodyGeo = shellGeometry(sub, () => true, 0, w => (/^shin/.test(dom[w]) && ny(w) < 0.115 ? sockC : skinC.clone().offsetHSL(0, 0, (Math.random() - 0.5) * 0.02)));
  const skinMat = new THREE.MeshPhysicalMaterial({
    vertexColors: true, roughness: 0.52, sheen: 0.35, sheenColor: new THREE.Color('#ffb8a0'), sheenRoughness: 0.45,
    normalMap: tex.skin, normalScale: new THREE.Vector2(0.35, 0.35),
  });
  const fabric = (color, sheen = 0.6) => new THREE.MeshPhysicalMaterial({
    color, roughness: 0.82, sheen, sheenColor: new THREE.Color(color).lerp(new THREE.Color('#ffffff'), 0.5), sheenRoughness: 0.6,
    normalMap: tex.knit, normalScale: new THREE.Vector2(0.55, 0.55),
  });
  const soleC = new THREE.Color('#9aa1ab'), shoeC = new THREE.Color('#f7f7f7'), accentC = new THREE.Color(colors.shirt);
  const parts = [
    [bodyGeo, skinMat],
    [shellGeometry(sub, anyOver(shortsMask), 0.009 * h, null, shortsMask), masked(fabric(colors.shorts, 0.4))],
    [shellGeometry(sub, anyOver(shirtMask), 0.012 * h, null, shirtMask), masked(fabric(colors.shirt))],
    [shellGeometry(sub, anyOver(shoeMask), 0.011 * h, w => (ny(w) < 0.012 ? soleC : (ny(w) > 0.045 && /^foot/.test(dom[w]) ? accentC : shoeC)), shoeMask),
      masked(new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.55, normalMap: tex.shoe, normalScale: new THREE.Vector2(0.3, 0.3) }))],
    [shellGeometry(sub, anyOver(hairMask), 0.008 * h, null, hairMask), masked(new THREE.MeshPhysicalMaterial({
      color: colors.hair, roughness: 0.62, sheen: 0.6, sheenColor: new THREE.Color('#8a6a50'), sheenRoughness: 0.35,
      normalMap: tex.hair, normalScale: new THREE.Vector2(0.9, 0.9),
    }))],
  ];
  const meshes = parts.map(([g, m]) => {
    const sm = new THREE.SkinnedMesh(g, m);
    sm.bind(base.skeleton, base.bindMatrix);
    sm.position.copy(base.position); sm.quaternion.copy(base.quaternion); sm.scale.copy(base.scale);
    sm.castShadow = true; sm.receiveShadow = true; sm.frustumCulled = false;
    base.parent.add(sm);
    return sm;
  });
  base.visible = false;   // il corpo originale, squadrato, non serve più

  // occhi: piccole sfere sulla testa, davanti, all'altezza giusta
  const head = bones.spine005;
  if (head) {
    let best = null;
    sub.P.forEach((p, i) => { if (dom[i] === 'spine005' && ny(i) > 0.925 && ny(i) < 0.945 && Math.abs(p.x - (box.min.x + box.max.x) / 2) < 0.03 * h) { if (!best || (p.z - best.z) * fwd > 0) best = p; } });
    if (best) {
      const cx = (box.min.x + box.max.x) / 2, ey = y0 + 0.919 * h;
      const front = (ex, yy) => {   // il punto più avanti della faccia vicino a (ex, yy)
        let f = null;
        sub.P.forEach((p, i) => { if (dom[i] === 'spine005' && Math.abs(p.x - ex) < 0.009 * h && Math.abs(p.y - yy) < 0.009 * h && (!f || (p.z - f) * fwd > 0)) f = p.z; });
        return f === null ? best.z : f;
      };
      // posizione nel mondo come la calcola lo scheletro (il corpo a riposo non coincide con la geometria grezza)
      const near = v => { let bi = 0, bd = Infinity; sub.P.forEach((p, i) => { const d = p.distanceToSquared(v); if (d < bd) { bd = d; bi = i; } }); return bi; };
      const skinW = v => {
        const out = new THREE.Vector3(), tmp = new THREE.Vector3(), m = new THREE.Matrix4(), sk = base.skeleton;
        for (const [b, wt] of sub.W[near(v)]) {
          if (!wt) continue;
          m.multiplyMatrices(sk.bones[b].matrixWorld, sk.boneInverses[b]);
          out.addScaledVector(tmp.copy(v).applyMatrix4(base.bindMatrix).applyMatrix4(m), wt);
        }
        return out.applyMatrix4(base.bindMatrixInverse).applyMatrix4(base.matrixWorld);
      };
      const toHead = v => head.worldToLocal(skinW(v));
      const headQ = head.getWorldQuaternion(new THREE.Quaternion()).invert().multiply(base.getWorldQuaternion(new THREE.Quaternion()));
      const dark = new THREE.MeshStandardMaterial({ color: '#2b1d14', roughness: 0.6 });
      for (const sx of [-1, 1]) {
        const ex = cx + sx * 0.02 * h, fz = front(ex, ey);
        // occhio a mandorla scuro e lucido: da lontano si legge meglio di un bulbo bianco
        const eye = new THREE.Mesh(new THREE.SphereGeometry(0.0062 * h, 14, 10), new THREE.MeshStandardMaterial({ color: '#2a1b12', roughness: 0.12 }));
        eye.scale.set(1.35, 0.72, 0.6);
        head.add(eye); eye.position.copy(toHead(new THREE.Vector3(ex, ey, fz - fwd * 0.0015 * h))); eye.quaternion.copy(headQ);
        // sopracciglio
        const brow = new THREE.Mesh(new THREE.BoxGeometry(0.022 * h, 0.0035 * h, 0.006 * h), dark);
        const by = ey + 0.012 * h;
        head.add(brow); brow.position.copy(toHead(new THREE.Vector3(ex, by, front(ex, by) + fwd * 0.0005 * h))); brow.quaternion.copy(headQ);
        brow.rotateZ(sx * -0.12);
      }
    }
  }

  const s = 1.8 / h; root.scale.setScalar(s);
  const rest = {};
  for (const [k, b] of Object.entries(bones)) rest[k] = b.quaternion.clone();
  // dita chiuse sul manico (destra) e un po' rilassate (sinistra): rotazione aggiunta a quella di riposo
  const curl = {};
  for (const b of Object.values(bones)) {
    const n = clean(b.name);
    const m = /^f_(index|middle|ring|pinky)0([123])([LR])$/.exec(n), t = /^thumb0([23])([LR])$/.exec(n);
    if (m) curl[n] = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), m[3] === 'R' ? 1.15 : 0.45);
    else if (t) curl[n] = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), t[2] === 'R' ? -0.6 : -0.25);
  }
  const g = new THREE.Group(); g.add(root);
  return { group: g, root, bones, rest, curl, yOff: -y0 * s, meshes };
}
