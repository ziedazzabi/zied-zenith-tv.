// Colour themes (same designs as the Android app) + themes built from the user's own pictures.
import { store } from './store.js';
import { uid } from './util.js';

const T = (id, light, bg, glow, s1, s2, s3, accent, accentFocus, onAccent, accent2, text, muted, line, tiles) =>
  ({ id, light, bg, glow, s1, s2, s3, accent, accentFocus, onAccent, accent2, text, muted, line, tiles });

export const THEMES = [
  T('midnight', false, '#0B0F17', '#10233A', '#131A26', '#1B2433', '#243044', '#22D3EE', '#67E8F9', '#00141A', '#F472B6', '#E6EDF7', '#8A97AB', '#263247',
    ['#0EA5E9', '#DB2777', '#7C3AED', '#059669', '#D97706', '#DC2626', '#475569', '#0F766E']),
  T('royal', true, '#F7F7FA', '#FFF1CC', '#FFFFFF', '#EEF0F6', '#E2E6F0', '#C9A227', '#E0B83A', '#1A1300', '#3B2A8C', '#0E1A3D', '#5A6482', '#D6DAE6',
    ['#00529F', '#C9A227', '#3B2A8C', '#0E1A3D', '#B8860B', '#A32638', '#5A6482', '#1F7A6B']),
  T('royal_night', false, '#070B1F', '#1C1652', '#0F1530', '#18203F', '#242E55', '#E6C15A', '#F5D77E', '#1A1300', '#8B7CF6', '#F4F1E8', '#9AA0BF', '#26305A',
    ['#1E4FA8', '#B8912F', '#5B3FB8', '#1F7A6B', '#C08A1E', '#A32638', '#3A4370', '#7A5C1E']),
  T('cinema', false, '#0A0A0A', '#2A0608', '#151515', '#1F1F1F', '#2C2C2C', '#E50914', '#FF3D47', '#FFFFFF', '#F5C518', '#F5F5F5', '#9E9E9E', '#2E2E2E',
    ['#B20710', '#E50914', '#7A0A10', '#B8860B', '#F5A300', '#8B0000', '#404040', '#5A1010']),
  T('stadium', false, '#06140D', '#0D3A22', '#0E2017', '#152C20', '#1E3B2B', '#A3E635', '#BEF264', '#0B1A00', '#FFFFFF', '#EAF5EE', '#8DA898', '#1F3A2B',
    ['#15803D', '#65A30D', '#0F766E', '#166534', '#CA8A04', '#B91C1C', '#3F5A4B', '#0E7490']),
  T('sunset', false, '#140B1F', '#3A1340', '#1E1230', '#2A1A40', '#382454', '#FB923C', '#FDBA74', '#1F0A00', '#F472B6', '#FBEFF5', '#B09BBF', '#3A2A50',
    ['#EA580C', '#DB2777', '#9333EA', '#C2410C', '#E11D48', '#BE123C', '#5B4570', '#7C2D12']),
  T('amoled', false, '#000000', '#000000', '#0C0C0C', '#161616', '#242424', '#FFFFFF', '#E0E0E0', '#000000', '#9CA3AF', '#F2F2F2', '#8C8C8C', '#222222',
    ['#262626', '#262626', '#262626', '#262626', '#262626', '#262626', '#262626', '#262626']),
];

export const WP = 'wp:';

export function activeWallpaper(s) {
  s = s || store.settings.get();
  return s.theme.indexOf(WP) === 0 ? (s.wallpapers || []).find((w) => WP + w.id === s.theme) || null : null;
}

