import { boot } from './app.js';

function start() {
  try {
    boot();
    setTimeout(() => { const sp = document.getElementById('splash'); if (sp) { sp.classList.add('out'); setTimeout(() => sp.remove(), 600); } }, 900);
  } catch (e) {
    console.error(e);
    document.body.innerHTML = '<div style="color:#fff;padding:40px;font:24px sans-serif">Z-Vision failed to start: ' + (e && e.message) + '</div>';
  }
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
