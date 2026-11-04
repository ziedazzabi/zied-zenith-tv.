// Builds the TV app: dist/www (plain web app), dist/ZVision.wgt (Samsung, unsigned) and dist/webos (for ares-package -> .ipk).
import * as esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const dev = process.argv.includes('--dev');
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const version = process.env.APP_VERSION || pkg.version;
const dist = path.join(root, 'dist');
fs.rmSync(dist, { recursive: true, force: true });
const www = path.join(dist, 'www');
fs.mkdirSync(www, { recursive: true });

await esbuild.build({
  entryPoints: [path.join(root, 'src/main.js')],
  bundle: true,
  outfile: path.join(www, 'app.js'),
  format: 'iife',
  target: ['chrome53'],
  minify: !dev,
  sourcemap: dev ? 'inline' : false,
  loader: { '.json': 'json' },
  define: { 'process.env.NODE_ENV': dev ? '"development"' : '"production"' },
  logLevel: 'warning',
});

const copyDir = (from, to) => {
  fs.mkdirSync(to, { recursive: true });
  for (const f of fs.readdirSync(from)) {
    const s = path.join(from, f), d = path.join(to, f);
    if (fs.statSync(s).isDirectory()) copyDir(s, d); else fs.copyFileSync(s, d);
  }
};
copyDir(path.join(root, 'static'), www);

// ---- Samsung Tizen ----
const tz = path.join(dist, 'tizen');
copyDir(www, tz);
copyDir(path.join(root, 'platform/tizen'), tz);
fs.writeFileSync(path.join(tz, 'config.xml'), fs.readFileSync(path.join(tz, 'config.xml'), 'utf8').replace('__VERSION__', version));
// Samsung's TV APIs (AVPlay, product info) are loaded from the TV itself
const tzIndex = path.join(tz, 'index.html');
fs.writeFileSync(tzIndex, fs.readFileSync(tzIndex, 'utf8').replace('<script src="app.js">', '<script src="$WEBAPIS/webapis/webapis.js"></script>\n  <script src="app.js">'));
try {
  execSync('cd "' + tz + '" && zip -qr -X "' + path.join(dist, 'ZVision.wgt') + '" .', { stdio: 'inherit' });
} catch (e) { console.warn('zip not available: .wgt not created'); }

// ---- LG webOS ----
const wo = path.join(dist, 'webos');
copyDir(www, wo);
copyDir(path.join(root, 'platform/webos'), wo);
fs.writeFileSync(path.join(wo, 'appinfo.json'), fs.readFileSync(path.join(wo, 'appinfo.json'), 'utf8').replace('__VERSION__', version));

console.log('Built Z-Vision TV ' + version + (dev ? ' (dev)' : ''));
