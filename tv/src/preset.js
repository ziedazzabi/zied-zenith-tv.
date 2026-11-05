// Custom edition: servers built in (custom/preset.json). If preset.remoteUrl is set, the server
// addresses are re-read online at every start, so they can change without a new version of the app.
/* global __PRESET__ */
import { store } from './store.js';

const KEY = 'zenith.presetServers';
const clean = (list) => (Array.isArray(list) ? list : []).filter((s) => s && s.url);

const BUILTIN = (function () {
  try {
    const p = typeof __PRESET__ !== 'undefined' ? __PRESET__ : null;
    if (!p) return null;
    const servers = clean(p.servers);
    return servers.length ? Object.assign({}, p, { servers }) : null;
  } catch (e) { return null; }
})();

function cached() {
  try { const v = JSON.parse(localStorage.getItem(KEY) || 'null'); return clean(v && v.servers); } catch (e) { return []; }
}

let current = BUILTIN ? Object.assign({}, BUILTIN, { servers: cached().length ? cached() : BUILTIN.servers }) : null;

/** The preset with the latest known servers, or null in the standard edition. */
export function getPreset() { return current; }

export const normUrl = (u) => { u = String(u || '').trim().replace(/\/+$/, ''); return /:\/\//.test(u) ? u : 'http://' + u; };

export function serverUrl(s, user, pass) {
  const u = normUrl(s.url);
  return s.type === 'm3u' ? u.replace('{username}', encodeURIComponent(user)).replace('{password}', encodeURIComponent(pass)) : u;
}

function matches(s, pl) {
  return (pl.url || '').replace(/\/+$/, '').toLowerCase() === serverUrl(s, pl.username || '', pl.password || '').toLowerCase();
}

/** Index of the preset server a playlist uses, -1 if none. */
export function presetIndex(pl) {
  if (!current) return -1;
  if (!pl) return 0;
  const n = (pl.presetServer || '').toLowerCase();
  if (n) { const i = current.servers.findIndex((s) => (s.name || '').toLowerCase() === n); if (i >= 0) return i; }
  return current.servers.findIndex((s) => matches(s, pl));
}

export function presetPlaylist(base, s, user, pass) {
  const name = [current.providerName, s.name].filter(Boolean).filter((x, i, a) => a.indexOf(x) === i).join(' · ') || user;
  return Object.assign({}, base, {
    type: s.type === 'm3u' ? 'm3u' : 'xtream', name, url: serverUrl(s, user, pass), username: user, password: pass,
    mac: '', epgUrl: s.epg || base.epgUrl || '', presetServer: s.name || '',
  });
}

let pending = null;
/** Reads the online server list once per start. Resolves to the active playlist when its address changed. */
export function refreshPreset() {
  if (pending) return pending;
  pending = (async () => {
    if (!BUILTIN || !BUILTIN.remoteUrl || typeof fetch !== 'function') return null;
    const sep = BUILTIN.remoteUrl.indexOf('?') >= 0 ? '&' : '?';
    const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = setTimeout(() => ctrl && ctrl.abort(), 10000);
    let servers = [];
    try {
      const r = await fetch(BUILTIN.remoteUrl + sep + 't=' + Math.floor(Date.now() / 60000), { cache: 'no-store', signal: ctrl ? ctrl.signal : undefined });
      if (r.ok) servers = clean((await r.json()).servers);
    } catch (e) { console.warn('preset refresh failed', e); }
    clearTimeout(timer);
    if (!servers.length) return null;
    const known = current.servers.concat(BUILTIN.servers);
    try { localStorage.setItem(KEY, JSON.stringify({ servers })); } catch (e) { /* ignore */ }
    current = Object.assign({}, BUILTIN, { servers });
    // point saved playlists at the new address of their server (matched by name)
    const activeId = store.settings.get().activePlaylistId;
    let changedActive = null;
    store.playlists.get().forEach((pl, i) => {
      let name = pl.presetServer;
      if (!name) { const k = known.find((s) => matches(s, pl)); name = k ? k.name : ''; }
      if (!name) return;
      const s = servers.find((x) => (x.name || '').toLowerCase() === name.toLowerCase());
      if (!s) return;
      const next = Object.assign({}, pl, { type: s.type === 'm3u' ? 'm3u' : 'xtream', url: serverUrl(s, pl.username || '', pl.password || ''), epgUrl: s.epg || pl.epgUrl || '', presetServer: s.name });
      if (next.url !== pl.url || next.presetServer !== pl.presetServer || next.epgUrl !== pl.epgUrl) {
        store.savePlaylist(next);
        if (next.url !== pl.url && (pl.id === activeId || (!activeId && i === 0))) changedActive = next;
      }
    });
    return changedActive;
  })();
  return pending;
}
