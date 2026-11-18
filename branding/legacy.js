// Renders the legacy launcher PNGs from the SVG sources into <res>/mipmap-*dpi/.
const { chromium } = require('playwright');
const fs = require('fs');
const [res, src] = process.argv.slice(2);
const sizes = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
(async () => {
  const b = await chromium.launch();
  for (const name of ['ic_yggdrasil', 'ic_yggdrasil_round']) {
    const svg = fs.readFileSync(`${src}/${name}.svg`, 'utf8');
    for (const [d, px] of Object.entries(sizes)) {
      const p = await b.newPage({ viewport: { width: px, height: px }, deviceScaleFactor: 1 });
      await p.setContent(`<html><body style="margin:0;background:transparent">${svg.replace('<svg ', `<svg width="${px}" height="${px}" `)}</body></html>`);
      await p.screenshot({ path: `${res}/mipmap-${d}/${name}.png`, omitBackground: true });
      await p.close();
    }
  }
  await b.close();
})();
