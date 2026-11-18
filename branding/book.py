"""Builds the Yggdrasil mark rationale (Rings + Flip), as HTML for a landscape A4 PDF."""
import sys, re, os
here = sys.argv[1]
sys.path.insert(0, here)
from geom import *

CSS = '''
@page { size: 297mm 210mm; margin: 0 }
* { box-sizing: border-box }
body { margin: 0; font-family: 'Instrument Sans', sans-serif; color: #14161B; -webkit-print-color-adjust: exact; print-color-adjust: exact }
section { width: 297mm; height: 210mm; padding: 16mm 18mm 14mm; page-break-after: always; position: relative; background: #FAFAFB; overflow: hidden }
section::after { content: "YGGDRASIL · MARK & RATIONALE"; position: absolute; left: 18mm; bottom: 8mm; font: 500 8pt 'JetBrains Mono'; letter-spacing: .12em; color: #8A909B }
.hd { display: flex; align-items: baseline; gap: 14px; border-bottom: 1px solid #E3E5EA; padding-bottom: 10px; margin-bottom: 18px }
.hd .n { font: 500 12pt 'JetBrains Mono'; color: #A8700C }
h2 { font: 600 24pt 'Instrument Sans'; margin: 0; letter-spacing: -.01em }
h3 { font-size: 13pt; margin: 10px 0 4px }
h3.sub { font: 500 9pt 'JetBrains Mono'; letter-spacing: .08em; text-transform: uppercase; color: #5E6570; margin: 18px 0 10px }
p, li { font-size: 11.5pt; line-height: 1.5; color: #2B2F38 }
.txt p { margin: 0 0 12px }
ul { padding-left: 18px; margin: 0 0 12px } li { margin-bottom: 6px }
.small, .small p { font-size: 9.5pt; color: #5E6570 }
.intro { font-family: 'Literata', serif; font-size: 13.5pt; line-height: 1.55; max-width: 230mm; color: #14161B }
.split { display: flex; gap: 34px; align-items: center }
.split .fig { flex: none; background: #fff; border: 1px solid #E3E5EA; border-radius: 14px; padding: 10px }
.split .txt { flex: 1 }
.trio { display: flex; align-items: center; gap: 14px; margin-top: 18px }
.trio > div:not(.plus) { flex: 1; text-align: center }
.trio .fig { display: inline-block; background: #fff; border: 1px solid #E3E5EA; border-radius: 14px }
.trio p { font-size: 10.5pt; margin: 0 8px }
.plus { font: 300 34pt 'Instrument Sans'; color: #A8700C }
.cover { display: flex; align-items: center; gap: 30px; background: #fff }
.cv-l { flex: none; border-radius: 26%; box-shadow: 0 2px 16px rgba(20,22,27,.1); background: #fff }
.kick { font: 500 9pt 'JetBrains Mono'; letter-spacing: .12em; color: #A8700C }
h1 { font: 600 54pt 'Instrument Sans'; margin: 8px 0 0; letter-spacing: -.02em }
.tag { font: italic 400 19pt 'Literata', serif; margin: 2px 0 18px; color: #2E6B4F }
.lede { font-size: 12.5pt; max-width: 120mm }
.toc { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 22px; margin-top: 22px; font-size: 10.5pt }
.toc span { font-family: 'JetBrains Mono'; color: #A8700C; margin-right: 10px; font-size: 9pt }
table.spec { border-collapse: collapse; width: 100%; margin: 4px 0 16px; font-size: 10.5pt }
.spec th { text-align: left; font: 500 8pt 'JetBrains Mono'; letter-spacing: .08em; text-transform: uppercase; color: #5E6570; border-bottom: 1px solid #E3E5EA; padding: 6px 4px }
.spec td { border-bottom: 1px solid #F1F2F5; padding: 6px 4px }
.m { font-family: 'JetBrains Mono'; font-size: 9.5pt } .r { text-align: right; white-space: nowrap }
.sws { display: flex; gap: 18px; margin-top: 20px }
.sw { flex: 1; background: #fff; border: 1px solid #E3E5EA; border-radius: 14px; padding: 12px }
.sw .chip { height: 70px; border-radius: 10px; border: 1px solid #E3E5EA; margin-bottom: 10px }
.sw p { font-size: 9.5pt; margin: 4px 0 0 }
.row { display: flex; gap: 22px; align-items: flex-end }
.lock { display: flex; align-items: center; gap: 16px; margin-top: 26px }
.wm { font: 600 26pt 'Instrument Sans'; letter-spacing: -.01em }
.wmt { font: italic 12pt 'Literata', serif; color: #2E6B4F }
.foot { position: absolute; right: 18mm; bottom: 7mm; font-size: 8.5pt; color: #8A909B; margin: 0 }
'''
GUIDE, GRID, MUTED = "#A8700C", "#C9CDD6", "#5E6570"
_uid = [0]


