// Shortcuts to Netflix, Prime Video, Disney+, Shahid, Spotify… and any app installed on the TV.
import { h, clear, norm } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { store } from '../store.js';
import { fz, toast, sectionTitle } from '../ui.js';
import { shell } from './common.js';
import { listApps, launchApp, openStore, isTizen, isWebOS, isTV } from '../platform.js';
import { Focus } from '../focus.js';

// name, colour, words to find it among installed apps, Samsung store ids, LG app ids
export const KNOWN = [
  ['Netflix', '#B20710', ['netflix'], ['3201907018807', '11101200001'], ['netflix']],
  ['Prime Video', '#0F79AF', ['prime video', 'amazon'], ['3201910019365', '3201910019354'], ['amazon', 'com.amazon.amazonvideo.webos']],
  ['Disney+', '#0E2A80', ['disney'], ['3201901017640'], ['com.disney.disneyplus-prod']],
  ['Shahid', '#1B8A4B', ['shahid', 'شاهد'], [], []],
  ['OSN+', '#3A2D7A', ['osn'], [], []],
  ['Max', '#1F2A9C', ['=max', 'hbo'], [], []],
  ['Apple TV', '#2B2B2B', ['apple tv'], ['3201807016597'], ['com.apple.appletv']],
  ['YouTube', '#CC0000', ['=youtube'], ['111299001912'], ['youtube.leanback.v4']],
  ['YouTube Kids', '#D93025', ['youtube kids'], [], ['youtube.leanback.kids.v4']],
  ['STARZPLAY', '#1D3557', ['starz'], [], []],
  ['beIN', '#5B2A86', ['bein'], [], []],
  ['TOD', '#6A1B9A', ['=tod'], [], []],
  ['Spotify', '#1AA34A', ['spotify'], ['3201606009684'], ['spotify-beehive']],
  ['Anghami', '#6C2BD9', ['anghami', 'أنغامي'], [], []],
  ['Twitch', '#6441A5', ['twitch'], [], []],
  ['Plex', '#8A6A00', ['plex'], [], ['cdp-30']],
].map(([name, color, words, samsung, lg]) => ({ name, color, words, samsung, lg }));

let cache = null;
async function installed() {
  if (!cache) cache = await listApps().catch(() => []);
  return cache;
}

function findInstalled(k, apps) {
  const ids = isWebOS ? k.lg : [];
  const byId = apps.find((a) => ids.indexOf(a.id) >= 0);
  if (byId) return byId;
  return apps.find((a) => {
    const l = norm(a.name).trim();
    return k.words.some((w) => (w[0] === '=' ? l === w.slice(1) : l.indexOf(norm(w)) >= 0));
  }) || null;
}

async function openKnown(k, app) {
  if (app) { if (await launchApp(app.id)) return; }
  // webOS: try the usual id even if the list call wasn't allowed
  if (!app && isWebOS) for (const id of k.lg) { if (await launchApp(id)) return; }
  if (!isTV) { toast(k.name + ' — ' + t('open_failed')); return; }
  toast(t('store_opened'));
  if (!(await openStore(k.name, isTizen ? k.samsung : k.lg))) toast(t('no_store'));
}

function pinned(apps) {
  const p = store.settings.get().pinnedApps;
  if (p) return p.map((id) => apps.find((a) => a.id === id)).filter(Boolean);
  return KNOWN.map((k) => findInstalled(k, apps)).filter(Boolean);
}

function togglePin(app, apps) {
  const cur = store.settings.get().pinnedApps || pinned(apps).map((a) => a.id);
  const next = cur.indexOf(app.id) >= 0 ? cur.filter((x) => x !== app.id) : cur.concat([app.id]);
  store.updateSettings(() => ({ pinnedApps: next }));
  toast(t(next.indexOf(app.id) >= 0 ? 'pinned_home' : 'unpinned_home'));
}

function appCard(name, color, iconUrl, o) {
  const img = iconUrl ? h('img', { src: iconUrl, alt: '' }) : null;
  if (img) img.onerror = () => img.remove();
  return fz(h('div', { class: 'app-card' + (o.missing ? ' missing' : '') },
    h('div', { class: 'app-art', style: 'background:' + (img ? 'var(--s2)' : color) }, img || h('span', { class: 'app-word', text: name }),
      o.missing ? h('div', { class: 'app-badge' }, icon('download')) : null, o.pinned ? h('div', { class: 'app-pin' }, icon('pin')) : null),
    h('div', { class: 'app-name', text: name })), { click: o.click, long: o.long });
}

/** "My apps" row for the home screen. */
export function homeAppsRow(nav) {
  const wrap = h('div', {}, sectionTitle(t('my_apps'), 'apps'));
  const row = h('div', { class: 'hrow fgroup' });
  wrap.appendChild(row);
  const allBtn = fz(h('div', { class: 'app-card' }, h('div', { class: 'app-art more' }, icon('add')), h('div', { class: 'app-name', text: t('all_apps') })), { click: () => nav.go('apps') });
  row.appendChild(allBtn);
  installed().then((apps) => {
    pinned(apps).forEach((a) => {
      const card = appCard(a.name, '#334155', a.icon, {
        click: () => launchApp(a.id).then((ok) => { if (!ok) toast(t('open_failed')); }),
        long: () => { togglePin(a, apps); card.remove(); Focus.set(allBtn); },
      });
      row.insertBefore(card, allBtn);
    });
  });
  return wrap;
}

export function AppsScreen(params, nav) {
  const sh = shell(t('apps'), nav, { cls: 'apps' });
  const body = h('div', { class: 'scroll apps-body' });
  sh.body.appendChild(body);

  async function draw() {
    const apps = await installed();
    const keepIdx = Focus.current && body.contains(Focus.current) ? Array.prototype.indexOf.call(body.querySelectorAll('.f'), Focus.current) : -1;
    clear(body);
    const pins = (store.settings.get().pinnedApps || pinned(apps).map((a) => a.id));
    body.appendChild(h('div', { class: 'muted small pad', text: t('tv_apps_hint') }));
    body.appendChild(sectionTitle(t('popular_apps'), 'star'));
    const g1 = h('div', { class: 'app-grid fgroup' });
    const used = new Set();
    KNOWN.forEach((k, i) => {
      const a = findInstalled(k, apps);
      if (a) used.add(a.id);
      const c = appCard(k.name, k.color, a && a.icon, {
        missing: !a, pinned: a && pins.indexOf(a.id) >= 0,
        click: () => openKnown(k, a), long: () => { if (a) { togglePin(a, apps); draw(); } else openKnown(k, null); },
      });
      if (i === 0) c.classList.add('autofocus');
      g1.appendChild(c);
    });
    body.appendChild(g1);
    const others = apps.filter((a) => !used.has(a.id)).sort((a, b) => a.name.localeCompare(b.name));
    if (others.length) {
      body.appendChild(sectionTitle(t('installed_apps'), 'apps'));
      const g2 = h('div', { class: 'app-grid fgroup' });
      others.forEach((a) => g2.appendChild(appCard(a.name, '#334155', a.icon, {
        pinned: pins.indexOf(a.id) >= 0,
        click: () => launchApp(a.id).then((ok) => { if (!ok) toast(t('open_failed')); }),
        long: () => { togglePin(a, apps); draw(); },
      })));
      body.appendChild(g2);
    }
    const all = body.querySelectorAll('.f');
    if (keepIdx >= 0 && all[keepIdx]) Focus.set(all[keepIdx]); else Focus.first(nav.root);
  }
  draw();
  return { el: sh.el, destroy: sh.destroy, focusFirst() {} };
}
