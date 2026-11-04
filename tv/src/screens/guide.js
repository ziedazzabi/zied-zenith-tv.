// TV guide grid (channels × time), TiviMate style.
import { h, add, clear, timeText, dayText } from '../util.js';
import { icon } from '../icons.js';
import { t, getLang } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, logo, chip, dialog, toast, spinner } from '../ui.js';
import { VList } from '../vlist.js';
import { Focus } from '../focus.js';
import { Playback } from '../player/playback.js';
import { shell, catalogGate, withUnlock } from './common.js';
import { specialCats, channelsFor, CAT_ALL } from './live.js';
import { scheduleSystemAlarm, cancelSystemAlarm } from '../platform.js';

const SLOT = 30 * 60000;
const side = () => (document.documentElement.dir === 'rtl' ? 'right' : 'left');

export function GuideScreen(params, nav) {
  const sh = shell(t('tv_guide'), nav, { cls: 'guide' });
  let cat = CAT_ALL;
  let channels = [];
  let start = Math.floor(Date.now() / SLOT) * SLOT - SLOT;
  const hours = 2.5;
  let span = hours * 3600000;
  let focusTime = Date.now();
  const timeline = h('div', { class: 'g-timeline' });
  const chips = h('div', { class: 'g-cats hrow fgroup' });
  const detail = h('div', { class: 'g-detail' });

  const list = new VList({
    itemH: 5.2, gap: 0.25, className: 'g-rows',
    render: (m, i) => renderRow(m, i),
  });

  function pct(ms) { return ((ms - start) / span) * 100; }

  function renderRow(m, rowIndex) {
    const use24h = store.settings.get().use24h;
    const progs = repo.programs(m).filter((p) => p.stop > start && p.start < start + span);
    repo.requestShortEpg(m);
    const lane = h('div', { class: 'g-lane' });
    const row = h('div', { class: 'g-row' }, h('div', { class: 'g-ch' }, h('span', { class: 'cnum', text: m.num }), logo(m.logo, m.name, 'small'), h('span', { class: 'g-chname', text: m.name })), lane);
    const blocks = [];
    const now = Date.now();
    const add = (p, empty) => {
      const l = Math.max(0, pct(p.start)), r = Math.min(100, pct(p.stop));
      const b = fz(h('div', { class: 'g-prog' + (p.start <= now && p.stop > now ? ' now' : '') + (p.stop <= now ? ' past' : '') + (empty ? ' noinfo' : ''), style: side() + ':' + l + '%;width:' + Math.max(0.5, r - l) + '%' },
        h('div', { class: 'g-ptitle', text: empty ? t('no_epg') : p.title }),
        empty ? null : h('div', { class: 'g-ptime', text: timeText(p.start, use24h) + ' – ' + timeText(p.stop, use24h) }),
        hasReminder(m, p) ? icon('bell', 'tiny') : null,
        p.stop <= now && m.cu > 0 && !empty ? icon('replay', 'tiny') : null), {
        click: () => (empty ? nav.open(m, channels) : programDialog(m, p)),
        focus: () => { focusTime = Math.max(p.start, start) + 1; showDetail(m, p, empty); },
      });
      b._prog = p; b._row = rowIndex;
      lane.appendChild(b); blocks.push(b);
    };
    if (progs.length) progs.forEach((p) => add(p)); else add({ start, stop: start + span, title: '' }, true);
    // left/right: previous/next programme (shift time at the edges); up/down: same time on the next channel
    row._nav = (dir, cur) => {
      const i = blocks.indexOf(cur);
      if (i < 0) return false;
      const rtl = document.documentElement.dir === 'rtl';
      const fwd = dir === (rtl ? 'left' : 'right'), back = dir === (rtl ? 'right' : 'left');
      if (fwd) {
        if (i + 1 < blocks.length) { Focus.set(blocks[i + 1]); return true; }
        shift(SLOT * 2, cur._prog.stop + 1); return true;
      }
      if (back) {
        if (i > 0) { Focus.set(blocks[i - 1]); return true; }
        if (start <= Date.now() - 86400000 * 2) return false;
        shift(-SLOT * 2, cur._prog.start - 1); return true;
      }
      if (dir === 'up' || dir === 'down') {
        const ni = rowIndex + (dir === 'up' ? -1 : 1);
        if (ni < 0) return false;
        if (ni >= channels.length) return true;
        list.ensureVisible(ni);
        const target = list.nodes.get(ni);
        if (target) focusAtTime(target);
        return true;
      }
      return false;
    };
    return row;
  }

  function focusAtTime(rowEl) {
    const blocks = rowEl.querySelectorAll('.g-prog');
    let best = blocks[0];
    for (let i = 0; i < blocks.length; i++) {
      const p = blocks[i]._prog;
      if (p.start <= focusTime && p.stop > focusTime) { best = blocks[i]; break; }
      if (p.start > focusTime) { best = blocks[i]; break; }
      best = blocks[i];
    }
    if (best) Focus.set(best, { noScroll: true });
  }

  function shift(delta, keepTime) {
    const row = Focus.current && Focus.current._row;
    start += delta;
    focusTime = keepTime;
    drawTimeline();
    list.refresh();
    const el = list.nodes.get(row != null ? row : list.index);
    if (el) focusAtTime(el);
  }

  function drawTimeline() {
    clear(timeline);
    const use24h = store.settings.get().use24h;
    timeline.appendChild(h('div', { class: 'g-day', text: dayText(start + span / 2, getLang()) }));
    const lane = h('div', { class: 'g-tl-lane' });
    for (let x = start; x < start + span; x += SLOT) lane.appendChild(h('div', { class: 'g-tick', style: side() + ':' + pct(x) + '%', text: timeText(x, use24h) }));
    const now = Date.now();
    if (now >= start && now < start + span) lane.appendChild(h('div', { class: 'g-nowline', style: side() + ':' + pct(now) + '%' }));
    timeline.appendChild(lane);
  }

  function showDetail(m, p, emptyP) {
    clear(detail);
    if (emptyP) { detail.appendChild(h('div', { class: 'g-dtitle', text: m.name })); return; }
    const use24h = store.settings.get().use24h;
    add(detail, h('div', { class: 'g-dtitle', text: p.title }),
      h('div', { class: 'muted', text: m.name + ' · ' + dayText(p.start, getLang()) + ' ' + timeText(p.start, use24h) + ' – ' + timeText(p.stop, use24h) }),
      p.desc ? h('div', { class: 'g-ddesc', text: p.desc }) : null);
  }

  function drawCats() {
    clear(chips);
    specialCats('live').concat(repo.categories('live')).forEach((c) => chips.appendChild(chip(c.name, c.id === cat, () => withUnlock(c, () => { cat = c.id; drawCats(); load(); }))));
  }

  function load() {
    channels = channelsFor(cat);
    list.setItems(channels);
  }

  let stopGate = null;
  const offs = [];
  const render = () => {
    clear(sh.body);
    if (repo.loadingEpg && !repo.epg.size) sh.body.appendChild(h('div', { class: 'row gap muted' }, spinner(), t('loading_epg')));
    add(sh.body, chips, detail, timeline, list.el);
    requestAnimationFrame(() => {
      drawCats(); drawTimeline(); load();
      const pi = Playback.item ? channels.findIndex((m) => m.key === Playback.item.key) : 0;
      list.ensureVisible(Math.max(0, pi));
      const el = list.nodes.get(Math.max(0, pi));
      if (el) focusAtTime(el);
    });
  };

  return {
    el: sh.el,
    onShow(fresh) {
      stopGate = catalogGate(sh.body, nav, render);
      offs.push(repo.on('epg', () => { const f = Focus.current && Focus.current._row; list.refresh(); const el = list.nodes.get(f != null ? f : 0); if (el) focusAtTime(el); }));
      offs.push(repo.on('tick', drawTimeline));
    },
    onHide() { if (stopGate) stopGate(); offs.splice(0).forEach((f) => f()); },
    destroy() { sh.destroy(); },
    focusFirst() {},
  };

  // ---------- programme dialog ----------
  function programDialog(m, p) {
    const now = Date.now();
    const use24h = store.settings.get().use24h;
    const buttons = [];
    if (p.start <= now && p.stop > now) buttons.push([t('watch_live'), () => nav.open(m, channels), { primary: true, icon: 'play' }]);
    else if (p.stop <= now && m.cu > 0) buttons.push([t('watch_catchup'), () => { Playback.play({ items: [m], index: 0 }, 'full'); Playback.playCatchup(m, p).then((ok) => { if (!ok) toast(t('catchup_unavailable')); }); nav.go('player'); }, { primary: true, icon: 'replay' }]);
    else if (p.start > now) {
      const has = hasReminder(m, p);
      buttons.push([has ? t('remove_reminder') : t('remind_me'), () => { toggleReminder(m, p); list.refresh(); }, { primary: true, icon: 'bell' }]);
      buttons.push([t('watch_live'), () => nav.open(m, channels), { icon: 'play' }]);
    }
    buttons.push([t('close'), null]);
    dialog({
      title: p.title,
      body: [h('div', { class: 'muted', text: m.name + ' · ' + dayText(p.start, getLang()) + ' ' + timeText(p.start, use24h) + ' – ' + timeText(p.stop, use24h) }),
        p.desc ? h('div', { class: 'desc', text: p.desc }) : null],
      buttons,
    });
  }
}

export function hasReminder(m, p) {
  return store.reminders.get().some((r) => r.mediaKey === m.key && r.start === p.start);
}

export function toggleReminder(m, p) {
  const list = store.reminders.get();
  const ex = list.find((r) => r.mediaKey === m.key && r.start === p.start);
  if (ex) {
    cancelSystemAlarm(ex.alarmId);
    store.reminders.set(list.filter((r) => r !== ex));
    toast(t('reminder_removed'));
  } else {
    const r = { id: 'r' + Date.now(), mediaKey: m.key, channelName: m.name, title: p.title, start: p.start, stop: p.stop };
    r.alarmId = scheduleSystemAlarm(r.id, p.start - 60000);
    store.reminders.set(list.concat([r]));
    toast(t('reminder_set'));
  }
}
