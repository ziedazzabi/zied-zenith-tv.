// HTTP helpers. XHR is used (not fetch) because it works on every TV generation and supports timeouts.

export function request(url, opts) {
  opts = opts || {};
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open(opts.method || 'GET', url, true);
    x.timeout = opts.timeout || 30000;
    if (opts.responseType) x.responseType = opts.responseType;
    const hd = opts.headers || {};
    for (const k in hd) {
      // browsers refuse some headers (Cookie, User-Agent); ignore those quietly
      try { x.setRequestHeader(k, hd[k]); } catch (e) { /* forbidden header */ }
    }
    if (opts.withCredentials) x.withCredentials = true;
    x.onload = () => {
      if (x.status >= 200 && x.status < 400) resolve(x);
      else reject(new Error('HTTP ' + x.status));
    };
    x.onerror = () => reject(new Error('Network error — check the address and your internet connection'));
    x.ontimeout = () => reject(new Error('The server took too long to answer'));
    x.send(opts.body || null);
  });
}

export async function getText(url, opts) {
  const x = await request(url, opts);
  return x.responseText;
}

export async function getJson(url, opts) {
  const text = await getText(url, opts);
  try { return JSON.parse(text); } catch (e) {
    const i = text.indexOf('{'), j = text.indexOf('[');
    const start = i < 0 ? j : j < 0 ? i : Math.min(i, j);
    if (start > 0) { try { return JSON.parse(text.slice(start)); } catch (e2) { /* fall through */ } }
    throw new Error('The server sent an unexpected answer');
  }
}

export async function getBytes(url, opts) {
  const x = await request(url, Object.assign({}, opts, { responseType: 'arraybuffer' }));
  return new Uint8Array(x.response);
}

/** Streams a (possibly gzipped) text document chunk by chunk. onText(chunk) receives decoded text. */
export async function streamText(url, onText, opts) {
  opts = opts || {};
  const pako = opts.pako;
  const decoder = new TextDecoder('utf-8');
  let inflator = null, sniffed = false;
  const feed = (bytes, last) => {
    if (!sniffed) {
      sniffed = true;
      if (bytes.length > 1 && bytes[0] === 0x1f && bytes[1] === 0x8b && pako) {
        inflator = new pako.Inflate();
        inflator.onData = (chunk) => onText(decoder.decode(chunk, { stream: true }));
      }
    }
    if (inflator) inflator.push(bytes, !!last);
    else onText(decoder.decode(bytes, { stream: !last }));
  };

  if (window.fetch && window.ReadableStream) {
    const res = await fetch(url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (res.body && res.body.getReader) {
      const reader = res.body.getReader();
      for (;;) {
        const r = await reader.read();
        if (r.done) { feed(new Uint8Array(0), true); break; }
        feed(r.value, false);
      }
      return;
    }
    feed(new Uint8Array(await res.arrayBuffer()), true);
    return;
  }
  const bytes = await getBytes(url, { timeout: 120000 });
  feed(bytes, true);
}
