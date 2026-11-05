// Reusable UI pieces built for the remote control.
import { h, clear, hashCode } from './util.js';
import { icon } from './icons.js';
import { Focus } from './focus.js';
import { t } from './i18n.js';

/** Makes an element focusable and clickable. */
export function fz(el, o) {
  el.classList.add('f');
  if (o) {
    if (o.click) el._click = o.click;
    if (o.long) el._long = o.long;
    if (o.key) el._key = o.key;
    if (o.focus) el._onfocus = o.focus;
    if (o.blur) el._onblur = o.blur;
    if (o.auto) el.classList.add('autofocus');
  }
  return el;
}

export function button(label, ic, click, o) {
  o = o || {};
  return fz(h('div', { class: 'btn' + (o.primary ? ' primary' : '') + (o.danger ? ' danger' : '') + (o.cls ? ' ' + o.cls : '') },
    ic ? icon(ic) : null, label ? h('span', { text: label }) : null), { click, long: o.long, auto: o.auto });
}

export function iconButton(ic, click, o) {
  o = o || {};
  return fz(h('div', { class: 'ibtn' + (o.cls ? ' ' + o.cls : ''), title: o.title || '' }, icon(ic)), { click, auto: o.auto });
}

export function chip(label, selected, click, o) {
  o = o || {};
  return fz(h('div', { class: 'chip' + (selected ? ' sel' : '') }, o.icon ? icon(o.icon) : null, h('span', { text: label })), { click, long: o.long });
}

export function letterTile(name) {
  const letter = (String(name || '').trim().match(/[A-Za-z0-9\u00C0-\u024F\u0600-\u06FF\u0400-\u04FF]/) || ['•'])[0].toUpperCase();
  const hue = Math.abs(hashCode(String(name))) % 360;
  return h('div', { class: 'letter', style: 'background:linear-gradient(135deg,hsl(' + hue + ',45%,32%),var(--s3))' }, letter);
}

/** Image with a coloured initial while loading / when missing. */
export function logo(url, name, cls) {
  const box = h('div', { class: 'logo ' + (cls || '') }, letterTile(name));
  if (url) {
    const img = h('img', { alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' });
    img.onload = () => { box.classList.add('ok'); };
    img.onerror = () => { img.remove(); };
    img.src = url;
    box.appendChild(img);
  }
  return box;
}

export function progressBar(frac, cls) {
  return h('div', { class: 'pbar ' + (cls || '') }, h('div', { style: 'width:' + Math.round(Math.max(0, Math.min(1, frac)) * 100) + '%' }));
}

let toastTimer = 0;
export function toast(msg, ms) {
  let el = document.getElementById('toast');
  if (!el) { el = h('div', { id: 'toast' }); document.body.appendChild(el); }
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), ms || 2600);
}

/**
 * Modal dialog on its own focus layer. BACK closes it.
 * o: {title, body (Node|Node[]), buttons: [[label, fn, {primary}]], wide, onClose, cls}
 */
export function dialog(o) {
  const root = h('div', { class: 'modal' });
  const box = h('div', { class: 'dialog' + (o.wide ? ' wide' : '') + (o.cls ? ' ' + o.cls : '') });
  if (o.title) box.appendChild(h('div', { class: 'dlg-title', text: o.title }));
  const body = h('div', { class: 'dlg-body' });
  [].concat(o.body || []).forEach((b) => b && body.appendChild(b));
  box.appendChild(body);
  let closed = false;
  const close = () => {
    if (closed) return; closed = true;
    root.remove(); Focus.popLayer(root);
    if (o.onClose) o.onClose();
  };
  if (o.buttons && o.buttons.length) {
    box.appendChild(h('div', { class: 'dlg-buttons' }, o.buttons.map((b, i) => button(b[0], b[2] && b[2].icon, () => { const keep = b[1] && b[1](); if (keep !== true) close(); },
      { primary: b[2] && b[2].primary, danger: b[2] && b[2].danger, auto: i === 0 && !(o.focusBody) }))));
  }
  root.appendChild(box);
  root._key = (name) => { if (name === 'back') { close(); return true; } return o.key ? o.key(name) : false; };
  root.addEventListener('click', (e) => { if (e.target === root) close(); });
  document.body.appendChild(root);
  Focus.pushLayer(root);
  requestAnimationFrame(() => Focus.first(root));
  return { close, root, body };
}

export function confirmDialog(title, msg, okLabel, onOk, danger) {
  return dialog({ title, body: msg ? h('div', { class: 'muted', text: msg }) : null, buttons: [[okLabel || t('ok'), onOk, { primary: !danger, danger }], [t('cancel'), null]] });
}

