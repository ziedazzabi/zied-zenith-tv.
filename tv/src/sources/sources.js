// Playlist sources: Xtream Codes API, M3U/M3U8 and MAG/Stalker (MAC) portals.
import { getJson, getText } from '../net.js';
import { b64decode, int, str, md5, sha256, pad2 } from '../util.js';

export function makeKey(pl, kind, id) { return pl + '|' + kind + '|' + id; }

function media(o) {
  o.key = makeKey(o.pl, o.kind, o.id);
  return o;
}

export function createSource(playlist, settings) {
  if (playlist.type === 'xtream') return new XtreamSource(playlist, settings);
  if (playlist.type === 'stalker') return new StalkerSource(playlist);
  return new M3uSource(playlist);
}

// ------------------------------------------------------------------ Xtream
export function normalizeBase(raw) {
  let u = str(raw).trim();
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u;
  u = u.split('/player_api.php')[0].split('/get.php')[0].split('/c/')[0];
  return u.replace(/\/+$/, '');
}

/** Splits a get.php?username=..&password=.. link into its parts. */
export function parseXtreamLink(link) {
  try {
    const m = /^(https?:\/\/[^/?#]+)[^?]*\?(.*)$/i.exec(str(link).trim());
    if (!m) return null;
    const q = {};
    m[2].split('&').forEach((kv) => { const i = kv.indexOf('='); if (i > 0) q[decodeURIComponent(kv.slice(0, i))] = decodeURIComponent(kv.slice(i + 1).replace(/\+/g, ' ')); });
    if (q.username && q.password) return { url: m[1], username: q.username, password: q.password };
  } catch (e) { /* ignore */ }
  return null;
}

class XtreamSource {
  constructor(pl, settings) {
    this.pl = pl; this.settings = settings;
    this.base = normalizeBase(pl.url);
    this.user = encodeURIComponent(str(pl.username).trim());
    this.pass = encodeURIComponent(str(pl.password).trim());
    this.tz = '';
  }

  api(action, extra) {
    let u = this.base + '/player_api.php?username=' + this.user + '&password=' + this.pass;
    if (action) u += '&action=' + action;
    if (extra) u += '&' + extra;
    return u;
  }

  async load(progress) {
    const auth = await getJson(this.api());
    const ui = auth && auth.user_info;
    if (!ui || str(ui.auth) === '0') throw new Error('Login failed — check server, username and password');
    this.tz = (auth.server_info && auth.server_info.timezone) || '';
    progress && progress('…');
    const safe = (p) => p.catch(() => []);
    const [lc, vc, sc, live, vod, ser] = await Promise.all([
      safe(getJson(this.api('get_live_categories'))), safe(getJson(this.api('get_vod_categories'))),
      safe(getJson(this.api('get_series_categories'))), getJson(this.api('get_live_streams'), { timeout: 90000 }),
      safe(getJson(this.api('get_vod_streams'), { timeout: 120000 })), safe(getJson(this.api('get_series'), { timeout: 120000 })),
    ]);
    const cats = [];
    const addCats = (arr, kind) => (Array.isArray(arr) ? arr : []).forEach((c) => cats.push({ id: str(c.category_id), name: str(c.category_name) || '—', kind }));
    addCats(lc, 'live'); addCats(vc, 'movie'); addCats(sc, 'series');

    const items = [];
    const ext = this.settings.liveFormat === 'ts' ? 'ts' : 'm3u8';
    const id = this.pl.id;
    (Array.isArray(live) ? live : []).forEach((o, i) => {
      const sid = str(o.stream_id);
      items.push(media({
        id: sid, pl: id, kind: 'live', name: str(o.name).trim(), cat: str(o.category_id),
        num: int(o.num) || i + 1, logo: str(o.stream_icon), url: this.base + '/live/' + this.user + '/' + this.pass + '/' + sid + '.' + ext,
        epg: str(o.epg_channel_id), cu: str(o.tv_archive) === '1' ? Math.max(1, int(o.tv_archive_duration)) : 0, cuType: 'xtream', added: int(o.added),
      }));
    });
    (Array.isArray(vod) ? vod : []).forEach((o) => {
      const sid = str(o.stream_id), cext = str(o.container_extension) || 'mp4';
      items.push(media({
        id: sid, pl: id, kind: 'movie', name: str(o.name).trim(), cat: str(o.category_id), logo: str(o.stream_icon),
        url: this.base + '/movie/' + this.user + '/' + this.pass + '/' + sid + '.' + cext,
        rating: str(o.rating), year: str(o.year), plot: str(o.plot), ext: cext, added: int(o.added),
      }));
    });
    (Array.isArray(ser) ? ser : []).forEach((o) => {
      items.push(media({
        id: str(o.series_id), pl: id, kind: 'series', name: str(o.name).trim(), cat: str(o.category_id), logo: str(o.cover),
        rating: str(o.rating), plot: str(o.plot), year: (str(o.releaseDate) || str(o.release_date)).slice(0, 4), added: int(o.last_modified),
      }));
    });
    const exp = int(ui.exp_date);
    const expTxt = exp > 0 ? new Date(exp * 1000).toISOString().slice(0, 10) : '∞';
    return {
      playlistId: id, categories: cats, items, tz: this.tz,
      accountInfo: 'Status: ' + str(ui.status) + ' · Expires: ' + expTxt + ' · Connections: ' + str(ui.active_cons) + '/' + str(ui.max_connections),
      epgUrl: this.pl.epgUrl || this.base + '/xmltv.php?username=' + this.user + '&password=' + this.pass,
    };
  }

  infoToDetails(info, item) {
    info = info || {};
    const bd = Array.isArray(info.backdrop_path) ? str(info.backdrop_path[0]) : str(info.backdrop_path);
    return {
      plot: str(info.plot) || str(info.description) || item.plot, cast: str(info.cast) || str(info.actors), director: str(info.director),
      genre: str(info.genre), releaseDate: str(info.releasedate) || str(info.releaseDate), duration: str(info.duration),
      rating: str(info.rating) || item.rating, backdrop: bd, trailer: str(info.youtube_trailer), seasons: null,
    };
  }

  async details(item) {
    if (item.kind === 'movie') {
      const o = await getJson(this.api('get_vod_info', 'vod_id=' + item.id));
      return this.infoToDetails(o && o.info, item);
    }
    if (item.kind === 'series') {
      const o = await getJson(this.api('get_series_info', 'series_id=' + item.id));
      const d = this.infoToDetails(o && o.info, item);
      const seasons = {};
      const eps = (o && o.episodes) || {};
      const groups = Array.isArray(eps) ? eps.map((e, i) => [String(i + 1), e]) : Object.keys(eps).map((k) => [k, eps[k]]);
      groups.forEach(([sk, list]) => (Array.isArray(list) ? list : []).forEach((e) => {
        const season = int(e.season) || int(sk) || 1;
        const cext = str(e.container_extension) || 'mp4';
        const info = e.info || {};
        (seasons[season] = seasons[season] || []).push({
          id: str(e.id), title: str(e.title), season, number: int(e.episode_num),
          url: this.base + '/series/' + this.user + '/' + this.pass + '/' + str(e.id) + '.' + cext,
          cover: str(info.movie_image), plot: str(info.plot), duration: str(info.duration),
        });
      }));
      Object.keys(seasons).forEach((k) => seasons[k].sort((a, b) => a.number - b.number));
      d.seasons = seasons;
      return d;
    }
    return {};
  }

  async resolve(item) { return item.url; }
  async resolveEpisode(series, ep) { return ep.url; }

  async shortEpg(item) {
    if (item.kind !== 'live') return [];
    const o = await getJson(this.api('get_short_epg', 'stream_id=' + item.id + '&limit=12'));
    return ((o && o.epg_listings) || []).map((e) => ({
      start: int(e.start_timestamp) * 1000, stop: int(e.stop_timestamp) * 1000, title: b64decode(str(e.title)), desc: b64decode(str(e.description)),
    })).filter((p) => p.start > 0);
  }

  async catchupUrl(item, start, stop) {
    if (!(item.cu > 0)) return null;
    const minutes = Math.max(1, Math.round((stop - start) / 60000));
    return this.base + '/timeshift/' + this.user + '/' + this.pass + '/' + minutes + '/' + formatInZone(start, this.tz) + '/' + item.id + '.ts';
  }
}

/** yyyy-MM-dd:HH-mm in the server's time zone (falls back to the TV's local time). */
function formatInZone(ms, tz) {
  const d = new Date(ms);
  if (tz) {
    try {
      const parts = {};
      new Intl.DateTimeFormat('en-GB', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
        .formatToParts(d).forEach((p) => { parts[p.type] = p.value; });
      const hh = parts.hour === '24' ? '00' : parts.hour;
      return parts.year + '-' + parts.month + '-' + parts.day + ':' + hh + '-' + parts.minute;
    } catch (e) { /* unknown zone */ }
  }
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ':' + pad2(d.getHours()) + '-' + pad2(d.getMinutes());
}

// ------------------------------------------------------------------ M3U
const ATTR = /([\w-]+)="([^"]*)"/g;
const MOVIE_EXT = ['.mp4', '.mkv', '.avi', '.mov', '.m4v', '.wmv', '.webm', '.mpg'];

export function m3uAttrs(line) {
  const a = {}; let m; ATTR.lastIndex = 0;
  while ((m = ATTR.exec(line))) a[m[1].toLowerCase()] = m[2];
  return a;
}

export function m3uName(line) {
  let inQ = false, idx = -1;
  for (let i = 0; i < line.length; i++) { const c = line[i]; if (c === '"') inQ = !inQ; else if (c === ',' && !inQ) idx = i; }
  return idx >= 0 ? line.slice(idx + 1).trim() : '';
}

export function m3uKind(url) {
  const p = url.split('?')[0].toLowerCase();
  if (p.indexOf('/series/') >= 0) return 'series';
  if (p.indexOf('/movie/') >= 0 || MOVIE_EXT.some((e) => p.endsWith(e))) return 'movie';
  return 'live';
}

/** Fills catch-up templates: {utc} {start} {utcend} {end} {duration} {offset} {lutc} {Y}{m}{d}{H}{M}{S}, ${...}, {utc:YmdHMS}. */
export function fillTemplate(tpl, start, stop) {
  const s = Math.floor(start / 1000), e = Math.floor(stop / 1000), now = Math.floor(Date.now() / 1000);
  const d = new Date(start);
  const cal = [d.getUTCFullYear(), pad2(d.getUTCMonth() + 1), pad2(d.getUTCDate()), pad2(d.getUTCHours()), pad2(d.getUTCMinutes()), pad2(d.getUTCSeconds())];
  let r = tpl.split('${').join('{');
  const map = {
    '{utc}': s, '{start}': s, '{timestamp}': s, '{utcend}': e, '{end}': e, '{lutc}': now, '{now}': now,
    '{duration}': e - s, '{offset}': now - s, '{Y}': cal[0], '{m}': cal[1], '{d}': cal[2], '{H}': cal[3], '{M}': cal[4], '{S}': cal[5],
  };
  Object.keys(map).forEach((k) => { r = r.split(k).join(String(map[k])); });
  r = r.replace(/\{(utc|start|end|utcend):([^}]+)\}/g, (m0, which, pat) => {
    const t = new Date(which === 'end' || which === 'utcend' ? stop : start);
    const v = { Y: t.getUTCFullYear(), m: pad2(t.getUTCMonth() + 1), d: pad2(t.getUTCDate()), H: pad2(t.getUTCHours()), M: pad2(t.getUTCMinutes()), S: pad2(t.getUTCSeconds()) };
    return pat.replace(/[YmdHMS]/g, (ch) => v[ch]);
  });
  return r;
}