def mk(**kw):
    _uid[0] += 1
    return mark2_svg(uid=f"k{_uid[0]}", **kw)


def svg(body, vb="18 18 72 72", w=None, h=None):
    size = (f' width="{w}"' if w else "") + (f' height="{h}"' if h else "")
    return f'<svg viewBox="{vb}"{size} xmlns="http://www.w3.org/2000/svg">{body}</svg>'


def circ(c, r, color=GUIDE, w=0.35, dash="1.2 0.9", op=1):
    return f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{f(r)}" fill="none" stroke="{color}" stroke-width="{w}" stroke-dasharray="{dash}" opacity="{op}"/>'


def arc(c, r, a0=180, a1=0, color=GUIDE, w=0.35, dash="1.2 0.9", op=1):
    """The part of a circle above its centre, from angle a0 down to a1 (maths angles)."""
    p0, p1 = pol(c, r, a0), pol(c, r, a1)
    return (f'<path d="M{f(p0[0])},{f(p0[1])} A{f(r)},{f(r)} 0 0,1 {f(p1[0])},{f(p1[1])}" fill="none" '
            f'stroke="{color}" stroke-width="{w}" stroke-dasharray="{dash}" opacity="{op}"/>')


def dot(c, r=0.75, color=GUIDE):
    return f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{r}" fill="{color}"/>'


def label(x, y, t, size=2.4, color=GUIDE, anchor="start", weight=500):
    return f'<text x="{f(x)}" y="{f(y)}" font-family="JetBrains Mono" font-size="{size}" fill="{color}" text-anchor="{anchor}" font-weight="{weight}">{t}</text>'


