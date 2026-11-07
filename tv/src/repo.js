// Catalog + EPG state for the active playlist, with caching in IndexedDB.
import { store, db } from './store.js';
import { createSource } from './sources/sources.js';
import { loadXmltv } from './epg.js';
import { norm } from './util.js';

const ADULT = /(adult|xxx|18\+|\+18|porn|erotic|sex|للكبار|بالغين)/i;

class Emitter {
  constructor() { this.m = {}; }
  on(ev, fn) { (this.m[ev] = this.m[ev] || new Set()).add(fn); return () => this.m[ev].delete(fn); }
  emit(ev, a) { (this.m[ev] || []).forEach((f) => { try { f(a); } catch (e) { console.error(e); } }); }
}

class Repo extends Emitter {
  constructor() {
    super();
    this.state = { status: 'idle', message: '' };
    this.catalog = null;
    this.source = null;
    this.playlist = null;
    this.epg = new Map();
    this.shortEpg = new Map();
    this.shortEpgAt = new Map();
    this.details = new Map();
    this.unlocked = new Set();
    this.byKey = new Map();
    this.byCat = new Map();
    this.loadingEpg = false;
    setInterval(() => this.emit('tick'), 30000);
  }

  setState(status, message) { this.state = { status, message: message || '' }; this.emit('state', this.state); }

  activePlaylist() {
    const s = store.settings.get();
    const list = store.playlists.get();
    return list.find((p) => p.id === s.activePlaylistId) || list[0] || null;
  }

  async open(pl, force) {
    if (!pl) { this.catalog = null; this.setState('idle'); this.emit('catalog'); return; }
    const switching = !this.playlist || this.playlist.id !== pl.id;
    this.playlist = pl;
    this.source = createSource(pl, store.settings.get());
    if (switching) {
      this.catalog = null; this.epg = new Map(); this.shortEpg.clear(); this.details.clear(); this.unlocked.clear();
      this.emit('catalog');
      const cached = await db.get('catalog', pl.id);
      if (cached && cached.items) { this.setCatalog(cached); }
    }
    const maxAge = (store.settings.get().catalogMaxAgeHours || 12) * 3600000;
    const stale = !this.catalog || force || !this.catalog.at || Date.now() - this.catalog.at > maxAge;
    if (stale) await this.refresh();
    else this.setState('ready');
    this.loadEpg(false);
  }

  async refresh() {
    const pl = this.playlist;
    if (!pl) return;
    this.setState('loading', pl.name);
    try {
      const cat = await this.source.load((m) => this.setState('loading', pl.name + ' ' + m));
      if (this.playlist !== pl) return;
      cat.at = Date.now();
      this.setCatalog(cat);
      this.setState('ready');
      db.put('catalog', pl.id, cat);
    } catch (e) {
      console.error(e);
      if (this.catalog) this.setState('ready', e.message);
      else this.setState('error', e.message || String(e));
    }
  }

  reload() { const pl = this.activePlaylist(); if (pl) { this.playlist = null; this.open(pl, true); } }

  setCatalog(cat) {
    this.catalog = cat;
    this.byKey = new Map();
    this.byCat = new Map();
    for (const it of cat.items) {
      this.byKey.set(it.key, it);
      const k = it.kind + '|' + it.cat;
      let l = this.byCat.get(k); if (!l) { l = []; this.byCat.set(k, l); } l.push(it);
    }
    this.catIndex = new Map();
    for (const c of cat.categories) this.catIndex.set(c.kind + '|' + c.id, c);
    this._channels = null;
    this.emit('catalog');
  }

  get(key) { return this.byKey.get(key) || null; }

  channels() {
    if (!this._channels) this._channels = (this.catalog ? this.catalog.items.filter((i) => i.kind === 'live') : []).sort((a, b) => a.num - b.num);
    return this._channels;
  }

  categoryKey(c) { return (this.playlist ? this.playlist.id : '') + '|' + c.kind + '|' + c.id; }
  isAdult(c) { return ADULT.test(c.name); }

  /** Visible categories for a kind (hidden ones removed, adult ones removed for kids profiles). */
  categories(kind) {
    if (!this.catalog) return [];
    const s = store.settings.get();
    const kids = store.activeProfile().kids;
    const hidden = new Set(s.hiddenCategories || []);
    return this.catalog.categories.filter((c) => c.kind === kind && !hidden.has(this.categoryKey(c)) && !(kids && this.isAdult(c)));
  }

  categoryOf(m) { return this.catIndex ? this.catIndex.get(m.kind + '|' + m.cat) || null : null; }

  isLocked(c) {
    if (!c) return false;
    const k = this.categoryKey(c);
    if (this.unlocked.has(k)) return false;
    const s = store.settings.get();
    return (s.lockedCategories || []).indexOf(k) >= 0 || (s.autoLockAdult && this.isAdult(c));
  }
  unlock(c) { this.unlocked.add(this.categoryKey(c)); }
  /** Locked or hidden category (even when unlocked for this session): kept out of "Recently watched" etc. */
  isRestricted(m) {
    const c = m && this.categoryOf(m);
    if (!c) return false;
    const s = store.settings.get(), k = this.categoryKey(c);
    return (s.lockedCategories || []).indexOf(k) >= 0 || (s.autoLockAdult && this.isAdult(c)) || (s.hiddenCategories || []).indexOf(k) >= 0;
  }

