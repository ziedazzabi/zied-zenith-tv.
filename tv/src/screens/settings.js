import { h, add, clear } from '../util.js';
import { icon } from '../icons.js';
import { t, setLang } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, settingRow, toggleRow, sectionTitle, choiceDialog, pinDialog, toast, dialog, field, button, spinner } from '../ui.js';
import { THEMES, WP, activeWallpaper, createWallpaper, applyTheme } from '../themes.js';
import { isTizen, platformName } from '../platform.js';
import { shell } from './common.js';
import { Focus } from '../focus.js';

export const APP_VERSION = '1.0.0';

export function themeName(id) {
  return t({ royal: 'theme_royal', royal_night: 'theme_royal_night', cinema: 'theme_cinema', stadium: 'theme_stadium', sunset: 'theme_sunset', amoled: 'theme_amoled' }[id] || 'theme_midnight');
}

export function SettingsScreen(params, nav) {
  const sh = shell(t('settings'), nav, { cls: 'settings' });
  const list = h('div', { class: 'slist scroll' });
  sh.body.appendChild(list);

  function draw() {
    const keepIdx = Focus.current && list.contains(Focus.current) ? Array.prototype.indexOf.call(list.querySelectorAll('.f'), Focus.current) : -1;
    clear(list);
    const s = store.settings.get();
    const up = (fn) => { store.updateSettings(fn); draw(); };
    const off = t('off');
    const w = activeWallpaper(s);

    add(list, sectionTitle(t('appearance'), 'palette'),
      settingRow('palette', t('theme'), w ? w.name : themeName(s.theme), () => themePicker(draw)));
    if (w) {
      const opts = [['0.3', t('dim_light')], ['0.45', t('dim_medium')], ['0.6', t('dim_strong')], ['0.75', t('dim_max')]];
      const cur = opts.reduce((a, b) => (Math.abs(+b[0] - w.dim) < Math.abs(+a[0] - w.dim) ? b : a))[0];
      list.appendChild(settingRow('brightness', t('dimming'), opts.find((o) => o[0] === cur)[1], () => choiceDialog(t('dimming'), opts, cur, (v) =>
        up((st) => ({ wallpapers: st.wallpapers.map((x) => (x.id === w.id ? Object.assign({}, x, { dim: +v }) : x)) })))));
    }
    const sizes = [['0.85', t('size_small')], ['1', t('size_normal')], ['1.15', t('size_large')], ['1.3', t('size_xlarge')]];
    const ops = [100, 90, 85, 75, 65, 50, 40].map((n) => [String(n), n + '%']);
    list.appendChild(settingRow('eye', t('panel_opacity'), (s.panelOpacity || 85) + '%', () => choiceDialog(t('panel_opacity'), ops, String(s.panelOpacity || 85), (v) => up(() => ({ panelOpacity: +v })))));
    list.appendChild(settingRow('size', t('ui_size'), (sizes.find((x) => +x[0] === s.uiScale) || ['', Math.round(s.uiScale * 100) + '%'])[1],
      () => choiceDialog(t('ui_size'), sizes, String(s.uiScale), (v) => up(() => ({ uiScale: +v })))));

    const langs = [['', t('lang_system')], ['en', 'English'], ['ar', 'العربية'], ['fr', 'Français']];
    add(list, sectionTitle(t('general'), 'settings'),
      settingRow('globe', t('language'), (langs.find((l) => l[0] === s.language) || langs[0])[1], () => choiceDialog(t('language'), langs, s.language, (v) => {
        store.updateSettings(() => ({ language: v })); setLang(v); applyTheme(); nav.home();
      })),
      settingRow('playlist', t('playlists'), (repo.activePlaylist() || {}).name || '', () => nav.go('playlists')),
      settingRow('person', t('profiles'), store.activeProfile().name, () => nav.go('profiles', {})));

    add(list, sectionTitle(t('parental_control'), 'lock'),
      settingRow('key', t('change_pin'), '••••', changePin),
      toggleRow('lock', t('auto_lock_adult'), s.autoLockAdult, (v) => store.updateSettings(() => ({ autoLockAdult: v }))),
      settingRow('lock', t('locked_categories'), String((s.lockedCategories || []).length), () => toast(t('lock_hint'), 5000)));
    if ((s.hiddenCategories || []).length) list.appendChild(settingRow('eye', t('show_hidden_categories'), String(s.hiddenCategories.length), () => up(() => ({ hiddenCategories: [] }))));

    list.appendChild(sectionTitle(t('player'), 'play'));
    const fmts = [['m3u8', 'HLS (.m3u8)'], ['ts', 'MPEG-TS (.ts)']];
    list.appendChild(settingRow('live', t('live_format'), (fmts.find((f) => f[0] === s.liveFormat) || fmts[0])[1], () => choiceDialog(t('live_format'), fmts, s.liveFormat, (v) => { up(() => ({ liveFormat: v })); repo.reload(); })));
    const bufs = [3, 5, 10, 15, 30].map((n) => [String(n), n + ' s']);
    list.appendChild(settingRow('timer', t('buffer_size'), s.bufferSeconds + ' s', () => choiceDialog(t('buffer_size'), bufs, String(s.bufferSeconds), (v) => up(() => ({ bufferSeconds: +v })))));
    const asp = [['fit', t('aspect_fit')], ['fill', t('aspect_fill')], ['zoom', t('aspect_zoom')]];
    list.appendChild(settingRow('aspect', t('aspect_ratio'), (asp.find((a) => a[0] === s.aspect) || asp[0])[1], () => choiceDialog(t('aspect_ratio'), asp, s.aspect, (v) => up(() => ({ aspect: v })))));
    if (isTizen) {
      const eng = [['auto', t('engine_auto')], ['html5', t('engine_html5')], ['native', t('engine_native')]];
      list.appendChild(settingRow('tv', t('player_engine'), (eng.find((e) => e[0] === s.engine) || eng[0])[1], () => choiceDialog(t('player_engine'), eng, s.engine, (v) => up(() => ({ engine: v })))));
    }
    list.appendChild(toggleRow('pip', t('mini_player'), s.miniPlayer, (v) => store.updateSettings(() => ({ miniPlayer: v }))));

    list.appendChild(sectionTitle(t('tv_guide'), 'guide'));
    const offs = []; for (let i = -12; i <= 12; i++) offs.push([String(i * 30), i === 0 ? off : (i > 0 ? '+' : '') + (i / 2).toFixed(1) + ' h']);
    add(list,
      settingRow('clock', t('epg_offset'), (offs.find((o) => +o[0] === s.epgOffsetMinutes) || offs[12])[1], () => choiceDialog(t('epg_offset'), offs, String(s.epgOffsetMinutes), (v) => { up(() => ({ epgOffsetMinutes: +v })); repo.loadEpg(true); })),
      toggleRow('clock', t('clock_24h'), s.use24h, (v) => store.updateSettings(() => ({ use24h: v }))),
      settingRow('refresh', t('reload_epg'), '', () => { repo.loadEpg(true); toast(t('loading_epg')); }),
      settingRow('refresh', t('auto_refresh'), s.catalogMaxAgeHours + ' h', () => choiceDialog(t('auto_refresh'), [6, 12, 24, 72].map((n) => [String(n), n + ' h']), String(s.catalogMaxAgeHours), (v) => up(() => ({ catalogMaxAgeHours: +v })))));

    list.appendChild(sectionTitle(t('about'), 'info'));
    if (repo.catalog && repo.catalog.accountInfo) list.appendChild(settingRow('person', t('account'), repo.catalog.accountInfo, () => {}));
    add(list,
      settingRow('heart', t('support_app'), 'PayPal · Neteller', () => nav.go('about')),
      settingRow('info', t('about_app'), t('idea_dev') + ': Zied Azzabi · ' + APP_VERSION + ' · ' + platformName, () => nav.go('about')),
      settingRow('person', t('contact'), 'zied.azzabi@gmail.com', () => nav.go('about')),
      settingRow('tv', t('remote_help'), '', () => helpDialog()));

    const all = list.querySelectorAll('.f');
    if (keepIdx >= 0 && all[keepIdx]) Focus.set(all[keepIdx]);
  }

  function changePin() {
    pinDialog(t('current_pin'), (pin) => {
      if (pin !== store.settings.get().parentalPin) { toast(t('wrong_pin')); return; }
      pinDialog(t('new_pin'), (np) => { store.updateSettings(() => ({ parentalPin: np })); toast(t('pin_changed')); });
    }, store.settings.get().parentalPin.length);
  }

  draw();
  return { el: sh.el, destroy: sh.destroy, onShow(fresh) { if (!fresh) draw(); } };
}

