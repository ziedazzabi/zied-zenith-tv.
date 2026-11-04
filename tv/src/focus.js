// Remote-control navigation: arrow keys move focus between elements with class "f".
// Elements can define: _click(), _long(), _onfocus(), _onblur(), _key(name) -> true if consumed.
// Any ancestor can define _nav(dir, el) -> true to handle movement itself (virtual lists),
// and _key(name, el) for screen-level keys. Class "fgroup" remembers the last focused child.

const layers = [];
let current = null;
let okDownAt = 0, okTimer = 0, okLongFired = false;

export const Focus = {
  get current() { return current; },

  pushLayer(root) { layers.push({ root, prev: current }); },
  popLayer(root) {
    const i = layers.findIndex((l) => l.root === root);
    if (i < 0) return;
    const l = layers.splice(i, 1)[0];
    if (l.prev && l.prev.isConnected) Focus.set(l.prev);
    else Focus.first();
  },
  layer() { return layers.length ? layers[layers.length - 1].root : document.body; },
  resetLayers(root) { layers.length = 0; layers.push({ root, prev: null }); },

  set(el, opts) {
    if (!el || el === current) { if (el && !(opts && opts.noScroll)) scrollIntoViewSafe(el); return; }
    if (current) {
      current.classList.remove('focused');
      if (current._onblur) try { current._onblur(); } catch (e) { console.error(e); }
    }
    current = el;
    el.classList.add('focused');
    let g = el.parentElement;
    while (g) { if (g.classList && g.classList.contains('fgroup')) g._last = el; g = g.parentElement; }
    if (!(opts && opts.noScroll)) scrollIntoViewSafe(el);
    if (el._onfocus) try { el._onfocus(); } catch (e) { console.error(e); }
  },

  /** Focuses the first (or remembered/autofocus) element in the active layer or in root. */
  first(root) {
    root = root || Focus.layer();
    const auto = root.querySelector('.f.autofocus');
    if (auto && visible(auto)) return Focus.set(auto);
    const all = candidates(root);
    // start on the content, not on the header's back / search buttons
    const content = all.filter((el) => !el.closest('.hdr'));
    const pick = content[0] || all[0];
    if (pick) Focus.set(pick);
  },

  /** Refocuses something sensible if the focused element disappeared. */
  ensure() {
    const layer = Focus.layer();
    if (!current || !current.isConnected || !layer.contains(current) || !visible(current)) Focus.first(layer);
  },

  move(dir) {
    const layer = Focus.layer();
    if (!current || !current.isConnected || !layer.contains(current)) { Focus.first(layer); return true; }
    // components that manage their own movement (virtual lists)
    for (let a = current; a && a !== document.body; a = a.parentElement) {
      if (a._nav && a._nav(dir, current)) return true;
      if (a === layer) break;
    }
    const next = findNext(current, dir, layer);
    if (next) { Focus.set(next); return true; }
    return false;
  },

  /** Dispatches a semantic key; returns true if something consumed it. */
  dispatch(name, e) {
    const active = document.activeElement;
    if (active && /INPUT|TEXTAREA/.test(active.tagName)) {
      if (name === 'ok' || name === 'back' || name === 'up' || name === 'down') {
        active.blur();
        if (name === 'ok' && active._submit) active._submit();
        if (name === 'up' || name === 'down') Focus.move(name);
        return true;
      }
      return false; // let the IME / input handle left/right/typing
    }
    if (name === 'ok') return false; // handled through keydown/keyup for long-press
    if (name === 'up' || name === 'down' || name === 'left' || name === 'right') {
      if (current && current._key && current._key(name, current)) return true;
      if (bubbleKey(name)) return true;
      Focus.move(name);
      return true;
    }
    if (current && current._key && current._key(name, current)) return true;
    return bubbleKey(name);
  },

  okDown(e) {
    if (e && e.repeat) return;
    const active = document.activeElement;
    if (active && /INPUT|TEXTAREA/.test(active.tagName)) return;
    okDownAt = Date.now(); okLongFired = false;
    clearTimeout(okTimer);
    const el = current;
    okTimer = setTimeout(() => {
      if (el && el === current && el._long) { okLongFired = true; el._long(); }
    }, 650);
  },

  okUp() {
    clearTimeout(okTimer);
    if (!okDownAt) return;
    okDownAt = 0;
    if (okLongFired) return;
    activate(current);
  },

  /** Pointer (LG Magic Remote / mouse) support. */
  installPointer() {
    document.addEventListener('mouseover', (e) => {
      const el = e.target.closest ? e.target.closest('.f') : null;
      if (el && Focus.layer().contains(el) && el !== current) Focus.set(el, { noScroll: true });
    });
    document.addEventListener('click', (e) => {
      const el = e.target.closest ? e.target.closest('.f') : null;
      if (el && Focus.layer().contains(el)) { e.preventDefault(); Focus.set(el, { noScroll: true }); activate(el); }
    });
    document.addEventListener('contextmenu', (e) => {
      const el = e.target.closest ? e.target.closest('.f') : null;
      if (el && el._long) { e.preventDefault(); el._long(); }
    });
  },
};

