// Persistent app state. Small things in localStorage, big catalogs/EPG in IndexedDB.
import { uid } from './util.js';

const LS_PREFIX = 'zenith.';

function load(key, def) {
  try { const v = localStorage.getItem(LS_PREFIX + key); return v ? JSON.parse(v) : def; } catch (e) { return def; }
}
function save(key, v) {
  try { localStorage.setItem(LS_PREFIX + key, JSON.stringify(v)); } catch (e) { console.warn('save failed', key, e); }
}

export const DEFAULT_SETTINGS = {
  language: '',
  activePlaylistId: '',
  activeProfileId: '',
  liveFormat: 'm3u8',
  bufferSeconds: 10,
  aspect: 'fit',
  parentalPin: '0000',
  lockedCategories: [],
  hiddenCategories: [],
  autoLockAdult: true,
  epgOffsetMinutes: 0,
  use24h: true,
  autoPlayLastChannel: false,
  catalogMaxAgeHours: 12,
  theme: 'midnight',
  uiScale: 1,
  wallpapers: [],
  pinnedApps: null,
  miniPlayer: true,
  panelOpacity: 85,
  engine: 'auto',
};

const PROFILE_COLORS = [0xFF22D3EE, 0xFFF472B6, 0xFFFBBF24, 0xFF34D399, 0xFFA78BFA, 0xFFF87171];

/** Very small observable value. */
class Box {
  constructor(key, def) { this.key = key; this.value = load(key, def); this.subs = new Set(); }
  get() { return this.value; }
  set(v) { this.value = v; save(this.key, v); this.subs.forEach((f) => { try { f(v); } catch (e) { console.error(e); } }); }
  update(fn) { this.set(fn(this.value)); }
  subscribe(fn) { this.subs.add(fn); return () => this.subs.delete(fn); }
}

class Store {
  constructor() {
    this.playlists = new Box('playlists', []);
    this.settings = new Box('settings', DEFAULT_SETTINGS);
    this.settings.value = Object.assign({}, DEFAULT_SETTINGS, this.settings.value);
    this.profiles = new Box('profiles', []);
    if (!this.profiles.value.length) this.profiles.set([{ id: 'p1', name: 'Me', color: PROFILE_COLORS[0], kids: false }]);
    this.reminders = new Box('reminders', []);
    this.profileData = new Box('pd.' + this.activeProfileId(), emptyData());
  }

  updateSettings(fn) { this.settings.set(Object.assign({}, this.settings.value, fn(this.settings.value))); }

  activeProfileId() {
    const id = this.settings.value.activeProfileId;
    const ps = this.profiles.value;
    return ps.some((p) => p.id === id) ? id : ps[0].id;
  }
  activeProfile() { const id = this.activeProfileId(); return this.profiles.value.find((p) => p.id === id) || this.profiles.value[0]; }

  switchProfile(id) {
    this.updateSettings(() => ({ activeProfileId: id }));
    const next = new Box('pd.' + id, emptyData());
    // keep subscribers
    next.subs = this.profileData.subs;
    this.profileData = next;
    next.set(Object.assign(emptyData(), next.value));
  }

  addProfile(name, kids) {
    const p = { id: 'p' + uid(), name, kids: !!kids, color: PROFILE_COLORS[this.profiles.value.length % PROFILE_COLORS.length] };
    this.profiles.set(this.profiles.value.concat([p]));
    return p;
  }
  updateProfile(p) { this.profiles.set(this.profiles.value.map((x) => (x.id === p.id ? p : x))); }
  deleteProfile(id) {
    if (this.profiles.value.length <= 1) return;
    this.profiles.set(this.profiles.value.filter((p) => p.id !== id));
    try { localStorage.removeItem(LS_PREFIX + 'pd.' + id); } catch (e) { /* ignore */ }
    if (this.settings.value.activeProfileId === id) this.switchProfile(this.profiles.value[0].id);
  }

