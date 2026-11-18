# Yggdrasil mark

- `yggdrasil-mark-rationale.pdf`: the mark and its story (the fan of pages, the growth rings, the
  nine worlds, the roots, colours, sizes).
- `geom.py`: the construction and the colours (forest `#2E6B4F`, gold `#D9961A`). Nine pages of
  length `R` fan up from the book's spine; nine evenly spaced growth rings (line `G`, band `φG`) are
  cut out of them, landing exactly on the tips.
- `android.py`: writes the launcher icon vectors (`app/src/main/res/drawable/ic_yggdrasil_fg.xml`,
  `ic_yggdrasil_mono.xml`) and the SVG sources of the legacy PNGs.
- `legacy.js`: renders the legacy PNGs (`mipmap-*dpi/ic_yggdrasil*.png`) with Playwright.
- `book.py` + `pdf.js`: rebuild the rationale PDF.

```bash
python3 branding/android.py app/src/main/res /tmp/ygg && node branding/legacy.js app/src/main/res /tmp/ygg
mkdir -p /tmp/ygg && python3 branding/book.py branding /tmp/ygg/book.html && node branding/pdf.js /tmp/ygg/book.html branding/yggdrasil-mark-rationale.pdf
```
