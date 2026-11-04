// Streaming XMLTV parser: reads huge (gzipped) guides chunk by chunk and keeps only the channels we have.
import pako from 'pako';
import { streamText } from './net.js';
import { norm } from './util.js';

export function parseXmltvTime(s) {
  if (!s) return 0;
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{2}):?(\d{2})?/.exec(s.trim());
  if (!m) return 0;
  let t = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0));
  if (m[7]) {
    const sign = m[7][0] === '-' ? -1 : 1;
    const off = (Math.abs(parseInt(m[7], 10)) * 60 + parseInt(m[8] || '0', 10)) * sign;
    t -= off * 60000;
  }
  return t;
}

const ENT = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
export function decodeXml(s) {
  if (s.indexOf('&') < 0) return s;
  return s.replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (m, e) => {
    if (e[0] === '#') { const n = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10); return isNaN(n) ? m : String.fromCodePoint(n); }
    return ENT[e] != null ? ENT[e] : m;
  });
}

function attr(tag, name) {
  const m = new RegExp('\\s' + name + '\\s*=\\s*"([^"]*)"').exec(tag) || new RegExp('\\s' + name + "\\s*=\\s*'([^']*)'").exec(tag);
  return m ? decodeXml(m[1]) : '';
}

function inner(block, tag) {
  const m = new RegExp('<' + tag + '\\b[^>]*>([\\s\\S]*?)</' + tag + '>').exec(block);
  return m ? decodeXml(m[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').trim()) : '';
}

/**
 * @param url      XMLTV address
 * @param channels playlist channels [{epg, name}] — used to keep only what we need
 * @param offsetMin EPG shift in minutes
 * @returns Map epgIdLower -> [{start, stop, title, desc}]
 */
export async function loadXmltv(url, channels, offsetMin, onProgress) {
  const wanted = new Set();
  const byName = new Map();
  channels.forEach((c) => {
    if (c.epg) wanted.add(c.epg.toLowerCase());
    const n = norm(c.name).replace(/\s+/g, '');
    if (n && !byName.has(n)) byName.set(n, (c.epg || c.name).toLowerCase());
  });
  const alias = new Map(); // xmltv channel id -> our id
  const out = new Map();
  const now = Date.now(), from = now - 86400000 * 2, to = now + 86400000 * 4;
  const off = (offsetMin || 0) * 60000;
  let buf = '';
  let count = 0, bytes = 0;

  const handle = () => {
    let p = 0;
    for (;;) {
      const ci = buf.indexOf('<channel', p), pi = buf.indexOf('<programme', p);
      let i = -1, isProg = false;
      if (pi >= 0 && (ci < 0 || pi < ci)) { i = pi; isProg = true; } else if (ci >= 0) i = ci;
      if (i < 0) { buf = buf.length - p > 200 ? buf.slice(-200) : buf.slice(p); return; }
      const closeTag = isProg ? '</programme>' : '</channel>';
      const headEnd = buf.indexOf('>', i);
      if (headEnd < 0) { buf = buf.slice(i); return; }
      let end;
      if (buf[headEnd - 1] === '/') end = headEnd + 1;
      else { const j = buf.indexOf(closeTag, headEnd); if (j < 0) { buf = buf.slice(i); return; } end = j + closeTag.length; }
      const block = buf.slice(i, end);
      p = end;
      if (isProg) {
        const ch = attr(block, 'channel').toLowerCase();
        const id = wanted.has(ch) ? ch : alias.get(ch);
        if (!id) continue;
        const start = parseXmltvTime(attr(block, 'start')) + off;
        const stop = parseXmltvTime(attr(block, 'stop')) + off;
        if (!start || stop < from || start > to) continue;
        let list = out.get(id);
        if (!list) { list = []; out.set(id, list); }
        list.push({ start, stop: stop || start + 3600000, title: inner(block, 'title'), desc: inner(block, 'desc').slice(0, 400) });
        count++;
      } else {
        const id = attr(block, 'id').toLowerCase();
        if (!id || wanted.has(id)) continue;
        const re = /<display-name\b[^>]*>([\s\S]*?)<\/display-name>/g; let m;
        while ((m = re.exec(block))) {
          const dn = norm(decodeXml(m[1])).replace(/\s+/g, '');
          const target = wanted.has(dn) ? dn : byName.get(dn);
          if (target) { alias.set(id, target); break; }
        }
      }
    }
  };

  await streamText(url, (text) => {
    buf += text; bytes += text.length;
    handle();
    if (onProgress && (bytes & 0xfffff) < text.length) onProgress(count);
  }, { pako });
  handle();
  out.forEach((list) => list.sort((a, b) => a.start - b.start));
  return out;
}