export function parseM3u(text, pl) {
  const cats = new Map();
  const items = [];
  let epgUrl = pl.epgUrl || '';
  let defCu = '', defDays = 0, defSrc = '';
  let attrs = null, name = '', group = '', counter = 0;
  const lines = text.split(/\r?\n/);
  for (let li = 0; li < lines.length; li++) {
    const t = lines[li].trim();
    if (!t) continue;
    if (t.indexOf('#EXTM3U') === 0) {
      const a = m3uAttrs(t);
      if (!epgUrl) epgUrl = str(a['url-tvg'] || a['x-tvg-url']).split(',')[0].trim();
      defCu = a.catchup || ''; defDays = int(a['catchup-days']); defSrc = a['catchup-source'] || '';
    } else if (t.indexOf('#EXTINF') === 0) {
      attrs = m3uAttrs(t); name = m3uName(t); group = attrs['group-title'] || '';
    } else if (t.indexOf('#EXTGRP:') === 0) {
      group = t.slice(8).trim();
    } else if (t[0] === '#') {
      // #EXTVLCOPT etc.
    } else {
      const a = attrs || {};
      counter++;
      const kind = m3uKind(t);
      const g = group || 'Uncategorized';
      const catId = kind + ':' + g;
      if (!cats.has(catId)) cats.set(catId, { id: catId, name: g, kind });
      const cu = a.catchup || a['catchup-type'] || defCu;
      const days = int(a['catchup-days'] || a['tvg-rec'] || a.timeshift) || defDays;
      items.push(media({
        id: String(counter), pl: pl.id, kind, name: name || a['tvg-name'] || 'Channel ' + counter, cat: catId,
        num: int(a['tvg-chno']) || counter, logo: a['tvg-logo'] || a.logo || '', url: t, epg: a['tvg-id'] || a['tvg-name'] || '',
        cu: cu || days > 0 ? Math.max(days, cu ? 1 : 0) : 0, cuType: cu, cuSrc: a['catchup-source'] || defSrc,
      }));
      attrs = null; name = ''; group = '';
    }
  }
  if (!items.length) throw new Error('No channels found in this playlist');
  return { playlistId: pl.id, categories: Array.from(cats.values()), items, epgUrl, accountInfo: '' };
}

