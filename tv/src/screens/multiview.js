// Watch 2 or 4 channels at once. Sound follows the focused tile.
import { h, add, clear } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { fz, logo, dialog, toast, field } from '../ui.js';
import { VList } from '../vlist.js';
import { Focus } from '../focus.js';
import { Playback } from '../player/playback.js';
import { Html5Engine } from '../player/engine.js';
import { store } from '../store.js';
import { norm } from '../util.js';
import { channelRow } from './common.js';

export function MultiViewScreen(params, nav) {
  let count = 4;
  const tiles = [];
  const grid = h('div', { class: 'mv-grid' });
  const top = h('div', { class: 'mv-top' },
    fz(h('div', { class: 'ibtn' }, icon('back')), { click: () => nav.back() }),
    h('div', { class: 'p-title', text: t('multiview') }), h('div', { class: 'grow' }),
    fz(h('div', { class: 'btn' }, icon('grid'), h('span', { text: '2 / 4' })), { click: () => { count = count === 4 ? 2 : 4; build(); } }),
    h('div', { class: 'muted small', text: t('multiview_limit') }));
  const el = h('div', { class: 'screen multiview' }, top, grid);

  const initial = (() => {
    const d = store.profileData.get();
    const keys = d.recentChannels.concat(d.favorites);
    const seen = new Set(); const out = [];
    keys.forEach((k) => { const m = repo.get(k); if (m && m.kind === 'live' && !seen.has(k)) { seen.add(k); out.push(m); } });
    return out;
  })();

  function makeTile(i) {
    const video = h('video', { muted: true, playsinline: true });
    video.muted = true;
    const label = h('div', { class: 'mv-label' });
    const ph = h('div', { class: 'mv-empty' }, icon('add'), h('span', { text: t('add_channel') }));
    const tile = { i, video, label, ph, engine: new Html5Engine(video, { bufferSeconds: 6 }), media: null };
    tile.engine.on('error', () => { label.textContent = (tile.media ? tile.media.name + ' · ' : '') + '⚠'; });
    tile.el = fz(h('div', { class: 'mv-tile' }, video, ph, label), {
      click: () => pick(tile),
      long: () => setMedia(tile, null),
      focus: () => tiles.forEach((x) => { x.video.muted = x !== tile; }),
      key: (name) => { if (name === 'menu' || name === 'red') { setMedia(tile, null); return true; } return false; },
    });
    return tile;
  }

  async function setMedia(tile, m) {
    tile.media = m;
    tile.engine.stop();
    tile.ph.classList.toggle('hidden', !!m);
    tile.label.textContent = m ? m.num + '  ' + m.name : '';
    if (!m) return;
    try { tile.engine.load(await repo.resolve(m), { live: true }); } catch (e) { toast(e.message); }
  }

  function pick(tile) {
    const all = repo.channels();
    let shown = all;
    const vl = new VList({ itemH: 5.4, gap: 0.3, className: 'chans picker', render: (m) => channelRow(m, { click: () => { d.close(); setMedia(tile, m); } }) });
    const f = field(t('search'), '', { onInput: (q) => { q = norm(q); shown = q.length < 2 ? all : all.filter((m) => norm(m.name).indexOf(q) >= 0); vl.setItems(shown); } });
    const d = dialog({ title: t('choose_channel'), body: [f, vl.el], wide: true, cls: 'picker-dlg' });
    requestAnimationFrame(() => { vl.setItems(shown); const cur = tile.media ? all.indexOf(tile.media) : 0; vl.focusIndex(Math.max(0, cur)); });
  }

  function build() {
    const keep = tiles.map((x) => x.media);
    tiles.splice(0).forEach((x) => x.engine.stop());
    clear(grid);
    grid.className = 'mv-grid n' + count;
    for (let i = 0; i < count; i++) {
      const tl = makeTile(i);
      tiles.push(tl);
      grid.appendChild(tl.el);
      const m = keep[i] !== undefined ? keep[i] : initial[i];
      if (m) setMedia(tl, m);
    }
    requestAnimationFrame(() => Focus.set(tiles[0].el));
  }

  return {
    el,
    onShow(fresh) { if (Playback.active) Playback.stop(); if (fresh) build(); },
    onHide() {},
    destroy() { tiles.forEach((x) => x.engine.stop()); },
    focusFirst() { if (tiles[0]) Focus.set(tiles[0].el); },
  };
}