function activate(el) {
  if (!el) return;
  if (/INPUT|TEXTAREA/.test(el.tagName)) { el.focus(); return; }
  if (el._click) el._click();
}

function bubbleKey(name) {
  const layer = Focus.layer();
  for (let a = current && current.parentElement; a; a = a.parentElement) {
    if (a._key && a._key(name, current)) return true;
    if (a === layer) break;
  }
  if (layer._key && layer._key(name, current)) return true;
  return false;
}

function visible(el) {
  if (!el.isConnected) return false;
  const r = el.getBoundingClientRect();
  if (r.width === 0 && r.height === 0) return false;
  for (let a = el; a && a !== document.body; a = a.parentElement) {
    const cs = a.style;
    if (cs && (cs.display === 'none' || cs.visibility === 'hidden')) return false;
    if (a.classList && a.classList.contains('hidden')) return false;
  }
  return true;
}

function candidates(root) {
  const list = root.querySelectorAll('.f');
  const out = [];
  for (let i = 0; i < list.length; i++) {
    const el = list[i];
    if (el.classList.contains('disabled')) continue;
    if (visible(el)) out.push(el);
  }
  return out;
}

function findNext(from, dir, layer) {
  const r = from.getBoundingClientRect();
  const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
  let best = null, bestScore = Infinity;
  const list = candidates(layer);
  for (const el of list) {
    if (el === from) continue;
    const b = el.getBoundingClientRect();
    const bx = b.left + b.width / 2, by = b.top + b.height / 2;
    let primary, secondary, overlap;
    if (dir === 'up') { if (b.bottom > r.top + 2 && by >= cy) continue; primary = r.top - b.bottom; secondary = Math.abs(bx - cx); overlap = Math.min(r.right, b.right) - Math.max(r.left, b.left); }
    else if (dir === 'down') { if (b.top < r.bottom - 2 && by <= cy) continue; primary = b.top - r.bottom; secondary = Math.abs(bx - cx); overlap = Math.min(r.right, b.right) - Math.max(r.left, b.left); }
    else if (dir === 'left') { if (b.right > r.left + 2 && bx >= cx) continue; primary = r.left - b.right; secondary = Math.abs(by - cy); overlap = Math.min(r.bottom, b.bottom) - Math.max(r.top, b.top); }
    else { if (b.left < r.right - 2 && bx <= cx) continue; primary = b.left - r.right; secondary = Math.abs(by - cy); overlap = Math.min(r.bottom, b.bottom) - Math.max(r.top, b.top); }
    if (primary < -2) primary = 0;
    const score = Math.max(primary, 0) + secondary * (overlap > 0 ? 0.3 : 2) + (overlap > 0 ? 0 : 400);
    if (score < bestScore) { bestScore = score; best = el; }
  }
  if (!best) return null;
  // entering a group: go back to its remembered child
  for (let g = best.parentElement; g && g !== layer; g = g.parentElement) {
    if (g.classList.contains('fgroup') && !g.contains(from) && g._last && g._last.isConnected && g.contains(g._last) && visible(g._last)) return g._last;
  }
  return best;
}

function scrollIntoViewSafe(el) {
  try {
    if (el.scrollIntoViewIfNeeded) el.scrollIntoViewIfNeeded(false);
    else el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  } catch (e) { /* ignore */ }
}
