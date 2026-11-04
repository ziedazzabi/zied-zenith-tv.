// Full-screen player UI on top of the global Playback.
import { h, add, clear, timeText, fmtDuration } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, logo, progressBar, toast, choiceDialog, dialog, spinner } from '../ui.js';
import { VList } from '../vlist.js';
import { Focus } from '../focus.js';
import { Playback } from '../player/playback.js';
import { channelRow } from './common.js';

export function PlayerScreen(params, nav) {
  const catcher = fz(h('div', { class: 'catcher autofocus' }));
  const spin = spinner();
  const errBox = h('div', { class: 'p-error hidden' });
  const numBox = h('div', { class: 'p-num hidden' });
  const subBox = h('div', { class: 'p-sub hidden' });
  const banner = h('div', { class: 'p-banner hidden' });
  const topBar = h('div', { class: 'p-top hidden' });
  const listPanel = h('div', { class: 'p-list hidden' });
  const el = h('div', { class: 'screen player' }, catcher, spin, errBox, numBox, subBox, topBar, banner, listPanel);
  let uiTimer = 0, numBuf = '', numTimer = 0, sleepTimer = 0, sleepAt = 0;
  let listOpen = false;
  const offs = [];

  const chList = new VList({
    itemH: 5.4, gap: 0.3, className: 'chans',
    render: (m, i) => channelRow(m, { active: i === Playback.index, click: () => { closeList(); Playback.playIndex(i); }, long: () => toggleFav(m) }),
  });
  listPanel.appendChild(h('div', { class: 'p-list-title', text: t('channel_list') }));
  listPanel.appendChild(chList.el);

  function showUi(ms) {
    banner.classList.remove('hidden'); topBar.classList.remove('hidden');
    drawBanner();
    clearTimeout(uiTimer);
    uiTimer = setTimeout(hideUi, ms || 5000);
  }
  function hideUi() {
    if (Focus.current && (banner.contains(Focus.current) || topBar.contains(Focus.current))) { clearTimeout(uiTimer); uiTimer = setTimeout(hideUi, 5000); return; }
    banner.classList.add('hidden'); topBar.classList.add('hidden');
  }

  function drawBanner() {
    clear(banner); clear(topBar);
    const m = Playback.item;
    const use24h = store.settings.get().use24h;
    add(topBar, fz(h('div', { class: 'ibtn' }, icon('back')), { click: () => exit() }),
      h('div', { class: 'p-title', text: Playback.title }),
      h('div', { class: 'grow' }),
      Playback.live && store.settings.get().miniPlayer ? fz(h('div', { class: 'ibtn' }, icon('pip')), { click: () => exit() }) : null,
      Playback.live ? fz(h('div', { class: 'ibtn' }, icon('list')), { click: () => openList() }) : null,
      fz(h('div', { class: 'ibtn' }, icon('more')), { click: () => openMenu() }),
      h('div', { class: 'clock', text: timeText(Date.now(), use24h) }));
    if (!m) return;
    if (Playback.live || (Playback.req && Playback.req.program)) {
      const [now, next] = repo.nowNext(m);
      add(banner, h('div', { class: 'b-logo' }, logo(m.logo, m.name)),
        h('div', { class: 'b-main' },
          h('div', { class: 'b-line1' }, h('span', { class: 'b-num', text: m.num }), h('span', { class: 'b-name', text: m.name }),
            store.isFavorite(m.key) ? icon('heart', 'tiny fav') : null, m.cu > 0 ? icon('replay', 'tiny') : null,
            Playback.engine && Playback.engine.videoSize ? h('span', { class: 'b-res', text: Playback.engine.videoSize }) : null),
          Playback.req.program ? h('div', { class: 'b-now' }, h('span', { class: 'b-tag', text: t('catchup') }), Playback.req.program.title) :
            now ? h('div', { class: 'b-now' }, h('span', { class: 'b-time', text: timeText(now.start, use24h) + ' – ' + timeText(now.stop, use24h) }), now.title) : h('div', { class: 'b-now muted', text: t('no_epg') }),
          now && !Playback.req.program ? progressBar((Date.now() - now.start) / (now.stop - now.start)) : null,
          next && !Playback.req.program ? h('div', { class: 'b-next' }, h('span', { class: 'b-time', text: t('next') + ' ' + timeText(next.start, use24h) }), next.title) : null,
          h('div', { class: 'b-hints', text: '▲▼ ' + t('channels') + '   OK ' + t('channel_list') + '   ▶ ' + t('options') })));
    } else {
      const e = Playback.engine;
      const pos = e ? e.time : 0, dur = e ? e.duration : 0;
      const r = Playback.req;
      add(banner, h('div', { class: 'b-main' },
        h('div', { class: 'b-line1' }, h('span', { class: 'b-name', text: Playback.title })),
        h('div', { class: 'vod-bar' }, h('span', { text: fmtDuration(pos) }), progressBar(dur ? pos / dur : 0, 'vod'), h('span', { text: fmtDuration(dur) })),
        h('div', { class: 'vod-btns fgroup' },
          fz(h('div', { class: 'ibtn' }, icon('rw')), { click: () => { Playback.seekBy(-30000); showUi(); } }),
          fz(h('div', { class: 'ibtn big' }, icon(Playback.state.paused ? 'play' : 'pause')), { click: () => { Playback.togglePause(); showUi(); } }),
          fz(h('div', { class: 'ibtn' }, icon('ff')), { click: () => { Playback.seekBy(30000); showUi(); } }),
          r && r.series && r.episodeIndex + 1 < r.episodes.length ? fz(h('div', { class: 'btn' }, icon('next'), h('span', { text: t('next_episode') })), { click: () => Playback.playEpisode(r.episodeIndex + 1) }) : null)));
    }
  }

  function drawState() {
    const s = Playback.state;
    spin.classList.toggle('hidden', !s.buffering || !!s.error);
    errBox.classList.toggle('hidden', !s.error);
    if (s.error) {
      clear(errBox);
      add(errBox, h('div', { class: 'err-title', text: t('playback_error') }), h('div', { class: 'muted', text: s.error }),
        Playback.live ? h('div', { class: 'muted', text: t('zap_hint') }) : null);
    }
  }

  function toggleFav(m) {
    m = m || Playback.item; if (!m) return;
    toast(t(store.toggleFavorite(m.key) ? 'added_favorite' : 'removed_favorite'));
    drawBanner(); if (listOpen) chList.refresh();
  }

  function openList() {
    if (!Playback.req) return;
    listOpen = true; hideUi();
    listPanel.classList.remove('hidden');
    chList.setItems(Playback.req.items);
    chList.focusIndex(Playback.index);
  }
  function closeList() { listOpen = false; listPanel.classList.add('hidden'); Focus.set(catcher); }
  listPanel._key = (name) => {
    if (name === 'back' || (name === 'right' && document.documentElement.dir !== 'rtl') || (name === 'left' && document.documentElement.dir === 'rtl')) { closeList(); return true; }
    if (name === 'red') { toggleFav(Playback.req.items[chList.index]); return true; }
    return false;
  };

  function exit() {
    clearTimeout(sleepTimer);
    if (Playback.item && Playback.item.kind === 'live' && !Playback.state.error && store.settings.get().miniPlayer) Playback.setMode('mini');
    else Playback.stop();
    nav.back();
  }

  function jumpNumber(n) {
    const items = Playback.req ? Playback.req.items : [];
    let i = items.findIndex((m) => m.num === n);
    let list = items;
    if (i < 0) { list = repo.channels(); i = list.findIndex((m) => m.num === n); if (i >= 0) { Playback.play({ items: list, index: i }); return; } }
    if (i >= 0) Playback.playIndex(i); else toast(t('channel_not_found', n));
  }

  // ---------- options menu ----------
  function openMenu() {
    hideUi();
    const e = Playback.engine;
    const m = Playback.item;
    const rows = [];
    const audio = e ? e.audioTracks() : [], subs = e ? e.subTracks() : [];
    if (audio.length > 1) rows.push(['audio', t('audio_track'), 'audio', () => choiceDialog(t('audio_track'), audio.map((a) => [a.id, a.label]), (audio.find((a) => a.active) || {}).id, (id) => e.setAudio(id))]);
    if (subs.length) rows.push(['subs', t('subtitles'), 'subtitles', () => choiceDialog(t('subtitles'), [[-1, t('off')]].concat(subs.map((s) => [s.id, s.label])), (subs.find((s) => s.active) || { id: -1 }).id, (id) => e.setSub(id))]);
    rows.push(['aspect', t('aspect_ratio'), 'aspect', () => choiceDialog(t('aspect_ratio'), [['fit', t('aspect_fit')], ['fill', t('aspect_fill')], ['zoom', t('aspect_zoom')]], store.settings.get().aspect, (v) => { store.updateSettings(() => ({ aspect: v })); if (Playback.engine) Playback.engine.setAspect(v); })]);
    rows.push(['sleep', t('sleep_timer') + (sleepAt ? ' · ' + timeText(sleepAt, store.settings.get().use24h) : ''), 'timer', () => choiceDialog(t('sleep_timer'), [[0, t('off')], [15, '15 min'], [30, '30 min'], [60, '60 min'], [90, '90 min'], [120, '120 min']], 0, (min) => {
      clearTimeout(sleepTimer); sleepAt = 0;
      if (min > 0) { sleepAt = Date.now() + min * 60000; sleepTimer = setTimeout(() => { Playback.stop(); nav.back(); }, min * 60000); toast(t('sleep_set', min)); }
    })]);
    if (m && m.kind === 'live' && m.cu > 0) rows.push(['catchup', t('catchup'), 'replay', () => catchupMenu(m)]);
    if (Playback.req && Playback.req.program) rows.push(['live', t('back_to_live'), 'live', () => Playback.backToLive()]);
    if (m && m.kind === 'live') rows.push(['fav', store.isFavorite(m.key) ? t('remove_favorite') : t('add_favorite'), 'heart', () => toggleFav(m)]);
    if (Playback.live) rows.push(['guide', t('tv_guide'), 'guide', () => { Playback.setMode('mini'); nav.back(); nav.go('guide'); }]);
    rows.push(['restart', t('retry'), 'refresh', () => { Playback.retries = 0; Playback.start(Playback.url, 0); }]);
    const list = h('div', { class: 'choices' }, rows.map(([k, label, ic, fn], i) => fz(h('div', { class: 'choice' }, icon(ic), h('span', { class: 'grow', text: label })), { click: () => { d.close(); fn(); }, auto: i === 0 })));
    const d = dialog({ title: t('options'), body: list, cls: 'side' });
  }

  function catchupMenu(m) {
    const now = Date.now(), use24h = store.settings.get().use24h;
    const past = repo.programs(m).filter((p) => p.stop <= now && p.start >= now - m.cu * 86400000).reverse().slice(0, 80);
    if (!past.length) { toast(t('no_epg')); return; }
    choiceDialog(t('catchup'), past.map((p, i) => [i, new Date(p.start).toLocaleDateString(undefined, { weekday: 'short' }) + ' ' + timeText(p.start, use24h) + '  ' + p.title]), -1, (i) => {
      Playback.playCatchup(m, past[i]).then((ok) => { if (!ok) toast(t('catchup_unavailable')); });
    });
  }

  // ---------- keys ----------
  catcher._key = (name) => {
    const live = Playback.live || (Playback.req && Playback.req.program && false);
    if (/^d\d$/.test(name) && Playback.req && Playback.item && Playback.item.kind === 'live') {
      numBuf += name.slice(1); numBox.textContent = numBuf; numBox.classList.remove('hidden');
      clearTimeout(numTimer);
      numTimer = setTimeout(() => { const n = parseInt(numBuf, 10); numBuf = ''; numBox.classList.add('hidden'); jumpNumber(n); }, 1500);
      return true;
    }
    switch (name) {
      case 'back': exit(); return true;
      case 'up': case 'chup': if (live) { Playback.zap(-1); showUi(); } else showUi(); return true;
      case 'down': case 'chdown': if (live) { Playback.zap(1); showUi(); } else { showUi(); Focus.first(banner); } return true;
      case 'left': if (live) openList(); else { Playback.seekBy(-10000); showUi(); } return true;
      case 'right': if (live) openMenu(); else { Playback.seekBy(10000); showUi(); } return true;
      case 'rw': Playback.seekBy(-30000); showUi(); return true;
      case 'ff': Playback.seekBy(30000); showUi(); return true;
      case 'menu': openMenu(); return true;
      case 'info': if (banner.classList.contains('hidden')) showUi(8000); else hideUi(); return true;
      case 'red': toggleFav(); return true;
      case 'play': case 'pause': case 'playpause': if (!live) { Playback.togglePause(); showUi(); } return true;
      case 'stop': Playback.stop(); nav.back(); return true;
      case 'chlist': openList(); return true;
      case 'green': case 'guide': if (live) { Playback.setMode('mini'); nav.back(); nav.go('guide'); } return true;
      default: return false;
    }
  };
  catcher._click = () => { if (Playback.live) openList(); else { Playback.togglePause(); showUi(); } };
  // pointer: clicking the video toggles the controls
  el.addEventListener('click', (e) => { if (e.target === el || e.target === catcher) { if (banner.classList.contains('hidden')) showUi(); else hideUi(); } });
  banner._key = topBar._key = (name) => { if (name === 'back') { hideUi(); Focus.set(catcher); return true; } return false; };

  return {
    el,
    onShow() {
      if (Playback.mode !== 'full') Playback.setMode('full');
      offs.push(Playback.on('state', drawState));
      offs.push(Playback.on('item', () => { showUi(); if (listOpen) chList.refresh(); }));
      offs.push(Playback.on('time', () => { if (!banner.classList.contains('hidden') && !Playback.live) { const f = Focus.current; drawBanner(); if (f && !f.isConnected) Focus.first(banner); } }));
      offs.push(Playback.on('subtitle', (txt) => { subBox.textContent = txt; subBox.classList.toggle('hidden', !txt); }));
      offs.push(Playback.on('ended', () => showUi(60000)));
      offs.push(repo.on('epg', () => { if (!banner.classList.contains('hidden')) drawBanner(); }));
      drawState(); showUi();
      if (Playback.item) repo.requestShortEpg(Playback.item);
    },
    onHide() { offs.splice(0).forEach((f) => f()); clearTimeout(uiTimer); },
    destroy() { clearTimeout(numTimer); },
    focusFirst() { Focus.set(catcher); },
    key(name) { return catcher._key(name); },
  };
}