class M3uSource {
  constructor(pl) { this.pl = pl; }
  async load() {
    let u = str(this.pl.url).trim();
    if (!/^https?:\/\//i.test(u) && !/^file:/i.test(u)) u = 'http://' + u;
    const text = await getText(u, { timeout: 120000 });
    return parseM3u(text, this.pl);
  }
  async details(item) { return { plot: item.plot, rating: item.rating }; }
  async resolve(item) { return item.url; }
  async resolveEpisode(series, ep) { return ep.url; }
  async shortEpg() { return []; }
  async catchupUrl(item, start, stop) {
    if (!(item.cu > 0)) return null;
    const type = str(item.cuType).toLowerCase(), src = str(item.cuSrc);
    if (type === 'append' && src) return item.url + fillTemplate(src, start, stop);
    if ((type === 'default' || !type) && src) return fillTemplate(src, start, stop);
    if (type === 'shift' || type === 'timeshift') {
      const sep = item.url.indexOf('?') >= 0 ? '&' : '?';
      return item.url + sep + 'utc=' + Math.floor(start / 1000) + '&lutc=' + Math.floor(Date.now() / 1000);
    }
    if (type === 'flussonic' || type === 'fs' || type === 'flussonic-hls') {
      const i = item.url.lastIndexOf('/');
      return i < 0 ? null : item.url.slice(0, i) + '/index-' + Math.floor(start / 1000) + '-' + Math.floor((stop - start) / 1000) + '.m3u8';
    }
    return src ? fillTemplate(src, start, stop) : null;
  }
}

// ------------------------------------------------------------------ Stalker / MAC portal
class StalkerSource {
  constructor(pl) {
    this.pl = pl;
    let u = str(pl.url).trim();
    if (!/^https?:/i.test(u)) u = 'http://' + u;
    u = u.replace(/\/+$/, '');
    if (u.indexOf('/stalker_portal') >= 0) this.root = u.split('/stalker_portal')[0];
    else this.root = u.replace(/\/c$/, '').split('/portal.php')[0].split('/server/load.php')[0];
    this.endpoint = u.indexOf('/stalker_portal') >= 0 ? this.root + '/stalker_portal/server/load.php' : this.root + '/portal.php';
    this.mac = str(pl.mac).trim().toUpperCase();
    this.token = '';
    this.tokenP = null;
  }

