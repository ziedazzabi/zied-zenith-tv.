// App shell: screen stack, global keys, mini player frame, reminders.
import { h, clear, timeText } from './util.js';
import { refreshPreset } from './preset.js';
import { Focus } from './focus.js';
import { t, setLang } from './i18n.js';
import { store } from './store.js';
import { repo } from './repo.js';
import { applyTheme } from './themes.js';
import { registerKeys, keyName, exitApp, isTizen, launchReminderId, onAppVisibility, platformName } from './platform.js';
import { Playback } from './player/playback.js';
import { icon } from './icons.js';
import { dialog, toast, fz } from './ui.js';
import { screens } from './screens/index.js';
import { DLog } from './dlog.js';

const app = {
  stack: [],
  root: null,
  screensEl: null,

  closeDialogs() {
    const ms = document.querySelectorAll('.modal');
    for (let i = 0; i < ms.length; i++) ms[i].remove();
    if (ms.length) Focus.resetLayers(this.root);
  },

  go(name, params) {
    this.closeDialogs();
    const top = this.top();
    if (top) { top.savedFocus = Focus.current; if (top.onHide) top.onHide(); top.el.remove(); }
    const make = screens[name];
    if (!make) { console.error('no screen', name); return; }
    const s = make(params || {}, this);
    s.name = name;
    this.stack.push(s);
    this.mount(s, true);
  },

  replace(name, params) {
    const top = this.stack.pop();
    if (top) { if (top.onHide) top.onHide(); if (top.destroy) top.destroy(); top.el.remove(); }
    const make = screens[name];
    const s = make(params || {}, this); s.name = name;
    this.stack.push(s);
    this.mount(s, true);
  },

  /** Goes back to home, closing everything else. */
  home() {
    this.closeDialogs();
    while (this.stack.length) { const s = this.stack.pop(); if (s.onHide) s.onHide(); if (s.destroy) s.destroy(); s.el.remove(); }
    this.go('home');
  },

  back() {
    if (this.stack.length <= 1) { this.askExit(); return; }
    const s = this.stack.pop();
    if (s.onHide) s.onHide();
    if (s.destroy) s.destroy();
    s.el.remove();
    const prev = this.top();
    this.mount(prev, false);
  },

  top() { return this.stack[this.stack.length - 1] || null; },

  mount(s, fresh) {
    this.screensEl.appendChild(s.el);
    document.documentElement.classList.toggle('in-player', s.name === 'player' || s.name === 'multiview');
    if (s.onShow) s.onShow(fresh);
    requestAnimationFrame(() => {
      if (!fresh && s.savedFocus && s.savedFocus.isConnected) Focus.set(s.savedFocus);
      else if (s.focusFirst) s.focusFirst(); else Focus.first(this.root);
      updateMini();
    });
  },

  askExit() {
    dialog({ title: t('exit_app'), buttons: [[t('exit'), () => { Playback.stop(); exitApp(); }, { primary: true, icon: 'power' }], [t('cancel'), null]] });
  },

  /** Opens any item: channels play, movies & series open their page. */
  open(item, list) {
    if (!item) return;
    if (item.kind === 'live') {
      const l = (list || [item]).filter((x) => x.kind === 'live');
      const items = l.length ? l : [item];
      this.playFull({ items, index: Math.max(0, items.indexOf(item)) });
    } else this.go('details', { key: item.key });
  },

  playFull(req) {
    Playback.play(req, 'full');
    if (!this.top() || this.top().name !== 'player') this.go('player');
  },

  /** Returns to the full-screen player for whatever is playing (mini window / preview). */
  expandPlayer() {
    if (!Playback.active) return;
    Playback.setMode('full');
    if (!this.top() || this.top().name !== 'player') this.go('player');
  },
};