export function helpDialog() {
  dialog({
    title: t('remote_help'),
    body: ['help_ok', 'help_updown', 'help_right', 'help_back', 'help_long', 'help_colors'].map((k) => h('div', { class: 'help-line', text: t(k) })),
    buttons: [[t('close'), null]],
  });
}

/** Theme picker: colour themes + themes made from a picture link. */
export function themePicker(onDone) {
  const s = store.settings.get();
  const pick = (id) => { store.updateSettings(() => ({ theme: id })); d.close(); onDone && onDone(); };
  const cards = [];
  const pics = (s.wallpapers || []).map((w) => fz(h('div', { class: 'theme-card' + (s.theme === WP + w.id ? ' sel' : '') },
    h('div', { class: 'theme-prev pic', style: 'background-image:url("' + w.url.replace(/"/g, '%22') + '")' },
      h('div', { class: 'swatches' }, [w.base, w.accent, w.accent2].map((c) => h('span', { style: 'background:' + c })))),
    h('div', { class: 'theme-name', text: w.name })), {
    click: () => pick(WP + w.id),
    long: () => dialog({ title: w.name, buttons: [[t('delete'), () => {
      store.updateSettings((st) => ({ wallpapers: st.wallpapers.filter((x) => x.id !== w.id), theme: st.theme === WP + w.id ? 'midnight' : st.theme }));
      d.close(); themePicker(onDone);
    }, { danger: true, icon: 'trash' }], [t('cancel'), null]] }),
  }));
  const addCard = fz(h('div', { class: 'theme-card add' }, h('div', { class: 'theme-prev' }, icon('link')), h('div', { class: 'theme-name', text: t('add_from_link') })), { click: () => addPicture(() => { d.close(); themePicker(onDone); }) });
  THEMES.forEach((th) => cards.push(fz(h('div', { class: 'theme-card' + (s.theme === th.id ? ' sel' : '') },
    h('div', { class: 'theme-prev', style: 'background:linear-gradient(' + th.glow + ',' + th.bg + ')' },
      h('div', { class: 'tp-bar', style: 'background:' + th.text }), h('div', { class: 'tp-dot', style: 'background:' + th.accent }),
      h('div', { class: 'tp-tiles' }, th.tiles.slice(0, 3).map((c) => h('span', { style: 'background:' + c })))),
    h('div', { class: 'theme-name', text: themeName(th.id) })), { click: () => pick(th.id), auto: s.theme === th.id })));
  const d = dialog({
    title: t('theme'), wide: true, cls: 'theme-dlg',
    body: [h('div', { class: 'dlg-sub', text: t('my_pictures') }), h('div', { class: 'muted small', text: t('my_pictures_hint') }),
      h('div', { class: 'theme-grid fgroup' }, addCard, pics),
      pics.length ? h('div', { class: 'muted small', text: t('delete_hint') }) : null,
      h('div', { class: 'dlg-sub', text: t('color_themes') }), h('div', { class: 'theme-grid fgroup' }, cards)],
    buttons: [[t('close'), null]], focusBody: true,
  });
}

function addPicture(done) {
  let url = '', name = '';
  const status = h('div', { class: 'muted small' });
  const fu = field(t('image_link'), '', { onInput: (v) => { url = v.trim(); }, dir: 'ltr' });
  const fn = field(t('name'), '', { onInput: (v) => { name = v; } });
  const dd = dialog({
    title: t('add_from_link'),
    body: [fu, fn, h('div', { class: 'muted small', text: t('pictures_tv_hint') }), status],
    buttons: [[t('save'), () => {
      if (!/^https?:\/\//i.test(url)) { toast(t('image_link')); return true; }
      clear(status); add(status, spinner());
      createWallpaper(url, name || t('my_picture') + ' ' + ((store.settings.get().wallpapers || []).length + 1)).then((w) => {
        store.updateSettings((st) => ({ wallpapers: (st.wallpapers || []).concat([w]), theme: WP + w.id }));
        toast(t('wallpaper_added'));
        dd.close();
        done();
      }).catch((e) => { clear(status); status.textContent = t('wallpaper_failed') + ': ' + e.message; });
      return true;
    }, { primary: true }], [t('cancel'), null]],
  });
}
