// Everything that differs between Samsung (Tizen), LG (webOS) and a desktop browser.

const w = window;
export const isTizen = typeof w.tizen !== 'undefined';
export const isWebOS = !isTizen && (typeof w.webOS !== 'undefined' || typeof w.PalmSystem !== 'undefined' || /web0s|webos/i.test(navigator.userAgent));
export const isTV = isTizen || isWebOS;
export const platformName = isTizen ? 'tizen' : isWebOS ? 'webos' : 'browser';

/** Register the remote's extra keys (Tizen only delivers them after registering). */
export function registerKeys() {
  if (!isTizen) return;
  try {
    const want = ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind',
      'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue', 'ChannelUp', 'ChannelDown', 'Info', 'Guide',
      'ChannelList', 'Tools', 'Exit', 'Caption', 'PreviousChannel',
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
    const supported = w.tizen.tvinputdevice.getSupportedKeys().map((k) => k.name);
    const list = want.filter((k) => supported.indexOf(k) >= 0);
    if (w.tizen.tvinputdevice.registerKeyBatch) w.tizen.tvinputdevice.registerKeyBatch(list);
    else list.forEach((k) => { try { w.tizen.tvinputdevice.registerKey(k); } catch (e) { /* ignore */ } });
  } catch (e) { console.warn('registerKeys', e); }
}

/** Maps a keydown event to a semantic key name. */
export function keyName(e) {
  const c = e.keyCode;
  switch (c) {
    case 37: return 'left';
    case 38: return 'up';
    case 39: return 'right';
    case 40: return 'down';
    case 13: return 'ok';
    case 10009: case 461: case 27: return 'back'; // Tizen, webOS, Esc
    case 8: return e.target && /INPUT|TEXTAREA/.test(e.target.tagName) ? null : 'back';
    case 10182: return 'exit';
    case 415: return 'play';
    case 19: return 'pause';
    case 10252: case 179: return 'playpause';
    case 413: return 'stop';
    case 417: return 'ff';
    case 412: return 'rw';
    case 427: case 33: return 'chup';
    case 428: case 34: return 'chdown';
    case 403: return 'red';
    case 404: return 'green';
    case 405: return 'yellow';
    case 406: return 'blue';
    case 457: return 'info';
    case 458: return 'guide';
    case 10073: return 'chlist';
    case 10135: case 93: return 'menu';
    case 10190: return 'prevch';
    default: break;
  }
  if (c >= 48 && c <= 57 && !(e.target && /INPUT|TEXTAREA/.test(e.target.tagName))) return 'd' + (c - 48);
  if (c >= 96 && c <= 105 && !(e.target && /INPUT|TEXTAREA/.test(e.target.tagName))) return 'd' + (c - 96);
  if (!isTV && !(e.target && /INPUT|TEXTAREA/.test(e.target.tagName))) {
    // keyboard shortcuts for testing in a desktop browser
    const k = (e.key || '').toLowerCase();
    if (k === 'm') return 'menu';
    if (k === 'i') return 'info';
    if (k === 'r') return 'red';
    if (k === 'g') return 'green';
    if (k === 'y') return 'yellow';
    if (k === 'b') return 'blue';
    if (k === ' ') return 'playpause';
  }
  return null;
}

export function exitApp() {
  try {
    if (isTizen) { w.tizen.application.getCurrentApplication().exit(); return; }
    if (isWebOS && w.webOS && w.webOS.platformBack) { w.webOS.platformBack(); return; }
    if (w.PalmSystem && w.PalmSystem.platformBack) { w.PalmSystem.platformBack(); return; }
  } catch (e) { /* fall through */ }
  w.close();
}

/** Keeps the TV screen on while video plays. */
export function keepScreenOn(on) {
  try {
    if (isTizen && w.webapis && w.webapis.appcommon) {
      w.webapis.appcommon.setScreenSaver(on ? w.webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_OFF : w.webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_ON);
    }
  } catch (e) { /* ignore */ }
}

// ---------------- other apps (Netflix, Shahid, ...) ----------------

function lunaCall(uri, method, params) {
  return new Promise((resolve) => {
    try {
      const svc = w.webOS && w.webOS.service;
      if (svc) {
        svc.request(uri, { method, parameters: params || {}, onSuccess: (r) => resolve(r), onFailure: () => resolve(null) });
        return;
      }
      if (typeof w.PalmServiceBridge === 'undefined') return resolve(null);
      const bridge = new w.PalmServiceBridge();
      const timer = setTimeout(() => resolve(null), 5000);
      bridge.onservicecallback = (msg) => {
        clearTimeout(timer);
        let r = null; try { r = JSON.parse(msg); } catch (e) { /* ignore */ }
        resolve(r && r.returnValue !== false ? r : null);
      };
      bridge.call(uri.replace(/\/$/, '') + '/' + method, JSON.stringify(params || {}));
    } catch (e) { resolve(null); }
  });
}

