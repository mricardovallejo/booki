// Renders docs/booki-guide.html to docs/booki-guide.pdf and syncs the copies the
// app ships: frontend/public (the landing "Learn more" link) and the backend
// classpath (the welcome document every new account gets).
//
//   cd frontend && npm install      # once, for the Playwright + Chromium dep
//   node scripts/build-guide.mjs    # from the repo root
//
// Optional: if `gs` (Ghostscript) is on PATH the PDF is size-optimised.

import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const src = path.join(root, 'docs/booki-guide.html');
const out = path.join(root, 'docs/booki-guide.pdf');
const copies = [
  path.join(root, 'frontend/public/booki-guide.pdf'),
  path.join(root, 'backend/src/main/resources/welcome/booki-guide.pdf'),
];

const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto(pathToFileURL(src).href, { waitUntil: 'networkidle' });
await page.emulateMedia({ media: 'print' });
await page.pdf({ path: out, format: 'A4', printBackground: true, preferCSSPageSize: true });
await browser.close();

try {
  const tmp = out + '.tmp';
  execFileSync('gs', [
    '-sDEVICE=pdfwrite', '-dCompatibilityLevel=1.5', '-dPDFSETTINGS=/ebook',
    '-dNOPAUSE', '-dQUIET', '-dBATCH', `-sOutputFile=${tmp}`, out,
  ]);
  if (fs.statSync(tmp).size < fs.statSync(out).size) fs.renameSync(tmp, out);
  else fs.unlinkSync(tmp);
} catch {
  console.log('(gs not available — skipping PDF size optimisation)');
}

for (const dest of copies) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(out, dest);
}

const kb = (p) => Math.round(fs.statSync(p).size / 1024) + ' KB';
console.log(`wrote ${path.relative(root, out)} (${kb(out)})`);
for (const dest of copies) console.log(`  copied → ${path.relative(root, dest)}`);
