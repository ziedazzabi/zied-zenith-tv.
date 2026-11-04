// About Z-Vision: credits, contact and donations (PayPal / Neteller) with QR codes to scan from a phone.
import { h } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { button, toast } from '../ui.js';
import { shell } from './common.js';
import { APP_VERSION } from './settings.js';
import { isTizen, isWebOS } from '../platform.js';

export const DEVELOPER = 'Zied Azzabi';
export const CONTACT_EMAIL = 'zied.azzabi@gmail.com';
export const DONATE_EMAIL = 'zied.azzabi@yahoo.fr';
export const PAYPAL_URL = 'https://www.paypal.com/donate/?business=zied.azzabi%40yahoo.fr&no_recurring=0&currency_code=USD';

/** Tries to open a web page in the TV's own browser (best effort). */
function openInBrowser(url) {
  try {
    if (isTizen) {
      const ctl = new window.tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', url);
      window.tizen.application.launchAppControl(ctl, null, () => {}, () => toast(t('scan_hint')));
      return;
    }
    if (isWebOS && window.PalmServiceBridge) {
      const b = new window.PalmServiceBridge();
      b.call('luna://com.webos.applicationManager/launch', JSON.stringify({ id: 'com.webos.app.browser', params: { target: url } }));
      return;
    }
    window.open(url, '_blank');
  } catch (e) { toast(t('scan_hint')); }
}

function card(qr, title, lines, actions, color) {
  return h('div', { class: 'pay-card' },
    h('img', { class: 'qr', src: 'img/' + qr + '.png', alt: '' }),
    h('div', { class: 'pay-main' },
      h('div', { class: 'pay-title' }, color ? h('span', { class: 'pay-dot', style: 'background:' + color }) : null, title),
      lines.map((l, i) => h('div', { class: i === lines.length - 1 ? 'pay-email' : 'muted', text: l })),
      h('div', { class: 'muted small', text: t('scan_hint') }),
      actions && actions.length ? h('div', { class: 'row gap pay-actions fgroup' }, actions) : null));
}

export function AboutScreen(params, nav) {
  const sh = shell(t('about_app'), nav, { cls: 'about' });
  const page = h('div', { class: 'about-page scroll' },
    h('div', { class: 'about-head' },
      h('img', { src: 'img/mark.png', alt: '' }),
      h('div', {},
        h('div', { class: 'about-name' }, h('span', { class: 'z', text: 'Z-' }), 'Vision'),
        h('div', { class: 'muted', text: t('version_n', APP_VERSION) }),
        h('div', { class: 'about-credit', text: t('idea_dev') + ': ' + DEVELOPER }))),
    h('div', { class: 'section' }, icon('info'), h('span', { text: t('contact') })),
    card('qr_contact', DEVELOPER, [t('contact_hint'), CONTACT_EMAIL], []),
    h('div', { class: 'section' }, icon('heart'), h('span', { text: t('support_app') })),
    h('div', { class: 'donate-text', text: t('donate_text') }),
    h('div', { class: 'pay-row' },
      card('qr_paypal', 'PayPal', [t('paypal_hint'), DONATE_EMAIL], [button(t('open_paypal'), 'link', () => openInBrowser(PAYPAL_URL), { auto: true })], '#003087'),
      card('qr_neteller', 'Neteller', [t('neteller_hint'), DONATE_EMAIL], [], '#8DC63F')),
    h('div', { class: 'thanks', text: t('thanks_support') }));
  sh.body.appendChild(page);
  return { el: sh.el, destroy: sh.destroy };
}
