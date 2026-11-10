// Seite "Statistik": Spieler mit allen Werten, Verlauf, Bestenlisten, Freigabe fuer Spieler, Bearbeiten, Export.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let tab = 'players', cat = [], players = [], q = '', sortKey = 'play_ms', sortDir = -1, cur = null, detail = null;
  let topKey = 'play_ms', topPeriod = 'total', topRows = [];

  async function call(path, body, ok) {
    try { const r = await ctx.api(path, body === undefined ? undefined : { method: 'POST', body }); if (ok) ctx.toast(ok, 'good'); return r ?? true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return null; }
  }
  const btn = (label, fn, cls = '') => h('button', { class: 'px-btn small ' + cls, onclick: fn }, label);
  const label = k => { const d = cat.find(c => c.key === k); if (d) return L(d.labelDe, d.labelEn); return k.startsWith('mob:') ? L('Mob: ', 'Mob: ') + k.slice(4) : k.startsWith('mine:') ? L('Block: ', 'Block: ') + k.slice(5) : k; };
  const unit = k => (cat.find(c => c.key === k) || {}).unit || 'n';
  function fmt(k, v) {
    v = Number(v) || 0;
    const u = unit(k.startsWith('mob:') || k.startsWith('mine:') ? 'n' : k);
    if (u === 'ms') { const m = Math.floor(v / 60000); return Math.floor(m / 60) + ' h ' + (m % 60) + ' min'; }
    if (u === 'cm') return (v / 100000).toFixed(1) + ' km';
    if (u === 'hp10') return Math.round(v / 20).toLocaleString(ctx.lang) + ' ♥';
    return v.toLocaleString(ctx.lang);
  }
  const when = ms => ms ? new Date(ms).toLocaleString(ctx.lang) : '–';

  if (!document.getElementById('ps-style')) {
    const st = document.createElement('style'); st.id = 'ps-style';
    st.textContent = `.ps-tabs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}.ps-tabs button.on{background:var(--accent);color:var(--on-accent)}
.ps-table{width:100%;border-collapse:collapse}.ps-table th,.ps-table td{padding:5px 8px;text-align:left;border-bottom:1px solid var(--line);white-space:nowrap}.ps-table th{cursor:pointer}.ps-table tr.row:hover{background:var(--panel2);cursor:pointer}
.ps-wrap{overflow:auto;max-height:70vh}.ps-box{border:1px solid var(--line);padding:10px;margin:8px 0;display:grid;gap:8px}.ps-acts{display:flex;gap:6px;flex-wrap:wrap;align-items:center}
.ps-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:8px}.ps-grid div{border:1px solid var(--line);padding:6px 8px}.ps-grid b{display:block;font-size:18px}
.ps-bars{display:flex;align-items:flex-end;gap:2px;height:70px}.ps-bars i{flex:1;background:var(--accent);min-height:1px;opacity:.85}`;
    document.head.appendChild(st);
  }

  async function loadAll() { cat = (await call('catalog')) || []; players = (await call('players')) || []; }
  async function open(p) { cur = p; detail = await call('player/' + p.uuid); render(); }
  async function loadTop() { topRows = (await call('top?key=' + encodeURIComponent(topKey) + '&period=' + topPeriod + '&limit=25')) || []; if (!Array.isArray(topRows)) topRows = []; }

  function bars(obj, key) {
    const days = []; const now = new Date();
    for (let i = 29; i >= 0; i--) { const d = new Date(now); d.setDate(d.getDate() - i); days.push(d.toLocaleDateString('sv-SE')); }
    const vals = days.map(d => Number(obj[d] || 0)), max = Math.max(1, ...vals);
    return h('div', {}, h('div', { class: 'sub' }, label(key) + ' – ' + L('letzte 30 Tage', 'last 30 days')),
      h('div', { class: 'ps-bars' }, vals.map((v, i) => h('i', { style: `height:${Math.round(100 * v / max)}%`, title: days[i] + ': ' + fmt(key, v) }))));
  }

  function playerView() {
    if (!cur || !detail) return h('div', { class: 'sub' }, L('Links einen Spieler wählen.', 'Pick a player.'));
    const d = detail, tot = d.totals || {};
    const mains = cat.map(c => h('div', {}, h('span', { class: 'sub' }, label(c.key)), h('b', {}, fmt(c.key, tot[c.key]))));
    const detailKeys = Object.keys(tot).filter(k => k.startsWith('mob:') || k.startsWith('mine:')).sort((a, b) => tot[b] - tot[a]).slice(0, 24);
    const key = h('select', { class: 'px-input' }, cat.map(c => h('option', { value: c.key }, label(c.key))));
    const val = h('input', { class: 'px-input', type: 'number', min: 0, placeholder: L('Neuer Wert (Rohwert)', 'New value (raw)'), style: 'width:180px' });
    return h('div', {},
      h('h3', {}, d.name), h('div', { class: 'sub' }, d.uuid),
      h('div', { class: 'ps-grid' }, mains),
      detailKeys.length ? h('div', { class: 'ps-box' }, h('b', {}, L('Einzelwerte', 'Details')), h('div', { class: 'ps-grid' }, detailKeys.map(k => h('div', {}, h('span', { class: 'sub' }, label(k)), h('b', {}, fmt(k, tot[k])))))) : null,
      h('div', { class: 'ps-box' }, ['play_ms', 'blocks_mined', 'kills_mob', 'distance_cm'].map(k => bars(d.daily[k] || {}, k))),
      h('div', { class: 'ps-box' }, h('b', {}, L('Letzte Ereignisse', 'Recent events')),
        d.events.length ? d.events.slice(0, 30).map(e => h('div', { class: 'sub' }, when(e.time) + ' · ' + e.type + (e.detail ? ' · ' + e.detail : '') + (e.world ? ' · ' + e.world + ' ' + e.x + ' ' + e.y + ' ' + e.z : ''))) : h('div', { class: 'sub' }, L('Keine.', 'None.'))),
      h('div', { class: 'ps-box' }, h('b', {}, L('Bearbeiten (nur Operatoren, wird protokolliert)', 'Edit (operators only, logged)')),
        h('div', { class: 'ps-acts' }, key, val, btn(L('Wert setzen', 'Set value'), async () => { if (val.value === '') return; await call('set', { uuid: d.uuid, key: key.value, value: Math.round(+val.value) }, L('Gespeichert', 'Saved')); await open(cur); await loadAll(); render(); }, 'primary'),
          btn(L('Alle Werte dieses Spielers zurücksetzen', 'Reset all values of this player'), async () => { if (await ctx.confirm(L('Alle Werte von ', 'Reset all values of ') + d.name + L(' zurücksetzen?', '?'))) { await call('reset', { uuid: d.uuid }, L('Zurückgesetzt', 'Reset')); await loadAll(); await open(cur); } }, 'danger'))));
  }

  function playersTab() {
    const f = q.toLowerCase();
    const rows = players.filter(p => !f || p.name.toLowerCase().includes(f)).sort((a, b) => sortKey === 'name' ? sortDir * a.name.localeCompare(b.name) : sortDir * ((a.stats[sortKey] || 0) - (b.stats[sortKey] || 0)));
    const cols = ['play_ms', 'afk_ms', 'kills_player', 'kills_mob', 'deaths', 'blocks_mined', 'blocks_placed', 'distance_cm'];
    const th = (k, txt) => h('th', { onclick: () => { if (sortKey === k) sortDir = -sortDir; else { sortKey = k; sortDir = -1; } render(); } }, txt + (sortKey === k ? (sortDir < 0 ? ' ↓' : ' ↑') : ''));
    return h('div', {},
      h('div', { class: 'ps-acts' }, h('input', { class: 'px-input', placeholder: L('Spieler suchen...', 'Search players...'), value: q, oninput: e => { q = e.target.value; render(); const i = root.querySelector('input.px-input'); if (i) { i.focus(); i.setSelectionRange(q.length, q.length); } } }),
        h('a', { class: 'px-btn small', href: '/api/peakstats/export.csv', download: 'peakstats.csv' }, L('Export (CSV)', 'Export (CSV)')), h('span', { class: 'sub' }, players.length + ' ' + L('Spieler', 'players'))),
      h('div', { class: 'ps-wrap' }, h('table', { class: 'ps-table' }, h('thead', {}, h('tr', {}, th('name', L('Name', 'Name')), cols.map(k => th(k, label(k))), h('th', {}, L('Zuletzt', 'Last seen')))),
        h('tbody', {}, rows.slice(0, 300).map(p => h('tr', { class: 'row', onclick: () => open(p) }, h('td', {}, h('b', {}, p.name), p.online ? h('span', { class: 'tag', style: 'margin-left:6px' }, 'online') : null), cols.map(k => h('td', {}, fmt(k, p.stats[k]))), h('td', {}, when(p.lastSeen))))))),
      h('div', {}, playerView()));
  }

  function topTab() {
    const keys = cat.map(c => c.key);
    return h('div', {},
      h('div', { class: 'ps-acts' },
        h('select', { class: 'px-input', onchange: async e => { topKey = e.target.value; await loadTop(); render(); } }, keys.map(k => h('option', { value: k, selected: k === topKey }, label(k)))),
        ['today', 'week', 'month', 'total'].map(p => btn({ today: L('Heute', 'Today'), week: L('Woche', 'Week'), month: L('Monat', 'Month'), total: L('Gesamt', 'All time') }[p], async () => { topPeriod = p; await loadTop(); render(); }, topPeriod === p ? 'primary' : ''))),
      h('div', { class: 'ps-box' }, topRows.length ? topRows.map(r => h('div', { class: 'ps-acts' }, h('b', { style: 'width:36px' }, '#' + r.rank), h('span', { style: 'min-width:160px' }, r.name), h('span', { class: 'sub' }, fmt(topKey, r.value)))) : h('div', { class: 'sub' }, L('Noch keine Werte.', 'No values yet.'))));
  }

  function visTab() {
    return h('div', {},
      h('div', { class: 'sub' }, L('Hier legst du fest, was normale Spieler sehen dürfen. Standard: nur Admins. Diese Freigabe gilt für Bestenlisten, /stats und die Spieler-Seiten (kommen in den nächsten Paketen).', 'Choose what normal players may see. Default: admins only. This applies to leaderboards, /stats and the player pages (coming in the next packages).')),
      h('div', { class: 'ps-box' }, cat.map(c => h('label', { class: 'ps-acts' }, h('input', { type: 'checkbox', checked: c.visible, onchange: async e => { const r = await call('visibility', { key: c.key, visible: e.target.checked }, L('Gespeichert', 'Saved')); if (Array.isArray(r)) cat = r; render(); } }), h('span', {}, label(c.key)), h('span', { class: 'sub' }, c.visible ? L('für Spieler sichtbar', 'visible to players') : L('nur Admins', 'admins only'))))),
      h('div', { class: 'ps-box' }, h('b', {}, L('Alles zurücksetzen', 'Reset everything')), h('div', { class: 'sub' }, L('Löscht Werte, Verlauf und Ereignisse ALLER Spieler. Nicht rückgängig zu machen.', 'Deletes values, history and events of ALL players. Cannot be undone.')),
        btn(L('Alle Statistiken löschen', 'Delete all statistics'), async () => { if (await ctx.confirm(L('Wirklich ALLE Statistiken löschen?', 'Really delete ALL statistics?'))) { await call('reset', { all: true, confirm: 'ALLE' }, L('Gelöscht', 'Deleted')); await loadAll(); cur = null; detail = null; render(); } }, 'danger')));
  }

  const TABS = [['players', L('Spieler', 'Players')], ['top', L('Bestenlisten', 'Leaderboards')], ['vis', L('Freigabe für Spieler', 'Visibility for players')]];
  function render() {
    const body = tab === 'players' ? playersTab() : tab === 'top' ? topTab() : visTab();
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'ps-tabs' }, TABS.map(([id, txt]) => h('button', { class: 'px-btn small' + (tab === id ? ' on' : ''), onclick: async () => { tab = id; if (id === 'top') await loadTop(); else await loadAll(); render(); } }, txt))), body));
  }
  ctx.on(() => { if (tab === 'players') loadAll().then(render); });
  await loadAll();
  render();
}