function hexToRgb(hex) { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
function rgbToHex(r, g, b) { return '#' + ((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1).toUpperCase(); }
function mix(hex, toHex, f) { const a = hexToRgb(hex), b = hexToRgb(toHex); return rgbToHex(...a.map((v, i) => Math.round(v + (b[i] - v) * f))); }
function lum(hex) { const [r, g, b] = hexToRgb(hex).map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; }
function rgba(hex, a) { const [r, g, b] = hexToRgb(hex); return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')'; }

/** Builds a dark theme from a wallpaper's extracted colours; panels are translucent so the picture shows. */
export function wallpaperTheme(w) {
  let accent = w.accent || '#22D3EE';
  let guard = 0;
  while (lum(accent) < 0.25 && guard++ < 8) accent = mix(accent, '#FFFFFF', 0.2);
  const base = w.base || '#1E293B';
  const tiles = (w.tiles && w.tiles.length ? w.tiles : [accent]).concat(Array(8).fill(accent)).slice(0, 8).map((t) => {
    let c = t, g = 0; while (lum(c) > 0.35 && g++ < 8) c = mix(c, '#000000', 0.2); return c;
  });
  return {
    id: WP + w.id, light: false, bg: mix(base, '#000000', 0.82), glow: 'transparent',
    s1: rgba(mix(base, '#000000', 0.55), 0.78), s2: rgba(mix(base, '#000000', 0.4), 0.84), s3: rgba(mix(base, '#000000', 0.2), 0.92),
    accent, accentFocus: mix(accent, '#FFFFFF', 0.3), onAccent: lum(accent) > 0.45 ? '#0A0A0A' : '#FFFFFF', accent2: w.accent2 || accent,
    text: '#F5F7FA', muted: '#C2C9D4', line: 'rgba(255,255,255,0.16)', tiles,
  };
}

export function currentTheme(s) {
  s = s || store.settings.get();
  const w = activeWallpaper(s);
  if (w) return wallpaperTheme(w);
  return THEMES.find((t) => t.id === s.theme) || THEMES[0];
}

/** Applies the theme as CSS variables + background picture. */
export function applyTheme() {
  const s = store.settings.get();
  const th = currentTheme(s);
  const r = document.documentElement.style;
  r.setProperty('--bg', th.bg); r.setProperty('--glow', th.glow);
  r.setProperty('--s1', th.s1); r.setProperty('--s2', th.s2); r.setProperty('--s3', th.s3);
  r.setProperty('--accent', th.accent); r.setProperty('--accent-f', th.accentFocus); r.setProperty('--on-accent', th.onAccent);
  r.setProperty('--accent2', th.accent2); r.setProperty('--text', th.text); r.setProperty('--muted', th.muted); r.setProperty('--line', th.line);
  th.tiles.forEach((c, i) => r.setProperty('--t' + i, c));
  document.documentElement.classList.toggle('light', !!th.light);
  const bgImg = document.getElementById('bg-img');
  const bgDim = document.getElementById('bg-dim');
  const w = activeWallpaper(s);
  if (bgImg) {
    bgImg.style.backgroundImage = w ? 'url("' + w.url.replace(/"/g, '%22') + '")' : 'none';
    bgDim.style.background = w ? 'linear-gradient(rgba(0,0,0,' + Math.max(0, w.dim - 0.15) + '), rgba(0,0,0,' + w.dim + ') 50%, rgba(0,0,0,' + Math.min(0.95, w.dim + 0.2) + '))' : '';
  }
  r.setProperty('--panel-a', String(Math.max(0.3, Math.min(1, (s.panelOpacity || 85) / 100))));
  // interface size: everything is in rem relative to a 1920px wide design
  const scale = (window.innerWidth / 1920) * 16 * (s.uiScale || 1);
  document.documentElement.style.fontSize = scale + 'px';
}

/** Loads an image and extracts dominant / vibrant colours. Falls back to defaults when the server blocks reading pixels. */
export function createWallpaper(url, name) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    let triedPlain = false;
    img.onload = () => {
      let colors = null;
      try { colors = extractColors(img); } catch (e) { colors = null; }
      resolve(Object.assign({ id: uid(), name, url, dim: 0.55 }, colors || { base: '#1E293B', accent: '#22D3EE', accent2: '#F472B6', tiles: [] }));
    };
    img.onerror = () => {
      if (!triedPlain) { triedPlain = true; img.removeAttribute('crossorigin'); img.crossOrigin = null; img.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + '_z=1'; return; }
      reject(new Error('Image could not be loaded'));
    };
    img.src = url;
  });
}

function extractColors(img) {
  const c = document.createElement('canvas');
  const W = 64, H = Math.max(1, Math.round(64 * img.height / img.width));
  c.width = W; c.height = H;
  const ctx = c.getContext('2d');
  ctx.drawImage(img, 0, 0, W, H);
  const d = ctx.getImageData(0, 0, W, H).data; // throws when tainted
  const buckets = {};
  for (let i = 0; i < d.length; i += 4) {
    const r = d[i], g = d[i + 1], b = d[i + 2];
    const k = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
    const bk = buckets[k] || (buckets[k] = { n: 0, r: 0, g: 0, b: 0 });
    bk.n++; bk.r += r; bk.g += g; bk.b += b;
  }
  const list = Object.keys(buckets).map((k) => {
    const bk = buckets[k]; const r = bk.r / bk.n, g = bk.g / bk.n, b = bk.b / bk.n;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b);
    return { n: bk.n, hex: rgbToHex(Math.round(r), Math.round(g), Math.round(b)), sat: mx ? (mx - mn) / mx : 0, v: mx / 255 };
  }).sort((a, b) => b.n - a.n);
  const dominant = list[0];
  const vivid = list.filter((x) => x.sat > 0.35 && x.v > 0.35).sort((a, b) => (b.n * b.sat) - (a.n * a.sat));
  const dark = list.filter((x) => x.v < 0.45).sort((a, b) => b.n - a.n);
  const accent = (vivid[0] || dominant).hex;
  const accent2 = (vivid.find((x) => Math.abs(lum(x.hex) - lum(accent)) > 0.1) || vivid[1] || dominant).hex;
  return {
    base: (dark[0] || dominant).hex, accent, accent2,
    tiles: vivid.slice(0, 7).map((x) => x.hex).concat(dark.slice(0, 2).map((x) => x.hex)),
  };
}
