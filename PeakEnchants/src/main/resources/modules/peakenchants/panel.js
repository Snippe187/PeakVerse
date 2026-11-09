// Seite "Verzauberungen": Datapack-Addons verwalten (hochladen, an/aus, entfernen) und alle Verzauberungen bearbeiten, anlegen, loeschen und vergeben.
// Aenderungen an Verzauberungen landen zuerst im Entwurf; Minecraft liest sie erst nach einem Server-Neustart.
export default async function mount(root, ctx) {
  const { h, t } = ctx;
  const L = (de, en) => t({ de, en });
  let st = null, sel = null, search = '', form = null, fileTab = 'form';

  async function reload() { st = await ctx.api('state'); render(); }
  async function send(path, body, ok) {
    try { st = await ctx.api(path, { method: 'POST', body: body ?? '' }); if (ok) ctx.toast(ok, 'good'); render(); return true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return false; }
  }
  const find = () => st.enchants.find(e => e.addon === sel?.addon && e.key === sel?.key);
  const num = (v, d) => { const n = parseInt(v, 10); return Number.isFinite(n) ? n : d; };
  function field(label, input, hint) { return h('label', { class: 'pe-f' }, h('span', {}, label), input, hint ? h('small', { class: 'sub' }, hint) : null); }

  // ---------- Addons ----------
  const picker = h('input', { type: 'file', accept: '.zip', hidden: true, onchange: async e => {
    const f = e.target.files[0]; picker.value = ''; if (!f) return;
    if (st.addons.some(a => a.id === f.name.toLowerCase().replace(/\.zip$/, '').replace(/[^a-z0-9_.-]/g, '_')) && !(await ctx.confirm(L('Es gibt schon ein Addon mit diesem Namen. Ersetzen? Das alte wird gesichert.', 'An addon with this name exists. Replace it? The old one is backed up.')))) return;
    try { st = await ctx.api('addon/upload?name=' + encodeURIComponent(f.name), { method: 'POST', body: await f.arrayBuffer() }); ctx.toast(L('Addon installiert. Neustart noetig.', 'Addon installed. Restart needed.'), 'good'); render(); }
    catch (err) { ctx.toast(err.message || 'Fehler', 'bad'); }
  } });
  function addons() {
    return h('div', { class: 'pe-box' },
      h('div', { class: 'row' }, h('b', {}, L('Datapack-Addons', 'Datapack addons')), h('button', { class: 'px-btn small primary', onclick: () => picker.click() }, L('ZIP hochladen / aktualisieren', 'Upload / update ZIP')), picker),
      st.addons.length ? st.addons.map(a => h('div', { class: 'row pe-addon' },
        h('span', { class: 'tag' + (a.enabled ? '' : ' warn') }, a.zip ? 'ZIP' : a.enabled ? L('an', 'on') : L('aus', 'off')),
        h('b', {}, a.id), h('span', { class: 'sub' }, a.count + ' ' + L('Verzauberungen', 'enchantments') + (a.description ? ' · ' + a.description : '')),
        a.zip ? h('button', { class: 'px-btn small', onclick: () => send('addon/unpack', { id: a.id }, L('Entpackt', 'Unpacked')) }, L('Entpacken (zum Bearbeiten)', 'Unpack (to edit)')) :
          h('button', { class: 'px-btn small', onclick: () => send('addon/enable', { id: a.id, enabled: !a.enabled }, L('Gespeichert. Neustart noetig.', 'Saved. Restart needed.')) }, a.enabled ? L('Ausschalten', 'Turn off') : L('Einschalten', 'Turn on')),
        a.zip ? null : h('button', { class: 'px-btn small ghost', onclick: async () => { if (await ctx.confirm(L('Addon "' + a.id + '" entfernen? Es wird in plugins/PeakEnchants/backups gesichert.', 'Remove addon "' + a.id + '"? It is backed up.'))) send('addon/remove', { id: a.id }, L('Entfernt', 'Removed')); } }, L('Entfernen', 'Remove')))) : h('div', { class: 'sub' }, L('Noch kein Addon mit Verzauberungen. ZIP hochladen (z. B. Enchantology).', 'No enchantment addon yet. Upload a ZIP (e.g. Enchantology).')));
  }

  // ---------- Liste ----------
  function list() {
    const q = search.toLowerCase();
    const items = st.enchants.filter(e => !q || e.key.includes(q) || e.name.toLowerCase().includes(q));
    return h('div', { class: 'pe-list' },
      h('input', { class: 'px-input', placeholder: L('Suchen...', 'Search...'), value: search, oninput: e => { search = e.target.value; renderList(); } }),
      h('div', { id: 'pe-items' }, items.map(e => h('div', { class: 'pe-item' + (sel && sel.key === e.key && sel.addon === e.addon ? ' sel' : ''), onclick: () => select(e) },
        h('b', {}, e.name), h('span', { class: 'sub' }, e.key), e.staged ? h('span', { class: 'tag warn' }, L('Entwurf', 'draft')) : null,
        !e.loaded ? h('span', { class: 'tag' }, L('nicht geladen', 'not loaded')) : null, !e.enabled ? h('span', { class: 'tag warn' }, L('Addon aus', 'addon off')) : null))));
  }
  function renderList() { const box = root.querySelector('#pe-items'); if (box) box.replaceWith(list().querySelector('#pe-items')); }

  function select(e) {
    sel = { addon: e.addon, key: e.key };
    const j = e.json || {};
    form = { json: JSON.parse(JSON.stringify(j)), tags: [...e.tags], raw: JSON.stringify(j, null, 2), isNew: false };
    render();
  }
  function newOne() {
    const addon = st.addons.find(a => !a.zip && a.id === 'peakenchants') ? 'peakenchants' : 'peakenchants';
    sel = { addon, key: 'peakenchants:neu' };
    form = { isNew: true, tags: ['in_enchanting_table'], raw: '', json: { description: 'Neue Verzauberung', supported_items: '#minecraft:enchantable/weapon', weight: 5, max_level: 3,
      min_cost: { base: 10, per_level_above_first: 10 }, max_cost: { base: 40, per_level_above_first: 10 }, anvil_cost: 4, slots: ['mainhand'], effects: {} } };
    form.raw = JSON.stringify(form.json, null, 2);
    render();
  }
  function duplicate(e) {
    sel = { addon: 'peakenchants', key: 'peakenchants:' + e.key.split(':')[1] + '_kopie' };
    form = { isNew: true, tags: [...e.tags], json: JSON.parse(JSON.stringify(e.json)), raw: '' };
    form.raw = JSON.stringify(form.json, null, 2);
    render();
  }

  // ---------- Bearbeiten ----------
  function editor() {
    if (!sel || !form) return h('div', { class: 'pe-side sub' }, L('Links eine Verzauberung waehlen oder eine neue anlegen.', 'Pick an enchantment on the left or create one.'));
    const e = find(); const j = form.json;
    const name = typeof j.description === 'string' ? j.description : (j.description && (j.description.text || j.description.translate)) || '';
    const cost = (k, f) => h('input', { class: 'px-input', type: 'number', value: (j[k] || {})[f] ?? 0, oninput: ev => { (j[k] = j[k] || {})[f] = num(ev.target.value, 0); } });
    const keyInput = form.isNew ? field(L('Schluessel (namespace:name)', 'Key (namespace:name)'), h('input', { class: 'px-input', value: sel.key, oninput: ev => { sel.key = ev.target.value.trim().toLowerCase(); } })) : null;
    const items = typeof j.supported_items === 'string' ? j.supported_items : JSON.stringify(j.supported_items || '');
    const simple = h('div', {},
      keyInput,
      field(L('Name', 'Name'), h('input', { class: 'px-input', value: name, oninput: ev => { j.description = ev.target.value; } }), typeof j.description === 'object' ? L('Achtung: der Name ist eine Text-Komponente; beim Speichern wird er zu einfachem Text.', 'Note: the name is a text component; saving makes it plain text.') : null),
      h('div', { class: 'pe-grid' },
        field(L('Max. Stufe', 'Max level'), h('input', { class: 'px-input', type: 'number', min: 1, max: 255, value: j.max_level ?? 1, oninput: ev => { j.max_level = num(ev.target.value, 1); } })),
        field(L('Gewicht (Seltenheit)', 'Weight (rarity)'), h('input', { class: 'px-input', type: 'number', min: 1, max: 1024, value: j.weight ?? 1, oninput: ev => { j.weight = num(ev.target.value, 1); } }), L('1 = sehr selten, 10 = haeufig', '1 = very rare, 10 = common')),
        field(L('Amboss-Kosten', 'Anvil cost'), h('input', { class: 'px-input', type: 'number', min: 0, value: j.anvil_cost ?? 0, oninput: ev => { j.anvil_cost = num(ev.target.value, 0); } })),
        field(L('Mindest-Kosten (Basis)', 'Min cost (base)'), cost('min_cost', 'base')), field(L('Mindest-Kosten pro Stufe', 'Min cost per level'), cost('min_cost', 'per_level_above_first')),
        field(L('Hoechst-Kosten (Basis)', 'Max cost (base)'), cost('max_cost', 'base')), field(L('Hoechst-Kosten pro Stufe', 'Max cost per level'), cost('max_cost', 'per_level_above_first'))),
      field(L('Passende Items', 'Supported items'), h('input', { class: 'px-input', value: items, oninput: ev => { const v = ev.target.value.trim(); try { j.supported_items = v.startsWith('[') ? JSON.parse(v) : v; } catch { j.supported_items = v; } } }), L('Item-Tag mit # (z. B. #minecraft:enchantable/sword) oder einzelnes Item', 'Item tag with # or single item')),
      field(L('Plaetze (mit Komma)', 'Slots (comma)'), h('input', { class: 'px-input', value: (j.slots || []).join(', '), oninput: ev => { j.slots = ev.target.value.split(',').map(s => s.trim()).filter(Boolean); } }), 'mainhand, offhand, head, chest, legs, feet, armor, hand, any'),
      field(L('Tabellen und Tags', 'Tables and tags'), h('div', { class: 'pe-tags' }, st.tagNames.map(tn => h('label', { class: 'pe-tag' }, h('input', { type: 'checkbox', checked: form.tags.includes(tn), onchange: ev => { form.tags = ev.target.checked ? [...form.tags, tn] : form.tags.filter(x => x !== tn); } }), tn))),
        L('in_enchanting_table = im Zaubertisch, on_random_loot = in Truhen, treasure = nur als Fund, curse = Fluch', 'in_enchanting_table = enchanting table, on_random_loot = chests, treasure = loot only, curse = curse')));
    const raw = h('div', {}, h('textarea', { class: 'px-input pe-raw', spellcheck: 'false', value: form.raw, oninput: ev => { form.raw = ev.target.value; } }),
      h('div', { class: 'sub' }, L('Komplette JSON-Datei, inklusive Effekte. Gilt statt der Felder, wenn du hier speicherst.', 'The complete JSON file including effects. Used instead of the fields when you save here.')));
    const save = async () => {
      let json = j;
      if (fileTab === 'raw') { try { json = JSON.parse(form.raw); } catch (err) { ctx.toast(L('Kein gueltiges JSON: ', 'Invalid JSON: ') + err.message, 'bad'); return; } }
      if (form.isNew && !/^[a-z0-9_.-]+:[a-z0-9_.-]+$/.test(sel.key)) { ctx.toast(L('Schluessel wie namespace:name', 'Key like namespace:name'), 'bad'); return; }
      if (form.isNew && st.enchants.some(x => x.key === sel.key)) { ctx.toast(L('Den Schluessel gibt es schon', 'Key exists'), 'bad'); return; }
      if (await send('enchant', { addon: sel.addon, key: sel.key, json, tags: form.tags }, L('Im Entwurf gespeichert', 'Saved to draft'))) { const n = st.enchants.find(x => x.key === sel.key); if (n) select(n); }
    };
    const give = e && e.loaded ? giveBox(e) : h('div', { class: 'sub' }, L('Vergeben geht, sobald die Verzauberung nach einem Neustart geladen ist.', 'Giving works once it is loaded after a restart.'));
    return h('div', { class: 'pe-side' },
      h('div', { class: 'row' }, h('b', {}, form.isNew ? L('Neue Verzauberung', 'New enchantment') : name || sel.key), h('span', { class: 'sub' }, sel.addon)),
      h('div', { class: 'row' }, h('button', { class: 'px-btn small' + (fileTab === 'form' ? ' active' : ''), onclick: () => { if (fileTab === 'raw') { try { form.json = JSON.parse(form.raw); } catch { ctx.toast(L('JSON ist ungueltig', 'JSON invalid'), 'bad'); return; } } fileTab = 'form'; render(); } }, L('Felder', 'Fields')),
        h('button', { class: 'px-btn small' + (fileTab === 'raw' ? ' active' : ''), onclick: () => { form.raw = JSON.stringify(form.json, null, 2); fileTab = 'raw'; render(); } }, 'JSON')),
      fileTab === 'raw' ? raw : simple,
      h('div', { class: 'row' }, h('button', { class: 'px-btn primary', onclick: save }, L('Speichern (Entwurf)', 'Save (draft)')),
        !form.isNew && e ? h('button', { class: 'px-btn small', onclick: () => duplicate(e) }, L('Kopie anlegen', 'Duplicate')) : null,
        !form.isNew && e ? h('button', { class: 'px-btn small ghost', onclick: async () => { if (await ctx.confirm(L('Verzauberung loeschen (im Entwurf)? Funktionen des Datapacks, die sie nennen, koennen dann Fehler melden.', 'Delete (in draft)? Datapack functions that use it may log errors.'))) { if (await send('delete', { addon: sel.addon, key: sel.key }, L('Geloescht (Entwurf)', 'Deleted (draft)'))) { sel = null; form = null; render(); } } } }, L('Loeschen', 'Delete')) : null),
      give);
  }

  function giveBox(e) {
    let player = st.players[0] || '', level = 1;
    const sel2 = h('select', { class: 'px-input', onchange: ev => { player = ev.target.value; } }, st.players.map(p => h('option', { value: p }, p)));
    const lv = h('input', { class: 'px-input', type: 'number', min: 1, max: 255, value: 1, oninput: ev => { level = num(ev.target.value, 1); } });
    if (!st.players.length) return h('div', { class: 'sub' }, L('Vergeben: kein Spieler online.', 'Give: nobody online.'));
    const go = async mode => { try { await ctx.api('give', { method: 'POST', body: { player, key: e.key, level, mode } }); ctx.toast(L('Erledigt', 'Done'), 'good'); } catch (err) { ctx.toast(err.message || 'Fehler', 'bad'); } };
    return h('div', { class: 'pe-box' }, h('b', {}, L('Spieler vergeben', 'Give to player')), h('div', { class: 'row' }, sel2, lv),
      h('div', { class: 'row' }, h('button', { class: 'px-btn small', onclick: () => go('book') }, L('Buch geben', 'Give book')), h('button', { class: 'px-btn small', onclick: () => go('hand') }, L('Item in der Hand verzaubern', 'Enchant held item'))));
  }

  // ---------- Seite ----------
  function render() {
    if (!st) return;
    root.replaceChildren(...[
      h('style', {}, `.pe-box{border:1px solid var(--line,#333);border-radius:8px;padding:10px;margin:8px 0;display:grid;gap:8px}.pe-addon{flex-wrap:wrap}.pe-cols{display:grid;grid-template-columns:minmax(220px,320px) 1fr;gap:12px}
        @media(max-width:800px){.pe-cols{grid-template-columns:1fr}}.pe-list{display:grid;gap:6px;align-content:start}#pe-items{display:grid;gap:4px;max-height:70vh;overflow:auto}.pe-item{display:flex;flex-wrap:wrap;gap:6px;align-items:center;padding:6px 8px;border:1px solid var(--line,#333);border-radius:6px;cursor:pointer}.pe-item.sel{outline:2px solid var(--accent,#7aa2ff)}
        .pe-side{display:grid;gap:10px;align-content:start}.pe-f{display:grid;gap:3px}.pe-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px}.pe-tags{display:flex;flex-wrap:wrap;gap:10px}.pe-tag{display:flex;gap:4px;align-items:center}.pe-raw{min-height:340px;font-family:monospace;width:100%}`),
      st.restartNeeded ? h('div', { class: 'tag warn', style: 'display:block;margin:8px 0' }, L('Neustart noetig: Minecraft liest Verzauberungen nur beim Start. Bis dahin gelten die alten Werte.', 'Restart needed: Minecraft reads enchantments only at startup. Old values apply until then.')) : null,
      addons(),
      h('div', { class: 'row' }, h('button', { class: 'px-btn small', onclick: newOne }, '+ ' + L('Neue Verzauberung', 'New enchantment')),
        h('span', { class: 'sub' }, st.draft ? st.draftSize + ' ' + L('Dateien im Entwurf', 'files in draft') : ''),
        h('button', { class: 'px-btn small primary', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf veroeffentlichen? Die Dateien werden geschrieben (alte werden gesichert), danach ist ein Neustart noetig.', 'Publish the draft? Files are written (old ones backed up); a restart is needed afterwards.'))) send('publish', '', L('Veroeffentlicht. Neustart noetig.', 'Published. Restart needed.')); } }, L('Veroeffentlichen', 'Publish')),
        h('button', { class: 'px-btn small ghost', disabled: !st.draft, onclick: async () => { if (await ctx.confirm(L('Entwurf verwerfen?', 'Discard the draft?'))) { sel = null; form = null; send('discard', '', L('Verworfen', 'Discarded')); } } }, L('Verwerfen', 'Discard'))),
      h('div', { class: 'pe-cols' }, list(), editor())].filter(Boolean));
  }

  ctx.on('enchants', () => reload());
  await reload();
}