  headers() {
    const h = { 'X-User-Agent': 'Model: MAG250; Link: WiFi', Accept: '*/*', Cookie: 'mac=' + encodeURIComponent(this.mac) + '; stb_lang=en; timezone=GMT' };
    if (this.token) h.Authorization = 'Bearer ' + this.token;
    return h;
  }

  /** The MAC is also sent as a query parameter because TV browsers can't send cookies to other sites. */
  macParam() { return '&mac=' + encodeURIComponent(this.mac); }

  async ensureToken() {
    if (this.token) return;
    if (!this.tokenP) {
      this.tokenP = (async () => {
        let t = await this.handshake(this.endpoint);
        if (!t && /portal\.php$/.test(this.endpoint)) {
          const alt = this.root + '/stalker_portal/server/load.php';
          t = await this.handshake(alt);
          if (t) this.endpoint = alt;
        }
        if (!t) throw new Error('Portal handshake failed — check the portal URL and MAC address');
        this.token = t;
        try {
          const sn = md5(this.mac).slice(0, 13).toUpperCase(), dev = sha256(this.mac).toUpperCase();
          await getJson(this.endpoint + '?type=stb&action=get_profile&hd=1&ver=ImageDescription:%200.2.18-r23-250;&num_banks=2&sn=' + sn +
            '&stb_type=MAG250&image_version=218&device_id=' + dev + '&device_id2=' + dev + '&signature=&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&JsHttpRequest=1-xml' + this.macParam(),
          { headers: this.headers() });
        } catch (e) { /* not fatal */ }
      })();
    }
    try { await this.tokenP; } finally { this.tokenP = null; }
  }

