import { h, add, clear, uid } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, button, chip, field, dialog, confirmDialog, toast } from '../ui.js';
import { parseXtreamLink } from '../sources/sources.js';
import { shell } from './common.js';
import { Focus } from '../focus.js';

const TYPES = [['xtream', 'xtream'], ['m3u', 'm3u_url'], ['stalker', 'mac_portal']];

export function PlaylistsScreen(params, nav) {
  const sh = shell(t('playlists'), nav, { actions: button(t('add_playlist'), 'add', () => nav.go('addPlaylist')) });
  const list = h('div', { class: 'pl-list scroll' });
  sh.body.appendChild(list);

  function draw() {
    clear(list);
    const active = repo.activePlaylist();
    const pls = store.playlists.get();
    if (!pls.length) list.appendChild(h('div', { class: 'gate' }, h('div', { class: 'muted', text: t('no_playlist') }), button(t('add_playlist'), 'add', () => nav.go('addPlaylist'), { primary: true, auto: true })));
    pls.forEach((p) => {
      const isActive = active && active.id === p.id;
      list.appendChild(fz(h('div', { class: 'pl-row' + (isActive ? ' sel' : '') },
        icon(p.type === 'xtream' ? 'tv' : p.type === 'stalker' ? 'key' : 'playlist'),
        h('div', { class: 'grow' }, h('div', { class: 'pl-name', text: p.name }), h('div', { class: 'muted small', text: t(TYPES.find((x) => x[0] === p.type)[1]) + ' · ' + (p.url || '').replace(/^https?:\/\//, '').slice(0, 50) })),
        isActive ? icon('check') : null), {
        click: () => {
          store.updateSettings(() => ({ activePlaylistId: p.id }));
          repo.open(p, false);
          nav.home();
        },
        long: () => dialog({
          title: p.name,
          buttons: [
            [t('edit'), () => nav.go('addPlaylist', { id: p.id }), { icon: 'edit' }],
            [t('refresh'), () => { if (isActive) repo.reload(); else { store.updateSettings(() => ({ activePlaylistId: p.id })); repo.open(p, true); } nav.home(); }, { icon: 'refresh' }],
            [t('delete'), () => confirmDialog(t('delete_playlist_q'), p.name, t('delete'), () => { store.deletePlaylist(p.id); if (isActive) { repo.playlist = null; repo.open(repo.activePlaylist()); } draw(); Focus.ensure(); }, true), { danger: true, icon: 'trash' }],
            [t('cancel'), null],
          ],
        }),
        auto: isActive,
      }));
    });
    list.appendChild(h('div', { class: 'muted small pad', text: t('playlist_hint') }));
  }
  draw();
  return { el: sh.el, destroy: sh.destroy, onShow(fresh) { if (!fresh) draw(); } };
}

// Custom edition: servers are built in (custom/preset.json), users only sign in.
/* global __PRESET__ */
const PRESET = (function () {
  try {
    const p = typeof __PRESET__ !== 'undefined' ? __PRESET__ : null;
    if (!p || !p.servers) return null;
    p.servers = p.servers.filter((s) => s && s.url);
    return p.servers.length ? p : null;
  } catch (e) { return null; }
})();
const normUrl = (u) => { u = String(u || '').trim().replace(/\/+$/, ''); return /:\/\//.test(u) ? u : 'http://' + u; };
function presetIndex(pl) {
  if (!pl) return 0;
  return PRESET.servers.findIndex((s) => {
    const u = normUrl(s.url);
    return (pl.url || '').replace(/\/+$/, '').toLowerCase() === u.toLowerCase() || (s.type === 'm3u' && (pl.url || '').indexOf(u.split('{')[0]) === 0);
  });
}

function PresetLogin(params, nav, existing, onAdvanced) {
  const sh = shell(t('login_title'), nav, { noBack: !!params.first && !store.playlists.get().length });
  const form = h('div', { class: 'form scroll' });
  sh.body.appendChild(form);
  let server = Math.max(0, presetIndex(existing));
  const cred = { username: existing ? existing.username : '', password: existing ? existing.password : '' };

  function draw() {
    clear(form);
    add(form, h('div', { class: 'welcome' }, h('img', { class: 'welcome-logo', src: 'img/logo.png', alt: '' }),
      PRESET.providerName ? h('div', { class: 'pl-name', text: PRESET.providerName }) : null,
      h('div', { class: 'muted', text: t('login_hint') })));
    if (PRESET.servers.length > 1) {
      form.appendChild(h('div', { class: 'muted small', text: t('choose_server') }));
      form.appendChild(h('div', { class: 'hrow fgroup types' }, PRESET.servers.map((s, i) => chip(s.name || t('choose_server') + ' ' + (i + 1), server === i, () => { server = i; draw(); }))));
    }
    form.appendChild(field(t('username'), cred.username, { dir: 'ltr', onInput: (v) => { cred.username = v; } }));
    form.appendChild(field(t('password'), cred.password, { dir: 'ltr', onInput: (v) => { cred.password = v; } }));
    form.appendChild(h('div', { class: 'row gap' }, button(t('sign_in'), 'check', signIn, { primary: true, auto: !existing }),
      onAdvanced ? button(t('add_playlist'), 'add', onAdvanced) : null,
      existing ? button(t('cancel'), 'close', () => nav.back()) : null));
  }

  function signIn() {
    const u = (cred.username || '').trim(), pw = (cred.password || '').trim();
    if (!u || !pw) { toast(t('fill_required')); return; }
    const s = PRESET.servers[server] || PRESET.servers[0];
    const m3u = s.type === 'm3u';
    const url = m3u ? normUrl(s.url).replace('{username}', encodeURIComponent(u)).replace('{password}', encodeURIComponent(pw)) : normUrl(s.url);
    const name = [PRESET.providerName, s.name].filter(Boolean).filter((x, i, a) => a.indexOf(x) === i).join(' · ') || u;
    const pl = Object.assign({}, existing || { id: uid() }, { type: m3u ? 'm3u' : 'xtream', name, url, username: u, password: pw, mac: '', epgUrl: s.epg || (existing && existing.epgUrl) || '' });
    store.savePlaylist(pl);
    store.updateSettings(() => ({ activePlaylistId: pl.id }));
    repo.playlist = null;
    repo.open(pl, true);
    nav.home();
  }

  draw();
  return { el: sh.el, destroy: sh.destroy };
}

export function AddPlaylistScreen(params, nav) {
  const existing = params.id ? store.playlists.get().find((p) => p.id === params.id) : null;
  if (PRESET && !params.advanced && (!existing || presetIndex(existing) >= 0)) {
    return PresetLogin(params, nav, existing, PRESET.allowOtherSources ? () => nav.go('addPlaylist', { advanced: true }) : null);
  }
  const sh = shell(existing ? t('edit_playlist') : t('add_playlist'), nav, { noBack: !!params.first && !store.playlists.get().length });
  const pl = Object.assign({ id: uid(), type: 'xtream', name: '', url: '', username: '', password: '', mac: '', epgUrl: '' }, existing || {});
  const form = h('div', { class: 'form scroll' });
  sh.body.appendChild(form);

  function draw() {
    clear(form);
    if (params.first && !store.playlists.get().length) add(form, h('div', { class: 'welcome' }, h('img', { class: 'welcome-logo', src: 'img/logo.png', alt: '' }), h('div', { class: 'muted', text: t('welcome_sub') })));
    form.appendChild(h('div', { class: 'hrow fgroup types' }, TYPES.map(([k, l]) => chip(t(l), pl.type === k, () => { pl.type = k; draw(); }))));
    const f = (label, key, o) => field(label, pl[key], Object.assign({ onInput: (v) => { pl[key] = v; } }, o || {}));
    form.appendChild(f(t('name'), 'name'));
    if (pl.type === 'xtream') {
      form.appendChild(f(t('server_url'), 'url', { dir: 'ltr', placeholder: 'http://example.com:8080', onInput: (v) => {
        pl.url = v;
        const parsed = parseXtreamLink(v);
        if (parsed) { pl.url = parsed.url; pl.username = parsed.username; pl.password = parsed.password; draw(); toast(t('link_split')); }
      } }));
      form.appendChild(f(t('username'), 'username', { dir: 'ltr' }));
      form.appendChild(f(t('password'), 'password', { dir: 'ltr' }));
    } else if (pl.type === 'm3u') {
      form.appendChild(f(t('m3u_url'), 'url', { dir: 'ltr', placeholder: 'http://…/playlist.m3u' }));
    } else {
      form.appendChild(f(t('portal_url'), 'url', { dir: 'ltr', placeholder: 'http://portal.example.com/c/' }));
      form.appendChild(f(t('mac_address'), 'mac', { dir: 'ltr', placeholder: '00:1A:79:XX:XX:XX' }));
      form.appendChild(h('div', { class: 'muted small', text: t('mac_warning') }));
    }
    form.appendChild(f(t('epg_url_optional'), 'epgUrl', { dir: 'ltr' }));
    form.appendChild(h('div', { class: 'row gap' }, button(t('save_and_load'), 'check', save, { primary: true }), existing ? button(t('cancel'), 'close', () => nav.back()) : null));
  }

  function save() {
    pl.url = (pl.url || '').trim();
    if (!pl.url || (pl.type === 'xtream' && (!pl.username || !pl.password)) || (pl.type === 'stalker' && !pl.mac)) { toast(t('fill_required')); return; }
    if (!pl.name) pl.name = pl.type === 'xtream' ? pl.url.replace(/^https?:\/\//, '').split(/[:/]/)[0] : pl.type === 'stalker' ? 'MAC ' + pl.mac.slice(-5) : 'M3U';
    store.savePlaylist(pl);
    store.updateSettings(() => ({ activePlaylistId: pl.id }));
    repo.playlist = null;
    repo.open(pl, true);
    nav.home();
  }

  draw();
  return { el: sh.el, destroy: sh.destroy };
}
