// Movie / series page: artwork, info, play/resume, seasons and episodes.
import { h, add, clear, fmtDuration } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { repo } from '../repo.js';
import { store } from '../store.js';
import { fz, button, chip, logo, progressBar, toast, spinner } from '../ui.js';
import { Focus } from '../focus.js';
import { shell } from './common.js';

export function DetailsScreen(params, nav) {
  const item = repo.get(params.key);
  const sh = shell(item ? item.name : '', nav, { cls: 'details' });
  if (!item) { sh.body.appendChild(h('div', { class: 'empty', text: t('nothing_here') })); return { el: sh.el, destroy: sh.destroy }; }
  let d = null, season = null;
  const backdrop = h('div', { class: 'backdrop' });
  const info = h('div', { class: 'd-info' });
  const actions = h('div', { class: 'd-actions fgroup' });
  const seasonsRow = h('div', { class: 'hrow fgroup seasons' });
  const episodes = h('div', { class: 'episodes fgroup' });
  const page = h('div', { class: 'd-page scroll' },
    h('div', { class: 'd-top' }, h('div', { class: 'd-poster' }, logo(item.logo, item.name, 'cover')), info), actions, seasonsRow, episodes);
  add(sh.body, backdrop, page);

  function drawInfo() {
    clear(info);
    const meta = [item.year || (d && d.releaseDate ? d.releaseDate.slice(0, 4) : ''), d && d.duration, d && d.genre].filter(Boolean).join('  ·  ');
    const rating = parseFloat((d && d.rating) || item.rating);
    add(info, h('div', { class: 'd-title', text: item.name }),
      h('div', { class: 'd-meta' }, rating > 0 ? h('span', { class: 'd-rating' }, icon('star'), (rating > 10 ? rating / 10 : rating).toFixed(1)) : null, h('span', { text: meta })),
      h('div', { class: 'd-plot', text: (d && d.plot) || item.plot || '' }),
      d && d.cast ? h('div', { class: 'd-small' }, h('b', { text: t('cast') + ': ' }), d.cast) : null,
      d && d.director ? h('div', { class: 'd-small' }, h('b', { text: t('director') + ': ' }), d.director) : null);
  }

  function drawActions() {
    clear(actions);
    const prog = store.profileData.get().progress;
    if (item.kind === 'movie') {
      const p = prog[item.key];
      if (p) {
        actions.appendChild(button(t('resume') + ' · ' + fmtDuration(p.positionMs), 'play', () => playMovie(p.positionMs), { primary: true, auto: true }));
        actions.appendChild(button(t('start_over'), 'replay', () => playMovie(0)));
      } else actions.appendChild(button(t('play'), 'play', () => playMovie(0), { primary: true, auto: true }));
    } else if (d && d.seasons) {
      const eps = allEpisodes();
      let last = -1, lastAt = 0;
      eps.forEach((e, i) => { const p = prog[epKey(e)]; if (p && p.updatedAt > lastAt) { last = i; lastAt = p.updatedAt; } });
      if (last >= 0) {
        const e = eps[last];
        actions.appendChild(button(t('resume') + ' S' + e.season + 'E' + e.number, 'play', () => playEpisode(last, prog[epKey(e)].positionMs), { primary: true, auto: true }));
      } else if (eps.length) actions.appendChild(button(t('play') + ' S' + eps[0].season + 'E' + eps[0].number, 'play', () => playEpisode(0, 0), { primary: true, auto: true }));
    }
    const fav = store.isFavorite(item.key);
    actions.appendChild(button(fav ? t('remove_my_list') : t('add_my_list'), fav ? 'check' : 'add', () => {
      toast(t(store.toggleFavorite(item.key) ? 'added_my_list' : 'removed_my_list')); drawActions();
    }));
  }

  const epKey = (e) => item.key + '#' + e.season + 'x' + e.number;
  function allEpisodes() {
    if (!d || !d.seasons) return [];
    return Object.keys(d.seasons).map(Number).sort((a, b) => a - b).reduce((acc, s) => acc.concat(d.seasons[s]), []);
  }

  function drawSeasons() {
    clear(seasonsRow); clear(episodes);
    if (!d || !d.seasons) return;
    const keys = Object.keys(d.seasons).map(Number).sort((a, b) => a - b);
    if (!keys.length) return;
    if (season == null || keys.indexOf(season) < 0) season = keys[0];
    keys.forEach((s) => seasonsRow.appendChild(chip(t('season_n', s), s === season, () => { season = s; drawSeasons(); })));
    const prog = store.profileData.get().progress;
    const all = allEpisodes();
    d.seasons[season].forEach((e) => {
      const p = prog[epKey(e)];
      episodes.appendChild(fz(h('div', { class: 'ep' },
        h('div', { class: 'ep-img' }, logo(e.cover || item.logo, e.title, 'cover'), p ? progressBar(p.positionMs / p.durationMs, 'on-img') : null),
        h('div', { class: 'ep-main' },
          h('div', { class: 'ep-title', text: e.number + '. ' + (e.title || t('episode_n', e.number)) }),
          e.duration ? h('div', { class: 'muted small', text: e.duration }) : null,
          e.plot ? h('div', { class: 'ep-plot', text: e.plot }) : null)), {
        click: () => playEpisode(all.indexOf(e), p ? p.positionMs : 0),
      }));
    });
  }

  function playMovie(startMs) {
    nav.playFull({ items: [item], index: 0, startMs, progressKey: item.key, title: item.name });
  }
  function playEpisode(i, startMs) {
    nav.playFull({ items: [item], index: 0, series: item, episodes: allEpisodes(), episodeIndex: i, startMs });
  }

  async function load() {
    drawInfo(); drawActions();
    episodes.appendChild(item.kind === 'series' ? spinner() : h('div'));
    try { d = await repo.getDetails(item); } catch (e) { d = { plot: item.plot }; toast(e.message); }
    if (d && d.backdrop) backdrop.style.backgroundImage = 'url("' + d.backdrop + '")';
    else if (item.logo) backdrop.style.backgroundImage = 'url("' + item.logo + '")';
    drawInfo(); drawSeasons(); drawActions();
    if (!Focus.current || !Focus.current.isConnected || !sh.el.contains(Focus.current)) Focus.first(nav.root);
  }
  load();

  return {
    el: sh.el,
    onShow(fresh) { if (!fresh) { drawActions(); drawSeasons(); } },
    destroy: sh.destroy,
  };
}
