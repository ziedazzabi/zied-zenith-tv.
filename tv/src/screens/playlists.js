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

export function AddPlaylistScreen(params, nav) {
  const existing = params.id ? store.playlists.get().find((p) => p.id === params.id) : null;
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
