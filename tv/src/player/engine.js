// Video engines. HTML5 <video> driven by hls.js (HLS), mpegts.js (live MPEG-TS/FLV) or the TV's native decoder;
// on Samsung TVs the native AVPlay player is used as a last resort (full screen only).
import Hls from 'hls.js';
import mpegts from 'mpegts.js';
import { isTizen } from '../platform.js';

export function urlType(url, live) {
  const u = url.toLowerCase().split('?')[0];
  if (/\.m3u8$/.test(u) || url.toLowerCase().indexOf('m3u8') >= 0) return 'hls';
  if (/\.mpd$/.test(u)) return 'native';
  if (/\.(ts|mpegts)$/.test(u)) return 'ts';
  if (/\.flv$/.test(u)) return 'flv';
  if (/\.(mp4|mkv|webm|mov|m4v|avi|mp3|aac)$/.test(u)) return 'native';
  return live ? 'ts' : 'native';
}

class Base {
  constructor() { this.listeners = {}; }
  on(ev, fn) { (this.listeners[ev] = this.listeners[ev] || []).push(fn); }
  emit(ev, a) { (this.listeners[ev] || []).forEach((f) => { try { f(a); } catch (e) { console.error(e); } }); }
}

/** HTML5 video with automatic choice and fallback between hls.js / mpegts.js / native. */
export class Html5Engine extends Base {
  constructor(video, opts) {
    super();
    this.v = video;
    this.opts = opts || {};
    this.hls = null; this.ts = null;
    this.kind = 'html5';
    const v = this.v;
    v.addEventListener('waiting', () => this.emit('buffering', true));
    v.addEventListener('playing', () => {
      this.emit('buffering', false); this.emit('playing');
      // Sound but no picture = the web decoder can't show this video (e.g. MPEG-2 / HEVC): try the next way to play it
      clearTimeout(this.noVideoTimer);
      const step = this.step;
      this.noVideoTimer = setTimeout(() => {
        if (this.step !== step || v.paused || v.videoWidth > 0) return;
        if (this.step + 1 < this.plan.length) this.next('No video');
        else this.emit('audioonly');
      }, 5000);
    });
    v.addEventListener('canplay', () => this.emit('buffering', false));
    v.addEventListener('pause', () => this.emit('paused'));
    v.addEventListener('ended', () => this.emit('ended'));
    v.addEventListener('timeupdate', () => this.emit('time'));
    v.addEventListener('loadedmetadata', () => this.emit('tracks'));
    v.addEventListener('resize', () => this.emit('videosize'));
    v.addEventListener('error', () => { if (this.mode === 'native') this.fail('Video error ' + (v.error ? v.error.code : '')); });
  }

  destroyLibs() {
    if (this.hls) { try { this.hls.destroy(); } catch (e) { /* ignore */ } this.hls = null; }
    if (this.ts) { try { this.ts.pause(); this.ts.unload(); this.ts.detachMediaElement(); this.ts.destroy(); } catch (e) { /* ignore */ } this.ts = null; }
  }

  load(url, o) {
    o = o || {};
    this.url = url; this.live = !!o.live; this.startMs = o.startMs || 0;
    const t = urlType(url, this.live);
    const canMse = !!(window.MediaSource || window.WebKitMediaSource);
    const plan = [];
    if (t === 'hls') { if (canMse && Hls.isSupported()) plan.push('hls'); plan.push('native'); if (this.live && canMse && mpegts.isSupported()) plan.push('ts'); }
    else if (t === 'ts' || t === 'flv') { if (canMse && mpegts.isSupported()) plan.push('ts'); plan.push('native'); if (canMse && Hls.isSupported()) plan.push('hls'); }
    else plan.push('native');
    this.plan = plan; this.step = -1;
    this.next();
  }

