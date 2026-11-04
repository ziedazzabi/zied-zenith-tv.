// Movies / Series browser: categories on the side, poster grid.
import { h, add, clear } from '../util.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { chip, toast, empty, spinner } from '../ui.js';
import { VList } from '../vlist.js';
import { shell, catalogGate, posterCard, categoryItem, withUnlock, categoryMenu } from './common.js';

const ALL = '__all', FAV = '__fav', CONT = '__cont';

export function VodScreen(params, nav) {
  const kind = params.kind || 'movie';
  const sh = shell(t(kind === 'movie' ? 'movies' : 'series'), nav, { cls: 'vod' });
  let selected = ALL, sort = 'default';
  let items = [];
  const specials = () => [
    { id: ALL, name: t('all'), kind, special: true, icon: 'grid' },
    { id: FAV, name: t('my_list'), kind, special: true, icon: 'heart' },
    { id: CONT, name: t('continue_watching'), kind, special: true, icon: 'play' },
  ];
  const catList = new VList({
    itemH: 3.6, gap: 0.3, className: 'cats',
    render: (c) => categoryItem(c, {
      selected: c.id === selected, count: c.special || c.lazy ? -1 : repo.items(kind, c.id).length,
      click: () => withUnlock(c, () => selectCat(c)), long: () => { if (!c.special) categoryMenu(c, buildCats); },
    }),
  });
  const grid = new VList({
    itemH: 21.5, itemW: 11.2, gap: 1.1, className: 'pgrid',
    render: (m) => {
      const p = store.profileData.get().progress[m.key];
      return posterCard(m, {
        progress: p ? p.positionMs / p.durationMs : 0,
        click: () => nav.open(m),
        long: () => { toast(t(store.toggleFavorite(m.key) ? 'added_my_list' : 'removed_my_list')); grid.refresh(); },
      });
    },
  });
  const sorts = h('div', { class: 'hrow fgroup sorts' });
  const status = h('div', { class: 'grid-status' });
  const layout = h('div', { class: 'vod-layout' }, catList.el, h('div', { class: 'vod-main' }, sorts, status, grid.el));

  function buildCats() { catList.setItems(specials().concat(repo.categories(kind)), true); }

  function drawSorts() {
    clear(sorts);
    [['default', 'sort_default'], ['az', 'sort_az'], ['new', 'sort_newest'], ['rating', 'sort_rating']].forEach(([k, l]) =>
      sorts.appendChild(chip(t(l), sort === k, () => { sort = k; drawSorts(); fill(true); })));
  }

  function compute() {
    const d = store.profileData.get();
    let base;
    if (selected === ALL) base = repo.items(kind, null);
    else if (selected === FAV) base = d.favorites.map((k) => repo.get(k)).filter((m) => m && m.kind === kind);
    else if (selected === CONT) base = Object.keys(d.progress).map((k) => d.progress[k]).sort((a, b) => b.updatedAt - a.updatedAt).map((p) => repo.get(p.key.split('#')[0])).filter((m, i, a) => m && m.kind === kind && a.indexOf(m) === i);
    else base = repo.items(kind, selected);
    base = base.slice();
    if (sort === 'az') base.sort((a, b) => a.name.localeCompare(b.name));
    else if (sort === 'new') base.sort((a, b) => (b.added || parseInt(b.year, 10) || 0) - (a.added || parseInt(a.year, 10) || 0));
    else if (sort === 'rating') base.sort((a, b) => (parseFloat(b.rating) || 0) - (parseFloat(a.rating) || 0));
    return base;
  }

  function fill(keep) {
    items = compute();
    grid.setItems(items, keep);
    clear(status);
    if (!items.length) status.appendChild(empty(t('nothing_here')));
  }

  async function selectCat(c) {
    selected = c.id;
    catList.refresh();
    if (c.lazy && !c._loaded) {
      clear(status); add(status, spinner());
      grid.setItems([]);
      await repo.loadLazy(c);
      if (selected !== c.id) return;
    }
    fill();
  }

  let stopGate = null;
  const offs = [];
  const render = () => {
    clear(sh.body);
    sh.body.appendChild(layout);
    requestAnimationFrame(() => { buildCats(); drawSorts(); fill(true); catList.refresh(); grid.refresh(); grid.focusCurrent(); });
  };

  return {
    el: sh.el,
    onShow(fresh) {
      stopGate = catalogGate(sh.body, nav, render);
      offs.push(store.profileData.subscribe(() => { if (selected === FAV || selected === CONT) fill(true); }));
    },
    onHide() { if (stopGate) stopGate(); offs.splice(0).forEach((f) => f()); },
    destroy() { sh.destroy(); },
    focusFirst() {},
  };
}
