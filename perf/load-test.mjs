/**
 * Test de charge DataShare (voir PERF.md).
 * Prérequis : l'API tourne sur http://localhost:8080 avec une base vide ou de test.
 * Usage : node load-test.mjs [durée en secondes]
 */
import autocannon from 'autocannon';

const API = process.env.API_URL ?? 'http://localhost:8080';
const DURATION = Number(process.argv[2] ?? 20);
const email = `perf-${Date.now()}@datashare.fr`;
const password = 'motdepasse';

async function json(path, options) {
  const res = await fetch(API + path, options);
  if (!res.ok) throw new Error(`${path} → ${res.status}`);
  return res.status === 204 ? null : res.json();
}

async function setup() {
  await json('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
  const { accessToken } = await json('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
  const form = new FormData();
  form.append('file', new Blob([new Uint8Array(1024 * 1024)]), 'un-mo.bin');
  form.append('expirationDays', '7');
  const file = await json('/api/files', { method: 'POST', headers: { Authorization: `Bearer ${accessToken}` }, body: form });
  return { accessToken, token: file.downloadToken };
}

function run(title, opts) {
  return new Promise((resolve, reject) => {
    const { path, ...rest } = opts;
    autocannon({ url: API + path, duration: DURATION, connections: 20, ...rest }, (err, r) => {
      if (err) return reject(err);
      const row = {
        scénario: title,
        'req/s (moy.)': Math.round(r.requests.average),
        'latence p50 (ms)': r.latency.p50,
        'latence p97,5 (ms)': r.latency.p97_5,
        'latence p99 (ms)': r.latency.p99,
        'latence max (ms)': r.latency.max,
        'erreurs': r.errors + r.non2xx,
        'débit (Mo/s)': (r.throughput.average / 1024 / 1024).toFixed(1),
      };
      resolve(row);
    });
  });
}

const { accessToken, token } = await setup();
const results = [];
results.push(await run('Page de téléchargement : métadonnées (GET /api/download/{token})', { path: `/api/download/${token}` }));
results.push(await run('Téléchargement d’un fichier de 1 Mo (GET …/content)', { path: `/api/download/${token}/content` }));
results.push(await run('Historique connecté (GET /api/files)', { path: '/api/files', headers: { Authorization: `Bearer ${accessToken}` } }));
results.push(await run('Connexion (POST /api/auth/login, BCrypt)', {
  path: '/api/auth/login', method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
}));
console.table(results);