def line(a, b, color=GUIDE, w=0.3, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{f(a[0])}" y1="{f(a[1])}" x2="{f(b[0])}" y2="{f(b[1])}" stroke="{color}" stroke-width="{w}"{d}/>'


def ghost(op=0.2):
    return f'<g opacity="{op}">{mk()}</g>'


def grid108():
    g = []
    for i in range(0, 109, 6):
        w = 0.25 if i % 18 else 0.42
        g.append(line((i, 0), (i, 108), GRID, w))
        g.append(line((0, i), (108, i), GRID, w))
    return "".join(g)


def ruler():
    g = [line((4, 0), (4, 108), MUTED, 0.25)]
    for y in range(0, 109, 2):
        g.append(line((4, y), (4 + (2.4 if y % 10 == 0 else 1.2), y), MUTED, 0.2))
        if y % 18 == 0:
            g.append(label(1.0, y + 0.8, str(y), 1.7, MUTED, "start", 400))
    return "".join(g)


TIPS = [page_shape(i)[1] for i in range(N_PAGES)]
ANGLES = [page_angle(i) for i in range(N_PAGES)]

# ---------------- figures ----------------
fig_cover = svg(mk(), "16 16 76 76", 430, 430)

# the idea: flip (solid pages) + rings (circles) = mark
flip_only = "".join(f'<path d="{page_shape(i)[0]}" fill="{page_color(i)}" stroke="#fff" stroke-width="1.2" stroke-linejoin="round"/>' for i in ORDER)
flip_only += "".join(f'<path d="{d}" fill="none" stroke="{GREEN}" stroke-width="{f(w)}" stroke-linecap="round"/>' for d, w in book_paths())
rings_only = "".join(circ(S, r, GREEN, 1.0, None) for r in RING_R[:-1]) + dot(S, 1.2, GREEN)
rings_only = rings_only.replace('stroke-dasharray="None"', "")
fig_idea = [svg(flip_only, "16 16 76 76", 190, 190),
            svg(f'<clipPath id="cl"><rect x="16" y="16" width="76" height="76"/></clipPath><g clip-path="url(#cl)">{rings_only}</g>', "16 16 76 76", 190, 190),
            svg(mk(), "16 16 76 76", 190, 190)]

# construction
cons = [grid108(), ruler(), ghost(0.22),
        circ((54, 54), 33, MUTED, 0.3, "0.6 0.8"), label(54, 19.4, "SAFE ZONE ⌀66", 1.8, MUTED, "middle", 400),
        circ(S, CROWN_R), label(S[0] - 1.6, S[1] - CROWN_R - 1.2, "C", 2.4, GUIDE, "end"),
        dot(S, 0.95), label(S[0] + 1.6, S[1] + 2.6, "S · spine", 2.2)]
for a, t in zip(ANGLES, TIPS):
    cons.append(line(S, t, GUIDE, 0.22, "0.8 0.6"))
    cons.append(dot(t, 0.6))
cons.append(line(S, pol(S, CROWN_R, 64), GUIDE, 0.35))
cons.append(label(*pol(S, CROWN_R * 0.55, 70), "R", 2.4))
fig_cons = svg("".join(cons), "0 0 108 108", 520, 520)

# rings: evenly spaced, line G and band phi x G
rg = [ghost(0.16)]
for k, r in enumerate(RING_R[:-1]):
    rg.append(arc(S, r, 180, 0, GREEN, 0.3, "0.6 0.5", 0.9))
for k, r in enumerate(RING_R[:-1]):
    a = 18 + k * 0
    p = pol(S, r, 180)
    rg.append(dot(p, 0.5, GREEN))
rg.append(line(pol(S, RING_R[0], 180), pol(S, RING_R[-2], 180), GREEN, 0.3))
rg.append(label(54, 91, f"r(n) = n × {RING_PITCH:.2f} u  ·  band = φ × line", 2.4, GREEN, "middle"))
rg.append(dot(S, 0.9, GREEN))
fig_rings = svg(f'<clipPath id="cr"><rect x="14" y="14" width="80" height="80"/></clipPath><g clip-path="url(#cr)">{"".join(rg)}</g>', "14 14 80 80", 440, 440)

# nine pages
nine = [ghost(0.3), arc(S, CROWN_R, 172, 8, GUIDE, 0.3)]
for i, t in enumerate(TIPS, 1):
    col = GOLD if i - 1 == MIDDLE else GUIDE
    x, y = pol(S, sqrt((t[0] - S[0]) ** 2 + (t[1] - S[1]) ** 2) + 4.2, ANGLES[i - 1])
    nine.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="2.4" fill="#fff" stroke="{col}" stroke-width=".4"/>')
    nine.append(label(x, y + 0.85, str(i), 2.2, col, "middle", 600))
fig_nine = svg("".join(nine), "12 12 84 84", 440, 440)

# roots and ground
roots = [ghost(0.18)]
for d in roots_paths():
    roots.append(f'<path d="{d}" fill="none" stroke="{GUIDE}" stroke-width="{f(TRUNK_W * 0.75)}" stroke-linecap="round"/>')
for d, w in book_paths():
    roots.append(f'<path d="{d}" fill="none" stroke="{GREEN}" stroke-width="{f(w)}" stroke-linecap="round"/>')
roots.append(line((22, BOOK_Y - 0.6), (86, BOOK_Y - 0.6), MUTED, 0.25, "1 1"))
roots.append(label(87, BOOK_Y, "ground", 2, MUTED, "start", 400))
roots.append(label(64, BOOK_Y + 12.5, "3 roots: the bookmark", 2.2, GUIDE))
roots.append(label(64, BOOK_Y + 15.3, "ribbon, split in three", 2.2, "#464C57", "start", 400))
fig_roots = svg("".join(roots), "14 30 92 64", 520, 362)


def tile(bg, m, size, radius="24%", shadow=True, vb="18 18 72 72"):
    sh = "box-shadow:0 1px 4px rgba(20,22,27,.18);" if shadow else ""
    return f'<div style="width:{size}px;height:{size}px;border-radius:{radius};background:{bg};overflow:hidden;{sh}flex:none">{svg(m, vb, size, size)}</div>'


DARK = dict(tree="#7FC3A0", gold="#F0B13A", root="#2F4A3D", paper="#14161B")
REV = dict(tree="#fff", gold="#F2B640", root="#6E9C85", paper=GREEN)
sizes_row = "".join(tile("#fff", mk(), s, r) for s, r in ((192, "24%"), (128, "50%"), (96, "30%"), (64, "50%"), (48, "24%"), (32, "50%")))
mono_row = (tile("#DCEBE2", mk(mono="#1F5A3F"), 96, "50%", False) + tile("#24332B", mk(mono="#CDE6D8"), 96, "50%", False) +
            tile("#14161B", mk(**DARK), 96, "24%") + tile(GREEN, mk(**REV), 96, "24%"))

specs = [
    ("R", "every page's length: the hidden circle C", f"{f(CROWN_R)} u"),
    ("R/φ³", "page width", f"{CROWN_R / PHI ** 3:.2f} u"),
    ("G", "a white ring line, and the lower page of the book", f"{RING_GAP:.2f} u"),
    ("φG", "a band of page between two rings; trunk, roots, top pages of the book", f"{RING_BAND:.2f} u"),
    ("φ²G", "from one ring to the next (10 steps − G/2 = R)", f"{RING_PITCH:.2f} u"),
]
spec_rows = "".join(f"<tr><td class='m'>{a}</td><td>{b}</td><td class='m r'>{c}</td></tr>" for a, b, c in specs)

P = []
P.append(f'''
<section class="cover">
  <div class="cv-l">{fig_cover}</div>
  <div class="cv-r">
    <div class="kick">MARK &amp; RATIONALE · V3 · 18 NOV 2026</div>
    <h1>Yggdrasil</h1>
    <p class="tag">The book that reads you back.</p>
    <p class="lede">A book flips open and its pages fan up into a crown. Cut across them are a tree's growth rings, which are also lines of text. One spine, one circle, one ratio.</p>
    <div class="toc">
      <div><span>01</span>The idea</div><div><span>02</span>The fan</div><div><span>03</span>The rings</div>
      <div><span>04</span>Hidden: nine worlds</div><div><span>05</span>Hidden: the ground</div><div><span>06</span>Colour</div>
      <div><span>07</span>In use</div>
    </div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">01</span><h2>The idea</h2></div>
  <p class="intro">Yggdrasil is the world tree of Norse myth. Paper is made from trees, so every book is a tree you can read. The mark says that in one figure: the pages of an open book become a tree, and the tree's age is written in lines.</p>
  <div class="trio">
    <div><div class="fig">{fig_idea[0]}</div><h3>Flip</h3><p>Nine pages fan up from the spine of an open book, like a book riffled open. Their outline is a tree's crown.</p></div>
    <div class="plus">+</div>
    <div><div class="fig">{fig_idea[1]}</div><h3>Rings</h3><p>A tree's growth rings, one per year, all centred on the heart of the trunk: here, the book's spine.</p></div>
    <div class="plus">=</div>
    <div><div class="fig">{fig_idea[2]}</div><h3>Yggdrasil</h3><p>The rings are cut out of the pages in white. On a page they read as lines of text; across the crown they read as rings.</p></div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">02</span><h2>The fan</h2></div>
  <div class="split">
    <div class="fig">{fig_cons}</div>
    <div class="txt">
      <p>Everything starts at one point, <b>S</b>, the spine of the book. It is the centre of the fan and the centre of the rings.</p>
      <ul>
        <li>Nine pages leave S at equal angles, spread over 136°. The outer two leave just above the book's own top pages, so the fan grows out of the book.</li>
        <li>Every page has the same length R, so all nine tips sit on one hidden circle, <b>C</b>, drawn around S. The crown is the outermost ring of the tree.</li>
        <li>Each page is a leaf shape whose width is its length ÷ φ³.</li>
        <li>The pages are laid outer first, so the middle page lies on top. A thin paper-coloured edge keeps every page separate.</li>
      </ul>
      <p class="small">Drawn on Android's 108-unit adaptive-icon grid (heavy lines every 18 units). The mark stays inside the ⌀66 safe zone, so no launcher mask ever cuts a page.</p>
    </div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">03</span><h2>The rings</h2></div>
  <div class="split">
    <div class="fig">{fig_rings}</div>
    <div class="txt">
      <p>The white lines are circles around the spine, evenly spaced like the lines of a page. Every line has the same width G, and every band of page between two lines is φ × G, so the step from one ring to the next is φ²G.</p>
      <p>The spacing is chosen so the rings land exactly on the crown: ten steps less half a line is R. Every page ends in a full band, never a sliver.</p>
      <table class="spec"><tr><th>Measure</th><th>Used for</th><th class="r">Size</th></tr>{spec_rows}</table>
      <p>The rings run unbroken from page to page, as if the whole crown had been cut from one trunk. That's the hidden part: a page reads as lines of text, and only the whole fan shows they're one set of rings.</p>
      <p>Count them: nine rings, for the nine worlds again.</p>
    </div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">04</span><h2>Hidden: nine worlds</h2></div>
  <div class="split">
    <div class="fig">{fig_nine}</div>
    <div class="txt">
      <p>Yggdrasil holds nine worlds in its branches. The book opens to exactly nine pages.</p>
      <ul>
        <li><b>1 to 4 and 6 to 9</b>, in green: the leaves of the tree.</li>
        <li><b>5</b>, in gold, the middle page, like the first leaf to turn in autumn. In the myth the middle world is Midgard, the world of people. Here it is your page, the one you're reading now.</li>
      </ul>
      <p>The tips of all nine pages sit on one circle that is never drawn.</p>
    </div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">05</span><h2>Hidden: the ground</h2></div>
  <div class="split">
    <div class="fig">{fig_roots}</div>
    <div class="txt">
      <p>The open book is the ground the tree stands on. The crown grows from its spine, and below it hangs the bookmark ribbon.</p>
      <p>The ribbon splits into three. In the myth, Yggdrasil stands on <b>three roots</b>, each reaching a well: one of fate, one of wisdom, one of the cold beginnings. The roots are drawn at half strength, as what is under the ground.</p>
      <p>A bookmark is also where you stopped. The roots are the reading you have already done.</p>
    </div>
  </div>
</section>''')

swatches = "".join(
    f'<div class="sw"><div class="chip" style="background:{c}"></div><div class="m">{h}</div><b>{n}</b><p>{d}</p></div>'
    for c, h, n, d in (
        (GREEN, GREEN, "Forest", "Eight pages, the trunk and the book. One colour for the whole tree."),
        (GOLD, GOLD, "Gold", "The middle page only, yours. Used nowhere else."),
        (ROOT, ROOT, "Root", "Forest at a third of its strength on paper: what is underground."),
    ))
P.append(f'''
<section>
  <div class="hd"><span class="n">06</span><h2>Colour</h2></div>
  <p class="intro">Two colours and a tint. The tree is green, as a tree should be. Your page is gold, like an autumn leaf, and it is the only warm colour in the mark, so the eye goes straight to it.</p>
  <div class="sws">{swatches}</div>
  <div class="row" style="margin-top:24px">
    {tile("#fff", mk(), 150)}{tile("#14161B", mk(**DARK), 150)}{tile(GREEN, mk(**REV), 150)}
    <div class="txt small" style="max-width:300px">The light tile is the app icon. The dark tile is for dark docks and store art, and the reversed forest tile is for a splash screen. The page edges always take the colour of the ground they sit on.</div>
  </div>
</section>''')

P.append(f'''
<section>
  <div class="hd"><span class="n">07</span><h2>In use</h2></div>
  <h3 class="sub">Launcher sizes, from the store listing down to a notification</h3>
  <div class="row">{sizes_row}</div>
  <h3 class="sub">Themed icons (Android 13+) and other grounds</h3>
  <div class="row">{mono_row}</div>
  <div class="lock">
    {tile("#fff", mk(), 64, "24%")}
    <div><div class="wm">Yggdrasil</div><div class="wmt">the book that reads you back</div></div>
  </div>
  <p class="foot">Yggdrasil is a fork of Librera Reader by Ivan Ivanenko, free software under the GNU GPL v3.</p>
</section>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>Yggdrasil mark and rationale</title>
<link href="https://fonts.googleapis.com/css2?family=Instrument+Sans:ital,wght@0,400;0,500;0,600;1,400&family=JetBrains+Mono:wght@400;500;600&family=Literata:ital,wght@0,400;1,400&display=swap" rel="stylesheet">
<style>{CSS}</style></head><body>{"".join(P)}</body></html>'''
open(sys.argv[2], "w").write(html)
