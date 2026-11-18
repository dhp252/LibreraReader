const { chromium } = require('playwright');
(async () => {
  const [html, pdf, png] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1123, height: 794 }, deviceScaleFactor: 1 });
  await p.goto('file://' + html, { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  await p.pdf({ path: pdf, width: '297mm', height: '210mm', printBackground: true, preferCSSPageSize: true });
  if (png) {
    const n = await p.$$eval('section', s => s.length);
    const secs = await p.$$('section');
    for (let i = 0; i < n; i++) await secs[i].screenshot({ path: `${png}-${i + 1}.png` });
  }
  await b.close();
})();
