// Edition branding: the custom edition is built with __PRESET__ (see scripts/build.mjs).
/* global __PRESET__ */
import { h } from './util.js';

const preset = typeof __PRESET__ !== 'undefined' ? __PRESET__ : null;
export const APP_NAME = (preset && preset.appName) || 'Z-Vision';
/** "Max" for "Z-Vision Max", '' for the standard edition. */
export const BRAND_SUFFIX = APP_NAME.replace(/^Z-?\s?Vision\s*/i, '').trim();

/** "Z-Vision" word mark (+ edition badge). */
export function brandName() {
  return h('span', { class: 'brand-name' }, h('span', { class: 'z', text: 'Z-' }), 'Vision',
    BRAND_SUFFIX ? h('span', { class: 'brand-sfx', text: BRAND_SUFFIX.toUpperCase() }) : null);
}
