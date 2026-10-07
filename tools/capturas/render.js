// Convierte SALIDA_DIR/pNN.html en SALIDA_DIR/pNN.pdf (842.88 × 595.92 pt) y SALIDA_DIR/pNN.png (vista previa).
// Requiere: npm i playwright && npx playwright install chromium
const { chromium } = require(process.env.PW_DIR ? process.env.PW_DIR + '/node_modules/playwright' : 'playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const dir = path.resolve(process.argv[2]);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1124, height: 795 } });
  for (const f of fs.readdirSync(dir).filter(f => f.endsWith('.html')).sort()) {
    await p.goto('file://' + path.join(dir, f));
    await p.evaluate(() => document.fonts.ready);
    const base = path.join(dir, f.replace('.html', ''));
    await p.pdf({ path: base + '.pdf', width: '11.7067in', height: '8.2767in', printBackground: true, pageRanges: '1' });
    if (process.env.PNG) await p.screenshot({ path: base + '.png' });
  }
  await b.close();
})();
