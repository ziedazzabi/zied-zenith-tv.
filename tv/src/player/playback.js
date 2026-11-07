// The one global player. Screens only change where the video is drawn: full screen, mini window, preview box.
import { Html5Engine, AvplayEngine } from './engine.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { keepScreenOn } from '../platform.js';

class PlaybackService {
  constructor() {
    this.listeners = {};
    this.req = null;
    this.index = 0;
    this.title = '';
    this.url = '';
    this.mode = 'hidden';
    this.rect = null;
    this.state = { buffering: false, error: '', paused: false };
    this.retries = 0;
    this.engine = null;
    this.layer = null;
    this.video = null;
  }

  on(ev, fn) { (this.listeners[ev] = this.listeners[ev] || new Set()).add(fn); return () => this.listeners[ev].delete(fn); }
  emit(ev, a) { (this.listeners[ev] || []).forEach((f) => { try { f(a); } catch (e) { console.error(e); } }); }

  init(layer) {
    this.layer = layer;
    this.video = layer.querySelector('video');
    this.html5 = new Html5Engine(this.video, { bufferSeconds: store.settings.get().bufferSeconds });
    this.wire(this.html5);
    this.avplay = null;
    setInterval(() => this.saveProgress(), 10000);
  }

  wire(engine) {
    engine.on('buffering', (b) => { if (engine === this.engine) this.setState({ buffering: b }); });
    engine.on('playing', () => { if (engine === this.engine) { this.retries = 0; this.setState({ buffering: false, error: '', paused: false }); } });
    engine.on('paused', () => { if (engine === this.engine) this.setState({ paused: true }); });
    engine.on('time', () => { if (engine === this.engine) this.emit('time'); });
    engine.on('tracks', () => { if (engine === this.engine) this.emit('tracks'); });
    engine.on('subtitle', (t) => { if (engine === this.engine) this.emit('subtitle', t); });
    engine.on('ended', () => { if (engine === this.engine) this.onEnded(); });
    engine.on('error', (msg) => { if (engine === this.engine) this.onError(msg); });
    engine.on('audioonly', () => {
      // Samsung: its native player decodes far more video formats than the web player
      if (engine === this.engine && engine === this.html5 && AvplayEngine.available() && this.mode === 'full' && !this.usedAvFallback) {
        this.usedAvFallback = true;
        this.pickEngine(true).load(this.url, { live: this.live, startMs: this.live ? 0 : engine.time });
      }
    });
  }

  setState(p) { this.state = Object.assign({}, this.state, p); this.emit('state', this.state); }

  get active() { return !!this.req; }
  get item() { return this.req ? (this.req.items[this.index] || null) : null; }
  get live() { return !!(this.req && !this.req.url && !this.req.series && this.item && this.item.kind === 'live'); }

  wantAvplay() {
    const s = store.settings.get();
    return this.mode === 'full' && AvplayEngine.available() && s.engine === 'native';
  }

  pickEngine(forceAv) {
    let e = this.html5;
    if ((forceAv || this.wantAvplay()) && AvplayEngine.available() && this.mode === 'full') {
      if (!this.avplay) { this.avplay = new AvplayEngine(); this.wire(this.avplay); }
      e = this.avplay;
    }
    if (this.engine && this.engine !== e) this.engine.stop();
    this.engine = e;
    document.documentElement.classList.toggle('avplay', e.kind === 'avplay');
    e.setAspect(store.settings.get().aspect);
    return e;
  }

  /**
   * req: { items, index, url?, title?, progressKey?, startMs?, series?, episodes?, episodeIndex?, program? }
   */
  async play(req, mode) {
    this.saveProgress();
    this.req = req;
    this.index = req.index || 0;
    this.retries = 0;
    this.usedAvFallback = false;
    if (mode) this.setMode(mode);
    const it = this.item;
    if (req.series && req.episodes && req.episodeIndex >= 0) return this.playEpisode(req.episodeIndex, req.startMs);
    this.title = req.title || (it ? it.name : '');
    this.emit('item');
    if (it && it.kind === 'live' && !req.url && !repo.isRestricted(it)) store.addRecent(it.key);
    let url = req.url;
    try { if (!url) url = await repo.resolve(it); }
    catch (e) { this.setState({ error: e.message || 'Error', buffering: false }); return; }
    this.start(url, req.startMs || 0);
  }

  start(url, startMs) {
    this.url = url;
    this.setState({ error: '', buffering: true, paused: false });
    keepScreenOn(true);
    this.pickEngine(this.usedAvFallback).load(url, { live: this.live, startMs });
  }

  async playIndex(i) {
    if (!this.req) return;
    const n = this.req.items.length;
    if (!n) return;
    this.saveProgress();
    this.index = ((i % n) + n) % n;
    this.retries = 0;
    this.usedAvFallback = false;
    this.req = Object.assign({}, this.req, { url: null, program: null, startMs: 0 });
    const it = this.item;
    this.title = it.name;
    this.emit('item');
    if (it.kind === 'live' && !repo.isRestricted(it)) store.addRecent(it.key);
    try { this.start(await repo.resolve(it), 0); } catch (e) { this.setState({ error: e.message, buffering: false }); }
  }

