// Seite "Statistik": Uebersicht (Kacheln, Aktivitaet, Podest, Heatmap), Spieler (Kartenraster und Profil), Bestenlisten, Freigabe.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let tab = 'overview', cat = [], players = [], ov = null, q = '', sortKey = 'play_ms', cur = null, detail = null;
  let topKey = 'play_ms', topPeriod = 'total', topRows = [], podKey = 'play_ms', texInfo = null, textures = null;

  async function call(path, body, ok) {
    try { const r = await ctx.api(path, body === undefined ? undefined : { method: 'POST', body }); if (ok) ctx.toast(ok, 'good'); return r ?? true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return null; }
  }
  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const btn = (label, fn, cls = '') => h('button', { class: 'px-btn small ' + cls, onclick: fn }, label);
  const label = k => { const d = cat.find(c => c.key === k); if (d) return L(d.labelDe, d.labelEn); return k.startsWith('mob:') ? pretty(k.slice(4)) : k.startsWith('mine:') ? pretty(k.slice(5)) : k; };
  const pretty = s => s.split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  const unit = k => (cat.find(c => c.key === k) || {}).unit || 'n';
  function fmt(k, v) {
    v = Number(v) || 0;
    const u = unit(k.startsWith('mob:') || k.startsWith('mine:') ? 'n' : k);
    if (u === 'ms') { const m = Math.floor(v / 60000); return m >= 60 ? Math.floor(m / 60) + ' h ' + (m % 60) + ' min' : m + ' min'; }
    if (u === 'cm') return v >= 100000 ? (v / 100000).toFixed(1) + ' km' : Math.round(v / 100) + ' m';
    if (u === 'hp10') return Math.round(v / 20).toLocaleString(ctx.lang) + ' ♥';
    return v.toLocaleString(ctx.lang);
  }
  const when = ms => ms ? new Date(ms).toLocaleString(ctx.lang) : '–';
  const COLOR = { time: '#5b9bff', combat: '#ff5d6c', build: '#4fd18b', dist: '#ffc14d', other: '#b58cff' };
  const KCOLOR = k => ({ play_ms: COLOR.time, afk_ms: '#7c8db5', logins: COLOR.time, kills_player: COLOR.combat, kills_mob: COLOR.combat, deaths: COLOR.combat, damage_dealt: COLOR.combat, damage_taken: COLOR.combat, blocks_mined: COLOR.build, ores_mined: COLOR.build, blocks_placed: COLOR.build, items_crafted: COLOR.build, distance_cm: COLOR.dist }[k] || COLOR.other);

  if (!document.getElementById('ps-style')) {
    const st = document.createElement('style'); st.id = 'ps-style';
    st.textContent = `.ps-tabs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px}.ps-tabs button.on{background:var(--accent);color:var(--on-accent)}
.ps-grid{display:grid;gap:12px}.ps-kpis{grid-template-columns:repeat(auto-fill,minmax(190px,1fr))}.ps-two{grid-template-columns:repeat(auto-fit,minmax(340px,1fr))}
.ps-card{position:relative;background:var(--panel2);border:2px solid var(--line);padding:14px;box-shadow:4px 4px 0 rgba(0,0,0,.35)}
.ps-card h4{margin:0 0 10px;font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:var(--dim)}
.ps-kpi{display:flex;gap:12px;align-items:center;border-top:4px solid var(--c);background:linear-gradient(135deg,color-mix(in srgb,var(--c) 16%,var(--panel2)),var(--panel2))}
.ps-kpi .ic{width:44px;height:44px;display:flex;align-items:center;justify-content:center;background:color-mix(in srgb,var(--c) 22%,transparent);flex:none;font-size:24px}
.ps-kpi b{display:block;font-size:24px;line-height:1.1}.ps-kpi span{color:var(--dim);font-size:12px}
.ps-ic{image-rendering:pixelated;display:inline-block;vertical-align:middle}
.ps-head{image-rendering:pixelated;display:block;background:#2a2f45}
.ps-chips{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px}
.ps-podium{display:flex;align-items:flex-end;justify-content:center;gap:10px;min-height:230px;padding-top:6px}
.ps-pod{flex:1;max-width:170px;display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center}
.ps-pod .step{width:100%;display:flex;align-items:flex-start;justify-content:center;font-size:30px;font-weight:bold;padding-top:6px;color:rgba(0,0,0,.55)}
.ps-pod.p1 .step{height:110px;background:linear-gradient(#ffd75e,#c99a1c)}.ps-pod.p2 .step{height:80px;background:linear-gradient(#e4e8ef,#9aa3b5)}.ps-pod.p3 .step{height:60px;background:linear-gradient(#e0a070,#9b6234)}
.ps-pod .nm{font-weight:bold;word-break:break-all}.ps-pod .vl{color:var(--dim);font-size:12px}
.ps-heat{display:grid;grid-template-columns:34px repeat(24,1fr);gap:2px;font-size:10px;color:var(--dim)}
.ps-heat i{display:block;height:18px;background:var(--cell);border:1px solid rgba(255,255,255,.04)}
.ps-heat span{display:flex;align-items:center}
.ps-cards{grid-template-columns:repeat(auto-fill,minmax(230px,1fr))}
.ps-pc{display:flex;gap:12px;cursor:pointer;transition:transform .15s,border-color .15s}.ps-pc:hover{transform:translateY(-3px);border-color:var(--accent)}
.ps-pc b{display:block;word-break:break-all}.ps-pc .sub{font-size:12px}
.ps-dot{display:inline-block;width:9px;height:9px;background:var(--dim);margin-right:5px}.ps-dot.on{background:#4fd18b;box-shadow:0 0 6px #4fd18b}
.ps-prof{display:flex;gap:16px;align-items:center;flex-wrap:wrap;background:linear-gradient(120deg,color-mix(in srgb,var(--accent) 16%,var(--panel2)),var(--panel2))}
.ps-prof h2{margin:0}
.ps-legend{display:grid;gap:5px;font-size:13px}.ps-legend div{display:flex;gap:8px;align-items:center}.ps-legend i{width:11px;height:11px;flex:none}.ps-legend em{margin-left:auto;color:var(--dim);font-style:normal}
.ps-donut{display:flex;gap:16px;align-items:center;flex-wrap:wrap}
.ps-line{width:100%;height:auto;display:block}
.ps-bars{display:flex;align-items:flex-end;gap:2px;height:80px}.ps-bars i{flex:1;background:var(--c);min-height:2px;opacity:.9}
.ps-tl{display:grid;gap:6px;max-height:340px;overflow:auto}.ps-tl div{display:flex;gap:10px;align-items:center;font-size:13px}.ps-tl .pt{width:12px;height:12px;flex:none}
.ps-row{display:flex;gap:10px;align-items:center;margin:4px 0}.ps-row .bar{flex:1;height:14px;background:var(--panel)}.ps-row .bar div{height:100%;background:var(--c)}
.ps-empty{text-align:center;padding:30px 10px;color:var(--dim)}.ps-empty .big{font-size:48px}
.ps-acts{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.ps-live{margin-left:auto;color:#4fd18b;font-size:12px;font-weight:bold;align-self:center;letter-spacing:.05em}.ps-live.flash{animation:psflash .9s ease-out}@keyframes psflash{0%{color:#fff;text-shadow:0 0 10px #4fd18b}100%{color:#4fd18b;text-shadow:none}}`;
    document.head.appendChild(st);
  }

  // ---------- Bilder: Item-Icons (TexturePeak) und Koepfe ----------
  async function loadTex() {
    try {
      const r = await fetch('/api/texturepeak/vanilla', { credentials: 'same-origin' });
      if (!r.ok) { texInfo = { missing: true }; textures = null; return; }
      texInfo = await r.json(); textures = texInfo.ready ? { item: new Set(texInfo.item), block: new Set(texInfo.block) } : null;
    } catch { texInfo = { missing: true }; textures = null; }
  }
  function icon(name, size, fb) {
    const n = (name || '').replace(/^minecraft:/, '');
    const kind = textures ? (textures.item.has(n) ? 'item' : textures.block.has(n) ? 'block' : null) : null;
    const ph = () => h('span', { class: 'ps-ic', style: `width:${size}px;height:${size}px;line-height:${size}px;font-size:${Math.round(size * .75)}px;text-align:center` }, fb || '▫');
    if (!kind) return ph();
    const img = h('img', { class: 'ps-ic', width: size, height: size, alt: '', draggable: 'false', src: '/api/texturepeak/vanilla/' + kind + '/' + encodeURIComponent(n) });
    img.onerror = () => img.replaceWith(ph());
    return img;
  }
  function hash(s) { let x = 2166136261; for (let i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; }
  // Ersatzkopf, wenn kein Skin gespeichert ist: 8x8-Gesicht aus der Spieler-ID
  function pixelHead(uuid, size) {
    const r = hash(uuid), skin = ['#e0b48a', '#c58c5a', '#f0cba5', '#a8714a'][r & 3], hair = ['#5a3a22', '#2b2118', '#d8b44a', '#8a4b2b', '#3b3f55', '#7a2f2f'][(r >> 3) % 6], eye = ['#3a5fd0', '#2f8f4e', '#6b4a2b', '#555'][(r >> 6) & 3];
    let s = `<svg viewBox="0 0 8 8" width="${size}" height="${size}" shape-rendering="crispEdges"><rect width="8" height="8" fill="${skin}"/><rect width="8" height="2" fill="${hair}"/>`;
    if ((r >> 9) & 1) s += `<rect y="2" width="1" height="2" fill="${hair}"/><rect x="7" y="2" width="1" height="2" fill="${hair}"/>`;
    s += `<rect x="1" y="4" width="2" height="1" fill="#fff"/><rect x="5" y="4" width="2" height="1" fill="#fff"/><rect x="2" y="4" width="1" height="1" fill="${eye}"/><rect x="5" y="4" width="1" height="1" fill="${eye}"/><rect x="3" y="6" width="2" height="1" fill="#b5694a"/></svg>`;
    const d = h('div', { class: 'ps-head', style: `width:${size}px;height:${size}px` }); d.innerHTML = s; return d;
  }
  function head(uuid, size) {
    const box = h('div', { style: `width:${size}px;height:${size}px;flex:none` });
    const img = h('img', { class: 'ps-head', width: size, height: size, alt: '', draggable: 'false', src: '/api/peakstats/head/' + encodeURIComponent(uuid) });
    img.onerror = () => img.replaceWith(pixelHead(uuid, size));
    box.append(img); return box;
  }

  // ---------- Diagramme ----------
  function lineChart(days, series) {
    const W = 720, H = 200, pl = 36, pr = 36, pt = 14, pb = 26, iw = W - pl - pr, ih = H - pt - pb;
    const x = i => pl + (days.length < 2 ? iw / 2 : i * iw / (days.length - 1));
    let svg = `<svg class="ps-line" viewBox="0 0 ${W} ${H}">`;
    for (let g = 0; g <= 4; g++) svg += `<line x1="${pl}" x2="${W - pr}" y1="${pt + ih * g / 4}" y2="${pt + ih * g / 4}" stroke="rgba(255,255,255,.07)"/>`;
    series.forEach((s, si) => {
      const max = Math.max(1, ...s.values), y = v => pt + ih - ih * v / max;
      const pts = s.values.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
      svg += `<polygon points="${pl},${pt + ih} ${pts} ${x(days.length - 1)},${pt + ih}" fill="${s.color}" opacity=".13"/><polyline points="${pts}" fill="none" stroke="${s.color}" stroke-width="2.5" stroke-linejoin="round"/>`;
      s.values.forEach((v, i) => { svg += `<circle cx="${x(i).toFixed(1)}" cy="${y(v).toFixed(1)}" r="3.2" fill="${s.color}"><title>${esc(days[i])}: ${esc(s.fmt(v))}</title></circle>`; });
      const ax = si === 0 ? pl - 6 : W - pr + 6;
      svg += `<text x="${ax}" y="${pt + 4}" fill="${s.color}" font-size="10" text-anchor="${si === 0 ? 'end' : 'start'}">${esc(s.axis(max))}</text>`;
    });
    days.forEach((d, i) => { if (i % 5 === 0 || i === days.length - 1) svg += `<text x="${x(i)}" y="${H - 8}" fill="#8a93a8" font-size="10" text-anchor="middle">${esc(d.slice(5))}</text>`; });
    const el = h('div'); el.innerHTML = svg + '</svg>'; return el;
  }
  function donut(items, fmtv) {
    const total = items.reduce((s, i) => s + i.value, 0) || 1, R = 52, C = 2 * Math.PI * R;
    let off = 0, svg = `<svg viewBox="0 0 140 140" width="150" height="150"><circle cx="70" cy="70" r="${R}" fill="none" stroke="rgba(255,255,255,.06)" stroke-width="22"/>`;
    items.forEach(it => { const len = C * it.value / total; svg += `<circle cx="70" cy="70" r="${R}" fill="none" stroke="${it.color}" stroke-width="22" stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" stroke-dashoffset="${(-off).toFixed(2)}" transform="rotate(-90 70 70)"><title>${esc(it.label)}: ${esc(fmtv(it.value))}</title></circle>`; off += len; });
    const wrap = h('div', { class: 'ps-donut' }); const sv = h('div'); sv.innerHTML = svg + `<text x="70" y="75" text-anchor="middle" fill="#fff" font-size="15" font-weight="bold">${esc(fmtv(total))}</text></svg>`;
    wrap.append(sv, h('div', { class: 'ps-legend' }, items.map(it => h('div', {}, h('i', { style: `background:${it.color}` }), it.icon, h('span', {}, it.label), h('em', {}, Math.round(100 * it.value / total) + ' % · ' + fmtv(it.value))))));
    return wrap;
  }
  const PALETTE = ['#5b9bff', '#4fd18b', '#ffc14d', '#ff5d6c', '#b58cff', '#3ed6d6', '#ff8fd0', '#8a93a8'];
  function slices(totals, prefix, iconFor) {
    const arr = Object.entries(totals).filter(([k, v]) => k.startsWith(prefix) && v > 0).map(([k, v]) => ({ key: k, value: v })).sort((a, b) => b.value - a.value);
    const top = arr.slice(0, 7).map((e, i) => ({ value: e.value, label: pretty(e.key.slice(prefix.length)), color: PALETTE[i], icon: iconFor(e.key.slice(prefix.length)) }));
    const rest = arr.slice(7).reduce((s, e) => s + e.value, 0);
    if (rest > 0) top.push({ value: rest, label: L('Sonstige', 'Other'), color: PALETTE[7], icon: h('span', {}) });
    return top;
  }
  function bars(obj, key) {
    const days = []; const now = new Date();
    for (let i = 29; i >= 0; i--) { const d = new Date(now); d.setDate(d.getDate() - i); days.push(d.toLocaleDateString('sv-SE')); }
    const vals = days.map(d => Number(obj[d] || 0)), max = Math.max(1, ...vals);
    return h('div', { class: 'ps-card' }, h('h4', {}, label(key) + ' · ' + L('30 Tage', '30 days')), h('div', { class: 'ps-bars', style: `--c:${KCOLOR(key)}` }, vals.map((v, i) => h('i', { style: `height:${Math.round(100 * v / max)}%`, title: days[i] + ': ' + fmt(key, v) }))));
  }
  function kpi(ic, fb, key, value, text, color) {
    return h('div', { class: 'ps-card ps-kpi', style: `--c:${color}` }, h('div', { class: 'ic' }, icon(ic, 32, fb)), h('div', {}, h('b', {}, value), h('span', {}, text)));
  }
  function empty(msg) { return h('div', { class: 'ps-empty' }, h('div', { class: 'big' }, '⛏'), h('div', {}, msg)); }

  // ---------- Uebersicht ----------
  const POD_KEYS = ['play_ms', 'blocks_mined', 'blocks_placed', 'kills_player', 'kills_mob', 'deaths', 'distance_cm'];
  function overviewTab() {
    if (!ov) return empty(L('Noch keine Daten.', 'No data yet.'));
    const tt = ov.totals || {};
    const kpis = h('div', { class: 'ps-grid ps-kpis' },
      kpi('player_head', '👥', '', ov.players.toLocaleString(ctx.lang), L('Spieler gesamt · ', 'Players total · ') + ov.online + ' ' + L('online jetzt', 'online now'), COLOR.time),
      kpi('clock', '⏱', '', fmt('play_ms', tt.play_ms), L('Spielzeit gesamt', 'Total play time'), COLOR.time),
      kpi('diamond_pickaxe', '⛏', '', fmt('blocks_mined', tt.blocks_mined), L('Blöcke abgebaut', 'Blocks mined'), COLOR.build),
      kpi('grass_block', '🧱', '', fmt('blocks_placed', tt.blocks_placed), L('Blöcke gesetzt', 'Blocks placed'), COLOR.build),
      kpi('iron_sword', '⚔', '', ((tt.kills_player || 0) + (tt.kills_mob || 0)).toLocaleString(ctx.lang), L('Kills (Spieler + Mobs)', 'Kills (players + mobs)'), COLOR.combat),
      kpi('skeleton_skull', '💀', '', (tt.deaths || 0).toLocaleString(ctx.lang), L('Tode', 'Deaths'), COLOR.combat));
    const days = []; const now = new Date();
    for (let i = 29; i >= 0; i--) { const d = new Date(now); d.setDate(d.getDate() - i); days.push(d.toLocaleDateString('sv-SE')); }
    const play = days.map(d => Math.round((ov.playPerDay[d] || 0) / 60000)), act = days.map(d => ov.activePerDay[d] || 0);
    const chart = h('div', { class: 'ps-card' }, h('h4', {}, L('Aktivität · letzte 30 Tage', 'Activity · last 30 days')),
      h('div', { class: 'ps-acts', style: 'margin-bottom:6px;font-size:12px' }, h('span', { style: `color:${COLOR.time}` }, '● ' + L('Spielzeit (Minuten)', 'Play time (minutes)')), h('span', { style: `color:${COLOR.build}` }, '● ' + L('aktive Spieler', 'active players'))),
      lineChart(days, [{ values: play, color: COLOR.time, fmt: v => v + ' min', axis: v => v + ' min' }, { values: act, color: COLOR.build, fmt: v => v + ' ' + L('Spieler', 'players'), axis: v => String(v) }]));
    const pk = POD_KEYS.includes(podKey) ? podKey : POD_KEYS[0], pod = (ov.podium && ov.podium[pk]) || [];
    const order = [pod[1], pod[0], pod[2]], cls = ['p2', 'p1', 'p3'];
    const podium = h('div', { class: 'ps-card' }, h('h4', {}, L('Top 3', 'Top 3')),
      h('div', { class: 'ps-chips' }, POD_KEYS.map(k => btn(label(k), () => { podKey = k; render(); }, k === pk ? 'primary' : ''))),
      pod.length ? h('div', { class: 'ps-podium' }, order.map((p, i) => p ? h('div', { class: 'ps-pod ' + cls[i], onclick: () => openUuid(p.uuid), style: 'cursor:pointer' }, head(p.uuid, i === 1 ? 64 : 52), h('div', { class: 'nm' }, p.name), h('div', { class: 'vl' }, fmt(pk, p.value)), h('div', { class: 'step' }, i === 1 ? '1' : i === 0 ? '2' : '3')) : h('div', { class: 'ps-pod ' + cls[i] }))) : empty(L('Noch keine Werte.', 'No values yet.')));
    const heatMax = Math.max(1, ...ov.heat.flat());
    const dayNames = L('Mo,Di,Mi,Do,Fr,Sa,So', 'Mon,Tue,Wed,Thu,Fri,Sat,Sun').split(',');
    const hm = h('div', { class: 'ps-heat' }, h('span', {}), [...Array(24).keys()].map(hr => h('span', { style: 'justify-content:center' }, hr % 3 === 0 ? String(hr) : '')),
      ov.heat.flatMap((row, d) => [h('span', {}, dayNames[d]), ...row.map((v, hr) => { const el = h('i', { title: dayNames[d] + ' ' + hr + ':00 – ' + v + ' ' + L('Anmeldungen', 'logins') }); el.style.setProperty('--cell', v ? `rgba(91,155,255,${(0.18 + 0.82 * v / heatMax).toFixed(2)})` : 'rgba(255,255,255,.04)'); return el; })]));
    const heat = h('div', { class: 'ps-card' }, h('h4', {}, L('Wann ist was los? · Anmeldungen je Wochentag und Stunde', 'When is it busy? · logins per weekday and hour')), hm);
    return h('div', { class: 'ps-grid' }, kpis, chart, h('div', { class: 'ps-grid ps-two' }, podium, heat));
  }

  // ---------- Spieler: Kartenraster und Profil ----------
  const hasData = p => Object.entries(p.stats || {}).some(([k, v]) => k !== 'logins' && v > 0);
  async function openUuid(uuid) { tab = 'players'; cur = { uuid }; detail = await call('player/' + uuid); render(); }
  async function openPlayer(p) { cur = p; detail = await call('player/' + p.uuid); render(); }
  function playersTab() {
    if (cur && detail) return profile();
    const f = q.toLowerCase();
    const list = players.filter(hasData).filter(p => !f || p.name.toLowerCase().includes(f)).sort((a, b) => sortKey === 'name' ? a.name.localeCompare(b.name) : sortKey === 'lastSeen' ? b.lastSeen - a.lastSeen : (b.stats[sortKey] || 0) - (a.stats[sortKey] || 0));
    const sel = h('select', { class: 'px-input', onchange: e => { sortKey = e.target.value; render(); } }, [['play_ms', L('Spielzeit', 'Play time')], ['blocks_mined', L('Blöcke abgebaut', 'Blocks mined')], ['kills_mob', L('Mob-Kills', 'Mob kills')], ['deaths', L('Tode', 'Deaths')], ['lastSeen', L('Zuletzt online', 'Last seen')], ['name', L('Name', 'Name')]].map(([v, l]) => h('option', { value: v, selected: v === sortKey }, l)));
    const search = h('input', { class: 'px-input', placeholder: L('Spieler suchen ...', 'Search players ...'), value: q, oninput: e => { q = e.target.value; const g = root.querySelector('.ps-cards'); if (g) g.replaceWith(cards(players.filter(hasData).filter(p => !q || p.name.toLowerCase().includes(q.toLowerCase())).sort((a, b) => (b.stats[sortKey] || 0) - (a.stats[sortKey] || 0)))); } });
    return h('div', { class: 'ps-grid' }, h('div', { class: 'ps-acts' }, search, sel, h('a', { class: 'px-btn small', href: '/api/peakstats/export.csv', download: 'peakstats.csv' }, L('Export (CSV)', 'Export (CSV)')), h('span', { class: 'sub' }, list.length + ' ' + L('Spieler mit Werten', 'players with data'))),
      list.length ? cards(list) : empty(L('Noch kein Spieler hat Werte. Spielt eine Runde, dann erscheinen hier die Karten.', 'No player has values yet. Play a round and the cards appear here.')));
  }
  function cards(list) {
    return h('div', { class: 'ps-grid ps-cards' }, list.slice(0, 300).map(p => h('div', { class: 'ps-card ps-pc', onclick: () => openPlayer(p) }, head(p.uuid, 56),
      h('div', {}, h('b', {}, h('span', { class: 'ps-dot' + (p.online ? ' on' : '') }), p.name),
        h('div', { class: 'sub' }, '⏱ ' + fmt('play_ms', p.stats.play_ms) + ' · ⛏ ' + fmt('blocks_mined', p.stats.blocks_mined)),
        h('div', { class: 'sub' }, '⚔ ' + ((p.stats.kills_player || 0) + (p.stats.kills_mob || 0)) + ' · 💀 ' + (p.stats.deaths || 0))))));
  }
  const EVENT = { login: ['#4fd18b', '→', L('angemeldet', 'logged in')], logout: ['#7c8db5', '←', L('abgemeldet', 'logged out')], death: ['#ff5d6c', '✕', L('gestorben', 'died')], kill: ['#ffc14d', '⚔', L('Spieler besiegt', 'killed player')] };
  function profile() {
    const d = detail, tot = d.totals || {};
    const mainKpi = [['clock', '⏱', 'play_ms', COLOR.time], ['barrier', '💤', 'afk_ms', '#7c8db5'], ['iron_sword', '⚔', 'kills_player', COLOR.combat], ['zombie_head', '☠', 'kills_mob', COLOR.combat], ['skeleton_skull', '💀', 'deaths', COLOR.combat], ['diamond_pickaxe', '⛏', 'blocks_mined', COLOR.build], ['grass_block', '🧱', 'blocks_placed', COLOR.build], ['leather_boots', '👣', 'distance_cm', COLOR.dist], ['crafting_table', '🔨', 'items_crafted', COLOR.build]];
    const blockSlices = slices(tot, 'mine:', n => icon(n, 18, '▫')), mobSlices = slices(tot, 'mob:', n => icon(n + '_spawn_egg', 18, '☗'));
    const key = h('select', { class: 'px-input' }, cat.map(c => h('option', { value: c.key }, label(c.key))));
    const val = h('input', { class: 'px-input', type: 'number', min: 0, placeholder: L('Neuer Wert (Rohwert)', 'New value (raw)'), style: 'width:170px' });
    return h('div', { class: 'ps-grid' },
      btn('← ' + L('Alle Spieler', 'All players'), () => { cur = null; detail = null; render(); }),
      h('div', { class: 'ps-card ps-prof' }, head(d.uuid, 96), h('div', {}, h('h2', {}, d.name), h('div', {}, h('span', { class: 'ps-dot' + (d.online ? ' on' : '') }), d.online ? 'online' : 'offline'),
        h('div', { class: 'sub' }, L('Erster Besuch: ', 'First seen: ') + when(d.firstSeen) + ' · ' + L('Zuletzt: ', 'Last seen: ') + when(d.lastSeen)), h('div', { class: 'sub', style: 'font-size:11px' }, d.uuid))),
      h('div', { class: 'ps-grid ps-kpis' }, mainKpi.map(([ic, fb, k, c]) => kpi(ic, fb, k, fmt(k, tot[k]), label(k), c))),
      h('div', { class: 'ps-grid ps-two' },
        h('div', { class: 'ps-card' }, h('h4', {}, L('Abgebaute Blöcke', 'Blocks mined')), blockSlices.length ? donut(blockSlices, v => v.toLocaleString(ctx.lang)) : h('div', { class: 'sub' }, L('Noch keine.', 'None yet.'))),
        h('div', { class: 'ps-card' }, h('h4', {}, L('Besiegte Mobs', 'Mobs killed')), mobSlices.length ? donut(mobSlices, v => v.toLocaleString(ctx.lang)) : h('div', { class: 'sub' }, L('Noch keine.', 'None yet.')))),
      h('div', { class: 'ps-grid ps-two' }, ['play_ms', 'blocks_mined', 'kills_mob', 'distance_cm'].map(k => bars(d.daily[k] || {}, k))),
      h('div', { class: 'ps-card' }, h('h4', {}, L('Zeitleiste', 'Timeline')), d.events.length ? h('div', { class: 'ps-tl' }, d.events.slice(0, 60).map(e => { const m = EVENT[e.type] || ['#b58cff', '•', e.type]; return h('div', {}, h('span', { class: 'pt', style: `background:${m[0]}` }), h('b', { style: `color:${m[0]}` }, m[1] + ' ' + m[2]), h('span', {}, when(e.time)), e.detail ? h('span', { class: 'sub' }, e.detail) : null, e.world ? h('span', { class: 'sub' }, e.world + ' ' + e.x + ' ' + e.y + ' ' + e.z) : null); })) : h('div', { class: 'sub' }, L('Keine Ereignisse.', 'No events.'))),
      h('div', { class: 'ps-card' }, h('h4', {}, L('Bearbeiten (nur Operatoren, wird protokolliert)', 'Edit (operators only, logged)')),
        h('div', { class: 'ps-acts' }, key, val, btn(L('Wert setzen', 'Set value'), async () => { if (val.value === '') return; await call('set', { uuid: d.uuid, key: key.value, value: Math.round(+val.value) }, L('Gespeichert', 'Saved')); await loadAll(); detail = await call('player/' + d.uuid); render(); }, 'primary'),
          btn(L('Alle Werte dieses Spielers zurücksetzen', 'Reset all values of this player'), async () => { if (await ctx.confirm(L('Alle Werte von ', 'Reset all values of ') + d.name + L(' zurücksetzen?', '?'))) { await call('reset', { uuid: d.uuid }, L('Zurückgesetzt', 'Reset')); await loadAll(); detail = await call('player/' + d.uuid); render(); } }, 'danger'))));
  }

  // ---------- Bestenlisten ----------
  async function loadTop() { topRows = (await call('top?key=' + encodeURIComponent(topKey) + '&period=' + topPeriod + '&limit=25')) || []; if (!Array.isArray(topRows)) topRows = []; }
  function topTab() {
    const max = Math.max(1, ...topRows.map(r => r.value));
    return h('div', { class: 'ps-grid' }, h('div', { class: 'ps-card' },
      h('div', { class: 'ps-acts' }, h('select', { class: 'px-input', onchange: async e => { topKey = e.target.value; await loadTop(); render(); } }, cat.map(c => h('option', { value: c.key, selected: c.key === topKey }, label(c.key)))),
        ['today', 'week', 'month', 'total'].map(p => btn({ today: L('Heute', 'Today'), week: L('Woche', 'Week'), month: L('Monat', 'Month'), total: L('Gesamt', 'All time') }[p], async () => { topPeriod = p; await loadTop(); render(); }, topPeriod === p ? 'primary' : ''))),
      topRows.length ? topRows.map(r => h('div', { class: 'ps-row', style: `--c:${KCOLOR(topKey)};cursor:pointer`, onclick: () => openUuid(r.uuid) }, h('b', { style: 'width:34px' }, '#' + r.rank), head(r.uuid, 28), h('span', { style: 'min-width:130px' }, r.name), h('div', { class: 'bar' }, h('div', { style: `width:${Math.round(100 * r.value / max)}%` })), h('span', { class: 'sub', style: 'min-width:90px;text-align:right' }, fmt(topKey, r.value)))) : empty(L('Noch keine Werte.', 'No values yet.'))));
  }

  // ---------- Freigabe ----------
  function visTab() {
    return h('div', { class: 'ps-grid' },
      h('div', { class: 'sub' }, L('Hier legst du fest, was normale Spieler sehen dürfen. Standard: nur Admins. Gilt für Bestenlisten, /stats und die Spieler-Seiten (kommen in den nächsten Paketen).', 'Choose what normal players may see. Default: admins only. Applies to leaderboards, /stats and the player pages (coming in the next packages).')),
      h('div', { class: 'ps-card' }, cat.map(c => h('label', { class: 'ps-acts', style: 'padding:4px 0' }, h('input', { type: 'checkbox', checked: c.visible, onchange: async e => { const r = await call('visibility', { key: c.key, visible: e.target.checked }, L('Gespeichert', 'Saved')); if (Array.isArray(r)) cat = r; render(); } }), icon(({ play_ms: 'clock', afk_ms: 'barrier', logins: 'oak_door', kills_player: 'iron_sword', kills_mob: 'zombie_head', deaths: 'skeleton_skull', damage_dealt: 'bow', damage_taken: 'shield', blocks_mined: 'diamond_pickaxe', ores_mined: 'diamond_ore', blocks_placed: 'grass_block', distance_cm: 'leather_boots', items_crafted: 'crafting_table' })[c.key], 20, '•'), h('b', {}, label(c.key)), h('span', { class: 'sub' }, c.visible ? L('für Spieler sichtbar', 'visible to players') : L('nur Admins', 'admins only'))))),
      h('div', { class: 'ps-card' }, h('h4', {}, L('Alles zurücksetzen', 'Reset everything')), h('div', { class: 'sub' }, L('Löscht Werte, Verlauf und Ereignisse ALLER Spieler. Nicht rückgängig zu machen.', 'Deletes values, history and events of ALL players. Cannot be undone.')),
        btn(L('Alle Statistiken löschen', 'Delete all statistics'), async () => { if (await ctx.confirm(L('Wirklich ALLE Statistiken löschen?', 'Really delete ALL statistics?'))) { await call('reset', { all: true, confirm: 'ALLE' }, L('Gelöscht', 'Deleted')); await loadAll(); cur = null; detail = null; render(); } }, 'danger')));
  }

  const TABS = [['overview', L('Übersicht', 'Overview')], ['players', L('Spieler', 'Players')], ['top', L('Bestenlisten', 'Leaderboards')], ['vis', L('Freigabe für Spieler', 'Visibility for players')]];
  function texBanner() {
    if (!texInfo || texInfo.ready) return null;
    return h('div', { class: 'tag warn', style: 'display:block;margin-bottom:10px' }, texInfo.missing ? L('Item-Bilder fehlen (TexturePeak aus oder nicht erreichbar): es erscheinen Symbole statt Bilder.', 'Item images missing (TexturePeak off or unreachable): symbols are shown instead.') : L('Die Minecraft-Bilder sind noch nicht geladen (Reiter TexturePeak, einmalig laden): es erscheinen Symbole statt Bildern.', 'Minecraft images are not loaded yet (TexturePeak tab, load once): symbols are shown instead.'));
  }
  function render() {
    const body = tab === 'overview' ? overviewTab() : tab === 'players' ? playersTab() : tab === 'top' ? topTab() : visTab();
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'ps-tabs' }, TABS.map(([id, txt]) => h('button', { class: 'px-btn small' + (tab === id ? ' on' : ''), onclick: async () => { tab = id; if (id !== 'players') { cur = null; detail = null; } await refresh(); render(); } }, txt)), h('span', { class: 'ps-live', title: L('Die Werte aktualisieren sich von selbst', 'Values update by themselves') }, '● Live')), texBanner(), body));
  }
  async function refresh() {
    if (tab === 'top') { cat = (await call('catalog')) || cat; await loadTop(); }
    else if (tab === 'overview') { [cat, ov] = [(await call('catalog')) || cat, await call('overview')]; if (ov === true) ov = null; }
    else await loadAll();
  }
  async function loadAll() { cat = (await call('catalog')) || []; players = (await call('players')) || []; }
  // Live: der Server meldet jede Aenderung der Werte (hoechstens einmal pro Sekunde); die Seite laedt dann nur neu, wenn gerade nichts eingegeben wird
  let liveTimer = null;
  const formBusy = () => { const a = document.activeElement; return !!a && root.contains(a) && /^(INPUT|SELECT|TEXTAREA)$/.test(a.tagName); };
  async function liveRefresh() {
    if (!root.isConnected || formBusy()) return;
    const y = window.scrollY;
    if (cur) detail = (await call('player/' + cur.uuid)) || detail; else await refresh();
    if (!root.isConnected || formBusy()) return;
    render();
    window.scrollTo(0, y);
    const b = root.querySelector('.ps-live');
    if (b) { b.classList.remove('flash'); void b.offsetWidth; b.classList.add('flash'); }
  }
  ctx.on(d => { if (!d || d.module !== 'peakstats') return; clearTimeout(liveTimer); liveTimer = setTimeout(liveRefresh, 400); });
  await loadTex();
  await refresh();
  render();
}
