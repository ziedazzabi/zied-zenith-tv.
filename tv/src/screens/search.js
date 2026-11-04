import { h, clear, debounce } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { field, empty } from '../ui.js';
import { shell, posterCard, channelCard, hrow } from './common.js';
import { Focus } from '../focus.js';

let lastQuery = '';

export function SearchScreen(params, nav) {
  const sh = shell(t('search'), nav, { cls: 'search' });
  const results = h('div', { class: 'results scroll' });
  const f = field(t('search_hint'), lastQuery, { onInput: debounce((v) => run(v), 350), onSubmit: (v) => { run(v); setTimeout(() => Focus.first(results), 50); } });
  f.input.classList.add('autofocus');
  sh.body.appendChild(h('div', { class: 'search-box' }, icon('search'), f));
  sh.body.appendChild(results);

  function run(q) {
    lastQuery = q;
    clear(results);
    if (q.trim().length < 2) { results.appendChild(h('div', { class: 'muted pad', text: t('search_min') })); return; }
    const r = repo.search(q);
    if (!r.live.length && !r.movie.length && !r.series.length) { results.appendChild(empty(t('no_results'))); return; }
    if (r.live.length) { results.appendChild(h('div', { class: 'section' }, icon('live'), h('span', { text: t('live_tv') + ' (' + r.live.length + ')' }))); results.appendChild(hrow(r.live.slice(0, 60).map((m) => channelCard(m, () => nav.open(m, r.live))))); }
    if (r.movie.length) { results.appendChild(h('div', { class: 'section' }, icon('movie'), h('span', { text: t('movies') + ' (' + r.movie.length + ')' }))); results.appendChild(hrow(r.movie.slice(0, 60).map((m) => posterCard(m, { click: () => nav.open(m) })))); }
    if (r.series.length) { results.appendChild(h('div', { class: 'section' }, icon('series'), h('span', { text: t('series') + ' (' + r.series.length + ')' }))); results.appendChild(hrow(r.series.slice(0, 60).map((m) => posterCard(m, { click: () => nav.open(m) })))); }
  }
  run(lastQuery);

  return { el: sh.el, destroy: sh.destroy };
}
