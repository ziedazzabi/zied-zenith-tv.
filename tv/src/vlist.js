// Virtualized list / grid: only the visible rows exist in the DOM, so 50,000 channels stay fast on a TV.
import { h } from './util.js';
import { Focus } from './focus.js';
import { isRtl } from './i18n.js';

function remPx() { return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16; }

export class VList {
  /**
   * @param o.itemH  row height in rem
   * @param o.itemW  cell width in rem (grid only; 0 = full width list)
   * @param o.gap    gap in rem
   * @param o.render (item, index) => element with class "f"
   * @param o.onFocusIndex (index, item)
   */
  constructor(o) {
    this.o = Object.assign({ itemH: 4, itemW: 0, gap: 0.5, className: '' }, o);
    this.items = [];
    this.index = 0;
    this.scroll = 0;
    this.cols = 1;
    this.nodes = new Map();
    this.inner = h('div', { class: 'vinner' });
    this.el = h('div', { class: 'vlist fgroup ' + this.o.className }, this.inner);
    this.el._nav = (dir, cur) => this.nav(dir, cur);
    this.el.addEventListener('wheel', (e) => {
      e.preventDefault();
      this.setScroll(this.scroll + (e.deltaY > 0 ? 1 : -1) * this.rowPx() * 2);
    }, { passive: false });
  }

  rowPx() { return (this.o.itemH + this.o.gap) * remPx(); }
  colPx() { return (this.o.itemW + this.o.gap) * remPx(); }

  layout() {
    const w = this.el.clientWidth || 800;
    this.viewH = this.el.clientHeight || 600;
    this.cols = this.o.itemW > 0 ? Math.max(1, Math.floor((w + this.o.gap * remPx()) / this.colPx())) : 1;
    const rows = Math.ceil(this.items.length / this.cols);
    this.inner.style.height = Math.max(0, rows * this.rowPx()) + 'px';
  }

  setItems(items, keepIndex) {
    const hadFocus = Focus.current && Focus.current._vlist === this;
    this.items = items || [];
    if (!keepIndex) { this.index = 0; this.scroll = 0; }
    this.index = Math.min(this.index, Math.max(0, this.items.length - 1));
    this.nodes.forEach((n) => n.remove());
    this.nodes.clear();
    this.layout();
    this.clampScroll();
    this.render();
    if (hadFocus && this.items.length) this.focusIndex(this.index);
  }

  /** Re-renders the visible items (e.g. after a favorite changed). */
  refresh() {
    const focusedIdx = Focus.current && Focus.current._vlist === this ? Focus.current._vi : -1;
    this.nodes.forEach((n) => n.remove());
    this.nodes.clear();
    this.layout();
    this.render();
    if (focusedIdx >= 0) this.focusIndex(Math.min(focusedIdx, this.items.length - 1));
  }

  clampScroll() {
    const rows = Math.ceil(this.items.length / this.cols);
    const max = Math.max(0, rows * this.rowPx() - this.viewH);
    this.scroll = Math.max(0, Math.min(this.scroll, max));
  }

  setScroll(px) { this.scroll = px; this.clampScroll(); this.render(); }

  render() {
    const rp = this.rowPx();
    this.inner.style.transform = 'translateY(' + (-this.scroll) + 'px)';
    const first = Math.max(0, Math.floor(this.scroll / rp) - 1);
    const last = Math.min(Math.ceil(this.items.length / this.cols) - 1, Math.ceil((this.scroll + this.viewH) / rp) + 1);
    const from = first * this.cols, to = Math.min(this.items.length - 1, (last + 1) * this.cols - 1);
    const rtl = isRtl();
    this.nodes.forEach((n, i) => { if (i < from || i > to) { n.remove(); this.nodes.delete(i); } });
    for (let i = from; i <= to; i++) {
      if (this.nodes.has(i)) continue;
      const el = this.o.render(this.items[i], i);
      el._vi = i; el._vlist = this;
      const prevFocus = el._onfocus;
      el._onfocus = () => { this.index = i; if (this.o.onFocusIndex) this.o.onFocusIndex(i, this.items[i]); if (prevFocus) prevFocus(); };
      const row = Math.floor(i / this.cols), col = i % this.cols;
      el.style.position = 'absolute';
      el.style.top = (row * rp) + 'px';
      el.style.height = (this.o.itemH * remPx()) + 'px';
      if (this.o.itemW > 0) {
        el.style.width = (this.o.itemW * remPx()) + 'px';
        el.style[rtl ? 'right' : 'left'] = (col * this.colPx()) + 'px';
      } else { el.style.left = '0'; el.style.right = '0'; }
      this.inner.appendChild(el);
      this.nodes.set(i, el);
    }
  }

  ensureVisible(i) {
    const rp = this.rowPx();
    const top = Math.floor(i / this.cols) * rp;
    if (top < this.scroll) this.setScroll(top);
    else if (top + rp > this.scroll + this.viewH) this.setScroll(top + rp - this.viewH);
    else this.render();
  }

  focusIndex(i) {
    if (!this.items.length) return false;
    i = Math.max(0, Math.min(i, this.items.length - 1));
    this.index = i;
    this.ensureVisible(i);
    const el = this.nodes.get(i);
    if (el) Focus.set(el, { noScroll: true });
    return !!el;
  }

  focusCurrent() { return this.focusIndex(this.index); }

  nav(dir, cur) {
    if (cur._vlist !== this) return false;
    const i = cur._vi, c = this.cols;
    let n = -1;
    const rtl = isRtl();
    const leftD = rtl ? 1 : -1;
    if (dir === 'up') n = i - c;
    else if (dir === 'down') { n = i + c; if (n >= this.items.length && Math.floor(i / c) < Math.floor((this.items.length - 1) / c)) n = this.items.length - 1; }
    else if (dir === 'left' && c > 1) { const m = i + leftD; if (Math.floor(m / c) === Math.floor(i / c) && m >= 0 && m < this.items.length) n = m; }
    else if (dir === 'right' && c > 1) { const m = i - leftD; if (Math.floor(m / c) === Math.floor(i / c) && m >= 0 && m < this.items.length) n = m; }
    if (n < 0 || n >= this.items.length) return false;
    this.focusIndex(n);
    return true;
  }

  pageBy(rows) { this.focusIndex(this.index + rows * this.cols); }
}