  zap(d) { if (this.req && this.req.items.length > 1) this.playIndex(this.index + d); }

  async playEpisode(ei, startMs) {
    const r = this.req, ep = r.episodes[ei];
    if (!ep) return;
    this.saveProgress();
    r.episodeIndex = ei;
    r.progressKey = r.series.key + '#' + ep.season + 'x' + ep.number;
    this.title = r.series.name + ' · S' + ep.season + 'E' + ep.number + (ep.title && !/^episode \d+$/i.test(ep.title) ? ' · ' + ep.title : '');
    this.emit('item');
    try { this.start(await repo.resolveEpisode(r.series, ep), startMs || 0); } catch (e) { this.setState({ error: e.message, buffering: false }); }
  }

  /** Plays a past programme of the current channel (catch-up). */
  async playCatchup(item, program) {
    const url = await repo.catchup(item, program);
    if (!url) return false;
    const items = this.req && this.req.items.indexOf(item) >= 0 ? this.req.items : [item];
    this.req = { items, index: items.indexOf(item), url, program, title: item.name + ' · ' + program.title };
    this.title = this.req.title;
    this.emit('item');
    this.start(url, 0);
    return true;
  }

  backToLive() { if (this.req) this.playIndex(this.index); }

  onEnded() {
    const r = this.req;
    if (r && r.series && r.episodeIndex + 1 < r.episodes.length) { this.playEpisode(r.episodeIndex + 1); return; }
    if (this.live) { this.onError('Stream ended'); return; }
    this.setState({ paused: true });
    this.emit('ended');
  }

  onError(msg) {
    // Samsung: try the native player once before giving up
    if (this.engine === this.html5 && AvplayEngine.available() && this.mode === 'full' && !this.usedAvFallback && store.settings.get().engine !== 'html5') {
      this.usedAvFallback = true;
      this.pickEngine(true).load(this.url, { live: this.live, startMs: 0 });
      return;
    }
    if (this.live && this.retries < 3) {
      this.retries++;
      this.setState({ buffering: true });
      clearTimeout(this.retryTimer);
      this.retryTimer = setTimeout(async () => {
        try { const u = this.req.url || await repo.resolve(this.item); this.start(u, 0); } catch (e) { this.setState({ error: e.message, buffering: false }); }
      }, 1500 * this.retries);
      return;
    }
    this.setState({ error: msg || 'Playback failed', buffering: false });
  }

  setMode(mode, rect) {
    const prev = this.mode;
    this.mode = mode; this.rect = rect || null;
    const L = this.layer;
    L.className = 'video-layer vl-' + mode;
    if (mode === 'rect' && rect) {
      L.style.left = rect.left + 'px'; L.style.top = rect.top + 'px'; L.style.width = rect.width + 'px'; L.style.height = rect.height + 'px';
    } else { L.style.left = ''; L.style.top = ''; L.style.width = ''; L.style.height = ''; }
    // AVPlay can't be shown small: hand over to the web player (and back) when the size changes
    if (this.req && this.url && this.engine) {
      const needHtml = mode !== 'full' && this.engine.kind === 'avplay';
      const wantAv = mode === 'full' && this.engine.kind !== 'avplay' && this.wantAvplay();
      if (needHtml || wantAv) {
        const t = this.live ? 0 : this.engine.time;
        this.usedAvFallback = false;
        this.start(this.url, t);
      }
    }
    if (prev !== mode) this.emit('mode', mode);
  }

  togglePause() {
    if (!this.engine) return;
    if (this.engine.paused) { this.engine.play(); this.setState({ paused: false }); } else { this.engine.pause(); this.setState({ paused: true }); }
  }

  seekBy(ms) { if (this.engine && !this.live) this.engine.seek(this.engine.time + ms); }

  saveProgress() {
    const r = this.req, e = this.engine;
    if (!r || !e || this.live || r.program) return;
    const key = r.progressKey || (this.item && this.item.kind === 'movie' ? this.item.key : null);
    if (key) store.saveProgress(key, e.time, e.duration);
    if (r.series && e.duration > 0 && e.time > 15000) {
      const ep = r.episodes[r.episodeIndex];
      if (e.time > e.duration * 0.95) {
        store.markWatched(key);
        store.setSeriesPointer(r.series.key, r.episodes[r.episodeIndex + 1] || null, 0, 1);
      } else if (ep) store.setSeriesPointer(r.series.key, ep, e.time, e.duration);
    }
  }

  stop() {
    this.saveProgress();
    clearTimeout(this.retryTimer);
    if (this.engine) this.engine.stop();
    this.req = null; this.url = '';
    keepScreenOn(false);
    this.setMode('hidden');
    this.setState({ buffering: false, error: '', paused: false });
    this.emit('item');
    document.documentElement.classList.remove('avplay');
  }
}

export const Playback = new PlaybackService();
