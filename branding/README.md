# Yggdrasil mark

- `yggdrasil-mark-rationale.pdf`: the mark and its story (construction, golden ratio, the hidden
  eye, runes and nine worlds, colours, sizes).
- `geom.py`: the construction. Every shape of the mark comes from two circles of radius `R`, one
  radius apart, and from `R / φⁿ`.
- `android.py`: writes the launcher icon vectors (`app/src/main/res/drawable/ic_yggdrasil_fg.xml`,
  `ic_yggdrasil_mono.xml`) and the SVG sources of the legacy PNGs.
- `legacy.js`: renders the legacy PNGs (`mipmap-*dpi/ic_yggdrasil*.png`) with Playwright.
- `book.py` + `pdf.js`: rebuild the rationale PDF.

```bash
python3 branding/android.py app/src/main/res /tmp/ygg && node branding/legacy.js app/src/main/res /tmp/ygg
python3 branding/book.py branding /tmp/ygg/book.html && node branding/pdf.js /tmp/ygg/book.html branding/yggdrasil-mark-rationale.pdf
```