/** List of options; current one is checked and focused. */
export function choiceDialog(title, options, current, onPick) {
  const list = h('div', { class: 'choices scroll' });
  options.forEach(([k, label]) => {
    const row = fz(h('div', { class: 'choice' + (k === current ? ' sel' : '') }, h('span', { text: label }), k === current ? icon('check') : null), {
      click: () => { d.close(); onPick(k); }, auto: k === current,
    });
    list.appendChild(row);
  });
  const d = dialog({ title, body: list, buttons: [[t('close'), null]], focusBody: true });
  return d;
}

/** PIN entry with an on-screen keypad (number keys on the remote work too). */
export function pinDialog(title, onPin, autoLen) {
  let pin = '';
  const dots = h('div', { class: 'pin-dots' });
  const render = () => { clear(dots); for (let i = 0; i < Math.max(4, pin.length); i++) dots.appendChild(h('span', { class: i < pin.length ? 'on' : '' })); };
  render();
  const press = (k) => {
    if (k === 'del') pin = pin.slice(0, -1);
    else if (k === 'ok') { if (pin.length >= 4) { d.close(); onPin(pin); } return; }
    else if (pin.length < 8) pin += k;
    render();
    if (pin.length === 8 || (autoLen && pin.length === autoLen)) { d.close(); onPin(pin); }
  };
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'del', '0', 'ok'];
  const pad = h('div', { class: 'keypad' }, keys.map((k) => fz(h('div', { class: 'key' + (k === 'ok' ? ' primary' : '') },
    k === 'del' ? icon('back') : k === 'ok' ? icon('check') : k), { click: () => press(k), auto: k === '5' })));
  const d = dialog({
    title: title || t('enter_pin'), body: [dots, pad], cls: 'pin',
    key: (name) => { if (/^d\d$/.test(name)) { press(name.slice(1)); return true; } return false; },
  });
  return d;
}

/** Labelled text field. OK opens the TV keyboard. */
export function field(label, value, o) {
  o = o || {};
  const input = h('input', { class: 'f', type: o.password ? 'password' : (o.type || 'text'), value: value || '', placeholder: o.placeholder || '', autocomplete: 'off', spellcheck: 'false', dir: o.dir || 'auto' });
  if (o.onInput) input.addEventListener('input', () => o.onInput(input.value));
  input.addEventListener('keydown', (e) => {
    // TV keyboards send OK/Done as Enter
    if (e.keyCode === 13 || e.keyCode === 65376) { e.preventDefault(); input.blur(); if (o.onSubmit) o.onSubmit(input.value); }
  });
  if (o.onSubmit) input._submit = () => o.onSubmit(input.value);
  const wrap = h('label', { class: 'field' + (o.cls ? ' ' + o.cls : '') }, h('span', { class: 'flabel', text: label }), input);
  wrap.input = input;
  if (!o.password) return wrap;
  // password: hidden by default, with a "Show password" switch under it
  const lbl = h('span', { text: t('show_password') });
  const ic = h('span', { class: 'f-eye-ic' }, icon('eye'));
  const toggle = fz(h('div', { class: 'f-eye' }, ic, lbl), {
    click: () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      toggle.classList.toggle('on', show);
      ic.innerHTML = ''; ic.appendChild(icon(show ? 'hide' : 'eye'));
      lbl.textContent = t(show ? 'hide_password' : 'show_password');
    },
  });
  const box = h('div', { class: 'field-pw' }, wrap, toggle);
  box.input = input;
  return box;
}

export function toggleRow(ic, label, value, onChange) {
  const sw = h('div', { class: 'switch' + (value ? ' on' : '') }, h('div'));
  const row = fz(h('div', { class: 'srow' }, icon(ic), h('span', { class: 'grow', text: label }), sw), {
    click: () => { value = !value; sw.classList.toggle('on', value); onChange(value); },
  });
  return row;
}

export function settingRow(ic, label, value, onClick) {
  const v = h('span', { class: 'sval', text: value || '' });
  const row = fz(h('div', { class: 'srow' }, icon(ic), h('span', { class: 'grow', text: label }), v, icon('chevron', 'chev')), { click: onClick });
  row.setValue = (x) => { v.textContent = x; };
  return row;
}

export function sectionTitle(text, ic) { return h('div', { class: 'section' }, ic ? icon(ic) : null, h('span', { text })); }

export function empty(text) { return h('div', { class: 'empty', text }); }

export function spinner() { return h('div', { class: 'spinner' }); }