  next(reason) {
    this.destroyLibs();
    this.step++;
    if (this.step >= this.plan.length) { this.emit('error', reason || 'Playback failed'); return; }
    this.mode = this.plan[this.step];
    const v = this.v, url = this.url;
    v.removeAttribute('src');
    try { v.load(); } catch (e) { /* ignore */ }
    this.emit('buffering', true);
    if (this.mode === 'hls') {
      const buf = this.opts.bufferSeconds || 10;
      const hls = new Hls({
        enableWorker: true, lowLatencyMode: false, backBufferLength: 30,
        maxBufferLength: Math.max(10, buf * 2), liveSyncDurationCount: 3, manifestLoadingTimeOut: 15000, fragLoadingTimeOut: 20000,
        startPosition: this.startMs ? this.startMs / 1000 : -1,
      });
      this.hls = hls;
      let recovered = false;
      hls.on(Hls.Events.MANIFEST_PARSED, () => { this.emit('tracks'); v.play().catch(() => {}); });
      hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => this.emit('tracks'));
      hls.on(Hls.Events.SUBTITLE_TRACKS_UPDATED, () => this.emit('tracks'));
      hls.on(Hls.Events.ERROR, (ev, d) => {
        if (!d.fatal) return;
        if (d.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) { recovered = true; hls.recoverMediaError(); return; }
        if (d.type === Hls.ErrorTypes.NETWORK_ERROR && d.details === 'manifestLoadError') { this.next('Cannot load stream (' + (d.response && d.response.code || 'network') + ')'); return; }
        this.next(d.details);
      });
      hls.loadSource(url);
      hls.attachMedia(v);
    } else if (this.mode === 'ts') {
      const p = mpegts.createPlayer({ type: urlType(url, true) === 'flv' ? 'flv' : 'mpegts', isLive: this.live, url },
        { enableWorker: false, lazyLoad: false, liveBufferLatencyChasing: this.live, liveBufferLatencyMaxLatency: 8, liveBufferLatencyMinRemain: 1.5,
          autoCleanupSourceBuffer: true, enableStashBuffer: true, stashInitialSize: 384 * 1024 });
      this.ts = p;
      let gotData = false;
      p.on(mpegts.Events.ERROR, (type, detail) => this.next(type + ' ' + detail));
      p.on(mpegts.Events.MEDIA_INFO, () => { gotData = true; this.emit('tracks'); });
      p.attachMediaElement(v);
      p.load();
      const pr = p.play(); if (pr && pr.catch) pr.catch(() => {});
      clearTimeout(this.tsTimer);
      this.tsTimer = setTimeout(() => { if (this.ts === p && !gotData && v.readyState < 2) this.next('No data'); }, 15000);
    } else {
      v.src = url;
      if (this.startMs) {
        const seek = () => { v.removeEventListener('loadedmetadata', seek); try { v.currentTime = this.startMs / 1000; } catch (e) { /* ignore */ } };
        v.addEventListener('loadedmetadata', seek);
      }
      v.play().catch(() => {});
    }
  }

  fail(reason) { if (this.mode === 'native') this.next(reason); }

  stop() {
    clearTimeout(this.tsTimer);
    clearTimeout(this.noVideoTimer);
    this.destroyLibs();
    const v = this.v;
    try { v.pause(); } catch (e) { /* ignore */ }
    v.removeAttribute('src');
    try { v.load(); } catch (e) { /* ignore */ }
  }

  play() { const p = this.v.play(); if (p && p.catch) p.catch(() => {}); }
  pause() { this.v.pause(); }
  get paused() { return this.v.paused; }
  get time() { return (this.v.currentTime || 0) * 1000; }
  get duration() { const d = this.v.duration; return isFinite(d) ? d * 1000 : 0; }
  seek(ms) { try { this.v.currentTime = Math.max(0, ms / 1000); } catch (e) { /* ignore */ } }
  get videoSize() { return this.v.videoHeight ? this.v.videoWidth + '×' + this.v.videoHeight : ''; }

  audioTracks() {
    if (this.hls) return this.hls.audioTracks.map((a, i) => ({ id: i, label: a.name || a.lang || 'Audio ' + (i + 1), active: i === this.hls.audioTrack }));
    const at = this.v.audioTracks;
    if (at && at.length) { const out = []; for (let i = 0; i < at.length; i++) out.push({ id: i, label: at[i].label || at[i].language || 'Audio ' + (i + 1), active: at[i].enabled }); return out; }
    return [];
  }
  setAudio(id) {
    if (this.hls) { this.hls.audioTrack = id; return; }
    const at = this.v.audioTracks; if (at) for (let i = 0; i < at.length; i++) at[i].enabled = i === id;
  }
  subTracks() {
    if (this.hls) return this.hls.subtitleTracks.map((s, i) => ({ id: i, label: s.name || s.lang || 'Sub ' + (i + 1), active: i === this.hls.subtitleTrack }));
    const tt = this.v.textTracks; const out = [];
    if (tt) for (let i = 0; i < tt.length; i++) out.push({ id: i, label: tt[i].label || tt[i].language || 'Sub ' + (i + 1), active: tt[i].mode === 'showing' });
    return out;
  }
  setSub(id) {
    if (this.hls) { this.hls.subtitleTrack = id; this.hls.subtitleDisplay = id >= 0; return; }
    const tt = this.v.textTracks; if (tt) for (let i = 0; i < tt.length; i++) tt[i].mode = i === id ? 'showing' : 'disabled';
  }
  setAspect(mode) { this.v.style.objectFit = mode === 'fill' ? 'fill' : mode === 'zoom' ? 'cover' : 'contain'; }
  setVolume(muted) { this.v.muted = !!muted; }
}

