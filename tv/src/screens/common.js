// Pieces shared by several screens.
import { h, clear, timeText } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, logo, progressBar, button, dialog, pinDialog, toast, spinner } from '../ui.js';

/** Standard screen frame: header (back, title, actions, clock) + body. */
export function shell(title, nav, o) {
  o = o || {};
  const clock = h('div', { class: 'clock' });
  const tick = () => { clock.textContent = timeText(Date.now(), store.settings.get().use24h); };
  tick();
  const timer = setInterval(tick, 15000);
  const header = h('div', { class: 'hdr' },
    o.noBack ? null : fz(h('div', { class: 'ibtn back' }, icon('back')), { click: () => nav.back() }),
    h('div', { class: 'hdr-title', text: title }),
    h('div', { class: 'grow' }),
    o.actions || null,
    clock);
  const body = h('div', { class: 'body' + (o.bodyCls ? ' ' + o.bodyCls : '') });
  const el = h('div', { class: 'screen ' + (o.cls || '') }, header, body);
  return { el, body, header, destroy: () => clearInterval(timer) };
}

/** Loading / error / no-playlist states shown until the catalog is ready. Calls render() once it is. */
export function catalogGate(body, nav, render) {
  let rendered = false;
  const draw = () => {
    if (repo.catalog) { if (!rendered) { rendered = true; clear(body); render(); } return; }
    rendered = false;
    clear(body);
    const st = repo.state;
    if (st.status === 'error') {
      body.appendChild(h('div', { class: 'gate' },
        h('div', { class: 'gate-title err', text: t('load_failed') }),
        h('div', { class: 'muted', text: st.message }),
        h('div', { class: 'row gap' }, button(t('retry'), 'refresh', () => repo.reload(), { primary: true, auto: true }), button(t('playlists'), 'playlist', () => nav.go('playlists')))));
    } else if (!repo.activePlaylist()) {
      body.appendChild(h('div', { class: 'gate' },
        h('div', { class: 'gate-title', text: t('welcome') }), h('div', { class: 'muted', text: t('welcome_sub') }),
        button(t('add_playlist'), 'add', () => nav.go('addPlaylist'), { primary: true, auto: true })));
    } else {
      body.appendChild(h('div', { class: 'gate' }, spinner(), h('div', { class: 'muted', text: t('loading_playlist', st.message || '') })));
    }
  };
  const offs = [repo.on('catalog', draw), repo.on('state', draw)];
  draw();
  return () => offs.forEach((f) => f());
}

export function posterCard(m, o) {
  o = o || {};
  const fav = store.isFavorite(m.key);
  const card = fz(h('div', { class: 'poster' },
    h('div', { class: 'pimg' }, logo(m.logo, m.name, 'cover'),
      fav ? h('div', { class: 'badge fav' }, icon('heart')) : null,
      m.rating && parseFloat(m.rating) > 0 ? h('div', { class: 'badge rating' }, icon('star'), (parseFloat(m.rating) > 10 ? parseFloat(m.rating) / 10 : parseFloat(m.rating)).toFixed(1)) : null,
      o.progress > 0 ? progressBar(o.progress, 'on-img') : null),
    h('div', { class: 'ptitle', text: m.name })), {
    click: o.click, long: o.long, focus: o.focus,
  });
  return card;
}

export function channelCard(m, onClick) {
  const now = repo.nowNext(m)[0];
  repo.requestShortEpg(m);
  return fz(h('div', { class: 'ccard' },
    h('div', { class: 'ccard-logo' }, logo(m.logo, m.name)),
    h('div', { class: 'ccard-name', text: m.name }),
    h('div', { class: 'ccard-now', text: now ? now.title : ' ' }),
    now ? progressBar((Date.now() - now.start) / (now.stop - now.start)) : null), { click: onClick });
}

/** One row in a channel list: number, logo, name, current programme. */
export function channelRow(m, o) {
  o = o || {};
  const [now] = repo.nowNext(m);
  const row = fz(h('div', { class: 'crow' + (o.active ? ' playing' : '') },
    h('div', { class: 'cnum', text: m.num }),
    h('div', { class: 'clogo' }, logo(m.logo, m.name)),
    h('div', { class: 'cmain' },
      h('div', { class: 'cname' }, h('span', { text: m.name }), m.cu > 0 ? icon('replay', 'tiny') : null, store.isFavorite(m.key) ? icon('heart', 'tiny fav') : null),
      h('div', { class: 'cnow', text: now ? timeText(now.start, store.settings.get().use24h) + '  ' + now.title : '' }),
      now ? progressBar((Date.now() - now.start) / (now.stop - now.start), 'thin') : null)), {
    click: o.click, long: o.long, focus: o.focus,
    key: o.key,
  });
  return row;
}

export function categoryItem(c, o) {
  o = o || {};
  const locked = !c.special && repo.isLocked(c);
  return fz(h('div', { class: 'cat' + (o.selected ? ' sel' : '') },
    c.icon ? icon(c.icon) : null,
    h('span', { class: 'grow', text: c.name }),
    locked ? icon('lock', 'tiny') : null,
    o.count >= 0 ? h('span', { class: 'count', text: o.count }) : null), { click: o.click, long: o.long, focus: o.focus });
}

/** Opens a category, asking for the PIN first when it's locked. */
export function withUnlock(c, then) {
  if (c.special || !repo.isLocked(c)) { then(); return; }
  pinDialog(t('enter_pin'), (pin) => {
    if (pin === store.settings.get().parentalPin) { repo.unlock(c); then(); }
    else toast(t('wrong_pin'));
  }, store.settings.get().parentalPin.length);
}

/** Long-press menu on a category: lock / unlock, hide. */
export function categoryMenu(c, onChange) {
  const locked = (store.settings.get().lockedCategories || []).indexOf(repo.categoryKey(c)) >= 0;
  const doIt = (fn) => pinDialog(t('enter_pin'), (pin) => {
    if (pin === store.settings.get().parentalPin) { fn(); if (onChange) onChange(); } else toast(t('wrong_pin'));
  }, store.settings.get().parentalPin.length);
  dialog({
    title: c.name,
    buttons: [
      [locked ? t('unlock_category') : t('lock_category'), () => doIt(() => repo.toggleLock(c)), { icon: 'lock' }],
      [t('hide_category'), () => doIt(() => repo.hide(c)), { icon: 'hide' }],
      [t('cancel'), null],
    ],
  });
}

export function hrow(cards) {
  return h('div', { class: 'hrow fgroup' }, cards);
}
