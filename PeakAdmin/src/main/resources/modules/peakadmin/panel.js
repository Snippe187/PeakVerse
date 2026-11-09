// Seite "Verwaltung": Spieler (Aktionen, Inventar, Endertruhe, Fortschritte), Konsole, Status, Chat, Sicherungen und Neustart.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let tab = 'players', players = [], q = '', cur = null, detail = null, advs = null, advTab = null, inv = 'inventory';
  let lp = null, lpSel = null, lpQ = '', lpNode = '', consolePos = -1, consoleLines = [], chatLast = 0, chatMsgs = [], chatMutes = [], status = null, bk = null, cfg = null;
  const timers = [];
  const alive = () => root.isConnected;
  const every = (fn, ms) => { const id = setInterval(() => { if (!alive()) { clearInterval(id); return; } fn(); }, ms); timers.push(id); };

  async function call(path, body, ok) {
    try { const r = await ctx.api(path, body === undefined ? undefined : { method: 'POST', body }); if (ok) ctx.toast(ok, 'good'); return r ?? true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return null; }
  }
  const when = ms => ms ? new Date(ms).toLocaleString(ctx.lang) : '–';
  const mb = b => (b / 1048576).toFixed(b > 1e9 ? 0 : 1) + ' MB';

  if (!document.getElementById('pa-style')) {
    const st = document.createElement('style'); st.id = 'pa-style';
    st.textContent = `.pa-tabs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}.pa-tabs button.on{background:var(--accent);color:var(--on-accent)}
.pa-cols{display:grid;grid-template-columns:300px 1fr;gap:14px;align-items:start}@media(max-width:900px){.pa-cols{grid-template-columns:1fr}}
.pa-list{max-height:70vh;overflow:auto}.pa-row{display:flex;gap:8px;align-items:center;padding:6px 8px;cursor:pointer;border:2px solid transparent}.pa-row:hover{background:var(--panel2)}.pa-row.sel{border-color:var(--accent)}
.pa-dot{width:10px;height:10px;background:var(--dim)}.pa-dot.on{background:var(--good)}
.pa-box{border:1px solid var(--line);padding:10px;margin:8px 0;display:grid;gap:8px}.pa-acts{display:flex;gap:6px;flex-wrap:wrap}
.pa-ic{image-rendering:pixelated;display:inline-block;vertical-align:middle;text-align:center;font-weight:bold}.pa-ic.ph{background:#555;color:#ddd}
.mc-panel{background:#c6c6c6;border:4px solid;border-color:#fff #555 #555 #fff;padding:10px;display:inline-block;max-width:100%;overflow:auto;color:#404040;font-family:monospace}
.mc-title{font-weight:bold;margin-bottom:6px;color:#404040}.mc-sub{font-size:11px;margin:8px 0 3px;color:#555}
.mc-row{display:flex}.mc-top{display:flex;gap:18px;align-items:flex-start}.mc-col{display:flex;flex-direction:column}.mc-off{margin-top:0}
.mc-slot{width:36px;height:36px;flex:none;background:#8b8b8b;border:2px solid;border-color:#373737 #fff #fff #373737;box-sizing:border-box;display:flex;align-items:center;justify-content:center;position:relative}
.mc-slot.full{cursor:pointer}.mc-slot.full:hover{background:#a9a9c9;outline:2px solid #fff;outline-offset:-2px}
.mc-slot.lab:empty::after{content:attr(data-label);font-size:8px;color:#444;text-align:center;line-height:1}
.mc-n{position:absolute;right:1px;bottom:-1px;color:#fff;font-size:12px;font-weight:bold;text-shadow:1px 1px 0 #3f3f3f}
.mc-dur{position:absolute;left:3px;bottom:2px;height:2px;pointer-events:none}
.pa-tip{position:fixed;z-index:9999;pointer-events:none;background:#100010f0;border:2px solid #2a0a5e;border-radius:3px;padding:6px 9px;color:#fff;font:12px/1.4 monospace;max-width:320px;box-shadow:0 0 0 1px #000}
.pa-tip .tn{font-weight:bold;color:#fff}.pa-tip .tn.ench{color:#5ff}.pa-tip .te{color:#a9a9a9}.pa-tip .tl{color:#b0b0e0;font-style:italic}.pa-tip .tg{color:#a9a9a9}
.pa-tip .ti{color:#555;margin-top:4px;font-size:11px}.pa-tip .th{color:#5f5;margin-top:3px;font-size:11px}
.pa-tip .adv-task{color:#fff}.pa-tip .adv-goal{color:#ffb454}.pa-tip .adv-challenge{color:#c77dff}
.pa-prog{display:flex;align-items:center;gap:10px;margin:6px 0}.pa-prog-bar{flex:1;max-width:340px;height:10px;background:var(--panel2);border:1px solid var(--line)}.pa-prog-bar div{height:100%;background:var(--accent)}
.pa-advtabs{display:flex;gap:6px;flex-wrap:wrap;margin:8px 0}.pa-advtabs button{display:flex;align-items:center;gap:4px}
.pa-advview{overflow:auto;max-height:60vh;background:#1e1e1e;border:2px solid #555;padding:4px}.pa-advcanvas{position:relative}
.pa-lines{position:absolute;left:0;top:0;pointer-events:none}.pa-lines path{fill:none;stroke:#555;stroke-width:3}.pa-lines path.on{stroke:#e8e8e8}
.pa-adv{position:absolute;width:44px;height:44px;box-sizing:border-box;display:flex;align-items:center;justify-content:center;cursor:pointer;background:#303030;border:3px solid #6b6b6b;opacity:.55}
.pa-adv.goal{border-radius:14px}.pa-adv.challenge{border-color:#8a5cc7;border-radius:4px;clip-path:polygon(10% 0,90% 0,100% 10%,100% 90%,90% 100%,10% 100%,0 90%,0 10%)}
.pa-adv.done{opacity:1;background:#4a4a4a;border-color:#e8e8e8}.pa-adv.goal.done{border-color:#ffb454}.pa-adv.challenge.done{border-color:#c77dff}
.pa-adv:hover{opacity:1;outline:2px solid #fff}
.pa-con{background:#0b0b0b;color:#ddd;font:12px/1.35 monospace;height:60vh;overflow:auto;padding:8px;white-space:pre-wrap;word-break:break-all}
.pa-chart{width:100%;height:110px;background:var(--panel2);border:1px solid var(--line)}.pa-stat{display:flex;gap:18px;flex-wrap:wrap}.pa-stat div{min-width:110px}.pa-stat b{font-size:20px;display:block}`;
    document.head.appendChild(st);
  }

  // ---------- Spieler ----------
  async function loadPlayers() { players = (await call('players')) || []; }
  async function open(p) {
    cur = p; advs = null; detail = await call('player/' + p.uuid); render();
    if (detail && detail.online) { advs = await call('player/' + p.uuid + '/advancements'); if (cur === p) render(); }
  }
  // ---------- Item-Bilder (TexturePeak) und Tooltip ----------
  let texInfo = null, textures = null; // texInfo: Antwort von /api/texturepeak/vanilla oder {missing:true}
  async function loadTex() {
    try {
      const r = await fetch('/api/texturepeak/vanilla', { credentials: 'same-origin' });
      if (!r.ok) { texInfo = { missing: true }; textures = null; return; }
      texInfo = await r.json(); textures = texInfo.ready ? { item: new Set(texInfo.item), block: new Set(texInfo.block) } : null;
    } catch { texInfo = { missing: true }; textures = null; }
  }
  async function downloadTex() {
    texInfo = { busy: true }; render();
    try { await fetch('/api/texturepeak/vanilla/download', { method: 'POST', headers: { 'X-Peak': '1' }, credentials: 'same-origin' }); } catch { /* unten geprueft */ }
    for (let i = 0; i < 60; i++) { await new Promise(r => setTimeout(r, 2000)); await loadTex(); if (!texInfo.busy) break; }
    render();
  }
  const pretty = id => id.replace(/^minecraft:/, '').split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  function icon(name, size) {
    const n = (name || '').replace(/^minecraft:/, '');
    const kind = textures ? (textures.item.has(n) ? 'item' : textures.block.has(n) ? 'block' : null) : (texInfo === null ? 'item' : null);
    const ph = () => h('span', { class: 'pa-ic ph', style: `width:${size}px;height:${size}px;line-height:${size}px;font-size:${Math.round(size / 2)}px` }, (n || '?')[0].toUpperCase());
    if (!kind) return ph();
    const img = h('img', { class: 'pa-ic', width: size, height: size, alt: '', draggable: 'false', src: '/api/texturepeak/vanilla/' + kind + '/' + encodeURIComponent(n) });
    img.onerror = () => img.replaceWith(ph());
    return img;
  }
  function texBanner() {
    if (!texInfo || texInfo.ready) return null;
    const msg = texInfo.missing ? L('Die Item-Bilder fehlen: Das Modul TexturePeak ist aus oder nicht erreichbar.', 'Item images missing: the TexturePeak module is off or unreachable.')
      : texInfo.busy ? L('Die Minecraft-Bilder werden gerade geladen ...', 'Loading the Minecraft images ...')
      : texInfo.error ? L('Laden der Bilder fehlgeschlagen: ', 'Loading images failed: ') + texInfo.error
      : L('Die Minecraft-Bilder sind noch nicht geladen, deshalb siehst du Buchstaben statt Symbolen.', 'The Minecraft images are not loaded yet, so you see letters instead of icons.');
    return h('div', { class: 'tag warn', style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:8px 0' }, h('span', {}, msg),
      texInfo.missing || texInfo.busy ? null : h('button', { class: 'px-btn small primary', onclick: downloadTex }, L('Jetzt laden (einmalig)', 'Load now (once)')));
  }
  let tipEl = null;
  function hideTip() { if (tipEl) { tipEl.remove(); tipEl = null; } }
  function showTip(e, lines) {
    if (!root.isConnected) { hideTip(); return; }
    if (!tipEl) { tipEl = h('div', { class: 'pa-tip' }); document.body.appendChild(tipEl); }
    tipEl.replaceChildren(...lines);
    const w = tipEl.offsetWidth, hgt = tipEl.offsetHeight;
    tipEl.style.left = Math.max(4, Math.min(e.clientX + 14, innerWidth - w - 6)) + 'px';
    tipEl.style.top = Math.max(4, Math.min(e.clientY - 8, innerHeight - hgt - 6)) + 'px';
  }
  const withTip = (el, linesFn) => { el.onmousemove = e => showTip(e, linesFn()); el.onmouseleave = hideTip; return el; };
  async function act(a, body, ok, confirmText) {
    if (confirmText && !(await ctx.confirm(confirmText))) return;
    const r = await call('player/' + cur.uuid + '/' + a, body ?? {}, ok);
    if (r && r !== true && r.uuid) detail = r;
    if (a === 'advancement') advs = await call('player/' + cur.uuid + '/advancements');
    await loadPlayers(); render();
  }
  const btn = (label, fn, cls = '') => h('button', { class: 'px-btn small ' + cls, onclick: fn }, label);
  // Tooltip wie im Spiel: Name, Verzauberungen, Beschreibung, Haltbarkeit, technische ID
  function itemTip(it) {
    const lines = [h('div', { class: 'tn' + (it.enchants ? ' ench' : '') }, it.name || pretty(it.type))];
    (it.enchants || []).forEach(e => lines.push(h('div', { class: 'te' }, pretty(e.replace(/ (\d+)$/, '')) + ' ' + (e.match(/ (\d+)$/) || ['', ''])[1])));
    (it.lore || []).forEach(l => lines.push(h('div', { class: 'tl' }, l)));
    if (it.maxDurability) lines.push(h('div', { class: 'tg' }, L('Haltbarkeit: ', 'Durability: ') + it.durability + ' / ' + it.maxDurability));
    lines.push(h('div', { class: 'ti' }, it.type));
    return lines;
  }
  function slotEl(it, slot, label) {
    const el = h('div', { class: 'mc-slot' + (it ? ' full' : '') + (label ? ' lab' : ''), 'data-label': label || '',
      onclick: it ? () => { hideTip(); act('remove-slot', { inv, slot }, L('Entfernt', 'Removed'), L('Gegenstand entfernen?', 'Remove item?') + ' ' + (it.name || pretty(it.type))); } : null },
      it ? [icon(it.type, 32), it.amount > 1 ? h('span', { class: 'mc-n' }, it.amount) : null,
        it.maxDurability && it.durability < it.maxDurability ? h('span', { class: 'mc-dur', style: `width:${Math.round(13 * it.durability / it.maxDurability)}px;background:hsl(${Math.round(120 * it.durability / it.maxDurability)},90%,45%)` }) : null] : null);
    if (it) withTip(el, () => [...itemTip(it), h('div', { class: 'th' }, L('Klick: entfernen', 'Click: remove'))]);
    return el;
  }
  // Aufbau wie im Spiel: Ruestung und Zweithand links, Rucksack 3x9, darunter die Hotbar
  function mcInventory(items, which) {
    const by = new Map(items.map(i => [i.slot, i]));
    const s = n => slotEl(by.get(n), n);
    if (which === 'ender') {
      const rows = [];
      for (let r = 0; r < 3; r++) rows.push(h('div', { class: 'mc-row' }, [...Array(9).keys()].map(c => s(r * 9 + c))));
      return h('div', { class: 'mc-panel' }, h('div', { class: 'mc-title' }, L('Endertruhe', 'Ender chest')), rows);
    }
    const main = [];
    for (let r = 0; r < 3; r++) main.push(h('div', { class: 'mc-row' }, [...Array(9).keys()].map(c => s(9 + r * 9 + c))));
    const armor = [[39, L('Kopf', 'Head')], [38, L('Brust', 'Chest')], [37, L('Beine', 'Legs')], [36, L('Füße', 'Feet')]];
    return h('div', { class: 'mc-panel' },
      h('div', { class: 'mc-title' }, L('Inventar', 'Inventory')),
      h('div', { class: 'mc-top' }, h('div', { class: 'mc-col' }, armor.map(([n, l]) => slotEl(by.get(n), n, l))),
        h('div', { class: 'mc-col mc-off' }, slotEl(by.get(40), 40, L('Zweithand', 'Offhand')))),
      h('div', { class: 'mc-sub' }, L('Rucksack', 'Backpack')), main,
      h('div', { class: 'mc-sub' }, L('Hotbar', 'Hotbar')), h('div', { class: 'mc-row' }, [...Array(9).keys()].map(c => s(c))));
  }
  // ---------- Fortschritte: Baum wie im Spiel, Klick auf einen Knoten vergibt oder entzieht ----------
  const FRAME = { task: L('Aufgabe', 'Task'), goal: L('Ziel', 'Goal'), challenge: L('Herausforderung', 'Challenge') };
  function advTree() {
    if (!advs) return h('div', { class: 'sub' }, L('Fortschritte werden geladen ...', 'Loading advancements ...'));
    const map = new Map(advs.map(a => [a.key, a])), kids = new Map();
    advs.forEach(a => { if (a.parent && map.has(a.parent)) { if (!kids.has(a.parent)) kids.set(a.parent, []); kids.get(a.parent).push(a); } });
    const roots = advs.filter(a => !a.parent || !map.has(a.parent));
    const countDone = a => (a.done ? 1 : 0) + (kids.get(a.key) || []).reduce((s, k) => s + countDone(k), 0);
    const countAll = a => 1 + (kids.get(a.key) || []).reduce((s, k) => s + countAll(k), 0);
    const total = advs.length, done = advs.filter(a => a.done).length;
    if (!advTab || !map.has(advTab)) advTab = roots[0] && roots[0].key;
    const root0 = map.get(advTab);
    const nodes = [], lines = [];
    let nextRow = 0, maxDepth = 0;
    (function place(a, depth) {
      maxDepth = Math.max(maxDepth, depth);
      const ch = kids.get(a.key) || [];
      let row;
      if (!ch.length) row = nextRow++;
      else { const rs = ch.map(c => place(c, depth + 1)); row = (rs[0] + rs[rs.length - 1]) / 2; }
      nodes.push({ a, depth, row });
      return row;
    })(root0, 0);
    const pos = new Map(nodes.map(n => [n.a.key, n]));
    const CW = 76, CH = 64, PAD = 36;
    nodes.forEach(n => (kids.get(n.a.key) || []).forEach(c => {
      const p = pos.get(n.a.key), q = pos.get(c.key), x1 = PAD + p.depth * CW, y1 = PAD + p.row * CH, x2 = PAD + q.depth * CW, y2 = PAD + q.row * CH, mx = (x1 + x2) / 2;
      lines.push(`<path d="M${x1} ${y1}H${mx}V${y2}H${x2}" class="${c.done ? 'on' : ''}"/>`);
    }));
    const svg = h('div', { class: 'pa-lines' }); svg.innerHTML = `<svg width="${PAD * 2 + maxDepth * CW}" height="${PAD * 2 + Math.max(0, nextRow - 1) * CH}">${lines.join('')}</svg>`;
    const el = n => {
      const a = n.a;
      const d = h('div', { class: 'pa-adv ' + a.frame + (a.done ? ' done' : ''), style: `left:${PAD + n.depth * CW - 22}px;top:${PAD + n.row * CH - 22}px`,
        onclick: () => { hideTip(); act('advancement', { key: a.key, grant: !a.done }, a.done ? L('Entzogen', 'Revoked') : L('Vergeben', 'Granted')); } }, icon(a.icon, 28));
      return withTip(d, () => [h('div', { class: 'tn adv-' + a.frame }, a.title), h('div', { class: 'tl' }, a.description), h('div', { class: 'tg' }, (FRAME[a.frame] || a.frame) + ' · ' + (a.done ? L('erreicht', 'done') : L('nicht erreicht', 'not done'))),
        h('div', { class: 'th' }, a.done ? L('Klick: entziehen', 'Click: revoke') : L('Klick: vergeben', 'Click: grant'))]);
    };
    return h('div', {},
      h('div', { class: 'pa-prog' }, h('div', { class: 'pa-prog-bar' }, h('div', { style: `width:${total ? Math.round(100 * done / total) : 0}%` })), h('b', {}, done + ' / ' + total), h('span', { class: 'sub' }, L('Fortschritte erreicht', 'advancements done'))),
      h('div', { class: 'pa-advtabs' }, roots.map(r => h('button', { class: 'px-btn small' + (r.key === advTab ? ' primary' : ''), onclick: () => { advTab = r.key; render(); } }, icon(r.icon, 18), ' ', r.title, ' ', h('span', { class: 'sub' }, countDone(r) + '/' + countAll(r))))),
      h('div', { class: 'pa-advview' }, h('div', { class: 'pa-advcanvas', style: `width:${PAD * 2 + maxDepth * CW}px;height:${PAD * 2 + Math.max(0, nextRow - 1) * CH}px` }, svg, nodes.map(el))),
      h('div', { class: 'sub' }, L('Hell = erreicht. Maus darüber: Beschreibung. Klick: vergeben oder entziehen.', 'Bright = done. Hover for the description. Click to grant or revoke.')));
  }
  function playerView() {
    if (!cur || !detail) return h('div', { class: 'sub' }, L('Links einen Spieler waehlen.', 'Pick a player on the left.'));
    const d = detail, on = d.online;
    const reason = h('input', { class: 'px-input', placeholder: L('Grund (optional)', 'Reason (optional)') });
    const item = h('input', { class: 'px-input', placeholder: 'minecraft:diamond' }), amount = h('input', { class: 'px-input', type: 'number', value: 1, min: 1, style: 'width:90px' });
    const lvl = h('input', { class: 'px-input', type: 'number', value: d.level ?? 0, style: 'width:90px' });
    const gm = h('select', { class: 'px-input' }, ['survival', 'creative', 'adventure', 'spectator'].map(m => h('option', { value: m, selected: m === d.gamemode }, m)));
    return h('div', {},
      h('h3', {}, d.name, ' ', h('span', { class: 'tag' + (on ? '' : ' warn') }, on ? 'online' : 'offline'), d.op ? h('span', { class: 'tag' }, 'OP') : null, d.banned ? h('span', { class: 'tag warn' }, L('gebannt', 'banned')) : null, d.muted ? h('span', { class: 'tag warn' }, L('stumm', 'muted')) : null),
      h('div', { class: 'sub' }, d.uuid + ' · ' + L('zuletzt', 'last seen') + ' ' + when(d.lastSeen)),
      on ? h('div', { class: 'pa-stat' }, h('div', {}, L('Leben', 'Health'), h('b', {}, Math.round(d.health))), h('div', {}, L('Hunger', 'Food'), h('b', {}, d.food)), h('div', {}, 'XP-Level', h('b', {}, d.level)), h('div', {}, 'Ping', h('b', {}, d.ping)), h('div', {}, L('Ort', 'Position'), h('b', {}, d.x + ' ' + d.y + ' ' + d.z), h('span', { class: 'sub' }, d.world))) : h('div', { class: 'sub' }, L('Inventar, Endertruhe und Fortschritte sind nur fuer Spieler online verfuegbar.', 'Inventory, ender chest and advancements need the player online.')),
      h('div', { class: 'pa-box' }, h('b', {}, L('Strafen und Zugang', 'Punishments and access')), reason,
        h('div', { class: 'pa-acts' },
          on ? btn('Kick', () => act('kick', { reason: reason.value }, 'Kick', L('Spieler kicken?', 'Kick player?'))) : null,
          d.banned ? btn(L('Entbannen', 'Unban'), () => act('pardon', {}, L('Entbannt', 'Unbanned'))) : btn('Ban', () => act('ban', { reason: reason.value }, 'Ban', L('Spieler dauerhaft bannen?', 'Ban player permanently?')), 'danger'),
          d.muted ? btn(L('Stumm aufheben', 'Unmute'), () => act('unmute', {}, L('Aufgehoben', 'Done'))) : btn(L('Stumm schalten', 'Mute'), () => act('mute', { reason: reason.value, hours: 0 }, L('Stumm', 'Muted'))),
          btn(d.whitelisted ? L('Von Whitelist', 'Un-whitelist') : 'Whitelist', () => act('whitelist', { on: !d.whitelisted }, 'OK')),
          btn(d.op ? 'De-OP' : 'OP', () => act('op', { on: !d.op }, 'OK', (d.op ? 'De-OP: ' : 'OP: ') + d.name + '?'), 'danger'))),
      on ? h('div', { class: 'pa-box' }, h('b', {}, L('Aktionen', 'Actions')),
        h('div', { class: 'pa-acts' }, btn(L('Heilen', 'Heal'), () => act('heal', {}, L('Geheilt', 'Healed'))), gm, btn(L('Spielmodus setzen', 'Set mode'), () => act('gamemode', { mode: gm.value }, 'OK')), lvl, btn(L('XP-Level setzen', 'Set XP level'), () => act('level', { level: +lvl.value }, 'OK'))),
        h('div', { class: 'pa-acts' }, h('span', { class: 'sub' }, L('Zu Spieler teleportieren:', 'Teleport to player:')), players.filter(p => p.online && p.uuid !== d.uuid).map(p => btn(p.name, () => act('tp-player', { target: p.uuid }, 'OK'))))) : null,
      on ? h('div', { class: 'pa-box' },
        h('div', { class: 'pa-acts' }, btn(L('Inventar', 'Inventory'), () => { inv = 'inventory'; render(); }, inv === 'inventory' ? 'primary' : ''), btn(L('Endertruhe', 'Ender chest'), () => { inv = 'ender'; render(); }, inv === 'ender' ? 'primary' : ''),
          btn(L('Alles leeren', 'Clear all'), () => act('clear', { inv }, L('Geleert', 'Cleared'), L('Dieses Inventar komplett leeren?', 'Clear this inventory completely?')), 'danger')),
        texBanner(), mcInventory(inv === 'ender' ? d.ender : d.inventory, inv),
        h('div', { class: 'sub' }, L('Maus über einen Gegenstand: Informationen. Klick: entfernen.', 'Hover an item for details. Click to remove it.')),
        h('div', { class: 'pa-acts' }, item, amount, btn(L('Geben', 'Give'), () => act('give', { inv, item: item.value, amount: +amount.value }, L('Gegeben', 'Given'))))) : null,
      on ? h('div', { class: 'pa-box' }, h('b', {}, L('Fortschritte', 'Advancements')), advTree()) : null,
      h('div', { class: 'sub' }, L('Rollen, Rechte, Notizen und Verwarnungen: Reiter "Spieler".', 'Roles, permissions, notes and warnings: tab "Players".')));
  }
  function playersTab() {
    const f = q.toLowerCase();
    const rows = players.filter(p => !f || p.name.toLowerCase().includes(f)).sort((a, b) => b.online - a.online || a.name.localeCompare(b.name)).slice(0, 300);
    return h('div', { class: 'pa-cols' },
      h('div', {}, h('input', { class: 'px-input', placeholder: L('Spieler suchen...', 'Search players...'), value: q, oninput: e => { q = e.target.value; const l = root.querySelector('.pa-list'); if (l) l.replaceWith(playersTab().querySelector('.pa-list')); } }),
        h('div', { class: 'pa-list' }, rows.map(p => h('div', { class: 'pa-row' + (cur && cur.uuid === p.uuid ? ' sel' : ''), onclick: () => open(p) }, h('span', { class: 'pa-dot' + (p.online ? ' on' : '') }), h('b', {}, p.name), p.op ? h('span', { class: 'tag' }, 'OP') : null, p.banned ? h('span', { class: 'tag warn' }, 'ban') : null)))),
      h('div', {}, playerView()));
  }

  // ---------- Konsole ----------
  async function pollConsole() {
    const r = await ctx.api('console?since=' + consolePos).catch(() => null); if (!r) return;
    if (r.reset) consoleLines = [];
    consolePos = r.position; if (r.lines.length) consoleLines = consoleLines.concat(r.lines).slice(-1500);
    const box = root.querySelector('.pa-con'); if (box && r.lines.length) { const bottom = box.scrollTop + box.clientHeight >= box.scrollHeight - 30; box.textContent = consoleLines.join('\n'); if (bottom) box.scrollTop = box.scrollHeight; }
  }
  function consoleTab() {
    const cmd = h('input', { class: 'px-input', placeholder: L('Befehl (ohne /), nur Operatoren', 'Command (no /), operators only'), style: 'flex:1', onkeydown: e => { if (e.key === 'Enter') run(); } });
    const run = async () => { const c = cmd.value.trim(); if (!c) return; cmd.value = ''; await call('console/run', { cmd: c }); setTimeout(pollConsole, 400); };
    const box = h('div', { class: 'pa-con' }, consoleLines.join('\n'));
    setTimeout(() => { box.scrollTop = box.scrollHeight; }, 0);
    return h('div', {}, box, h('div', { class: 'row', style: 'margin-top:8px' }, cmd, btn(L('Senden', 'Send'), run, 'primary')));
  }

  // ---------- Status ----------
  function chart(data, key, max, color) {
    const c = h('canvas', { class: 'pa-chart', width: 600, height: 110 }); const g = c.getContext('2d');
    const pts = data.map(d => d[key]); const m = max || Math.max(1, ...pts) * 1.1;
    g.strokeStyle = color; g.lineWidth = 2; g.beginPath();
    pts.forEach((v, i) => { const x = pts.length < 2 ? 0 : i / (pts.length - 1) * 600, y = 108 - Math.min(1, v / m) * 104; i ? g.lineTo(x, y) : g.moveTo(x, y); }); g.stroke();
    return c;
  }
  function statusTab() {
    if (!status) return h('div', { class: 'sub' }, '...');
    const s = status, up = Math.floor(s.uptime / 60000);
    return h('div', {},
      h('div', { class: 'pa-stat' }, h('div', {}, 'TPS', h('b', {}, s.tps.toFixed(1))), h('div', {}, 'MSPT', h('b', {}, s.mspt.toFixed(1))), h('div', {}, 'RAM', h('b', {}, mb(s.ramUsed) + ' / ' + mb(s.ramMax))), h('div', {}, L('Spieler', 'Players'), h('b', {}, s.players + ' / ' + s.maxPlayers)), h('div', {}, 'Uptime', h('b', {}, Math.floor(up / 60) + ' h ' + (up % 60) + ' min')), h('div', {}, 'Minecraft', h('b', {}, s.version))),
      h('div', { class: 'pa-box' }, h('b', {}, 'TPS'), chart(s.history, 'tps', 20, '#5bd25b'), h('b', {}, 'MSPT'), chart(s.history, 'mspt', 0, '#e0b040'), h('b', {}, 'RAM'), chart(s.history, 'ram', s.ramMax, '#6aa0ff'), h('b', {}, L('Spieler', 'Players')), chart(s.history, 'players', 0, '#c070ff'),
        h('div', { class: 'sub' }, L('Ein Punkt alle 30 Sekunden.', 'One point every 30 seconds.'))),
      h('div', { class: 'pa-box' }, h('b', {}, L('Welten', 'Worlds')), s.worlds.map(w => h('div', { class: 'row' }, h('b', {}, w.name), h('span', { class: 'sub' }, w.entities + ' ' + L('Entitaeten', 'entities') + ' · ' + w.chunks + ' Chunks · ' + w.players + ' ' + L('Spieler', 'players'))))));
  }

  // ---------- Chat ----------
  async function pollChat() {
    const r = await ctx.api('chat?since=' + chatLast).catch(() => null); if (!r) return;
    chatLast = r.last; chatMutes = r.mutes;
    if (r.messages.length) { chatMsgs = chatMsgs.concat(r.messages).slice(-500); const box = root.querySelector('.pa-con'); if (box && tab === 'chat') { box.textContent = chatText(); box.scrollTop = box.scrollHeight; } }
  }
  const chatText = () => chatMsgs.map(m => new Date(m.t).toLocaleTimeString(ctx.lang) + '  ' + (m.player ? m.player + ': ' : '') + m.text).join('\n');
  function chatTab() {
    const msg = h('input', { class: 'px-input', placeholder: L('Nachricht an alle Spieler...', 'Message to all players...'), style: 'flex:1', onkeydown: e => { if (e.key === 'Enter') send(); } });
    const send = async () => { const x = msg.value.trim(); if (!x) return; msg.value = ''; await call('chat/say', { text: x }); setTimeout(pollChat, 300); };
    const box = h('div', { class: 'pa-con' }, chatText()); setTimeout(() => { box.scrollTop = box.scrollHeight; }, 0);
    return h('div', {}, box, h('div', { class: 'row', style: 'margin-top:8px' }, msg, btn(L('Senden', 'Send'), send, 'primary')),
      chatMutes.length ? h('div', { class: 'pa-box' }, h('b', {}, L('Stumm geschaltet', 'Muted')), chatMutes.map(m => h('div', { class: 'row' }, h('b', {}, m.name), h('span', { class: 'sub' }, m.reason || ''), btn(L('Aufheben', 'Unmute'), async () => { await call('player/' + m.uuid + '/unmute', {}); await pollChat(); render(); })))) : null);
  }

  // ---------- Sicherungen & Neustart ----------
  async function loadBackups() { bk = await call('backups'); cfg = await call('settings'); }
  function backupsTab() {
    if (!bk || !cfg) return h('div', { class: 'sub' }, '...');
    const iv = h('input', { class: 'px-input', type: 'number', value: cfg.interval, min: 0, style: 'width:90px' }), keep = h('input', { class: 'px-input', type: 'number', value: cfg.keep, min: 1, style: 'width:90px' });
    const daily = h('input', { class: 'px-input', value: cfg.dailyAt, placeholder: '04:30', style: 'width:90px' }), warn = h('input', { class: 'px-input', type: 'number', value: cfg.warn, min: 0, style: 'width:90px' });
    const mins = h('input', { class: 'px-input', type: 'number', value: 5, min: 0, style: 'width:90px' }), why = h('input', { class: 'px-input', placeholder: L('Grund (optional)', 'Reason (optional)') });
    return h('div', {},
      h('div', { class: 'pa-box' }, h('div', { class: 'row' }, h('b', {}, L('Sicherungen', 'Backups')), btn(bk.running ? L('laeuft...', 'running...') : L('Jetzt sichern', 'Back up now'), async () => { const r = await call('backups/create', {}, L('Sicherung gestartet', 'Backup started')); if (r && r !== true) { bk = r; render(); } }, 'primary')),
        h('div', { class: 'sub' }, L('Ordner: ', 'Folder: ') + bk.folder + (bk.last ? ' · ' + L('zuletzt: ', 'last: ') + bk.last : '')),
        bk.backups.length ? bk.backups.map(b => h('div', { class: 'row' }, h('b', {}, b.name), h('span', { class: 'sub' }, mb(b.size) + ' · ' + when(b.time)), btn(L('Loeschen', 'Delete'), async () => { if (await ctx.confirm(L('Sicherung loeschen?', 'Delete backup?') + ' ' + b.name)) { const r = await call('backups/delete', { name: b.name }); if (r && r !== true) { bk = r; render(); } } }, 'danger'))) : h('div', { class: 'sub' }, L('Noch keine Sicherung.', 'No backups yet.')),
        h('div', { class: 'sub' }, L('Zum Zurueckspielen den Server beenden und die ZIP in den Welt-Ordner entpacken.', 'To restore, stop the server and unpack the ZIP into the world folder.'))),
      h('div', { class: 'pa-box' }, h('b', {}, L('Neustart', 'Restart')),
        bk.restartAt ? h('div', { class: 'row' }, h('span', { class: 'tag warn' }, L('Neustart geplant: ', 'Restart planned: ') + when(bk.restartAt)), btn(L('Absagen', 'Cancel'), async () => { const r = await call('restart', { cancel: true }); if (r && r !== true) { bk = r; render(); } })) :
          h('div', { class: 'pa-acts' }, h('span', {}, L('In', 'In')), mins, h('span', {}, L('Minuten', 'minutes')), why, btn(L('Neustart planen', 'Schedule restart'), async () => { if (!(await ctx.confirm(L('Server neu starten? Der Server beendet sich, der Hoster oder das Start-Skript startet ihn wieder.', 'Restart the server? It shuts down; the host or start script starts it again.')))) return; const r = await call('restart', { minutes: +mins.value, reason: why.value }); if (r && r !== true) { bk = r; render(); } }, 'danger'))),
      h('div', { class: 'pa-box' }, h('b', {}, L('Zeitplan', 'Schedule')),
        h('div', { class: 'pa-acts' }, h('span', {}, L('Sichern alle', 'Back up every')), iv, h('span', {}, L('Stunden (0 = aus), behalten', 'hours (0 = off), keep')), keep),
        h('div', { class: 'pa-acts' }, h('span', {}, L('Taeglicher Neustart um', 'Daily restart at')), daily, h('span', {}, L('(leer = aus), Vorwarnung', '(empty = off), warning')), warn, h('span', {}, 'min')),
        btn(L('Speichern', 'Save'), async () => { const r = await call('settings', { interval: +iv.value, keep: +keep.value, dailyAt: daily.value, warn: +warn.value }, L('Gespeichert', 'Saved')); if (r && r !== true) { cfg = r; render(); } }, 'primary')));
  }

  // ---------- Rechte (LuckPerms) ----------
  async function loadLp() { lp = await call('lp'); if (lp && lp.available && !lpSel && lp.groups.length) lpSel = lp.groups[0].name; }
  async function lpDo(body, ok) { const r = await call('lp/group', body, ok); if (r && r !== true) { lp = Object.assign({ available: true }, r); render(); } }
  function permsTab() {
    if (!lp) return h('div', { class: 'sub' }, '...');
    if (!lp.available) return h('div', { class: 'pa-box' }, h('b', {}, L('LuckPerms ist nicht installiert', 'LuckPerms is not installed')),
      h('div', { class: 'sub' }, L('Installiere das Plugin LuckPerms auf dem Server (Ordner plugins) und starte neu. Dann erscheint hier die komplette Rechte-Verwaltung: Gruppen, Rechte, Erben. Rechte aller Plugins (z. B. Assassin-Tales) kannst du dann aus einer Liste waehlen.', 'Install the LuckPerms plugin on the server (plugins folder) and restart. Then full permission management appears here: groups, permissions, inheritance. Permissions of all plugins can be picked from a list.')),
      h('div', { class: 'sub' }, L('Ohne LuckPerms gibt es die PeakCore-Rollen im Reiter "Spieler".', 'Without LuckPerms use the PeakCore roles in the "Players" tab.')));
    const g = lp.groups.find(x => x.name === lpSel);
    const nn = h('input', { class: 'px-input', placeholder: L('neue Gruppe', 'new group') });
    const node = h('input', { class: 'px-input', list: 'pa-nodes', placeholder: 'plugin.recht.name', value: lpNode, style: 'flex:1', oninput: e => { lpNode = e.target.value; } });
    const dl = h('datalist', { id: 'pa-nodes' }, lp.catalog.slice(0, 2000).map(c => h('option', { value: c.node }, c.desc)));
    const parent = h('select', { class: 'px-input' }, lp.groups.filter(x => g && x.name !== g.name && !g.parents.includes(x.name)).map(x => h('option', { value: x.name }, x.name)));
    return h('div', { class: 'pa-cols' },
      h('div', {}, h('div', { class: 'pa-list' }, lp.groups.map(x => h('div', { class: 'pa-row' + (x.name === lpSel ? ' sel' : ''), onclick: () => { lpSel = x.name; render(); } }, h('b', {}, x.name), h('span', { class: 'sub' }, x.permissions.length + ' ' + L('Rechte', 'perms'))))),
        h('div', { class: 'pa-acts' }, nn, btn(L('Anlegen', 'Create'), () => nn.value && (lpSel = nn.value.toLowerCase(), lpDo({ action: 'create', group: nn.value.toLowerCase() }, L('Angelegt', 'Created')))))),
      g ? h('div', {}, h('h3', {}, g.name, ' ', btn(L('Gruppe loeschen', 'Delete group'), async () => { if (await ctx.confirm(L('Gruppe loeschen?', 'Delete group?') + ' ' + g.name)) { lpSel = null; await lpDo({ action: 'delete', group: g.name }, L('Geloescht', 'Deleted')); } }, 'danger')),
        h('div', { class: 'pa-box' }, h('b', {}, L('Erbt von', 'Inherits from')), h('div', { class: 'pa-acts' }, g.parents.map(pn => h('span', { class: 'tag' }, pn, ' ', h('a', { style: 'cursor:pointer', onclick: () => lpDo({ action: 'parent', group: g.name, parent: pn, on: false }) }, '×'))), parent.options.length ? [parent, btn(L('Hinzufuegen', 'Add'), () => lpDo({ action: 'parent', group: g.name, parent: parent.value, on: true }))] : null)),
        h('div', { class: 'pa-box' }, h('b', {}, L('Rechte', 'Permissions')), dl,
          h('div', { class: 'pa-acts' }, node, btn(L('Erlauben', 'Allow'), () => node.value && lpDo({ action: 'perm', group: g.name, key: node.value.trim(), value: true }, 'OK')), btn(L('Verbieten', 'Deny'), () => node.value && lpDo({ action: 'perm', group: g.name, key: node.value.trim(), value: false }, 'OK'), 'danger')),
          h('input', { class: 'px-input', placeholder: L('Rechte filtern...', 'Filter permissions...'), value: lpQ, oninput: e => { lpQ = e.target.value; render(); } }),
          g.permissions.filter(p => !lpQ || p.key.includes(lpQ)).map(p => h('div', { class: 'row' }, h('span', { class: 'tag' + (p.value ? '' : ' warn') }, p.value ? L('erlaubt', 'allow') : L('verboten', 'deny')), h('span', {}, p.key), btn(L('Umschalten', 'Toggle'), () => lpDo({ action: 'perm', group: g.name, key: p.key, value: !p.value })), btn(L('Entfernen', 'Remove'), () => lpDo({ action: 'perm', group: g.name, key: p.key, value: null }), 'ghost'))))) : h('div', { class: 'sub' }, L('Gruppe waehlen oder anlegen.', 'Pick or create a group.')));
  }
  // ---------- Item-Log ----------
  let ilog = [], ilPlayer = '', ilSource = '';
  const SRC = { creative: L('Creative-Menü', 'Creative menu'), 'creative-drop': L('Creative: gedroppt', 'Creative: dropped'), 'creative-container': L('Creative: in Behälter', 'Creative: into container'), command: L('Befehl', 'Command'), 'web-give': L('Web: gegeben', 'Web: given'), 'mode-switch': L('Moduswechsel', 'Mode switch') };
  async function loadLog() { ilog = (await call('itemlog?limit=500' + (ilPlayer ? '&player=' + encodeURIComponent(ilPlayer) : '') + (ilSource ? '&source=' + encodeURIComponent(ilSource) : ''))) || []; if (!Array.isArray(ilog)) ilog = []; }
  function logTab() {
    return h('div', {}, h('div', { class: 'pa-box' },
      h('div', { class: 'pa-acts' },
        h('input', { class: 'px-input', placeholder: L('Spieler', 'Player'), value: ilPlayer, onchange: async e => { ilPlayer = e.target.value; await loadLog(); render(); } }),
        h('select', { class: 'px-input', onchange: async e => { ilSource = e.target.value; await loadLog(); render(); } },
          [['', L('Alle Quellen', 'All sources')], ...Object.entries(SRC)].map(([k, v]) => h('option', { value: k, selected: k === ilSource }, v))),
        btn(L('Neu laden', 'Reload'), async () => { await loadLog(); render(); })),
      h('div', { class: 'sub' }, L('Items aus dem Creative-Menü, per Befehl (give/item/summon), gedroppt oder in Behälter gelegt im Creative-Modus.', 'Items from the creative menu, by command (give/item/summon), dropped or put into containers in creative mode.')),
      ilog.length ? ilog.map(e => h('div', { class: 'row' }, h('span', { class: 'sub' }, when(e.time)), h('b', {}, e.player), h('span', { class: 'tag' }, SRC[e.source] || e.source),
        h('span', {}, e.item + (e.amount ? ' ×' + e.amount : '')), h('span', { class: 'sub' }, e.detail || ''))) : h('div', { class: 'sub' }, L('Noch keine Einträge.', 'No entries yet.'))));
  }
  // ---------- Rahmen ----------
  const TABS = [['players', L('Spieler', 'Players')], ['console', L('Konsole', 'Console')], ['status', 'Status'], ['chat', 'Chat'], ['perms', L('Rechte (LuckPerms)', 'Permissions (LuckPerms)')], ['itemlog', L('Item-Log', 'Item log')], ['backups', L('Sicherungen & Neustart', 'Backups & restart')]];
  function render() {
    const body = tab === 'players' ? playersTab() : tab === 'console' ? consoleTab() : tab === 'status' ? statusTab() : tab === 'chat' ? chatTab() : tab === 'perms' ? permsTab() : tab === 'itemlog' ? logTab() : backupsTab();
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'pa-tabs' }, TABS.map(([id, label]) => h('button', { class: 'px-btn small' + (tab === id ? ' on' : ''), onclick: () => go(id) }, label))), body));
  }
  async function go(id) {
    tab = id;
    if (id === 'players') await loadPlayers(); else if (id === 'status') status = await call('status'); else if (id === 'backups') await loadBackups(); else if (id === 'perms') await loadLp(); else if (id === 'itemlog') await loadLog();
    else if (id === 'console') { await pollConsole(); } else if (id === 'chat') await pollChat();
    render();
  }
  every(() => { if (tab === 'console') pollConsole(); else if (tab === 'chat') pollChat(); else if (tab === 'status') call('status').then(r => { if (r) { status = r; render(); } }); }, 3000);
  ctx.on(() => { if (tab === 'players') loadPlayers().then(render); else if (tab === 'backups') loadBackups().then(render); });
  await loadTex();
  await go('players');
}