/** Lists installed apps: [{id, name, icon}] (best effort). */
export async function listApps() {
  if (isTizen) {
    return new Promise((resolve) => {
      try {
        w.tizen.application.getAppsInfo((infos) => {
          const self = w.tizen.application.getCurrentApplication().appInfo.id;
          resolve(infos.filter((a) => a.show !== false && a.id !== self && a.name)
            .map((a) => ({ id: a.id, name: a.name, icon: a.iconPath || '' })));
        }, () => resolve([]));
      } catch (e) { resolve([]); }
    });
  }
  if (isWebOS) {
    const r = await lunaCall('luna://com.webos.applicationManager', 'listLaunchPoints', {});
    const pts = (r && r.launchPoints) || [];
    return pts.filter((p) => p.id && p.id !== 'com.zvision.tv')
      .map((p) => ({ id: p.id, name: p.title || p.id, icon: p.icon || p.largeIcon || '' }));
  }
  return [];
}

export async function launchApp(id) {
  if (isTizen) {
    return new Promise((resolve) => {
      try { w.tizen.application.launch(id, () => resolve(true), () => resolve(false)); } catch (e) { resolve(false); }
    });
  }
  if (isWebOS) {
    const r = await lunaCall('luna://com.webos.applicationManager', 'launch', { id });
    return !!(r && r.returnValue !== false);
  }
  return false;
}

/** Opens the TV app store on an app (or a search for its name). */
export async function openStore(name, ids) {
  if (isTizen) {
    const tries = [];
    (ids || []).forEach((id) => tries.push({ app: 'com.samsung.tv.store', data: [['Sub_Menu', 'detail'], ['widget_id', id]] }));
    tries.push({ app: 'com.samsung.tv.store', data: [['Sub_Menu', 'search'], ['Keyword', name]] });
    tries.push({ app: 'com.samsung.tv.store', data: [] });
    for (const tr of tries) {
      const ok = await new Promise((resolve) => {
        try {
          const ctl = new w.tizen.ApplicationControl('http://tizen.org/appcontrol/operation/default', null, null, null,
            tr.data.map((d) => new w.tizen.ApplicationControlData(d[0], [d[1]])));
          w.tizen.application.launchAppControl(ctl, tr.app, () => resolve(true), () => resolve(false));
        } catch (e) { resolve(false); }
      });
      if (ok) return true;
    }
    return false;
  }
  if (isWebOS) {
    const id = (ids || [])[0];
    let r = id ? await lunaCall('luna://com.webos.applicationManager', 'launch', { id: 'com.webos.app.discovery', params: { target: 'luna://com.webos.app.discovery/' + id } }) : null;
    if (!r) r = await lunaCall('luna://com.webos.applicationManager', 'launch', { id: 'com.webos.app.discovery', params: { query: name } });
    if (!r) r = await lunaCall('luna://com.webos.applicationManager', 'launch', { id: 'com.webos.app.lgstore', params: { query: name } });
    return !!r;
  }
  return false;
}

// ---------------- reminders that can wake the app (Tizen) ----------------

export function scheduleSystemAlarm(id, atMs) {
  if (!isTizen) return null;
  try {
    const app = w.tizen.application.getCurrentApplication().appInfo.id;
    const alarm = new w.tizen.AlarmAbsolute(new Date(atMs));
    const ctl = new w.tizen.ApplicationControl('http://tizen.org/appcontrol/operation/default', null, null, null,
      [new w.tizen.ApplicationControlData('reminder', [id])]);
    w.tizen.alarm.add(alarm, app, ctl);
    return alarm.id;
  } catch (e) { console.warn('alarm', e); return null; }
}

export function cancelSystemAlarm(alarmId) {
  if (!isTizen || !alarmId) return;
  try { w.tizen.alarm.remove(alarmId); } catch (e) { /* ignore */ }
}

/** Reminder id the app was launched with (Tizen alarm), if any. */
export function launchReminderId() {
  if (!isTizen) return null;
  try {
    const req = w.tizen.application.getCurrentApplication().getRequestedAppControl();
    const d = req && req.appControl && req.appControl.data;
    const r = d && d.find((x) => x.key === 'reminder');
    return r ? r.value[0] : null;
  } catch (e) { return null; }
}

export function onAppVisibility(cb) {
  document.addEventListener('visibilitychange', () => cb(!document.hidden));
  if (isWebOS) document.addEventListener('webOSRelaunch', () => cb(true));
}
