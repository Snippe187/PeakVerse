// Seite "Regionen": WorldGuard-Regionen und Flags, WorldEdit-Schematics.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let st = null, world = '', regions = [], sel = null, tab = 'regions', q = '';
  async function loadState() { st = await ctx.api('state'); if (!world) world = st.worlds[0] || ''; }
  async function loadRegions() { regions = st.worldGuard && world ? await ctx.api('regions?world=' + encodeURIComponent(world)).catch(e => { ctx.toast(e.message || 'Fehler', 'bad'); return []; }) : []; }
  async function send(path, body, ok) {
    try { await ctx.api(path, { method: 'POST', body }); if (ok) ctx.toast(ok, 'good'); await loadRegions(); await loadState(); render(); return true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return false; }
  }
  if (!document.getElementById('pr-style')) {
    const s = document.createElement('style'); s.id = 'pr-style';
    s.textContent = `.pr-cols{display:grid;grid-template-columns:300px 1fr;gap:14px;align-items:start}@media(max-width:900px){.pr-cols{grid-template-columns:1fr}}
.pr-row{display:flex;gap:8px;align-items:center;padding:6px 8px;cursor:pointer;border:2px solid transparent}.pr-row:hover{background:var(--panel2)}.pr-row.sel{border-color:var(--accent)}
.pr-box{border:1px solid var(--line);padding:10px;display:grid;gap:8px;margin-bottom:10px}.pr-tabs{display:flex;gap:6px;margin-bottom:12px}.pr-tabs button.on{background:var(--accent);color:var(--on-accent)}`;
    document.head.appendChild(s);
  }
  const btn = (label, fn, cls = '') => h('button', { class: 'px-btn small ' + cls, onclick: fn }, label);
  const kb = b => (b / 1024).toFixed(b > 1048576 ? 0 : 1) + ' KB';

  function regionsTab() {
    if (!st.worldGuard) return h('div', { class: 'pr-box' }, h('b', {}, L('WorldGuard ist nicht installiert', 'WorldGuard is not installed')), h('div', { class: 'sub' }, L('Installiere WorldGuard (und WorldEdit) auf dem Server und starte neu. Dann kannst du hier Regionen und Flags verwalten.', 'Install WorldGuard (and WorldEdit) on the server and restart. Then you can manage regions and flags here.')));
    const f = q.toLowerCase();
    const w = h('select', { class: 'px-input', onchange: async e => { world = e.target.value; sel = null; await loadRegions(); render(); } }, st.worlds.map(x => h('option', { value: x, selected: x === world }, x)));
    const cur = regions.find(r => r.id === sel);
    const nn = h('input', { class: 'px-input', placeholder: L('neue Region (Name)', 'new region (name)') });
    const co = ['x1', 'y1', 'z1', 'x2', 'y2', 'z2'].map(k => h('input', { class: 'px-input', type: 'number', placeholder: k, style: 'width:80px', value: k.startsWith('y') ? (k === 'y1' ? -64 : 320) : 0 }));
    return h('div', { class: 'pr-cols' },
      h('div', {}, w, h('input', { class: 'px-input', placeholder: L('Regionen suchen...', 'Search regions...'), value: q, oninput: e => { q = e.target.value; render(); } }),
        regions.filter(r => !f || r.id.toLowerCase().includes(f)).map(r => h('div', { class: 'pr-row' + (r.id === sel ? ' sel' : ''), onclick: () => { sel = r.id; render(); } }, h('b', {}, r.id), h('span', { class: 'sub' }, r.type + ' · ' + r.owners.length + ' ' + L('Besitzer', 'owners')))),
        h('div', { class: 'pr-box' }, h('b', {}, L('Quader-Region anlegen', 'Create cuboid region')), nn, h('div', { class: 'row' }, co.slice(0, 3)), h('div', { class: 'row' }, co.slice(3)),
          btn(L('Anlegen', 'Create'), () => send('region/create', { world, id: nn.value.trim(), x1: +co[0].value, y1: +co[1].value, z1: +co[2].value, x2: +co[3].value, y2: +co[4].value, z2: +co[5].value }, L('Angelegt', 'Created')), 'primary'))),
      h('div', {}, cur ? regionView(cur) : h('div', { class: 'sub' }, L('Links eine Region waehlen.', 'Pick a region on the left.'))));
  }
  function regionView(r) {
    const flag = h('input', { class: 'px-input', list: 'pr-flags', placeholder: 'flag (z. B. pvp)' }), val = h('input', { class: 'px-input', placeholder: L('Wert (z. B. deny, allow oder Text)', 'value (e.g. deny, allow or text)') });
    const pl = h('input', { class: 'px-input', placeholder: L('Spieler-UUID', 'player UUID') }), role = h('select', { class: 'px-input' }, h('option', { value: 'owner' }, L('Besitzer', 'Owner')), h('option', { value: 'member' }, L('Mitglied', 'Member')));
    const prio = h('input', { class: 'px-input', type: 'number', value: r.priority, style: 'width:90px' });
    const people = (list, label, roleName) => h('div', {}, h('b', {}, label), list.length ? list.map(p => h('div', { class: 'row' }, h('span', {}, p.name), btn(L('Entfernen', 'Remove'), () => send('region/player', { world, id: r.id, uuid: p.uuid, role: roleName, on: false }, 'OK'), 'ghost'))) : h('div', { class: 'sub' }, '–'));
    return h('div', {},
      h('h3', {}, r.id, ' ', h('span', { class: 'tag' }, r.type), r.parent ? h('span', { class: 'tag' }, L('Eltern: ', 'parent: ') + r.parent) : null),
      h('div', { class: 'sub' }, `(${r.x1} ${r.y1} ${r.z1}) – (${r.x2} ${r.y2} ${r.z2})`),
      h('div', { class: 'pr-box' }, h('b', {}, 'Flags'), h('datalist', { id: 'pr-flags' }, st.flags.map(x => h('option', { value: x }))),
        r.flags.length ? r.flags.map(x => h('div', { class: 'row' }, h('b', {}, x.name), h('span', {}, x.value), btn(L('Entfernen', 'Clear'), () => send('region/flag', { world, id: r.id, flag: x.name, value: null }, 'OK'), 'ghost'))) : h('div', { class: 'sub' }, L('Keine Flags gesetzt.', 'No flags set.')),
        h('div', { class: 'row' }, flag, val, btn(L('Setzen', 'Set'), () => flag.value && send('region/flag', { world, id: r.id, flag: flag.value.trim(), value: val.value }, 'OK'), 'primary')),
        h('div', { class: 'sub' }, L('Beispiele: pvp deny, entry deny, greeting Willkommen!, build allow', 'Examples: pvp deny, entry deny, greeting Welcome!, build allow'))),
      h('div', { class: 'pr-box' }, people(r.owners, L('Besitzer', 'Owners'), 'owner'), people(r.members, L('Mitglieder', 'Members'), 'member'),
        h('div', { class: 'row' }, pl, role, btn(L('Hinzufuegen', 'Add'), () => pl.value && send('region/player', { world, id: r.id, uuid: pl.value.trim(), role: role.value, on: true }, 'OK'))),
        h('div', { class: 'sub' }, L('Die UUID findest du im Reiter Verwaltung > Spieler.', 'Find the UUID in Admin > Players.'))),
      h('div', { class: 'pr-box' }, h('div', { class: 'row' }, h('b', {}, L('Prioritaet', 'Priority')), prio, btn(L('Speichern', 'Save'), () => send('region/priority', { world, id: r.id, priority: +prio.value }, 'OK'))),
        btn(L('Region loeschen', 'Delete region'), async () => { if (await ctx.confirm(L('Region loeschen?', 'Delete region?') + ' ' + r.id)) { if (await send('region/delete', { world, id: r.id }, L('Geloescht', 'Deleted'))) { sel = null; render(); } } }, 'danger')),
      st.map ? h('div', { class: 'sub' }, L('Quader-Regionen sind auf der SquareMap-Karte als Ebene "Regionen" (standardmaessig versteckt) sichtbar.', 'Cuboid regions show on the SquareMap as layer "Regions" (hidden by default).')) : null);
  }

  const picker = h('input', { type: 'file', accept: '.schem,.schematic', hidden: true, onchange: async e => {
    const f = e.target.files[0]; picker.value = ''; if (!f) return;
    try { await ctx.api('schem/upload?name=' + encodeURIComponent(f.name), { method: 'POST', body: await f.arrayBuffer() }); ctx.toast(L('Hochgeladen', 'Uploaded'), 'good'); await loadState(); render(); } catch (err) { ctx.toast(err.message || 'Fehler', 'bad'); }
  } });
  function schemTab() {
    return h('div', {},
      st.worldEdit ? null : h('div', { class: 'sub' }, L('WorldEdit ist nicht installiert: Dateien werden trotzdem im Ordner gespeichert.', 'WorldEdit is not installed: files are still stored in the folder.')),
      h('div', { class: 'row' }, h('b', {}, L('Schematics', 'Schematics')), btn(L('Hochladen', 'Upload'), () => picker.click(), 'primary'), picker, h('span', { class: 'sub' }, st.schematicFolder)),
      st.schematics.length ? st.schematics.map(s => h('div', { class: 'row' }, h('b', {}, s.name), h('span', { class: 'sub' }, kb(s.size) + ' · ' + new Date(s.time).toLocaleString(ctx.lang)),
        h('a', { class: 'px-btn small', href: '/api/peakregions/schem/download?name=' + encodeURIComponent(s.name), download: s.name }, L('Herunterladen', 'Download')),
        btn(L('Loeschen', 'Delete'), async () => { if (await ctx.confirm(L('Schematic loeschen?', 'Delete schematic?') + ' ' + s.name)) send('schem/delete', { name: s.name }, L('Geloescht', 'Deleted')); }, 'danger'))) : h('div', { class: 'sub' }, L('Noch keine Schematics.', 'No schematics yet.')),
      h('div', { class: 'sub' }, L('Im Spiel: //schem load <name> und //paste.', 'In game: //schem load <name> and //paste.')));
  }
  function render() {
    root.replaceChildren(h('div', { class: 'page' }, h('div', { class: 'pr-tabs' }, [['regions', 'WorldGuard'], ['schem', 'Schematics']].map(([id, l]) => h('button', { class: 'px-btn small' + (tab === id ? ' on' : ''), onclick: () => { tab = id; render(); } }, l))), tab === 'regions' ? regionsTab() : schemTab()));
  }
  ctx.on(async () => { await loadState(); await loadRegions(); render(); });
  await loadState(); await loadRegions(); render();
}