  toggleLock(c) {
    const k = this.categoryKey(c);
    store.updateSettings((s) => {
      const l = (s.lockedCategories || []).slice(); const i = l.indexOf(k);
      if (i >= 0) l.splice(i, 1); else l.push(k);
      return { lockedCategories: l };
    });
  }
  hide(c) { const k = this.categoryKey(c); store.updateSettings((s) => ({ hiddenCategories: (s.hiddenCategories || []).concat([k]) })); }

  items(kind, catId) {
    if (!this.catalog) return [];
    if (catId == null) {
      const cats = new Set(this.categories(kind).filter((c) => !this.isLocked(c)).map((c) => c.id));
      const all = kind === 'live' ? this.channels() : this.catalog.items.filter((i) => i.kind === kind);
      return all.filter((i) => cats.has(i.cat) || !this.catalog.categories.some((c) => c.kind === kind && c.id === i.cat));
    }
    const l = this.byCat.get(kind + '|' + catId) || [];
    return kind === 'live' ? l.slice().sort((a, b) => a.num - b.num) : l;
  }

  async loadLazy(cat) {
    if (!cat.lazy || !this.source.loadCategory || cat._loaded) return;
    const all = [];
    for (let p = 1; p <= 15; p++) {
      const page = await this.source.loadCategory(cat, p).catch(() => []);
      if (!page.length) break;
      all.push(...page);
      if (page.length < 10) break;
    }
    cat._loaded = true;
    const keys = new Set(all.map((i) => i.key));
    this.catalog.items = this.catalog.items.filter((i) => !keys.has(i.key)).concat(all);
    this.setCatalog(this.catalog);
  }

  search(q) {
    q = norm(q).trim();
    if (q.length < 2 || !this.catalog) return { live: [], movie: [], series: [] };
    const res = { live: [], movie: [], series: [] };
    const kids = store.activeProfile().kids;
    const okCat = new Map();
    const allowed = (m) => {
      const k = m.kind + '|' + m.cat;
      if (!okCat.has(k)) { const c = this.categoryOf(m); okCat.set(k, !c || (!this.isLocked(c) && !(kids && this.isAdult(c)))); }
      return okCat.get(k);
    };
    for (const it of this.catalog.items) {
      const r = res[it.kind];
      if (r.length >= 150) continue;
      if (norm(it.name).indexOf(q) >= 0 && allowed(it)) r.push(it);
    }
    return res;
  }

  // ---------------- EPG ----------------
  async loadEpg(force) {
    const pl = this.playlist, cat = this.catalog;
    if (!pl || !cat || this.loadingEpg) return;
    const url = pl.epgUrl || cat.epgUrl;
    if (!url) return;
    if (!force) {
      const cached = await db.get('epg', pl.id);
      if (cached && cached.url === url && Date.now() - cached.at < 12 * 3600000) {
        this.epg = new Map(cached.entries); this.emit('epg'); return;
      }
    }
    this.loadingEpg = true; this.emit('epgLoading', true);
    try {
      const map = await loadXmltv(url, this.channels(), store.settings.get().epgOffsetMinutes);
      if (this.playlist !== pl) return;
      this.epg = map;
      this.emit('epg');
      db.put('epg', pl.id, { url, at: Date.now(), entries: Array.from(map.entries()) });
    } catch (e) { console.warn('EPG failed', e); }
    finally { this.loadingEpg = false; this.emit('epgLoading', false); }
  }

  programs(m) {
    if (!m || m.kind !== 'live') return [];
    return this.epg.get((m.epg || '').toLowerCase()) || this.epg.get(m.name.toLowerCase()) || this.shortEpg.get(m.key) || [];
  }

  nowNext(m) {
    const list = this.programs(m), now = Date.now();
    for (let i = 0; i < list.length; i++) if (list[i].start <= now && list[i].stop > now) return [list[i], list[i + 1] || null];
    const nx = list.find((p) => p.start > now);
    return [null, nx || null];
  }

  /** Fetches the server's short EPG for a channel when the XMLTV guide has nothing for it. */
  requestShortEpg(m) {
    if (!m || m.kind !== 'live' || !this.source || !this.source.shortEpg) return;
    if (this.epg.get((m.epg || '').toLowerCase())) return;
    const at = this.shortEpgAt.get(m.key) || 0;
    if (Date.now() - at < 15 * 60000) return;
    this.shortEpgAt.set(m.key, Date.now());
    this.source.shortEpg(m).then((list) => { if (list.length) { this.shortEpg.set(m.key, list); this.emit('epg'); } }).catch(() => {});
  }

  async getDetails(m) {
    if (this.details.has(m.key)) return this.details.get(m.key);
    const d = await this.source.details(m);
    this.details.set(m.key, d);
    return d;
  }

  resolve(m) { return this.source.resolve(m); }
  resolveEpisode(series, ep) { return this.source.resolveEpisode(series, ep); }
  catchup(m, p) { return this.source.catchupUrl(m, p.start, p.stop); }
}

export const repo = new Repo();
