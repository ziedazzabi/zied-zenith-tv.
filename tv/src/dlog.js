// Small in-memory log of what the player does, shown on screen (blue key) to find why a channel doesn't open.
const lines = [];
const subs = new Set();
const t0 = Date.now();

/** Hides credentials in stream addresses (…/live/user/pass/1.m3u8 → …/live/***\/1.m3u8). */
export function maskUrl(u) {
  return String(u || '')
    .replace(/(\/(?:live|movie|series|timeshift)\/)[^/]+\/[^/]+\//i, '$1***/')
    .replace(/(username|password)=[^&]*/gi, '$1=***');
}

export const DLog = {
  add(msg) {
    const s = ((Date.now() - t0) / 1000).toFixed(1) + 's ' + msg;
    lines.push(s); if (lines.length > 60) lines.shift();
    subs.forEach((f) => { try { f(); } catch (e) { /* ignore */ } });
  },
  lines() { return lines.slice(); },
  on(fn) { subs.add(fn); return () => subs.delete(fn); },
};