  // ---- favorites / recents / progress ----
  isFavorite(key) { return this.profileData.value.favorites.indexOf(key) >= 0; }
  toggleFavorite(key) {
    let added = false;
    this.profileData.update((d) => {
      const f = d.favorites.slice();
      const i = f.indexOf(key);
      if (i >= 0) f.splice(i, 1); else { f.unshift(key); added = true; }
      return Object.assign({}, d, { favorites: f });
    });
    return added;
  }
  addRecent(key) {
    this.profileData.update((d) => {
      const r = [key].concat(d.recentChannels.filter((k) => k !== key)).slice(0, 30);
      return Object.assign({}, d, { recentChannels: r });
    });
  }
  removeRecent(key) { this.profileData.update((d) => Object.assign({}, d, { recentChannels: d.recentChannels.filter((k) => k !== key) })); }
  clearRecents() { this.profileData.update((d) => Object.assign({}, d, { recentChannels: [] })); }
  /** Removes a movie / series and all its episodes from "Continue watching". */
  removeProgress(key) {
    this.profileData.update((d) => {
      const p = {};
      Object.keys(d.progress).forEach((k) => { if (k !== key && k.indexOf(key + '#') !== 0) p[k] = d.progress[k]; });
      return Object.assign({}, d, { progress: p });
    });
  }
  /** Remembers where to continue a series: episode (season/number) + position. */
  setSeriesPointer(seriesKey, ep, positionMs, durationMs) {
    this.profileData.update((d) => {
      const p = Object.assign({}, d.progress);
      if (!ep) delete p[seriesKey];
      else p[seriesKey] = { key: seriesKey, season: ep.season, number: ep.number, positionMs: positionMs || 0, durationMs: durationMs || 1, updatedAt: Date.now() };
      return Object.assign({}, d, { progress: p });
    });
  }
  markWatched(key) {
    this.profileData.update((d) => {
      const w = (d.watched || []).filter((k) => k !== key).concat([key]).slice(-3000);
      return Object.assign({}, d, { watched: w });
    });
  }
  clearProgress() { this.profileData.update((d) => Object.assign({}, d, { progress: {} })); }

  saveProgress(key, positionMs, durationMs) {
    if (!key || !(durationMs > 0)) return;
    this.profileData.update((d) => {
      const p = Object.assign({}, d.progress);
      if (positionMs > durationMs * 0.95 || positionMs < 15000) delete p[key];
      else p[key] = { key, positionMs, durationMs, updatedAt: Date.now() };
      return Object.assign({}, d, { progress: p });
    });
  }

  // ---- playlists ----
  savePlaylist(pl) {
    const list = this.playlists.value.slice();
    const i = list.findIndex((p) => p.id === pl.id);
    if (i >= 0) list[i] = pl; else list.push(pl);
    this.playlists.set(list);
  }
  deletePlaylist(id) {
    this.playlists.set(this.playlists.value.filter((p) => p.id !== id));
    db.del('catalog', id); db.del('epg', id);
    if (this.settings.value.activePlaylistId === id) this.updateSettings(() => ({ activePlaylistId: (this.playlists.value[0] || {}).id || '' }));
  }
}

function emptyData() { return { favorites: [], recentChannels: [], progress: {} }; }

// ---------- IndexedDB (catalog + EPG caches) ----------
const db = {
  _p: null,
  open() {
    if (this._p) return this._p;
    this._p = new Promise((res, rej) => {
      if (!window.indexedDB) return rej(new Error('no indexedDB'));
      const r = indexedDB.open('zenith', 1);
      r.onupgradeneeded = () => { r.result.createObjectStore('catalog'); r.result.createObjectStore('epg'); };
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    });
    return this._p;
  },
  async get(store, key) {
    try {
      const d = await this.open();
      return await new Promise((res) => {
        const q = d.transaction(store, 'readonly').objectStore(store).get(key);
        q.onsuccess = () => res(q.result || null); q.onerror = () => res(null);
      });
    } catch (e) { return null; }
  },
  async put(store, key, value) {
    try {
      const d = await this.open();
      await new Promise((res) => {
        const tx = d.transaction(store, 'readwrite');
        tx.objectStore(store).put(value, key);
        tx.oncomplete = res; tx.onerror = res; tx.onabort = res;
      });
    } catch (e) { console.warn('idb put failed', e); }
  },
  async del(store, key) {
    try {
      const d = await this.open();
      d.transaction(store, 'readwrite').objectStore(store).delete(key);
    } catch (e) { /* ignore */ }
  },
};

export const store = new Store();
export { db };
