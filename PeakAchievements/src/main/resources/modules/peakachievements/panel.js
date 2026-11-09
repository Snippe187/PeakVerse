// Seite "Achievements": Skill-Tree aller Minecraft-Fortschritte und eigenen Achievements. Knoten anklicken zum Bearbeiten, auf einen anderen ziehen zum Umhaengen.
// Alles landet zuerst im Entwurf und gilt erst nach "Veroeffentlichen".
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  const NODE = 46, SPACING = 62, GAP = 84;
  let tab = 'minecraft', st = null, meta = null, sel = null, search = '', view = { x: 40, y: 40, k: 1 }, drag = null;
  const iconCache = {};

  const FRAMES = { task: L('Aufgabe', 'Task'), goal: L('Ziel', 'Goal'), challenge: L('Herausforderung', 'Challenge') };
  const VIS = { ALWAYS: L('Immer sichtbar', 'Always visible'), AFTER_PARENT: L('Sichtbar, wenn der Knoten davor erreicht ist', 'Visible once the node before is done'), UNTIL_DONE: L('Verborgen bis erreicht', 'Hidden until done') };
  const TYPES = { KILL: L('Mobs toeten', 'Kill mobs'), BREAK: L('Bloecke abbauen', 'Break blocks'), PLACE: L('Bloecke setzen', 'Place blocks'), CRAFT: L('Herstellen', 'Craft'), FISH: L('Angeln', 'Fish'), JUMP: L('Spruenge', 'Jumps'), WALK: L('Bloecke laufen', 'Blocks walked'), PLAYTIME: L('Spielzeit (Minuten)', 'Playtime (minutes)'), DEATH: L('Tode', 'Deaths'), MANUAL: L('Nur manuell vergeben', 'Manual only') };

  // ---------- Daten ----------
  async function reload() { st = await ctx.api('state'); render(); }
  async function send(path, body, ok) {
    try { st = await ctx.api(path, { method: 'POST', body: body ?? '' }); if (ok) ctx.toast(ok, 'good'); render(); return true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return false; }
  }
  const byKey = () => new Map(st.nodes.map(n => [n.key, n]));
  /** Die Knoten des gerade gezeigten Baums: Minecraft oder Eigene. */
  const shown = () => st.nodes.filter(n => tab === 'own' ? n.kind === 'custom' : n.kind === 'vanilla');

  // ---------- Layout: Mindmap (PeakVerse in der Mitte, die Aeste strahlen nach aussen) ----------
  const BRANCH_COLORS = { 'minecraft:story/': '#6fbf4a', 'minecraft:nether/': '#e0524a', 'minecraft:end/': '#b58cff', 'minecraft:adventure/': '#f0a030', 'minecraft:husbandry/': '#d6d94a' };
  const OWN_COLOR = '#4cd8e8';
  function layout(nodes) {
    const map = new Map(nodes.map(n => [n.key, n])), kids = new Map();
    const parentOf = n => (n.parent && map.has(n.parent)) ? n.parent : '';
    for (const n of nodes) { const p = parentOf(n); if (!kids.has(p)) kids.set(p, []); kids.get(p).push(n); }
    for (const l of kids.values()) l.sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'vanilla' ? -1 : 1) || a.key.localeCompare(b.key));
    const weight = new Map();
    const count = n => {
      const l = kids.get(n.key) || [];
      const c = l.length ? l.reduce((s, x) => s + count(x), 0) : 1;
      weight.set(n.key, Math.max(c, 2.2)); return weight.get(n.key); // auch ein einzelner Knoten bekommt genug Winkel
    };
    const tops = kids.get('') || [];
    const total = tops.reduce((s, x) => s + count(x), 0) || 1;
    // 1) Winkel: jeder Knoten liegt in der Mitte seines Abschnitts
    const ang = new Map(), depthOf = new Map(), branch = new Map(), topOf = new Map(), byDepth = [];
    const place = (n, depth, a0, a1, br, top) => {
      ang.set(n.key, (a0 + a1) / 2); depthOf.set(n.key, depth); branch.set(n.key, br); topOf.set(n.key, top);
      const l = kids.get(n.key) || []; const sum = l.reduce((s, x) => s + weight.get(x.key), 0); let a = a0;
      for (const ch of l) { const w = (a1 - a0) * weight.get(ch.key) / sum; place(ch, depth + 1, a, a + w, br, top); a += w; }
    };
    let a = -Math.PI / 2;
    for (const t of tops) {
      const w = 2 * Math.PI * weight.get(t.key) / total;
      const col = t.kind === 'custom' ? OWN_COLOR : (Object.entries(BRANCH_COLORS).find(([k]) => t.key.startsWith(k))?.[1] || '#9aa4b8');
      place(t, 1, a, a + w, col, t.key); a += w;
    }
    // 2) Radien: der erste Ring ist gemeinsam, danach bekommt jeder Ast seine eigenen Abstaende (kleine Aeste bleiben kompakt).
    //    Ist ein Ring zu eng, wird jeder zweite Knoten nach aussen versetzt.
    const STAGGER = 70, extra = new Map(), radiusOf = new Map(); // Knoten -> Radius
    const sep = (list, step) => {
      const angles = list.map(k => ang.get(k)).sort((x, y) => x - y);
      let m = Infinity;
      for (let i = 0; i < angles.length; i++) {
        let d = (angles[(i + step) % angles.length] - angles[i]) * (angles.length > step ? 1 : 0);
        if (d <= 0) d += 2 * Math.PI;
        m = Math.min(m, d);
      }
      return Math.max(m, 0.012);
    };
    const ring = (list, startOuter, first) => {
      const base = startOuter + (first ? 150 : 74);
      const rPlain = Math.max(base, list.length > 1 ? SPACING / sep(list, 1) : 0);
      let r = rPlain, stag = false;
      if (list.length > 2 && rPlain > base * 1.3) {
        const rStag = Math.max(base, SPACING / sep(list, 2));
        if (rStag < rPlain) { r = rStag; stag = true; }
      }
      const sorted = [...list].sort((x, y) => ang.get(x) - ang.get(y));
      sorted.forEach((k, i) => { radiusOf.set(k, r); if (stag && i % 2) extra.set(k, STAGGER); });
      return r + (stag ? STAGGER : 0);
    };
    const level1 = tops.map(t => t.key);
    const outer1 = ring(level1, 0, true);
    const perTop = new Map();
    for (const [k, d] of depthOf) {
      if (d < 2) continue;
      const g = perTop.get(topOf.get(k)) || perTop.set(topOf.get(k), []).get(topOf.get(k));
      (g[d] ||= []).push(k);
    }
    for (const levels of perTop.values()) {
      let prev = outer1;
      for (let d = 2; d < levels.length; d++) if (levels[d]) prev = ring(levels[d], prev, false);
    }
    const pos = new Map([['', { x: 0, y: 0 }]]);
    for (const [k, an] of ang) { const r = (radiusOf.get(k) || 0) + (extra.get(k) || 0); pos.set(k, { x: Math.cos(an) * r, y: Math.sin(an) * r }); }    // 3) Rest-Ueberlappungen: die weiter aussen liegende Kachel rueckt radial nach aussen
    const keys = [...pos.keys()].filter(k => k !== '');
    for (let round = 0; round < 40; round++) {
      let moved = false;
      for (let i = 0; i < keys.length; i++) for (let j = i + 1; j < keys.length; j++) {
        const pa = pos.get(keys[i]), pb = pos.get(keys[j]);
        if (Math.abs(pa.x - pb.x) < NODE + 4 && Math.abs(pa.y - pb.y) < NODE + 4) {
          const far = Math.hypot(pa.x, pa.y) > Math.hypot(pb.x, pb.y) ? pa : pb, rr = Math.hypot(far.x, far.y) || 1;
          far.x += far.x / rr * 16; far.y += far.y / rr * 16; moved = true;
        }
      }
      if (!moved) break;
    }
    return { pos, kids, branch };
  }
  // ---------- Linien: sanfte Kurven, die um Kacheln herumfuehren und sich moeglichst nicht kreuzen ----------
  const PAD = 10;
  const halfOf = k => (k === '' ? NODE + 18 : NODE) / 2;
  /** Wo die Strecke p->q in das Quadrat c +- h eintritt (0..1) oder null. */
  function segEnter(p, q, c, h) {
    let t0 = 0, t1 = 1; const dx = q.x - p.x, dy = q.y - p.y;
    const clip = (pp, qq) => { if (pp === 0) return qq >= 0; const r = qq / pp; if (pp < 0) { if (r > t1) return false; if (r > t0) t0 = r; } else { if (r < t0) return false; if (r < t1) t1 = r; } return true; };
    return clip(-dx, p.x - (c.x - h)) && clip(dx, (c.x + h) - p.x) && clip(-dy, p.y - (c.y - h)) && clip(dy, (c.y + h) - p.y) && t0 <= t1 ? t0 : null;
  }
  /** Catmull-Rom durch die Punkte -> Bezier-Stuecke [p1, c1, c2, p2]. */
  function curveSegs(pts) {
    const out = [];
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || pts[i + 1];
      out.push([p1, { x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6 }, { x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6 }, p2]);
    }
    return out;
  }
  function sampleCurve(pts, per = 8) {
    const out = [pts[0]];
    for (const [a, b, c, d] of curveSegs(pts)) for (let i = 1; i <= per; i++) {
      const t = i / per, u = 1 - t;
      out.push({ x: u * u * u * a.x + 3 * u * u * t * b.x + 3 * u * t * t * c.x + t * t * t * d.x, y: u * u * u * a.y + 3 * u * u * t * b.y + 3 * u * t * t * c.y + t * t * t * d.y });
    }
    return out;
  }
  function curveD(pts, ox, oy) {
    const f = v => Math.round(v * 10) / 10;
    return 'M' + f(pts[0].x + ox) + ' ' + f(pts[0].y + oy) + curveSegs(pts).map(([, b, c, d]) => ' C' + f(b.x + ox) + ' ' + f(b.y + oy) + ' ' + f(c.x + ox) + ' ' + f(c.y + oy) + ' ' + f(d.x + ox) + ' ' + f(d.y + oy)).join('');
  }
  /**
   * Linie a->b: gerade, wenn nichts im Weg steht; sonst der kuerzeste Weg um die Kachelecken herum (Sichtbarkeitsgraph).
   * ban: Ecken, die nicht benutzt werden duerfen (so entsteht der Weg auf der anderen Seite).
   */
  function route(aKey, bKey, pos, boxes, ban, extra) {
    const A = pos.get(aKey), B = pos.get(bKey);
    const dx = B.x - A.x, dy = B.y - A.y, len2 = dx * dx + dy * dy || 1;
    const inf = bx => bx.h + PAD + extra;
    const obs = boxes.filter(bx => {
      if (bx.key === aKey || bx.key === bKey) return false;
      const t = Math.max(0, Math.min(1, ((bx.c.x - A.x) * dx + (bx.c.y - A.y) * dy) / len2));
      return Math.hypot(A.x + dx * t - bx.c.x, A.y + dy * t - bx.c.y) < 280;
    });
    const blocked = (p, q) => obs.some(bx => segEnter(p, q, bx.c, inf(bx) - 0.6) !== null);
    if (!blocked(A, B)) return { pts: [A, B], hits: new Set(), ids: [] };
    const nodes = [{ p: A, id: 'A' }, { p: B, id: 'B' }];
    for (const bx of obs) {
      const hh = inf(bx);
      for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
        const id = bx.key + '|' + sx + sy;
        if (ban && ban.has(id)) continue;
        const p = { x: bx.c.x + sx * hh, y: bx.c.y + sy * hh };
        if (obs.some(o => o !== bx && Math.abs(p.x - o.c.x) < inf(o) - 0.6 && Math.abs(p.y - o.c.y) < inf(o) - 0.6)) continue; // Ecke liegt in einer anderen Kachel
        nodes.push({ p, id, box: bx.key });
      }
    }
    const n = nodes.length, dist = new Array(n).fill(Infinity), prev = new Array(n).fill(-1), done = new Array(n).fill(false);
    dist[0] = 0;
    for (let it = 0; it < n; it++) {
      let u = -1;
      for (let i = 0; i < n; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i;
      if (u < 0 || dist[u] === Infinity) break;
      done[u] = true; if (u === 1) break;
      for (let v = 0; v < n; v++) {
        if (done[v]) continue;
        const d = dist[u] + Math.hypot(nodes[u].p.x - nodes[v].p.x, nodes[u].p.y - nodes[v].p.y);
        if (d < dist[v] && !blocked(nodes[u].p, nodes[v].p)) { dist[v] = d; prev[v] = u; }
      }
    }
    if (prev[1] < 0) return { pts: [A, B], hits: new Set(), ids: [], failed: true }; // kein Weg gefunden: bleibt gerade
    const path = [];
    for (let i = 1; i !== -1; i = prev[i]) path.unshift(nodes[i]);
    const mid = [];
    for (const x of path.slice(1, -1)) { const last = mid.length ? mid[mid.length - 1].p : A; if (Math.hypot(x.p.x - last.x, x.p.y - last.y) >= 16) mid.push(x); }
    path.splice(1, path.length - 2, ...mid);
    return { pts: path.map(x => x.p), hits: new Set(mid.map(x => x.box)), ids: mid.map(x => x.id) };
  }  const clearOf = (samples, boxes, aKey, bKey) => !samples.some((s, i) => i > 0 && boxes.some(bx => bx.key !== aKey && bx.key !== bKey && Math.abs(s.x - bx.c.x) < bx.h + 3 && Math.abs(s.y - bx.c.y) < bx.h + 3));
  /** Route plus Pruefung der geglaetteten Kurve; bleibt sie an einer Kachel haengen, wird der Abstand schrittweise vergroessert. */
  function routeChecked(aKey, bKey, pos, boxes, ban) {
    let r;
    for (const extra of [0, 10, 22, 36]) {
      r = route(aKey, bKey, pos, boxes, ban, extra);
      r.samples = sampleCurve(r.pts);
      if (clearOf(r.samples, boxes, aKey, bKey)) break;
    }
    return r;
  }
  const segCross = (p, q, r, s) => {
    const d = (q.x - p.x) * (s.y - r.y) - (q.y - p.y) * (s.x - r.x);
    if (d === 0) return false;
    const t = ((r.x - p.x) * (s.y - r.y) - (r.y - p.y) * (s.x - r.x)) / d, u = ((r.x - p.x) * (q.y - p.y) - (r.y - p.y) * (q.x - p.x)) / d;
    return t > 0.001 && t < 0.999 && u > 0.001 && u < 0.999;
  };
  function bounds(smp) { let a = 1e9, b = 1e9, c = -1e9, d = -1e9; for (const s of smp) { a = Math.min(a, s.x); b = Math.min(b, s.y); c = Math.max(c, s.x); d = Math.max(d, s.y); } return { a, b, c, d }; }
  function crossings(e, edges) {
    let n = 0;
    for (const o of edges) {
      if (o === e || o.a === e.a || o.a === e.b || o.b === e.a || o.b === e.b) continue; // Linien, die sich an einer Kachel treffen, kreuzen sich nicht
      const A = e.bb, B = o.bb; if (A.c < B.a || B.c < A.a || A.d < B.b || B.d < A.b) continue;
      let hit = false;
      for (let i = 0; i < e.samples.length - 1 && !hit; i++) for (let j = 0; j < o.samples.length - 1; j++) if (segCross(e.samples[i], e.samples[i + 1], o.samples[j], o.samples[j + 1])) { hit = true; break; }
      if (hit) n++;
    }
    return n;
  }
  /** Alle Linien des Baums: Kurven um Kacheln herum; bei Kreuzungen wird, wo moeglich, auf der anderen Seite vorbeigefuehrt. */
  function routeAll(kids, pos) {
    const boxes = [...pos].map(([key, c]) => ({ key, c, h: halfOf(key) }));
    const edges = [];
    for (const [pk, list] of kids) for (const ch of list) {
      const r = routeChecked(pk, ch.key, pos, boxes, null);
      edges.push({ a: pk, b: ch.key, pts: r.pts, samples: r.samples, hits: r.hits, ids: r.ids, bb: bounds(r.samples) });
    }
    // Kreuzungen verringern: eine Linie oder ihr Kreuzungspartner wird, wenn sie einen Umweg hat, auf der anderen Seite vorbeigefuehrt (nur wenn es insgesamt besser wird)
    const alts = e => { // andere Wege: alle benutzten Ecken sperren, nur die erste oder nur die letzte
      if (!e.hits.size) return [];
      const out = [];
      for (const ban of [e.ids, e.ids.slice(0, 1)]) {
        const r = routeChecked(e.a, e.b, pos, boxes, new Set(ban));
        out.push({ pts: r.pts, samples: r.samples, bb: bounds(r.samples), hits: r.hits, ids: r.ids });
      }
      return out;
    };    const partners = e => edges.filter(o => o !== e && crossings({ ...e, samples: e.samples, bb: e.bb }, [e, o]) > 0);
    for (let pass = 0; pass < 3; pass++) {
      let improved = false;
      for (const e of edges) {
        const now = crossings(e, edges);
        if (now === 0) continue;
        for (const who of [e, ...partners(e)]) {
          const before = crossings(e, edges) + (who === e ? 0 : crossings(who, edges));
          const old = { pts: who.pts, samples: who.samples, bb: who.bb, hits: who.hits, ids: who.ids };
          let best = null, bestAfter = before;
          for (const cand of alts(who)) {
            Object.assign(who, cand);
            const after = crossings(e, edges) + (who === e ? 0 : crossings(who, edges));
            if (after < bestAfter) { bestAfter = after; best = cand; }
          }
          if (best) { Object.assign(who, best); improved = true; break; }
          Object.assign(who, old);
        }
      }
      if (!improved) break;
    }    return { edges, boxes, crossings: edges.reduce((s, e) => s + crossings(e, edges), 0) / 2 };
  }
  // ---------- Symbole ----------
  let texStatus = null; // null = unbekannt, sonst die Antwort von TexturePeak ({ready, busy, error}) oder {missing:true}
  let textures = null; // Namen der vorhandenen Standard-Texturen (TexturePeak), damit nur existierende Bilder angefragt werden
  function icon(name, size = 32) {
    const kind = textures ? (textures.item.has(name) ? 'item' : textures.block.has(name) ? 'block' : null) : (texStatus === null ? 'item' : null);
    if (!kind) return h('span', { class: 'pa-ic ph', style: 'width:' + size + 'px;height:' + size + 'px;line-height:' + size + 'px' }, (name || '?')[0].toUpperCase());
    const img = h('img', { class: 'pa-ic', width: size, height: size, alt: '', draggable: 'false', src: '/api/texturepeak/vanilla/' + kind + '/' + encodeURIComponent(name) });
    img.onerror = () => img.replaceWith(h('span', { class: 'pa-ic ph', style: 'width:' + size + 'px;height:' + size + 'px;line-height:' + size + 'px' }, (name || '?')[0].toUpperCase()));
    return img;
  }
  // ---------- Zeichnen ----------
  const treeEl = h('div', { class: 'pa-tree' });
  const sideEl = h('div', { class: 'pa-side' });
  const barEl = h('div', { class: 'pa-bar' });

  function render() {
    if (!st) return;
    root.replaceChildren(...[
      h('h1', {}, 'Achievements'),
      h('p', { class: 'sub' }, L('Alle Minecraft-Fortschritte und eigene Achievements in einem Baum. Knoten anklicken zum Bearbeiten, auf einen anderen Knoten ziehen zum Umhaengen. Aenderungen gelten erst nach dem Veroeffentlichen.', 'All Minecraft advancements and custom achievements in one tree. Click a node to edit, drag it onto another node to re-attach. Changes apply after publishing.')),
      st.mode !== 'peak' ? h('div', { class: 'tag warn', style: 'display:block;margin:8px 0' }, L('Der Vanilla-Modus ist an: hier laesst sich nichts bearbeiten. Mit /peakachievements mode peak einschalten.', 'Vanilla mode is on: nothing can be edited. Enable with /peakachievements mode peak.')) : null,
      texBanner(), barEl, h('div', { class: 'pa-main' }, treeEl, sideEl)].filter(Boolean));
    drawBar(); drawTree(); drawSide();
  }

  function texBanner() {
    if (!texStatus || texStatus.ready) return null;
    if (texStatus.missing) return h('div', { class: 'tag warn', style: 'display:block;margin:8px 0' }, L('Die Item-Bilder fehlen: das Modul TexturePeak ist aus oder nicht erreichbar. Ohne es siehst du Buchstaben statt Symbolen.', 'Item images are missing: the TexturePeak module is off or unreachable. Without it you see letters instead of icons.'));
    return h('div', { class: 'tag warn', style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:8px 0' },
      h('span', {}, texStatus.busy ? L('Die Standard-Bilder von Minecraft werden gerade geladen ...', 'Loading the standard Minecraft images ...') : texStatus.error ? L('Laden der Standard-Bilder fehlgeschlagen: ', 'Loading the standard images failed: ') + texStatus.error : L('Die Standard-Bilder von Minecraft sind noch nicht geladen, deshalb siehst du Buchstaben statt Symbolen.', 'The standard Minecraft images are not loaded yet, so you see letters instead of icons.')),
      texStatus.busy ? null : h('button', { class: 'px-btn small primary', onclick: downloadTextures }, texStatus.error ? L('Nochmal versuchen', 'Try again') : L('Jetzt laden (einmalig)', 'Load now (once)')));
  }

  function drawBar() {
    const q = h('input', { placeholder: L('Suchen...', 'Search...'), value: search, class: 'grow', oninput: e => { search = e.target.value; drawTree(); } });
    const count = k => st.nodes.filter(n => n.kind === k).length;
    const tabBtn = (id, label, n) => h('button', { class: 'px-btn small' + (tab === id ? ' active' : ''), onclick: () => { if (tab === id) return; tab = id; sel = null; firstDraw = true; render(); } }, label + ' (' + n + ')');
    barEl.replaceChildren(tabBtn('minecraft', 'Minecraft', count('vanilla')), tabBtn('own', L('Eigene', 'Own'), count('custom')), q,
      h('button', { class: 'px-btn small', onclick: fit }, L('Alles zeigen', 'Show all')),
      h('button', { class: 'px-btn small', disabled: st.mode !== 'peak', onclick: newCustom }, '+ ' + L('Eigenes Achievement', 'Custom achievement')),
      st.draft ? h('span', { class: 'tag warn' }, L('Entwurf: noch nicht live', 'Draft: not live yet')) : h('span', { class: 'tag good' }, L('Live-Stand', 'Live')),
      h('button', { class: 'px-btn small primary', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf veroeffentlichen? Der Baum wird fuer alle Spieler neu geladen (mit Sicherung der alten Dateien).', 'Publish the draft? The tree is reloaded for all players (old files are backed up).'))) send('publish', '', L('Veroeffentlicht', 'Published')); } }, L('Veroeffentlichen', 'Publish')),
      h('button', { class: 'px-btn small ghost', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf verwerfen?', 'Discard the draft?'))) send('discard', '', L('Entwurf verworfen', 'Draft discarded')); } }, L('Verwerfen', 'Discard')));
  }

  function applyView() { const c = treeEl.querySelector('.pa-canvas'); if (c) c.style.transform = `translate(${view.x}px,${view.y}px) scale(${view.k})`; }

  let bbox = { w: 1, h: 1 };
  /** Zeigt den ganzen Baum im Fenster. */
  function fit() {
    const w = treeEl.clientWidth || 800, h = treeEl.clientHeight || 500;
    const k = Math.min(1.4, Math.max(0.12, Math.min(w / bbox.w, h / bbox.h) * 0.95));
    view = { k, x: (w - bbox.w * k) / 2, y: (h - bbox.h * k) / 2 }; applyView();
  }
  function zoomBy(f) {
    const w = treeEl.clientWidth / 2, h = treeEl.clientHeight / 2, k = Math.min(2.5, Math.max(0.1, view.k * f));
    view.x = w - (w - view.x) * (k / view.k); view.y = h - (h - view.y) * (k / view.k); view.k = k; applyView();
  }

  let firstDraw = true, treeCtx = null, routeCache = { sig: '', net: null };
  function drawTree() {
    const nodes = shown();
    const { pos, kids, branch } = layout(nodes);
    const map = byKey();
    const half = NODE / 2 + 24;
    const xs = [...pos.values()].map(p => p.x), ys = [...pos.values()].map(p => p.y);
    const minX = Math.min(...xs) - half, minY = Math.min(...ys) - half;
    const W = Math.max(...xs) - minX + half, H = Math.max(...ys) - minY + half;
    bbox = { w: W, h: H };
    const cx = p => p.x - minX, cy = p => p.y - minY; // Mittelpunkt der Kachel in der Flaeche
    const ql = search.trim().toLowerCase();
    const hit = n => !ql || n.title.toLowerCase().includes(ql) || n.description.toLowerCase().includes(ql) || n.key.toLowerCase().includes(ql);
    const onPath = new Set(); // Weg der Auswahl bis zur Mitte
    for (let k = sel; k !== undefined && k !== ''; k = (map.get(k)?.parent && map.has(map.get(k).parent)) ? map.get(k).parent : '') { onPath.add(k); if (!map.get(k)) break; }
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg'); svg.setAttribute('width', W); svg.setAttribute('height', H); svg.setAttribute('class', 'pa-lines');
    const sig = tab + '|' + nodes.map(n => n.key + '>' + n.parent).join(',');
    const net = routeCache.sig === sig ? routeCache.net : (routeCache = { sig, net: routeAll(kids, pos) }).net;
    const edgeEls = new Map();
    for (const e of net.edges) {
      const c = map.get(e.b), l = document.createElementNS(svgNS, 'path');
      l.setAttribute('d', curveD(e.pts, -minX, -minY)); l.setAttribute('fill', 'none');
      const hot = onPath.has(e.b);
      l.setAttribute('stroke', hot ? '#ffffff' : (branch.get(e.b) || '#9aa4b8'));
      l.setAttribute('class', 'pa-line' + (c && !c.enabled ? ' dim' : '') + (hot ? ' hot' : ''));
      svg.append(l); edgeEls.set(e.b, l);
    }
    const preview = document.createElementNS(svgNS, 'path'); preview.setAttribute('class', 'pa-preview'); preview.setAttribute('fill', 'none'); svg.append(preview);
    treeCtx = { pos, boxes: net.boxes, edgeEls, preview, ox: -minX, oy: -minY };
    const rootNode = tab === 'own' ? { key: '', title: L('Eigene Achievements', 'Own achievements'), icon: 'nether_star', frame: 'challenge', enabled: true, kind: 'root', description: '' } : { key: '', title: 'Minecraft', icon: 'grass_block', frame: 'challenge', enabled: true, kind: 'root', description: '' };
    const els = [];
    for (const n of [rootNode, ...nodes]) {
      const p = pos.get(n.key); if (!p) continue;
      const size = n.kind === 'root' ? NODE + 18 : NODE;
      const col = n.kind === 'root' ? '#7aa2ff' : (branch.get(n.key) || '#9aa4b8');
      const el = h('div', { class: 'pa-node ' + n.frame + (n.enabled ? '' : ' off') + (sel === n.key ? ' sel' : '') + (n.kind === 'custom' ? ' own' : '') + (n.kind === 'root' ? ' rootn' : '') + (n.changed ? ' chg' : '') + (hit(n) ? '' : ' dim'),
        style: `left:${cx(p) - size / 2}px;top:${cy(p) - size / 2}px;width:${size}px;height:${size}px;--bc:${col}`, 'data-key': n.key, title: n.title + (n.description ? '\n' + n.description : '') }, icon(n.icon, n.kind === 'root' ? 40 : 30));
      if (n.kind !== 'root') el.addEventListener('pointerdown', e => startDrag(e, n, el));
      els.push(el);
    }
    treeEl.replaceChildren(...[h('div', { class: 'pa-canvas', style: `width:${W}px;height:${H}px` }, svg, ...els), !nodes.length ? h('div', { class: 'pa-empty' }, L('Noch keine eigenen Achievements. Mit dem Knopf oben ein neues anlegen.', 'No own achievements yet. Create one with the button above.')) : null,
      h('div', { class: 'pa-zoom' }, h('button', { class: 'px-btn small', title: L('Hineinzoomen', 'Zoom in'), onclick: () => zoomBy(1.25) }, '+'), h('button', { class: 'px-btn small', title: L('Herauszoomen', 'Zoom out'), onclick: () => zoomBy(0.8) }, '−'), h('button', { class: 'px-btn small', title: L('Alles zeigen', 'Show all'), onclick: fit }, '⤢'))].filter(Boolean));
    if (firstDraw) { firstDraw = false; requestAnimationFrame(fit); } else applyView();
  }
  // ---------- Ziehen (Knoten umhaengen) und Schieben/Zoomen der Flaeche ----------
  treeEl.addEventListener('wheel', e => {
    e.preventDefault();
    const r = treeEl.getBoundingClientRect(), mx = e.clientX - r.left, my = e.clientY - r.top;
    const k = Math.min(2.5, Math.max(0.1, view.k * Math.exp(-e.deltaY * 0.0015)));
    view.x = mx - (mx - view.x) * (k / view.k); view.y = my - (my - view.y) * (k / view.k); view.k = k; applyView();
  }, { passive: false });
  treeEl.addEventListener('dblclick', e => { if (!e.target.closest('.pa-node, .pa-zoom')) fit(); });
  treeEl.addEventListener('pointerdown', e => {
    if (e.target.closest('.pa-node, .pa-zoom')) return;
    const sx = e.clientX, sy = e.clientY, ox = view.x, oy = view.y;
    treeEl.setPointerCapture(e.pointerId); treeEl.classList.add('pan');
    const move = ev => { view.x = ox + ev.clientX - sx; view.y = oy + ev.clientY - sy; applyView(); };
    const up = () => { treeEl.removeEventListener('pointermove', move); treeEl.removeEventListener('pointerup', up); treeEl.classList.remove('pan'); };
    treeEl.addEventListener('pointermove', move); treeEl.addEventListener('pointerup', up);
  });

  /** Beim Ziehen: zeigt live, wie die Linie zum gerade angepeilten neuen Knoten verlaufen wuerde (um Kacheln herum). */
  function previewLine(childKey, targetKey) {
    if (!treeCtx) return;
    const { pos, boxes, edgeEls, preview, ox, oy } = treeCtx;
    const own = edgeEls.get(childKey);
    if (own) own.classList.toggle('faded', targetKey !== null);
    if (targetKey === null || !pos.has(targetKey) || !pos.has(childKey)) { preview.setAttribute('d', ''); return; }
    const r = routeChecked(targetKey, childKey, pos, boxes, null);
    preview.setAttribute('d', curveD(r.pts, ox, oy));
  }

  function startDrag(e, n, el) {
    e.preventDefault(); e.stopPropagation();
    const sx = e.clientX, sy = e.clientY; let ghost = null, over = null, moved = false;
    const move = ev => {
      if (!moved && Math.hypot(ev.clientX - sx, ev.clientY - sy) < 6) return;
      if (!moved) { moved = true; ghost = el.cloneNode(true); ghost.classList.add('ghost'); document.body.append(ghost); el.classList.add('lifted'); }
      ghost.style.left = ev.clientX - 28 + 'px'; ghost.style.top = ev.clientY - 28 + 'px';
      ghost.style.display = 'none'; const t2 = document.elementFromPoint(ev.clientX, ev.clientY); ghost.style.display = '';
      const target = t2 && t2.closest('.pa-node');
      if (over && over !== target) over.classList.remove('drop');
      over = target && target !== el ? target : null;
      if (over) over.classList.add('drop');
      previewLine(n.key, over ? over.dataset.key : null);
    };
    const up = async () => {
      document.removeEventListener('pointermove', move); document.removeEventListener('pointerup', up);
      previewLine(n.key, null);
      if (ghost) ghost.remove(); el.classList.remove('lifted'); if (over) over.classList.remove('drop');
      if (!moved) { sel = n.key; render(); return; }
      if (over) await send('parent', { key: n.key, parent: over.dataset.key }, L('Umgehaengt (im Entwurf)', 'Re-attached (in draft)'));
    };
    document.addEventListener('pointermove', move); document.addEventListener('pointerup', up);
  }

  // ---------- Seitenleiste: Vorschau und Bearbeiten ----------
  function tile(n) {
    return h('div', { class: 'pa-tile' }, h('div', { class: 'pa-node static ' + n.frame }, icon(n.icon, 32)),
      h('div', { class: 'pa-tile-text' }, h('b', {}, n.title || '-'), h('div', { class: 'sub' }, n.description || '')));
  }
  function toastPreview(n) {
    const el = h('div', { class: 'pa-toast ' + n.frame }, h('div', { class: 'pa-toast-h' }, n.frame === 'challenge' ? L('Herausforderung gemeistert!', 'Challenge complete!') : n.frame === 'goal' ? L('Ziel erreicht!', 'Goal reached!') : L('Fortschritt gemacht!', 'Advancement made!')),
      h('div', { class: 'pa-toast-b' }, icon(n.icon, 28), h('span', {}, n.title)));
    document.body.append(el); setTimeout(() => el.remove(), 4200);
  }

  function field(label, input, hint) { return h('label', { class: 'pa-f' }, h('span', {}, label), input, hint ? h('small', { class: 'sub' }, hint) : null); }
  function sel2(opts, val, on) { const s = h('select', { onchange: e => on(e.target.value) }, Object.entries(opts).map(([k, v]) => h('option', { value: k, selected: k === val }, v))); return s; }
  function iconInput(val, on) {
    const id = 'pa-mats'; if (!document.getElementById(id) && meta) document.body.append(h('datalist', { id }, meta.materials.map(m => h('option', { value: m }))));
    return h('input', { value: val, list: id, placeholder: 'diamond_sword', onchange: e => on(e.target.value.trim()) });
  }

  function drawSide() {
    const map = byKey(), n = map.get(sel);
    if (!n) { sideEl.replaceChildren(h('div', { class: 'empty' }, L('Knoten anklicken, um ihn zu bearbeiten.', 'Click a node to edit it.'))); return; }
    const parentTitle = n.parent ? (map.get(n.parent)?.title || n.parent) : 'PeakVerse';
    const f = {}; // Eingabewerte
    const body = [tile(n), h('div', { class: 'row' }, h('button', { class: 'px-btn small', onclick: () => toastPreview(n) }, L('Toast ansehen', 'Preview toast')),
      h('span', { class: 'tag' }, n.kind === 'custom' ? L('Eigenes Achievement', 'Custom') : 'Minecraft'), n.changed ? h('span', { class: 'tag warn' }, L('geaendert', 'changed')) : null)];
    body.push(h('div', { class: 'sub' }, L('Haengt an: ', 'Attached to: ') + parentTitle),
      h('div', { class: 'row' }, h('button', { class: 'px-btn small', disabled: !n.parent, onclick: () => send('parent', { key: n.key, parent: '' }, L('An die Wurzel gehaengt (Entwurf)', 'Moved to root (draft)')) }, L('An die Wurzel haengen', 'Attach to root'))));
    if (n.kind === 'vanilla') {
      const save = (obj, ok) => send('vanilla', { key: n.key, ...obj }, ok || L('Gespeichert (Entwurf)', 'Saved (draft)'));
      body.push(
        field(L('Aktiv', 'Enabled'), h('input', { type: 'checkbox', checked: n.enabled, onchange: e => save({ enabled: e.target.checked }) }), L('Aus = wird nicht vergeben und nicht angezeigt', 'Off = never granted and not shown')),
        field(L('Titel', 'Title'), h('input', { value: n.title, maxlength: 60, onchange: e => save({ title: e.target.value === n.baseTitle ? '' : e.target.value }) }), L('Original: ', 'Original: ') + n.baseTitle),
        field(L('Beschreibung', 'Description'), h('textarea', { rows: 3, maxlength: 200, onchange: e => save({ description: e.target.value === n.baseDescription ? '' : e.target.value }) }, n.description)),
        field(L('Symbol', 'Icon'), iconInput(n.icon, v => save({ icon: v === n.baseIcon ? '' : v }))),
        field(L('Sichtbarkeit', 'Visibility'), sel2(VIS, n.visibility, v => save({ visibility: v }))),
        field(L('Punkte', 'Points'), h('input', { type: 'number', min: 0, max: 10000, value: n.points, onchange: e => save({ points: +e.target.value }) })),
        n.changed ? h('button', { class: 'px-btn small ghost', onclick: () => save({ reset: true }, L('Zurueckgesetzt (Entwurf)', 'Reset (draft)')) }, L('Alles auf Minecraft zuruecksetzen', 'Reset to Minecraft')) : null);
    } else body.push(...customForm(n));
    sideEl.replaceChildren(...body.filter(Boolean));
  }

  function customForm(n) {
    const a = JSON.parse(JSON.stringify(n)); delete a.kind; delete a.key; a.icon = (a.icon || 'stone').toUpperCase(); a.frame = a.frame.toUpperCase();
    const rw = a.rewards || (a.rewards = { xp: 0, items: [], commands: [], title: '', firework: false, sound: '' });
    const showTarget = !['JUMP', 'WALK', 'PLAYTIME', 'DEATH', 'MANUAL'].includes(a.type);
    return [
      field('ID', h('input', { value: a.id, disabled: true })),
      field(L('Titel', 'Title'), h('input', { value: a.title, maxlength: 60, oninput: e => { a.title = e.target.value; } })),
      field(L('Beschreibung', 'Description'), h('textarea', { rows: 3, maxlength: 200, oninput: e => { a.description = e.target.value; } }, a.description)),
      field(L('Symbol', 'Icon'), iconInput(a.icon.toLowerCase(), v => { a.icon = v; })),
      field(L('Form', 'Frame'), sel2(FRAMES, a.frame.toLowerCase(), v => { a.frame = v.toUpperCase(); })),
      field(L('Aktiv', 'Enabled'), h('input', { type: 'checkbox', checked: a.enabled, onchange: e => { a.enabled = e.target.checked; } })),
      field(L('Sichtbarkeit', 'Visibility'), sel2(VIS, a.visibility, v => { a.visibility = v; })),
      h('h3', {}, L('Ausloeser', 'Trigger')),
      field(L('Art', 'Type'), sel2(TYPES, a.type, v => { a.type = v; })),
      field(L('Ziel', 'Target'), h('input', { value: a.target, placeholder: L('z. B. ZOMBIE oder DIAMOND_ORE, leer = alles', 'e.g. ZOMBIE or DIAMOND_ORE, empty = anything'), oninput: e => { a.target = e.target.value; } }), showTarget ? null : L('Bei dieser Art wird kein Ziel gebraucht.', 'No target needed for this type.')),
      field(L('Anzahl', 'Count'), h('input', { type: 'number', min: 1, value: a.count, oninput: e => { a.count = +e.target.value; } })),
      h('h3', {}, L('Belohnung', 'Reward')),
      field(L('Punkte', 'Points'), h('input', { type: 'number', min: 0, value: a.points, oninput: e => { a.points = +e.target.value; } })),
      field('XP', h('input', { type: 'number', min: 0, value: rw.xp, oninput: e => { rw.xp = +e.target.value; } })),
      field(L('Items (je Zeile MATERIAL:Anzahl)', 'Items (one per line MATERIAL:count)'), h('textarea', { rows: 2, oninput: e => { rw.items = e.target.value.split('\n').map(s => s.trim()).filter(Boolean); } }, rw.items.join('\n'))),
      field(L('Bildschirm-Titel', 'Screen title'), h('input', { value: rw.title, oninput: e => { rw.title = e.target.value; } })),
      field(L('Sound', 'Sound'), h('input', { value: rw.sound, placeholder: 'ENTITY_PLAYER_LEVELUP', oninput: e => { rw.sound = e.target.value; } })),
      field('Feuerwerk', h('input', { type: 'checkbox', checked: rw.firework, onchange: e => { rw.firework = e.target.checked; } })),
      field(L('Konsolen-Befehle (je Zeile, %player%) - nur Operator', 'Console commands (one per line, %player%) - operators only'), h('textarea', { rows: 2, oninput: e => { rw.commands = e.target.value.split('\n').map(s => s.trim()).filter(Boolean); } }, (rw.commands || []).join('\n'))),
      h('div', { class: 'row' },
        h('button', { class: 'px-btn primary', onclick: () => send('custom', a, L('Gespeichert (Entwurf)', 'Saved (draft)')) }, L('Speichern', 'Save')),
        h('button', { class: 'px-btn danger', onclick: async () => { if (await ctx.confirm(L('Dieses Achievement loeschen (im Entwurf)? Knoten darunter haengen danach an der Wurzel.', 'Delete this achievement (in draft)? Nodes below move to the root.'))) { sel = null; send('delete', { id: a.id }, L('Geloescht (Entwurf)', 'Deleted (draft)')); } } }, L('Loeschen', 'Delete'))),
    ];
  }

  async function newCustom() {
    tab = 'own'; firstDraw = true;
    const id = (prompt(L('Kennung des neuen Achievements (a-z, 0-9, _):', 'ID of the new achievement (a-z, 0-9, _):')) || '').trim().toLowerCase();
    if (!id) return;
    const a = { id, title: id, description: '', icon: 'STONE', frame: 'TASK', enabled: true, hidden: false, category: 'allgemein', parent: sel && st.nodes.find(x => x.key === sel && x.kind === 'custom') ? sel : '', points: 10, visibility: 'AFTER_PARENT', type: 'MANUAL', target: '', count: 1, rewards: { xp: 0, items: [], commands: [], title: '', firework: false, sound: '' } };
    if (await send('custom', a, L('Angelegt (Entwurf)', 'Created (draft)'))) { sel = id; render(); }
  }

  // ---------- Stil ----------
  if (!document.getElementById('pa-style')) {
    const s = document.createElement('style'); s.id = 'pa-style';
    s.textContent = `
.pa-bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:10px 0}
.pa-main{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:14px}
@media(max-width:900px){.pa-main{grid-template-columns:1fr}}
.pa-empty{position:absolute;left:50%;top:62%;transform:translateX(-50%);color:var(--dim);text-align:center;max-width:320px;pointer-events:none}.pa-zoom{position:absolute;right:10px;bottom:10px;display:flex;gap:6px;z-index:3}.pa-tree{position:relative;height:72vh;min-height:420px;overflow:hidden;border:2px solid var(--line);background:radial-gradient(circle at 20% 20%,var(--panel2),var(--panel));cursor:grab;touch-action:none}
.pa-tree.pan{cursor:grabbing}
.pa-canvas{position:absolute;left:0;top:0;transform-origin:0 0}
.pa-lines{position:absolute;left:0;top:0;pointer-events:none}
.pa-line{stroke-width:3;opacity:.55;stroke-linecap:square}.pa-line.dim{opacity:.2;stroke-dasharray:5 5}.pa-line.hot{stroke-width:4;opacity:1}.pa-line.faded{opacity:.12}.pa-preview{stroke:#fff;stroke-width:4;stroke-dasharray:8 6;opacity:.95;pointer-events:none}
.pa-node{position:absolute;display:flex;align-items:center;justify-content:center;box-sizing:border-box;background:#4a4a4a;border:3px solid var(--bc,#a9a9a9);cursor:pointer;user-select:none;transition:transform .12s,box-shadow .12s}
.pa-node.static{position:relative;flex:none}
.pa-node:hover{transform:translateY(-2px);box-shadow:0 4px 0 rgba(0,0,0,.35)}
.pa-node.task{border-radius:4px}
.pa-node.goal{border-radius:22px;background:#5f5330}
.pa-node.challenge{border-radius:6px;background:#4a2f5e;clip-path:polygon(22% 0,78% 0,100% 22%,100% 78%,78% 100%,22% 100%,0 78%,0 22%)}
.pa-node.own{box-shadow:0 0 0 3px var(--accent)}
.pa-node.off{opacity:.4;filter:grayscale(.8)}
.pa-node.dim{opacity:.15}
.pa-node.chg::after{content:'';position:absolute;right:-3px;top:-3px;width:10px;height:10px;border-radius:50%;background:#ffb020;border:2px solid #000}
.pa-node.sel{outline:3px solid #fff;outline-offset:2px}
.pa-node.rootn{background:#34466e;cursor:default;box-shadow:0 0 18px #7aa2ff88}
.pa-node.drop{outline:3px dashed var(--good);outline-offset:3px;transform:scale(1.08)}
.pa-node.lifted{opacity:.35}
.pa-node.ghost{position:fixed;z-index:9999;pointer-events:none;opacity:.9;transform:scale(1.1)}
.pa-ic{image-rendering:pixelated;pointer-events:none}.pa-ic.ph{display:inline-block;text-align:center;font-weight:700;color:#fff}
.pa-side{border:2px solid var(--line);background:var(--panel);padding:12px;max-height:68vh;overflow:auto;display:flex;flex-direction:column;gap:10px}
.pa-f{display:flex;flex-direction:column;gap:4px}.pa-f span{font-size:13px;color:var(--dim)}
.pa-tile{display:flex;gap:12px;align-items:center;padding:8px;background:var(--panel2);border:2px solid var(--line)}
.pa-tile-text{min-width:0;word-break:break-word}
.pa-toast{position:fixed;right:16px;top:16px;z-index:10000;width:300px;background:#2b2b2b;border:3px solid #bbb;color:#fff;padding:8px 12px;animation:pa-in .35s ease-out,pa-out .4s 3.7s forwards}
.pa-toast.challenge{border-color:#b45bd6}.pa-toast.goal{border-color:#e0b64a}
.pa-toast-h{color:#ffff55;font-size:13px;margin-bottom:6px}.pa-toast.challenge .pa-toast-h{color:#d98bff}
.pa-toast-b{display:flex;gap:10px;align-items:center}
@keyframes pa-in{from{transform:translateX(120%)}to{transform:none}}@keyframes pa-out{to{transform:translateX(120%);opacity:0}}`;
    document.head.appendChild(s);
  }

  ctx.on(async d => { if (d.module === 'peakachievements') { try { st = await ctx.api('state'); } catch { return; } render(); } });
  try { meta = await ctx.api('meta'); } catch { meta = null; }
  async function loadTextures() {
    try {
      const r = await fetch('/api/texturepeak/vanilla', { credentials: 'same-origin' });
      if (!r.ok) { texStatus = { missing: true }; textures = null; return; }
      const v = await r.json(); texStatus = v;
      textures = v.ready ? { item: new Set(v.item), block: new Set(v.block) } : null;
    } catch { texStatus = { missing: true }; textures = null; }
  }
  /** Laedt die Standard-Bilder von Minecraft ueber TexturePeak herunter (einmalig) und wartet, bis sie da sind. */
  async function downloadTextures() {
    try { await fetch('/api/texturepeak/vanilla/download', { method: 'POST', headers: { 'X-Peak': '1' }, credentials: 'same-origin' }); } catch { /* wird unten geprueft */ }
    for (let i = 0; i < 90; i++) {
      await new Promise(r => setTimeout(r, 2000));
      await loadTextures(); render();
      if (texStatus && (texStatus.ready || (!texStatus.busy && texStatus.error))) break;
    }
  }
  await loadTextures();
  await reload();
}