  async handshake(ep) {
    try {
      const r = await getJson(ep + '?type=stb&action=handshake&token=&prehash=0&JsHttpRequest=1-xml' + this.macParam(), { headers: this.headers() });
      return (r && r.js && r.js.token) || null;
    } catch (e) { return null; }
  }

  async call(query, retry) {
    if (retry === undefined) retry = true;
    await this.ensureToken();
    let res;
    try { res = await getJson(this.endpoint + '?' + query + '&JsHttpRequest=1-xml' + this.macParam(), { headers: this.headers(), timeout: 60000 }); }
    catch (e) { if (retry) { this.token = ''; return this.call(query, false); } throw e; }
    const js = res && res.js;
    if ((js == null || js === false) && retry) { this.token = ''; return this.call(query, false); }
    return js;
  }

  fixLogo(l) {
    if (!l) return '';
    if (/^http/.test(l)) return l;
    if (l[0] === '/') return this.root + l;
    return this.root + '/stalker_portal/misc/logos/320/' + l;
  }

  async load() {
    await this.ensureToken();
    const soft = (p) => p.catch(() => null);
    const [genres, channels, vodCats, serCats, acc] = await Promise.all([
      this.call('type=itv&action=get_genres'), this.call('type=itv&action=get_all_channels'),
      soft(this.call('type=vod&action=get_categories')), soft(this.call('type=series&action=get_categories')),
      soft(this.call('type=account_info&action=get_main_info')),
    ]);
    const cats = [];
    (genres || []).forEach((g) => { if (str(g.id) !== '*') cats.push({ id: str(g.id), name: str(g.title), kind: 'live' }); });
    (vodCats || []).forEach((g) => { if (str(g.id) !== '*') cats.push({ id: 'vod:' + g.id, name: str(g.title), kind: 'movie', lazy: true }); });
    (serCats || []).forEach((g) => { if (str(g.id) !== '*') cats.push({ id: 'series:' + g.id, name: str(g.title), kind: 'series', lazy: true }); });
    const items = [];
    ((channels && channels.data) || []).forEach((o, i) => items.push(media({
      id: str(o.id), pl: this.pl.id, kind: 'live', name: str(o.name), cat: str(o.tv_genre_id), num: int(o.number) || i + 1,
      logo: this.fixLogo(str(o.logo)), cmd: str(o.cmd), epg: str(o.xmltv_id),
      cu: str(o.tv_archive) === '1' || int(o.archive) === 1 ? Math.max(1, Math.floor(int(o.tv_archive_duration) / 24)) : 0, cuType: 'stalker',
    })));
    const info = acc ? 'Expires: ' + (str(acc.phone) || str(acc.end_date) || '—') : 'MAC ' + this.mac;
    return { playlistId: this.pl.id, categories: cats, items, accountInfo: info, epgUrl: this.pl.epgUrl || '' };
  }