/** Samsung's native player. It draws on a video plane *behind* the web page, so it is only used full screen. */
export class AvplayEngine extends Base {
  constructor() {
    super();
    this.kind = 'avplay';
    this.av = window.webapis && window.webapis.avplay;
    if (!document.getElementById('av-object')) {
      const o = document.createElement('object');
      o.id = 'av-object'; o.type = 'application/avplayer';
      o.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;z-index:0';
      document.body.insertBefore(o, document.body.firstChild);
    }
    this.subText = '';
    this.aspect = 'fit';
  }
  static available() { return isTizen && !!(window.webapis && window.webapis.avplay); }

  load(url, o) {
    o = o || {};
    const av = this.av;
    this.stop();
    try {
      av.open(url);
      av.setDisplayRect(0, 0, 1920, 1080);
      this.setAspect(this.aspect);
      av.setListener({
        onbufferingstart: () => this.emit('buffering', true),
        onbufferingcomplete: () => this.emit('buffering', false),
        oncurrentplaytime: () => this.emit('time'),
        onstreamcompleted: () => this.emit('ended'),
        onerror: (e) => this.emit('error', 'AVPlay ' + e),
        onsubtitlechange: (dur, text) => { this.subText = text || ''; this.emit('subtitle', this.subText); },
      });
      try { av.setTimeoutForBuffering(20); } catch (e) { /* older models */ }
      try { av.setBufferingParam('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', 4); } catch (e) { /* ignore */ }
      this.emit('buffering', true);
      av.prepareAsync(() => {
        if (o.startMs) try { av.seekTo(o.startMs); } catch (e) { /* ignore */ }
        av.play();
        this.emit('buffering', false); this.emit('playing'); this.emit('tracks');
      }, (e) => this.emit('error', 'AVPlay ' + e));
    } catch (e) { this.emit('error', 'AVPlay ' + (e.message || e)); }
  }
  stop() { try { this.av.stop(); } catch (e) { /* ignore */ } try { this.av.close(); } catch (e) { /* ignore */ } }
  play() { try { this.av.play(); this.emit('playing'); } catch (e) { /* ignore */ } }
  pause() { try { this.av.pause(); this.emit('paused'); } catch (e) { /* ignore */ } }
  get paused() { try { return this.av.getState() === 'PAUSED'; } catch (e) { return false; } }
  get time() { try { return this.av.getCurrentTime(); } catch (e) { return 0; } }
  get duration() { try { return this.av.getDuration(); } catch (e) { return 0; } }
  seek(ms) { try { this.av.seekTo(Math.max(0, ms)); } catch (e) { /* ignore */ } }
  get videoSize() {
    try {
      const v = this.av.getTotalTrackInfo().find((t) => t.type === 'VIDEO');
      const x = v && JSON.parse(v.extra_info); return x ? x.Width + '×' + x.Height : '';
    } catch (e) { return ''; }
  }
  tracks(type) {
    try {
      const cur = this.av.getCurrentStreamInfo().filter((t) => t.type === type).map((t) => t.index);
      return this.av.getTotalTrackInfo().filter((t) => t.type === type).map((t, i) => {
        let label = type === 'AUDIO' ? 'Audio ' + (i + 1) : 'Sub ' + (i + 1);
        try { const x = JSON.parse(t.extra_info); label = x.language || x.track_lang || label; } catch (e) { /* ignore */ }
        return { id: t.index, label, active: cur.indexOf(t.index) >= 0 };
      });
    } catch (e) { return []; }
  }
  audioTracks() { return this.tracks('AUDIO'); }
  setAudio(id) { try { this.av.setSelectTrack('AUDIO', id); } catch (e) { /* ignore */ } }
  subTracks() { return this.tracks('TEXT'); }
  setSub(id) {
    try {
      if (id < 0) { this.av.setSilentSubtitle(true); this.subText = ''; this.emit('subtitle', ''); }
      else { this.av.setSelectTrack('TEXT', id); this.av.setSilentSubtitle(false); }
    } catch (e) { /* ignore */ }
  }
  setAspect(mode) {
    this.aspect = mode;
    try {
      this.av.setDisplayMethod(mode === 'fill' ? 'PLAYER_DISPLAY_MODE_FULL_SCREEN' : mode === 'zoom' ? 'PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO' : 'PLAYER_DISPLAY_MODE_LETTER_BOX');
    } catch (e) { /* ignore */ }
  }
  setVolume() { /* TV volume keys handle it */ }
}
