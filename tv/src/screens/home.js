import { h, clear, timeText } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, toast } from '../ui.js';
import { catalogGate, posterCard, channelCard, hrow } from './common.js';
import { homeAppsRow } from './apps.js';
import { Focus } from '../focus.js';

export function HomeScreen(params, nav) {
  const clock = h('div', { class: 'clock big' });
  const tick = () => { clock.textContent = timeText(Date.now(), store.settings.get().use24h); };
  tick();
  const timer = setInterval(tick, 15000);
  const plChip = fz(h('div', { class: 'pl-chip' }), { click: () => nav.go('playlists') });
  const prof = store.activeProfile();
  const avatar = fz(h('div', { class: 'avatar', style: 'background:#' + (prof.color >>> 0).toString(16).slice(2) }, prof.name.slice(0, 1).toUpperCase()), { click: () => nav.go('profiles', {}) });
  const header = h('div', { class: 'hdr home-hdr' },
    h('div', { class: 'brand' }, h('img', { src: 'img/mark.png', alt: '' }), h('span', {}, h('span', { class: 'z', text: 'Z-' }), 'Vision')),
    plChip, h('div', { class: 'grow' }), clock,
    fz(h('div', { class: 'ibtn' }, icon('search')), { click: () => nav.go('search') }), avatar);
  const body = h('div', { class: 'body home scroll' });
  const el = h('div', { class: 'screen' }, header, body);

  const updatePl = () => { const p = repo.activePlaylist(); plChip.textContent = p ? p.name : t('add_playlist'); };
  updatePl();

  const tiles = [
    ['live_tv', 'live', 'live', 0, 'live'], ['movies', 'movie', 'movies', 1, 'movie'], ['series', 'series', 'series', 2, 'series'],
    ['tv_guide', 'guide', 'guide', 3], ['multiview', 'grid', 'multiview', 4], ['apps', 'apps', 'apps', 5], ['settings', 'settings', 'settings', 6],
  ];

  function render() {
    clear(body);
    const c = repo.catalog;
    const counts = { live: 0, movie: 0, series: 0 };
    c.items.forEach((i) => { counts[i.kind]++; });
    body.appendChild(h('div', { class: 'tiles fgroup' }, tiles.map(([label, ic, route, ci, kind]) => fz(h('div', { class: 'tile', style: 'background:linear-gradient(135deg,var(--t' + ci + '),rgba(0,0,0,.15))' },
      icon(ic), h('div', { class: 'tile-label', text: t(label) }), kind ? h('div', { class: 'tile-count', text: counts[kind] }) : null),
    { click: () => nav.go(route), auto: ci === 0 }))));

    const d = store.profileData.get();
    const cont = Object.keys(d.progress).map((k) => d.progress[k]).sort((a, b) => b.updatedAt - a.updatedAt)
      .map((p) => { const base = p.key.split('#')[0]; const m = repo.get(base); return m ? [m, p] : null; }).filter(Boolean).slice(0, 20);
    if (cont.length) {
      body.appendChild(h('div', { class: 'section' }, icon('play'), h('span', { text: t('continue_watching') })));
      body.appendChild(hrow(cont.map(([m, p]) => posterCard(m, { progress: p.positionMs / p.durationMs, click: () => nav.open(m) }))));
    }
    const recents = d.recentChannels.map((k) => repo.get(k)).filter(Boolean).slice(0, 20);
    if (recents.length) {
      body.appendChild(h('div', { class: 'section' }, icon('live'), h('span', { text: t('recent_channels') })));
      body.appendChild(hrow(recents.map((m) => channelCard(m, () => nav.open(m, recents)))));
    }
    const favs = d.favorites.map((k) => repo.get(k)).filter(Boolean);
    const favCh = favs.filter((m) => m.kind === 'live');
    const favVod = favs.filter((m) => m.kind !== 'live');
    if (favCh.length) {
      body.appendChild(h('div', { class: 'section' }, icon('heart'), h('span', { text: t('favorite_channels') })));
      body.appendChild(hrow(favCh.map((m) => channelCard(m, () => nav.open(m, favCh)))));
    }
    if (favVod.length) {
      body.appendChild(h('div', { class: 'section' }, icon('star'), h('span', { text: t('my_list') })));
      body.appendChild(hrow(favVod.map((m) => posterCard(m, { click: () => nav.open(m) }))));
    }
    const newest = c.items.filter((i) => i.kind === 'movie' && i.added > 0).sort((a, b) => b.added - a.added).slice(0, 20);
    if (newest.length) {
      body.appendChild(h('div', { class: 'section' }, icon('movie'), h('span', { text: t('recently_added') })));
      body.appendChild(hrow(newest.map((m) => posterCard(m, { click: () => nav.open(m), long: () => toast(t(store.toggleFavorite(m.key) ? 'added_my_list' : 'removed_my_list')) }))));
    }
    body.appendChild(homeAppsRow(nav));
    if (c.accountInfo) body.appendChild(h('div', { class: 'muted small account', text: c.accountInfo }));
    // first time the catalog appears: start on the Live TV tile
    if (!Focus.current || !Focus.current.isConnected || !el.contains(Focus.current) || !body.contains(Focus.current) && wasGate) Focus.first(body);
    wasGate = false;
  }

  let stopGate = null, wasGate = true;
  const offs = [];
  return {
    el,
    onShow(fresh) {
      updatePl();
      if (stopGate) stopGate();
      wasGate = !repo.catalog || fresh;
      stopGate = catalogGate(body, nav, render);
      offs.push(repo.on('catalog', () => { updatePl(); if (repo.catalog) render(); }));
    },
    onHide() { if (stopGate) stopGate(); stopGate = null; offs.splice(0).forEach((f) => f()); },
    destroy() { clearInterval(timer); },
    focusFirst() { Focus.first(nav.root); },
  };
}
