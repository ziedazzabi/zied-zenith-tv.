import { h, clear } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { store } from '../store.js';
import { repo } from '../repo.js';
import { fz, dialog, field, toggleRow, confirmDialog, pinDialog, toast } from '../ui.js';
import { shell } from './common.js';
import { Focus } from '../focus.js';

const colorCss = (c) => '#' + (c >>> 0).toString(16).slice(2);

export function ProfilesScreen(params, nav) {
  const pick = !!params.pick;
  const sh = shell(pick ? t('whos_watching') : t('profiles'), nav, { noBack: pick, cls: 'profiles' });
  const grid = h('div', { class: 'prof-grid fgroup' });
  sh.body.appendChild(grid);

  function choose(p) {
    const go = () => { store.switchProfile(p.id); repo.emit('catalog'); nav.home(); };
    if (p.kids || !store.activeProfile().kids || p.id === store.activeProfileId()) go();
    else pinDialog(t('enter_pin'), (pin) => { if (pin === store.settings.get().parentalPin) go(); else toast(t('wrong_pin')); }, store.settings.get().parentalPin.length);
  }

  function draw() {
    clear(grid);
    const active = store.activeProfileId();
    store.profiles.get().forEach((p) => grid.appendChild(fz(h('div', { class: 'prof' + (p.id === active ? ' sel' : '') },
      h('div', { class: 'prof-av', style: 'background:' + colorCss(p.color) }, p.name.slice(0, 1).toUpperCase(), p.kids ? icon('kids', 'kid') : null),
      h('div', { class: 'prof-name', text: p.name })), {
      click: () => choose(p), long: () => edit(p), auto: p.id === active,
    })));
    grid.appendChild(fz(h('div', { class: 'prof add' }, h('div', { class: 'prof-av' }, icon('add')), h('div', { class: 'prof-name', text: t('add_profile') })), { click: () => edit(null) }));
    grid.appendChild(h('div', { class: 'muted small full', text: t('profile_hint') }));
  }

  function edit(p) {
    let name = p ? p.name : '', kids = p ? p.kids : false;
    const f = field(t('name'), name, { onInput: (v) => { name = v; } });
    const k = toggleRow('kids', t('kids_profile'), kids, (v) => { kids = v; });
    const buttons = [[t('save'), () => {
      if (!name.trim()) { toast(t('name')); return true; }
      if (p) store.updateProfile(Object.assign({}, p, { name: name.trim(), kids })); else store.addProfile(name.trim(), kids);
      draw(); Focus.ensure();
    }, { primary: true }]];
    if (p && store.profiles.get().length > 1) buttons.push([t('delete'), () => confirmDialog(t('delete_profile_q'), p.name, t('delete'), () => { store.deleteProfile(p.id); draw(); Focus.ensure(); }, true), { danger: true }]);
    buttons.push([t('cancel'), null]);
    dialog({ title: p ? t('edit_profile') : t('add_profile'), body: [f, k], buttons });
  }

  draw();
  return { el: sh.el, destroy: sh.destroy, onShow(fresh) { if (!fresh) draw(); } };
}