// ---------------- mini player frame ----------------
let miniEl = null;
function updateMini() {
  const show = Playback.active && Playback.mode === 'mini';
  if (!miniEl) return;
  miniEl.classList.toggle('hidden', !show);
  if (show) {
    clear(miniEl._title);
    miniEl._title.appendChild(document.createTextNode(Playback.title || (Playback.item && Playback.item.name) || ''));
    miniEl._spin.classList.toggle('hidden', !Playback.state.buffering);
  }
}

function buildMini() {
  const title = h('div', { class: 'mini-title' });
  const spin = h('div', { class: 'spinner small hidden' });
  const hint = h('div', { class: 'mini-hint', text: 'OK ⤢   ·   ' + t('close') + ': MENU' });
  miniEl = fz(h('div', { id: 'mini-frame', class: 'hidden' }, title, spin, hint,
    h('div', { class: 'mini-btns' },
      fz(h('div', { class: 'mini-b' }, icon('fullscreen')), { click: () => app.expandPlayer() }),
      fz(h('div', { class: 'mini-b' }, icon('close')), { click: () => { Playback.stop(); Focus.ensure(); } }))), {
    click: () => app.expandPlayer(),
    long: () => { Playback.stop(); Focus.ensure(); },
    key: (name) => { if (name === 'menu' || name === 'stop') { Playback.stop(); Focus.ensure(); return true; } return false; },
  });
  miniEl._title = title; miniEl._spin = spin;
  return miniEl;
}

// ---------------- reminders ----------------
function checkReminders() {
  const now = Date.now();
  const list = store.reminders.get();
  const due = list.filter((r) => r.start <= now + 30000 && r.start > now - 5 * 60000 && !r.fired);
  if (!due.length) return;
  store.reminders.set(list.map((r) => (due.indexOf(r) >= 0 ? Object.assign({}, r, { fired: true }) : r)).filter((r) => r.stop > now - 3600000));
  due.forEach(showReminder);
}

function showReminder(r) {
  const item = repo.get(r.mediaKey);
  dialog({
    title: t('reminder_now'),
    body: h('div', {}, h('div', { class: 'big', text: r.title }), h('div', { class: 'muted', text: r.channelName + ' · ' + timeText(r.start, store.settings.get().use24h) })),
    buttons: [[t('watch'), () => { if (item) app.open(item, repo.channels()); }, { primary: true, icon: 'play' }], [t('later'), null]],
  });
}

// ---------------- boot ----------------
// ---------- diagnostics: yellow key shows what the player did (also opens by itself when a channel won't start) ----------
let diagEl = null, diagOn = false, diagTimer = 0;
function drawDiag() { if (diagOn && diagEl) diagEl.textContent = DLog.lines().slice(-24).join('\n') || '…'; }
function setDiag(on) {
  if (!diagEl) { diagEl = h('div', { class: 'diag hidden' }); document.body.appendChild(diagEl); DLog.on(drawDiag); }
  diagOn = on; diagEl.classList.toggle('hidden', !on); drawDiag();
}
Playback.on('state', (s) => {
  clearTimeout(diagTimer);
  if (s.error) setDiag(true);
  else if (s.buffering) diagTimer = setTimeout(() => { if (Playback.state.buffering) setDiag(true); }, 12000);
});
window.addEventListener('error', (e) => { DLog.add('JS error: ' + (e.message || e) + (e.filename ? ' @' + String(e.filename).split('/').pop() + ':' + e.lineno : '')); });
window.addEventListener('unhandledrejection', (e) => { DLog.add('JS promise: ' + (e.reason && (e.reason.message || e.reason))); });

