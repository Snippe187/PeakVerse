// Seite "Achievements": Skill-Tree aller Minecraft-Fortschritte und eigenen Achievements. Knoten anklicken zum Bearbeiten, auf einen anderen ziehen zum Umhaengen.
// Alles landet zuerst im Entwurf und gilt erst nach "Veroeffentlichen".
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  const NODE = 56, GAPX = 96, GAPY = 74;
  let st = null, meta = null, sel = null, search = '', view = { x: 40, y: 40, k: 1 }, drag = null;
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

  // ---------- Layout: waagerechter Baum ----------
  function layout() {
    const nodes = st.nodes, map = byKey(), kids = new Map();
    const parentOf = n => (n.parent && map.has(n.parent)) ? n.parent : '';
    for (const n of nodes) { const p = parentOf(n); if (!kids.has(p)) kids.set(p, []); kids.get(p).push(n); }
    for (const l of kids.values()) l.sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'vanilla' ? -1 : 1) || a.title.localeCompare(b.title));
    const pos = new Map(); let row = 0;
    // virtuelle Wurzel "PeakVerse"
    const place = (n, depth) => {
      const list = kids.get(n.key) || [];
      if (!list.length) { pos.set(n.key, { x: depth, y: row++ }); return; }
      const ys = list.map(c => { place(c, depth + 1); return pos.get(c.key).y; });
      pos.set(n.key, { x: depth, y: (Math.min(...ys) + Math.max(...ys)) / 2 });
    };
    const tops = kids.get('') || [];
    const topYs = tops.map(c => { place(c, 1); return pos.get(c.key).y; });
    pos.set('', { x: 0, y: topYs.length ? (Math.min(...topYs) + Math.max(...topYs)) / 2 : 0 });
    return { pos, rows: row, kids };
  }

  // ---------- Symbole ----------
  function icon(name, size = 32) {
    const img = h('img', { class: 'pa-ic', width: size, height: size, alt: '', draggable: 'false' });
    const tries = ['item', 'block'];
    let i = 0;
    const next = () => {
      if (iconCache[name] === false || i >= tries.length) { img.replaceWith(h('span', { class: 'pa-ic ph', style: 'width:' + size + 'px;height:' + size + 'px;line-height:' + size + 'px' }, (name || '?')[0].toUpperCase())); return; }
      img.src = '/api/texturepeak/vanilla/' + tries[i++] + '/' + encodeURIComponent(name);
    };
    img.onerror = () => { if (i >= tries.length) iconCache[name] = false; next(); };
    next();
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
      barEl, h('div', { class: 'pa-main' }, treeEl, sideEl)].filter(Boolean));
    drawBar(); drawTree(); drawSide();
  }

  function drawBar() {
    const q = h('input', { placeholder: L('Suchen...', 'Search...'), value: search, class: 'grow', oninput: e => { search = e.target.value; drawTree(); } });
    barEl.replaceChildren(q,
      h('button', { class: 'px-btn small', onclick: () => { view = { x: 40, y: 40, k: 1 }; applyView(); } }, L('Ansicht zuruecksetzen', 'Reset view')),
      h('button', { class: 'px-btn small', disabled: st.mode !== 'peak', onclick: newCustom }, '+ ' + L('Eigenes Achievement', 'Custom achievement')),
      st.draft ? h('span', { class: 'tag warn' }, L('Entwurf: noch nicht live', 'Draft: not live yet')) : h('span', { class: 'tag good' }, L('Live-Stand', 'Live')),
      h('button', { class: 'px-btn small primary', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf veroeffentlichen? Der Baum wird fuer alle Spieler neu geladen (mit Sicherung der alten Dateien).', 'Publish the draft? The tree is reloaded for all players (old files are backed up).'))) send('publish', '', L('Veroeffentlicht', 'Published')); } }, L('Veroeffentlichen', 'Publish')),
      h('button', { class: 'px-btn small ghost', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf verwerfen?', 'Discard the draft?'))) send('discard', '', L('Entwurf verworfen', 'Draft discarded')); } }, L('Verwerfen', 'Discard')));
  }

  function applyView() { const c = treeEl.querySelector('.pa-canvas'); if (c) c.style.transform = `translate(${view.x}px,${view.y}px) scale(${view.k})`; }

  function drawTree() {
    const { pos, rows, kids } = layout();
    const map = byKey();
    const W = (Math.max(...[...pos.values()].map(p => p.x)) + 1) * GAPX + NODE + 40, H = Math.max(1, rows) * GAPY + NODE + 40;
    const px = p => p.x * GAPX, py = p => p.y * GAPY;
    const ql = search.trim().toLowerCase();
    const hit = n => !ql || n.title.toLowerCase().includes(ql) || n.description.toLowerCase().includes(ql) || n.key.toLowerCase().includes(ql);
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg'); svg.setAttribute('width', W); svg.setAttribute('height', H); svg.setAttribute('class', 'pa-lines');
    const line = (a, b, dim) => {
      const x1 = px(a) + NODE, y1 = py(a) + NODE / 2, x2 = px(b), y2 = py(b) + NODE / 2, mx = (x1 + x2) / 2;
      const p = document.createElementNS(svgNS, 'path'); p.setAttribute('d', `M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2},${y2}`); p.setAttribute('class', 'pa-line' + (dim ? ' dim' : '')); svg.append(p);
    };
    const els = [];
    const rootNode = { key: '', title: 'PeakVerse', icon: 'nether_star', frame: 'challenge', enabled: true, kind: 'root', description: '' };
    for (const [pk, list] of kids) for (const c of list) line(pos.get(pk), pos.get(c.key), !c.enabled);
    for (const n of [rootNode, ...st.nodes]) {
      const p = pos.get(n.key); if (!p) continue;
      const el = h('div', { class: 'pa-node ' + n.frame + (n.enabled ? '' : ' off') + (sel === n.key ? ' sel' : '') + (n.kind === 'custom' ? ' own' : '') + (n.kind === 'root' ? ' rootn' : '') + (n.changed ? ' chg' : '') + (hit(n) ? '' : ' dim'),
        style: `left:${px(p)}px;top:${py(p)}px;width:${NODE}px;height:${NODE}px`, 'data-key': n.key, title: n.title + (n.description ? '\n' + n.description : '') }, icon(n.icon, 32));
      if (n.kind !== 'root') el.addEventListener('pointerdown', e => startDrag(e, n, el));
      els.push(el);
    }
    const canvas = h('div', { class: 'pa-canvas', style: `width:${W}px;height:${H}px` }, svg, ...els);
    treeEl.replaceChildren(canvas);
    applyView();
  }

  // ---------- Ziehen (Knoten umhaengen) und Schieben/Zoomen der Flaeche ----------
  treeEl.addEventListener('wheel', e => {
    e.preventDefault();
    const r = treeEl.getBoundingClientRect(), mx = e.clientX - r.left, my = e.clientY - r.top;
    const k = Math.min(2.2, Math.max(0.35, view.k * (e.deltaY < 0 ? 1.1 : 0.9)));
    view.x = mx - (mx - view.x) * (k / view.k); view.y = my - (my - view.y) * (k / view.k); view.k = k; applyView();
  }, { passive: false });
  treeEl.addEventListener('pointerdown', e => {
    if (e.target.closest('.pa-node')) return;
    const sx = e.clientX, sy = e.clientY, ox = view.x, oy = view.y;
    treeEl.setPointerCapture(e.pointerId); treeEl.classList.add('pan');
    const move = ev => { view.x = ox + ev.clientX - sx; view.y = oy + ev.clientY - sy; applyView(); };
    const up = () => { treeEl.removeEventListener('pointermove', move); treeEl.removeEventListener('pointerup', up); treeEl.classList.remove('pan'); };
    treeEl.addEventListener('pointermove', move); treeEl.addEventListener('pointerup', up);
  });

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
    };
    const up = async () => {
      document.removeEventListener('pointermove', move); document.removeEventListener('pointerup', up);
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
    const id = (prompt(L('Kennung des neuen Achievements (a-z, 0-9, _):', 'ID of the new achievement (a-z, 0-9, _):')) || '').trim().toLowerCase();
    if (!id) return;
    const a = { id, title: id, description: '', icon: 'STONE', frame: 'TASK', enabled: true, hidden: false, category: 'allgemein', parent: sel && st.nodes.find(x => x.key === sel) ? sel : '', points: 10, visibility: 'AFTER_PARENT', type: 'MANUAL', target: '', count: 1, rewards: { xp: 0, items: [], commands: [], title: '', firework: false, sound: '' } };
    if (await send('custom', a, L('Angelegt (Entwurf)', 'Created (draft)'))) { sel = id; render(); }
  }

  // ---------- Stil ----------
  if (!document.getElementById('pa-style')) {
    const s = document.createElement('style'); s.id = 'pa-style';
    s.textContent = `
.pa-bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:10px 0}
.pa-main{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:14px}
@media(max-width:900px){.pa-main{grid-template-columns:1fr}}
.pa-tree{position:relative;height:68vh;min-height:360px;overflow:hidden;border:2px solid var(--line);background:radial-gradient(circle at 20% 20%,var(--panel2),var(--panel));cursor:grab;touch-action:none}
.pa-tree.pan{cursor:grabbing}
.pa-canvas{position:absolute;left:0;top:0;transform-origin:0 0}
.pa-lines{position:absolute;left:0;top:0;pointer-events:none}
.pa-line{fill:none;stroke:var(--dim);stroke-width:3;opacity:.7}.pa-line.dim{opacity:.25;stroke-dasharray:5 5}
.pa-node{position:absolute;display:flex;align-items:center;justify-content:center;box-sizing:border-box;background:#6b6b6b;border:3px solid #a9a9a9;cursor:pointer;user-select:none;transition:transform .12s,box-shadow .12s}
.pa-node.static{position:relative;flex:none}
.pa-node:hover{transform:translateY(-2px);box-shadow:0 4px 0 rgba(0,0,0,.35)}
.pa-node.task{border-radius:4px}
.pa-node.goal{border-radius:26px;border-color:#e0b64a;background:#7a6a3c}
.pa-node.challenge{border-radius:6px;border-color:#b45bd6;background:#5b3a73;clip-path:polygon(25% 0,75% 0,100% 25%,100% 75%,75% 100%,25% 100%,0 75%,0 25%)}
.pa-node.own{box-shadow:0 0 0 3px var(--accent)}
.pa-node.off{opacity:.4;filter:grayscale(.8)}
.pa-node.dim{opacity:.15}
.pa-node.chg::after{content:'';position:absolute;right:-3px;top:-3px;width:10px;height:10px;border-radius:50%;background:#ffb020;border:2px solid #000}
.pa-node.sel{outline:3px solid #fff;outline-offset:2px}
.pa-node.rootn{border-color:#7aa2ff;background:#34466e;cursor:default}
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
  await reload();
}