  async loadCategory(cat, page) {
    const [type, id] = cat.id.split(':');
    const kind = type === 'series' ? 'series' : 'movie';
    const js = await this.call('type=' + type + '&action=get_ordered_list&category=' + id + '&genre=' + id + '&sortby=added&p=' + page);
    return ((js && js.data) || []).map((o) => {
      const sl = Array.isArray(o.series) ? o.series.map(String) : [];
      return media({
        id: str(o.id), pl: this.pl.id, kind: sl.length ? 'series' : kind, name: str(o.name), cat: cat.id,
        logo: this.fixLogo(str(o.screenshot_uri) || str(o.cover_big)), cmd: str(o.cmd), rating: str(o.rating_imdb) || str(o.rating_kinopoisk),
        year: str(o.year), plot: str(o.description), ext: sl.join(','),
      });
    });
  }

  async details(item) {
    if (item.kind !== 'series') return { plot: item.plot, rating: item.rating };
    const nums = str(item.ext).split(',').map((x) => int(x)).filter((x) => x > 0);
    if (nums.length) return { plot: item.plot, rating: item.rating, seasons: { 1: nums.map((n) => ({ id: String(n), title: 'Episode ' + n, season: 1, number: n, cmd: item.cmd })) } };
    const js = await this.call('type=series&action=get_ordered_list&movie_id=' + encodeURIComponent(item.id) + '&season_id=0&episode_id=0');
    const seasons = {};
    ((js && js.data) || []).forEach((s, si) => {
      const sn = int(s.season_number) || si + 1;
      seasons[sn] = (Array.isArray(s.series) ? s.series : []).map((n) => ({ id: String(n), title: 'Episode ' + n, season: sn, number: int(n), cmd: str(s.cmd) }));
    });
    return { plot: item.plot, rating: item.rating, seasons };
  }

  async createLink(type, cmd, series) {
    const js = await this.call('type=' + type + '&action=create_link&cmd=' + encodeURIComponent(cmd) + (series != null ? '&series=' + series : '') + '&forced_storage=undefined&disable_ad=0');
    const link = str(js && js.cmd) || cmd;
    return link.trim().replace(/^(ffmpeg|ffrt|auto)\s+/, '').trim();
  }

  resolve(item) { return this.createLink(item.kind === 'live' ? 'itv' : 'vod', item.cmd); }
  resolveEpisode(series, ep) { return this.createLink('vod', ep.cmd || series.cmd, ep.number); }

  async shortEpg(item) {
    const js = await this.call('type=itv&action=get_short_epg&ch_id=' + item.id + '&size=10');
    return (Array.isArray(js) ? js : []).map((o) => ({ start: int(o.start_timestamp) * 1000, stop: int(o.stop_timestamp) * 1000, title: str(o.name), desc: str(o.descr) })).filter((p) => p.start > 0);
  }

  async catchupUrl(item, start, stop) {
    if (!(item.cu > 0)) return null;
    const live = await this.resolve(item);
    const minutes = Math.max(1, Math.round((stop - start) / 60000));
    if (live.indexOf('/live/') >= 0) {
      const parts = live.split('/live/');
      const p = parts[1].split('/');
      if (p.length >= 3) return parts[0] + '/timeshift/' + p[0] + '/' + p[1] + '/' + minutes + '/' + formatInZone(start, '') + '/' + p[2].split('.')[0] + '.ts';
    }
    const sep = live.indexOf('?') >= 0 ? '&' : '?';
    return live + sep + 'utc=' + Math.floor(start / 1000) + '&lutc=' + Math.floor(Date.now() / 1000);
  }
}