function onKeyDown(e) {
  const name = keyName(e);
  if (name === 'yellow') { e.preventDefault(); setDiag(!diagOn); return; }
  if (name === 'back' && diagOn) { e.preventDefault(); setDiag(false); return; }
  if (!name) return;
  if (name === 'ok') { if (!(document.activeElement && /INPUT/.test(document.activeElement.tagName))) { e.preventDefault(); Focus.okDown(e); } return; }
  if (name !== 'back' || !(document.activeElement && /INPUT/.test(document.activeElement.tagName))) e.preventDefault();
  if (name === 'exit') { Playback.stop(); exitApp(); return; }
  if (!Focus.current || !Focus.current.isConnected) Focus.ensure();
  if (Focus.dispatch(name, e)) return;
  // CH+/CH− inside a list (channels, movies…): jump one page
  if ((name === 'chup' || name === 'chdown') && Focus.current && Focus.current._vlist && !(Playback.active && Playback.mode === 'full')) {
    Focus.current._vlist.page(name === 'chdown' ? 1 : -1); return;
  }
  const top = app.top();
  if (top && top.key && top.key(name)) return;
  // global fallbacks
  switch (name) {
    case 'back': app.back(); break;
    case 'green': case 'guide': app.go('guide'); break;
    case 'yellow': app.go('search'); break;
    case 'blue': app.go('multiview'); break;
    case 'chup': if (Playback.active && Playback.live) Playback.zap(-1); break;
    case 'chdown': if (Playback.active && Playback.live) Playback.zap(1); break;
    case 'play': case 'playpause': if (Playback.active && Playback.mode !== 'full') app.expandPlayer(); break;
    case 'stop': if (Playback.active) { Playback.stop(); Focus.ensure(); } break;
    default: break;
  }
}

function onKeyUp(e) { if (keyName(e) === 'ok') Focus.okUp(); }

export function boot() {
  const s = store.settings.get();
  setLang(s.language);
  document.documentElement.classList.add('pf-' + platformName);
  // older TV browsers: no flexbox gap / CSS grid
  try {
    const probe = document.createElement('div');
    probe.style.cssText = 'display:flex;flex-direction:column;gap:1px;position:absolute;visibility:hidden';
    probe.appendChild(document.createElement('div')); probe.appendChild(document.createElement('div'));
    document.body.appendChild(probe);
    if (probe.scrollHeight !== 1) document.documentElement.classList.add('no-gap');
    probe.remove();
    if (!(window.CSS && CSS.supports && CSS.supports('display', 'grid'))) document.documentElement.classList.add('no-grid');
  } catch (e) { /* ignore */ }
  applyTheme();
  registerKeys();

  const layer = document.getElementById('video-layer');
  Playback.init(layer);
  app.root = document.getElementById('app');
  app.screensEl = document.getElementById('screens');
  app.root.appendChild(buildMini());
  Focus.resetLayers(app.root);
  Focus.installPointer();

  Playback.on('mode', updateMini);
  Playback.on('item', updateMini);
  Playback.on('state', updateMini);

  store.settings.subscribe(() => applyTheme());
  window.addEventListener('resize', () => applyTheme());
  document.addEventListener('keydown', onKeyDown, true);
  document.addEventListener('keyup', onKeyUp, true);

  onAppVisibility((visible) => {
    if (!Playback.active || !Playback.engine) return;
    if (!visible) Playback.engine.pause(); else if (!Playback.state.paused) Playback.engine.play();
  });

  setInterval(checkReminders, 20000);

  const pl = repo.activePlaylist();
  if (!pl) app.go('addPlaylist', { first: true });
  else {
    if (store.profiles.get().length > 1) app.go('profiles', { pick: true }); else app.go('home');
    repo.open(pl);
  }
  // custom edition: newest server addresses (custom/servers.json); reload when the active server moved
  refreshPreset().then((moved) => { if (moved) { repo.playlist = null; repo.open(moved, true); } }).catch(() => {});

  // started by a reminder alarm (Samsung)
  const rid = launchReminderId();
  if (rid) {
    const r = store.reminders.get().find((x) => x.id === rid);
    if (r) {
      const off = repo.on('catalog', () => { const it = repo.get(r.mediaKey); if (it) { off(); app.open(it, repo.channels()); } });
    }
  }
  if (isTizen) document.addEventListener('appcontrol', () => { /* relaunch: nothing special */ });
  window.zenith = { app, repo, store, Playback }; // handy for debugging
}

export { app };
