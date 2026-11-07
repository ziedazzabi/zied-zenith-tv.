import { h, clear, timeText } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { toast, empty, progressBar } from '../ui.js';
import { VList } from '../vlist.js';
import { Focus } from '../focus.js';
import { Playback } from '../player/playback.js';
import { shell, catalogGate, channelRow, categoryItem, withUnlock, categoryMenu } from './common.js';

export const CAT_ALL = '__all', CAT_FAV = '__fav', CAT_RECENT = '__recent';

export function specialCats(kind) {
  return [
    { id: CAT_ALL, name: t('all_channels'), kind, special: true, icon: 'list' },
    { id: CAT_FAV, name: t('favorites'), kind, special: true, icon: 'heart' },
    { id: CAT_RECENT, name: t('recent'), kind, special: true, icon: 'replay' },
  ];
}

export function channelsFor(catId) {
  const d = store.profileData.get();
  if (catId === CAT_ALL) return repo.items('live', null);
  if (catId === CAT_FAV) return d.favorites.map((k) => repo.get(k)).filter((m) => m && m.kind === 'live');
  if (catId === CAT_RECENT) return d.recentChannels.map((k) => repo.get(k)).filter((m) => m && !repo.isRestricted(m));
  return repo.items('live', catId);
}

export function LiveScreen(params, nav) {
  const sh = shell(t('live_tv'), nav, { cls: 'live' });
  let selected = params.cat || CAT_ALL;
  let channels = [];
  let focused = null;
  let numBuf = '', numTimer = 0;

  const catList = new VList({
    itemH: 3.6, gap: 0.3, className: 'cats',
    render: (c) => categoryItem(c, {
      selected: c.id === selected, count: c.special ? -1 : repo.items('live', c.id).length,
      click: () => withUnlock(c, () => selectCat(c.id)),
      long: () => { if (!c.special) categoryMenu(c, () => buildCats()); },
    }),
  });
  const chList = new VList({
    itemH: 5.4, gap: 0.3, className: 'chans',
    render: (m) => channelRow(m, {
      active: Playback.active && Playback.item && Playback.item.key === m.key,
      click: () => onChannel(m),
      long: () => toggleFav(m),
      focus: () => { focused = m; repo.requestShortEpg(m); drawEpg(); },
      key: (name) => { if (name === 'red') { toggleFav(m); return true; } return false; },
    }),
  });

  const preview = h('div', { class: 'preview' }, h('div', { class: 'preview-hint', text: t('preview_hint') }));
  const epgBox = h('div', { class: 'epg-panel scroll' });
  const right = h('div', { class: 'live-right' }, preview, epgBox);
  const catTitle = h('div', { class: 'pane-title' });
  const layout = h('div', { class: 'live-layout' }, catList.el, h('div', { class: 'live-mid' }, catTitle, chList.el), right);

  function buildCats() {
    catList.setItems(specialCats('live').concat(repo.categories('live')), true);
  }

  function selectCat(id) {
    selected = id;
    catList.refresh();
    const c = specialCats('live').concat(repo.categories('live')).find((x) => x.id === id);
    catTitle.textContent = c ? c.name : '';
    channels = channelsFor(id);
    chList.setItems(channels);
    if (!channels.length) chList.inner.appendChild(empty(t('nothing_here')));
    const playing = Playback.item && channels.findIndex((m) => m.key === Playback.item.key);
    if (playing > 0) chList.ensureVisible(playing);
  }

  function toggleFav(m) {
    const added = store.toggleFavorite(m.key);
    toast(t(added ? 'added_favorite' : 'removed_favorite'));
    chList.refresh();
  }

  function previewRect() {
    const r = preview.getBoundingClientRect();
    return { left: r.left, top: r.top, width: r.width, height: r.height };
  }

  function onChannel(m) {
    const idx = channels.indexOf(m);
    if (Playback.active && Playback.item && Playback.item.key === m.key && !Playback.req.url) { nav.expandPlayer(); return; }
    Playback.play({ items: channels, index: Math.max(0, idx) }, 'rect');
    Playback.setMode('rect', previewRect());
    chList.refresh();
  }

  function drawEpg() {
    clear(epgBox);
    const m = focused || Playback.item;
    if (!m) return;
    const list = repo.programs(m);
    const now = Date.now();
    const use24h = store.settings.get().use24h;
    epgBox.appendChild(h('div', { class: 'epg-ch', text: m.num + '  ' + m.name }));
    const upcoming = list.filter((p) => p.stop > now).slice(0, 8);
    if (!upcoming.length) { epgBox.appendChild(h('div', { class: 'muted', text: t('no_epg') })); return; }
    upcoming.forEach((p, i) => {
      epgBox.appendChild(h('div', { class: 'epg-item' + (i === 0 && p.start <= now ? ' now' : '') },
        h('div', { class: 'epg-time', text: timeText(p.start, use24h) }),
        h('div', { class: 'grow' }, h('div', { class: 'epg-title', text: p.title }),
          i === 0 && p.start <= now ? progressBar((now - p.start) / (p.stop - p.start), 'thin') : null,
          i === 0 && p.desc ? h('div', { class: 'epg-desc', text: p.desc }) : null)));
    });
  }

  function jumpTo(n) {
    const i = channels.findIndex((m) => m.num === n);
    if (i >= 0) { chList.focusIndex(i); onChannel(channels[i]); } else toast(t('channel_not_found', n));
  }

  sh.el._key = (name) => {
    if (/^d\d$/.test(name)) {
      numBuf += name.slice(1); toast(numBuf, 1500);
      clearTimeout(numTimer); numTimer = setTimeout(() => { const n = parseInt(numBuf, 10); numBuf = ''; jumpTo(n); }, 1500);
      return true;
    }
    if (name === 'chup' || name === 'chdown') {
      if (Playback.active && Playback.mode === 'rect') { Playback.zap(name === 'chup' ? -1 : 1); return true; }
    }
    if (name === 'play' || name === 'playpause') { if (Playback.active) { nav.expandPlayer(); return true; } }
    if (name === 'blue' && Playback.active && Playback.mode === 'rect') { nav.expandPlayer(); return true; }
    return false;
  };

  let stopGate = null;
  const offs = [];
  const render = () => {
    clear(sh.body);
    sh.body.appendChild(layout);
    requestAnimationFrame(() => {
      buildCats();
      selectCat(selected);
      catList.refresh(); chList.refresh();
      if (Playback.active && (Playback.mode === 'mini' || Playback.mode === 'rect')) Playback.setMode('rect', previewRect());
      const pi = Playback.item ? channels.findIndex((m) => m.key === Playback.item.key) : -1;
      chList.focusIndex(Math.max(0, pi));
      drawEpg();
    });
  };

  return {
    el: sh.el,
    onShow(fresh) {
      stopGate = catalogGate(sh.body, nav, render);
      offs.push(repo.on('epg', () => { chList.refresh(); drawEpg(); }));
      offs.push(repo.on('tick', () => { chList.refresh(); drawEpg(); }));
      offs.push(Playback.on('item', () => { chList.refresh(); drawEpg(); }));
      offs.push(store.profileData.subscribe(() => { if (selected === CAT_FAV || selected === CAT_RECENT) { channels = channelsFor(selected); chList.setItems(channels, true); } }));
    },
    onHide() {
      if (stopGate) stopGate();
      offs.splice(0).forEach((f) => f());
      if (Playback.active && Playback.mode === 'rect') {
        if (store.settings.get().miniPlayer) Playback.setMode('mini'); else Playback.stop();
      }
    },
    destroy() { sh.destroy(); },
    focusFirst() {},
  };
}
