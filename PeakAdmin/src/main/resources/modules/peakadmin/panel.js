// Seite "Verwaltung": Spieler (Aktionen, Inventar, Endertruhe, Fortschritte), Konsole, Status, Chat, Sicherungen und Neustart.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let tab = 'players', players = [], q = '', cur = null, detail = null, advs = null, advQ = '', inv = 'inventory';
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
.pa-slots{display:grid;grid-template-columns:repeat(9,1fr);gap:4px}@media(max-width:700px){.pa-slots{grid-template-columns:repeat(5,1fr)}}
.pa-slot{border:1px solid var(--line);min-height:46px;padding:3px;font-size:11px;position:relative;background:var(--panel2);overflow:hidden}.pa-slot.full{cursor:pointer;border-color:var(--accent)}
.pa-slot b{display:block;word-break:break-all}.pa-n{position:absolute;right:3px;bottom:1px;font-weight:bold}
.pa-con{background:#0b0b0b;color:#ddd;font:12px/1.35 monospace;height:60vh;overflow:auto;padding:8px;white-space:pre-wrap;word-break:break-all}
.pa-chart{width:100%;height:110px;background:var(--panel2);border:1px solid var(--line)}.pa-stat{display:flex;gap:18px;flex-wrap:wrap}.pa-stat div{min-width:110px}.pa-stat b{font-size:20px;display:block}`;
    document.head.appendChild(st);
  }

  // ---------- Spieler ----------
  async function loadPlayers() { players = (await call('players')) || []; }
  async function open(p) { cur = p; advs = null; detail = await call('player/' + p.uuid); render(); }
  async function act(a, body, ok, confirmText) {
    if (confirmText && !(await ctx.confirm(confirmText))) return;
    const r = await call('player/' + cur.uuid + '/' + a, body ?? {}, ok);
    if (r && r !== true && r.uuid) detail = r;
    if (a === 'advancement') advs = await call('player/' + cur.uuid + '/advancements');
    await loadPlayers(); render();
  }
  const btn = (label, fn, cls = '') => h('button', { class: 'px-btn small ' + cls, onclick: fn }, label);
  function slotGrid(items, size) {
    const by = new Map(items.map(i => [i.slot, i]));
    const cells = [];
    for (let s = 0; s < size; s++) {
      const it = by.get(s);
      cells.push(h('div', { class: 'pa-slot' + (it ? ' full' : ''), title: it ? [it.type, it.name, ...(it.enchants || [])].filter(Boolean).join('\n') + '\n' + L('Klick: entfernen', 'Click: remove') : '',
        onclick: it ? () => act('remove-slot', { inv, slot: s }, L('Entfernt', 'Removed'), L('Gegenstand entfernen?', 'Remove item?') + ' ' + it.type) : null },
        it ? [h('b', {}, it.name || it.type.replace('minecraft:', '')), h('span', { class: 'pa-n' }, it.amount > 1 ? it.amount : '')] : ''));
    }
    return h('div', { class: 'pa-slots' }, cells);
  }
  function playerView() {
    if (!cur || !detail) return h('div', { class: 'sub' }, L('Links einen Spieler waehlen.', 'Pick a player on the left.'));
    const d = detail, on = d.online;
    const reason = h('input', { class: 'px-input', placeholder: L('Grund (optional)', 'Reason (optional)') });
    const item = h('input', { class: 'px-input', placeholder: 'minecraft:diamond' }), amount = h('input', { class: 'px-input', type: 'number', value: 1, min: 1, style: 'width:90px' });
    const lvl = h('input', { class: 'px-input', type: 'number', value: d.level ?? 0, style: 'width:90px' });
    const gm = h('select', { class: 'px-input' }, ['survival', 'creative', 'adventure', 'spectator'].map(m => h('option', { value: m, selected: m === d.gamemode }, m)));
    const advBox = h('div', {});
    if (advs) {
      const f = advQ.toLowerCase();
      const list = advs.filter(a => !f || a.key.includes(f)).slice(0, 150);
      advBox.append(h('input', { class: 'px-input', placeholder: L('Fortschritte suchen...', 'Search advancements...'), value: advQ, oninput: e => { advQ = e.target.value; render(); } }),
        h('div', { class: 'sub' }, advs.filter(a => a.done).length + ' / ' + advs.length + ' ' + L('erreicht', 'done')),
        list.map(a => h('div', { class: 'row' }, h('span', { class: 'tag' + (a.done ? '' : ' warn') }, a.done ? '✓' : '–'), h('span', {}, a.key),
          a.done ? btn(L('Entziehen', 'Revoke'), () => act('advancement', { key: a.key, grant: false }, L('Entzogen', 'Revoked'))) : btn(L('Vergeben', 'Grant'), () => act('advancement', { key: a.key, grant: true }, L('Vergeben', 'Granted'))))));
    }
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
        slotGrid(inv === 'ender' ? d.ender : d.inventory, inv === 'ender' ? 27 : 41),
        h('div', { class: 'sub' }, inv === 'inventory' ? L('Plaetze 0-8 Hotbar, 9-35 Rucksack, 36-39 Ruestung (Fuesse bis Kopf), 40 Zweithand.', 'Slots 0-8 hotbar, 9-35 main, 36-39 armor (feet to head), 40 offhand.') : ''),
        h('div', { class: 'pa-acts' }, item, amount, btn(L('Geben', 'Give'), () => act('give', { inv, item: item.value, amount: +amount.value }, L('Gegeben', 'Given'))))) : null,
      on ? h('div', { class: 'pa-box' }, h('div', { class: 'pa-acts' }, h('b', {}, L('Fortschritte', 'Advancements')),
          btn(L('Anzeigen / laden', 'Show / load'), async () => { advs = await call('player/' + cur.uuid + '/advancements'); render(); }),
          btn(L('Alle vergeben', 'Grant all'), () => act('advancement', { key: '*', grant: true }, 'OK', L('ALLE Fortschritte vergeben?', 'Grant ALL advancements?'))),
          btn(L('Alle entziehen', 'Revoke all'), () => act('advancement', { key: '*', grant: false }, 'OK', L('ALLE Fortschritte entziehen?', 'Revoke ALL advancements?')), 'danger')), advBox) : null,
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
  // ---------- Rahmen ----------
  const TABS = [['players', L('Spieler', 'Players')], ['console', L('Konsole', 'Console')], ['status', 'Status'], ['chat', 'Chat'], ['perms', L('Rechte (LuckPerms)', 'Permissions (LuckPerms)')], ['backups', L('Sicherungen & Neustart', 'Backups & restart')]];
  function render() {
    const body = tab === 'players' ? playersTab() : tab === 'console' ? consoleTab() : tab === 'status' ? statusTab() : tab === 'chat' ? chatTab() : tab === 'perms' ? permsTab() : backupsTab();
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'pa-tabs' }, TABS.map(([id, label]) => h('button', { class: 'px-btn small' + (tab === id ? ' on' : ''), onclick: () => go(id) }, label))), body));
  }
  async function go(id) {
    tab = id;
    if (id === 'players') await loadPlayers(); else if (id === 'status') status = await call('status'); else if (id === 'backups') await loadBackups(); else if (id === 'perms') await loadLp();
    else if (id === 'console') { await pollConsole(); } else if (id === 'chat') await pollChat();
    render();
  }
  every(() => { if (tab === 'console') pollConsole(); else if (tab === 'chat') pollChat(); else if (tab === 'status') call('status').then(r => { if (r) { status = r; render(); } }); }, 3000);
  ctx.on(() => { if (tab === 'players') loadPlayers().then(render); else if (tab === 'backups') loadBackups().then(render); });
  await go('players');
}