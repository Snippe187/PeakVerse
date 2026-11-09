// Seite "Warps": Warp-Punkte anlegen, aendern, loeschen; Zugriff je Warp, Anzeige auf SquareMap.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let st = null, sel = null, form = null, q = '';
  async function reload() { st = await ctx.api('state'); render(); }
  async function send(path, body, ok) {
    try { st = await ctx.api(path, { method: 'POST', body }); if (ok) ctx.toast(ok, 'good'); render(); return true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return false; }
  }
  if (!document.getElementById('pw-style')) {
    const s = document.createElement('style'); s.id = 'pw-style';
    s.textContent = `.pw-cols{display:grid;grid-template-columns:300px 1fr;gap:14px;align-items:start}@media(max-width:900px){.pw-cols{grid-template-columns:1fr}}
.pw-row{display:flex;gap:8px;align-items:center;padding:6px 8px;cursor:pointer;border:2px solid transparent}.pw-row:hover{background:var(--panel2)}.pw-row.sel{border-color:var(--accent)}
.pw-box{border:1px solid var(--line);padding:10px;display:grid;gap:8px}.pw-f{display:grid;gap:3px}.pw-g{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}`;
    document.head.appendChild(s);
  }
  const field = (label, input, hint) => h('label', { class: 'pw-f' }, h('span', {}, label), input, hint ? h('small', { class: 'sub' }, hint) : null);
  const blank = () => ({ name: '', world: st.worlds[0] || 'world', x: 0, y: 64, z: 0, yaw: 0, pitch: 0, icon: 'ender_pearl', description: '', restricted: false, onMap: true });
  function pick(w) { sel = w ? w.name : null; form = w ? JSON.parse(JSON.stringify(w)) : blank(); render(); }

  function list() {
    const f = q.toLowerCase();
    return h('div', {}, h('input', { class: 'px-input', placeholder: L('Warps suchen...', 'Search warps...'), value: q, oninput: e => { q = e.target.value; render(); } }),
      st.warps.filter(w => !f || w.name.includes(f)).map(w => h('div', { class: 'pw-row' + (w.name === sel ? ' sel' : ''), onclick: () => pick(w) }, h('b', {}, w.name), h('span', { class: 'sub' }, w.world + ' ' + Math.round(w.x) + ' ' + Math.round(w.y) + ' ' + Math.round(w.z)), w.restricted ? h('span', { class: 'tag warn' }, L('Recht', 'perm')) : null)),
      h('button', { class: 'px-btn small primary', onclick: () => pick(null) }, '+ ' + L('Neuer Warp', 'New warp')));
  }

  function editor() {
    if (!form) return h('div', { class: 'sub' }, L('Links einen Warp waehlen oder einen neuen anlegen.', 'Pick a warp on the left or create one.'));
    const f = form, isNew = sel === null;
    const num = (k, label) => field(label, h('input', { class: 'px-input', type: 'number', step: '0.1', value: f[k], oninput: e => { f[k] = parseFloat(e.target.value) || 0; } }));
    const who = h('select', { class: 'px-input' }, st.players.map(p => h('option', { value: p }, p)));
    return h('div', { class: 'pw-box' },
      field(L('Name', 'Name'), h('input', { class: 'px-input', value: f.name, disabled: !isNew, oninput: e => { f.name = e.target.value.toLowerCase(); } }), isNew ? L('a-z, 0-9, _ und -, bis 24 Zeichen. Spaeter nicht mehr aenderbar.', 'a-z, 0-9, _ and -, up to 24 chars. Cannot be changed later.') : ''),
      field(L('Beschreibung', 'Description'), h('input', { class: 'px-input', value: f.description, oninput: e => { f.description = e.target.value; } })),
      h('div', { class: 'pw-g' }, field(L('Welt', 'World'), h('select', { class: 'px-input', onchange: e => { f.world = e.target.value; } }, st.worlds.map(w => h('option', { value: w, selected: w === f.world }, w)))), num('x', 'X'), num('y', 'Y')),
      h('div', { class: 'pw-g' }, num('z', 'Z'), num('yaw', L('Blickrichtung (Yaw)', 'Yaw')), num('pitch', L('Neigung (Pitch)', 'Pitch'))),
      st.players.length ? h('div', { class: 'row' }, h('span', { class: 'sub' }, L('Position von Spieler uebernehmen:', 'Take position from player:')), who,
        h('button', { class: 'px-btn small', onclick: async () => { try { const p = await ctx.api('here?player=' + encodeURIComponent(who.value)); Object.assign(f, { world: p.world, x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch }); render(); } catch (e) { ctx.toast(e.message || 'Fehler', 'bad'); } } }, L('Uebernehmen', 'Take'))) : h('div', { class: 'sub' }, L('Kein Spieler online: Position von Hand eintragen oder im Spiel /setwarp <name> benutzen.', 'No player online: enter the position or use /setwarp <name> in game.')),
      field(L('Symbol (Gegenstand)', 'Icon (item)'), h('input', { class: 'px-input', value: f.icon, oninput: e => { f.icon = e.target.value.toLowerCase(); } }), L('z. B. ender_pearl, compass, oak_door', 'e.g. ender_pearl, compass, oak_door')),
      h('label', { class: 'row' }, h('input', { type: 'checkbox', checked: f.restricted, onchange: e => { f.restricted = e.target.checked; render(); } }), h('span', {}, L('Nur mit Recht benutzbar', 'Needs a permission'))),
      f.restricted ? h('div', { class: 'sub' }, L('Recht:', 'Permission:') + ' peakwarps.warp.' + (f.name || '<name>') + ' ' + L('(im Reiter Verwaltung > Rechte oder bei den Spieler-Rollen vergeben)', '(grant it under Admin > Permissions or in the player roles)')) : null,
      h('label', { class: 'row' }, h('input', { type: 'checkbox', checked: f.onMap, onchange: e => { f.onMap = e.target.checked; } }), h('span', {}, L('Auf der SquareMap-Karte zeigen', 'Show on the SquareMap')),
        h('span', { class: 'sub' }, st.map ? '' : L('(SquareMap ist nicht installiert)', '(SquareMap is not installed)')), f.restricted ? h('span', { class: 'sub' }, L('(Warps mit Recht erscheinen nie auf der Karte)', '(restricted warps never appear on the map)')) : null),
      h('div', { class: 'row' }, h('button', { class: 'px-btn primary', onclick: async () => { if (await send('save', f, L('Gespeichert', 'Saved')) && isNew) sel = f.name; } }, L('Speichern', 'Save')),
        !isNew ? h('button', { class: 'px-btn danger', onclick: async () => { if (await ctx.confirm(L('Warp loeschen?', 'Delete warp?') + ' ' + sel)) { if (await send('delete', { name: sel }, L('Geloescht', 'Deleted'))) { sel = null; form = null; render(); } } } }, L('Loeschen', 'Delete')) : null));
  }

  function render() {
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'pw-cols' }, h('div', {}, list()), h('div', {}, editor()))));
  }
  ctx.on(() => { reload(); });
  await reload();
}