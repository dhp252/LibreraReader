"""Builds the Yggdrasil mark rationale (HTML for a landscape A4 PDF)."""
import sys
sys.path.insert(0, sys.argv[1])
from geom import *

GUIDE = "#B4460A"
GRID = "#C9CDD6"


def svg(body, vb="0 0 108 108", w=None, h=None, extra=""):
    size = (f' width="{w}"' if w else "") + (f' height="{h}"' if h else "")
    return f'<svg viewBox="{vb}"{size} xmlns="http://www.w3.org/2000/svg" {extra}>{body}</svg>'


def dashed_circle(c, r, color=GUIDE, w=0.35, dash="1.2 0.9", op=1):
    return f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{f(r)}" fill="none" stroke="{color}" stroke-width="{w}" stroke-dasharray="{dash}" opacity="{op}"/>'


def dot(c, r=0.7, color=GUIDE):
    return f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{r}" fill="{color}"/>'


def label(x, y, t, size=2.6, color=GUIDE, anchor="start", weight=500):
    return f'<text x="{f(x)}" y="{f(y)}" font-family="JetBrains Mono" font-size="{size}" fill="{color}" text-anchor="{anchor}" font-weight="{weight}">{t}</text>'


def line(a, b, color=GUIDE, w=0.3, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{f(a[0])}" y1="{f(a[1])}" x2="{f(b[0])}" y2="{f(b[1])}" stroke="{color}" stroke-width="{w}"{d}/>'


def ghost(op=0.18):
    return f'<g opacity="{op}">{mark_svg()}</g>'


def grid108():
    g = []
    for i in range(0, 109, 6):
        w = 0.25 if i % 18 else 0.4
        g.append(line((i, 0), (i, 108), GRID, w))
        g.append(line((0, i), (108, i), GRID, w))
    return "".join(g)


def ruler_left():
    g = [line((4, 0), (4, 108), "#5E6570", 0.25)]
    for y in range(0, 109, 2):
        ln = 2.4 if y % 10 == 0 else 1.2
        g.append(line((4, y), (4 + ln, y), "#5E6570", 0.2))
        if y % 18 == 0:
            g.append(label(1.2, y + 0.8, str(y), 1.8, "#5E6570", "start", 400))
    return "".join(g)


MARK = mark_svg()

# ---------- page figures ----------
fig_cover = svg(MARK, "16 16 76 76", 430, 430)

fig_story = [
    svg(f'<g opacity="1">' + "".join(f'<path d="{d}" fill="none" stroke="{INDIGO}" stroke-width="{f(w)}" stroke-linecap="round"/>' for d, w in crown_strokes()) +
        f'<path d="{trunk_low()}" fill="none" stroke="{INDIGO}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"/>' +
        "".join(f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="{f(LEAF_R)}" fill="{INDIGO}"/>' for t in CROWN_TIPS) +
        f'<g opacity=".5">' + "".join(f'<path d="{d}" fill="none" stroke="{INDIGO}" stroke-width="{f(w)}" stroke-linecap="round"/>' for d, w in root_strokes()) +
        "".join(f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="{f(LEAF_R*.8)}" fill="{INDIGO}"/>' for t in ROOT_TIPS) + '</g></g>', "16 16 76 76", 190, 190),
    svg("".join(f'<path d="{d}" fill="none" stroke="{INK}" stroke-width="{f(w)}" stroke-linecap="round"/>' for d, w in book_strokes()), "16 16 76 76", 190, 190),
    svg(f'<path d="{lid_path()}" fill="none" stroke="{INDIGO}" stroke-width="{f(LID_W)}" stroke-linecap="round"/>' +
        f'<path d="{book_strokes()[0][0]} {book_strokes()[1][0]}" fill="none" stroke="{INK}" stroke-width="{f(BOOK_LAYERS[0][2])}" stroke-linecap="round"/>' +
        f'<circle cx="54" cy="54" r="{f(PUPIL_R)}" fill="{EMBER}"/>', "16 16 76 76", 190, 190),
]

# construction: the two circles
cons = [grid108(), ruler_left(), ghost(0.22),
        dashed_circle((54, 54), 33, "#5E6570", 0.3, "0.6 0.8"), label(54, 19.2, "SAFE ZONE ⌀66", 1.9, "#5E6570", "middle", 400),
        dashed_circle(C1, R), dashed_circle(C2, R),
        dot(C1), dot(C2), dot(EYE_L), dot(EYE_R),
        line(C1, C2, GUIDE, 0.35), line(C1, ARM_R, GUIDE, 0.25, "0.8 0.6"),
        label(55.4, 43.2, "C1"), label(55.4, 66.4, "C2"),
        label((C1[0] + ARM_R[0]) / 2 + 0.6, (C1[1] + ARM_R[1]) / 2 - 1.4, "R", 2.6), label(52.2, C1[1] + 3.6, "R", 2.2, GUIDE, "end"),
        line(EYE_L, EYE_R, GUIDE, 0.25, "0.5 0.5"),
        label(EYE_L[0] - 1.2, 52.6, "×", 2.6, GUIDE, "end"),
        ]
fig_construct = svg("".join(cons), "0 0 108 108", 520, 520)

# golden ratio: inner rune = outer rune / phi
gold = [ghost(0.16), dashed_circle(C1, R, GUIDE, 0.3),
        f'<path d="M{p(C1)} L{p(ARM_L)} M{p(C1)} L{p(ARM_R)} M{p(C1)} L{p(TOP)}" stroke="{INDIGO}" stroke-width="1.3" fill="none" stroke-linecap="round"/>',
        f'<path d="M{p(FORK_IN)} L{p(TIP_IL)} M{p(FORK_IN)} L{p(TIP_IR)}" stroke="{EMBER}" stroke-width="1.3" fill="none" stroke-linecap="round"/>',
        dot(C1, 0.9, INDIGO), dot(FORK_IN, 0.9, EMBER),
        label(ARM_R[0] + 2, ARM_R[1] + 1, "R", 2.4, INDIGO), label(TIP_IR[0] + 2.4, TIP_IR[1], "R/φ", 2.4, EMBER),
        label(54, 92, "the small fork is the big fork × 1/φ,", 2.2, "#464C57", "middle", 400),
        label(54, 95.4, "slid up the trunk until its tips touch the same circle", 2.2, "#464C57", "middle", 400)]
fig_gold = svg("".join(gold), "14 14 80 86", 420, 452)

# hidden eye
eye = [ghost(0.2),
       f'<path d="{lid_path()} {lower_lid_path()}" fill="{EMBER}" fill-opacity=".08" stroke="{GUIDE}" stroke-width=".45" stroke-dasharray="1.2 .8"/>',
       f'<circle cx="54" cy="54" r="{f(PUPIL_R)}" fill="{EMBER}"/>',
       dashed_circle(C1, R, GRID, 0.3, "0.6 0.8"), dashed_circle(C2, R, GRID, 0.3, "0.6 0.8"),
       dot(C1, 0.8, INDIGO),
       line((64, 46.0 if False else C2[1] - (R**2 - 100) ** 0.5), (72, 30), GUIDE, 0.25),
       label(73, 29, "upper lid: arc of C2,", 2.1, GUIDE), label(73, 31.8, "through C1's centre, where", 2.1, "#464C57", "start", 400),
       label(73, 34.6, "the branches split", 2.1, "#464C57", "start", 400),
       line((54 + PUPIL_R, 54), (73, 54), EMBER, 0.25), label(74, 54.8, "pupil: the fruit, you", 2.1, EMBER),
       line((62, C1[1] + (R**2 - 64) ** 0.5), (72, 78), GUIDE, 0.25),
       label(73, 78, "lower lid: the top page,", 2.1, GUIDE), label(73, 80.8, "an arc of C1", 2.1, "#464C57", "start", 400)]
fig_eye = svg("".join(eye), "20 18 92 72", 560, 438)

# runes
runes = [mark_svg(roots_op=0.5),
         line((24, 54), (84, 54), "#5E6570", 0.3, "1 1"), label(86, 54.8, "waterline", 2, "#5E6570", "start", 400),
         f'<text x="16" y="38" font-size="11" fill="{GUIDE}" text-anchor="middle">ᛉ</text>',
         label(16, 42.5, "ALGIZ", 2, GUIDE, "middle"), label(16, 45.3, "life · a Y", 1.8, "#464C57", "middle", 400),
         f'<text x="16" y="80" font-size="11" fill="{GUIDE}" text-anchor="middle">ᛦ</text>',
         label(16, 84.5, "YR", 2, GUIDE, "middle"), label(16, 87.3, "the yew", 1.8, "#464C57", "middle", 400)]
fig_runes = svg("".join(runes), "4 14 100 80", 560, 448)

# nine worlds
nine = [ghost(0.32), dashed_circle(C1, R, GUIDE, 0.3), dashed_circle(C2, R, GUIDE, 0.3)]
pts = CROWN_TIPS + [(54, 54)] + ROOT_TIPS
for i, t in enumerate(pts, 1):
    col = EMBER if i == 6 else GUIDE
    nine.append(f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="2.6" fill="#fff" stroke="{col}" stroke-width=".4"/>')
    nine.append(label(t[0], t[1] + 0.9, str(i), 2.4, col, "middle", 600))
nine += [label(54, 99, "5 in the crown · 1 in the middle · 3 in the roots", 2.2, "#464C57", "middle", 400)]
fig_nine = svg("".join(nine), "14 14 80 88", 420, 462)

# colours on tiles
def tile(bg, m, size, radius="24%", shadow=True, vb="18 18 72 72"):
    sh = "box-shadow:0 1px 4px rgba(20,22,27,.18);" if shadow else ""
    return f'<div style="width:{size}px;height:{size}px;border-radius:{radius};background:{bg};overflow:hidden;{sh}flex:none">{svg(m, vb, size, size)}</div>'

DARK = mark_svg(ink="#E9EBF2", indigo="#8E9BFF", ember="#FF8A4C")
MONO_L = mark_svg(mono="#2337C4")
MONO_D = mark_svg(mono="#C9D0FF")

sizes_row = "".join(tile("#fff", MARK, s, r) for s, r in ((192, "24%"), (128, "50%"), (96, "30%"), (64, "50%"), (48, "24%"), (32, "50%")))
mono_row = (tile("#DDE1F8", MONO_L, 96, "50%", False) + tile("#2A2F45", MONO_D, 96, "50%", False) +
            tile("#14161B", DARK, 96, "24%") + tile("#2F45E0", mark_svg(ink="#fff", indigo="#fff", ember="#FFB27A"), 96, "24%"))

# construction sheet values
specs = [
    ("R", "radius of both circles", f"{f(R)} u", "the root measure"),
    ("R/φ", "small fork arms", f"{R/PHI:.2f} u", "1 step"),
    ("R/φ³", "pupil radius, page thickness", f"{PUPIL_R:.2f} u", "3 steps"),
    ("R/φ⁴", "trunk, leaves", f"{TRUNK_W:.2f} u", "4 steps"),
    ("R/φ⁵", "branches", f"{BRANCH_W:.2f} u", "5 steps"),
    ("R/φ⁶", "eyelid line, spine gap", f"{LID_W:.2f} u", "6 steps"),
]
spec_rows = "".join(f"<tr><td class='m'>{a}</td><td>{b}</td><td class='m r'>{c}</td></tr>" for a, b, c, _ in specs)

PAGES = []

PAGES.append(f'''
<section class="cover">
  <div class="cv-l">{fig_cover}</div>
  <div class="cv-r">
    <div class="kick">MARK &amp; RATIONALE · V1 · 18 NOV 2026</div>
    <h1>Yggdrasil</h1>
    <p class="tag">The book that reads you back.</p>
    <p class="lede">A tree that grows out of an open book, drawn with two circles, one ratio and three things you only notice the second time you look.</p>
    <div class="toc">
      <div><span>01</span>The idea</div><div><span>02</span>Two circles</div><div><span>03</span>One ratio</div>
      <div><span>04</span>Hidden: the eye</div><div><span>05</span>Hidden: the runes</div><div><span>06</span>Hidden: nine worlds</div>
      <div><span>07</span>Colour</div><div><span>08</span>In use</div>
    </div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">01</span><h2>The idea</h2></div>
  <p class="intro">In Norse myth, Yggdrasil is the world tree: its crown holds the heavens, its roots reach the wells of fate and memory, and all nine worlds hang in its branches. This reader is built around one promise, that the book also reads you, so the mark puts three things into one figure.</p>
  <div class="trio">
    <div><div class="fig">{fig_story[0]}</div><h3>A tree</h3><p>The crown above, the roots below: what you are reading grows, and what you have read takes root.</p></div>
    <div class="plus">+</div>
    <div><div class="fig">{fig_story[1]}</div><h3>An open book</h3><p>Three stacked pages, split at the spine. The tree does not stand on the book; it grows through it.</p></div>
    <div class="plus">=</div>
    <div><div class="fig">{fig_story[2]}</div><h3>A watching eye</h3><p>The top page and one fine arc close into an eye. Its pupil is the orange fruit at the heart of the tree.</p></div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">02</span><h2>Two circles</h2></div>
  <div class="split">
    <div class="fig big">{fig_construct}</div>
    <div class="txt">
      <p>The whole mark sits on two equal circles, <b>C1</b> and <b>C2</b>, whose centres are exactly one radius apart. Each circle passes through the other's centre. The almond where they overlap is the <i>vesica piscis</i>, the oldest construction in geometry and the shape of an eye.</p>
      <ul>
        <li><b>C1</b> carries the crown: all five leaves sit on it.</li>
        <li><b>C2</b> carries the roots: all three root tips sit on it.</li>
        <li>The two <b>×</b> points where the circles cross are the corners of the eye.</li>
        <li>C2 is C1 mirrored in the horizontal centre line, so the roots are the crown's reflection.</li>
      </ul>
      <p class="small">Drawn on Android's 108-unit adaptive-icon grid (heavy lines every 18 units). The whole mark fits in a ⌀60 circle, well inside the ⌀66 safe zone, so no launcher mask ever cuts a leaf.</p>
    </div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">03</span><h2>One ratio</h2></div>
  <div class="split">
    <div class="fig">{fig_gold}</div>
    <div class="txt">
      <p>The main sizes are the radius divided by the golden ratio, φ = 1.618, a whole number of times. Change R and the whole mark scales and stays in tune.</p>
      <table class="spec"><tr><th>Measure</th><th>Used for</th><th class="r">Size</th></tr>{spec_rows}</table>
      <p>The crown is a tree inside a tree. The small fork is the large fork shrunk by φ, and it sits exactly where its tips land on the same circle as the large one. It's the same self-similarity a real branch has.</p>
    </div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">04</span><h2>Hidden: the eye</h2></div>
  <div class="split">
    <div class="fig">{fig_eye}</div>
    <div class="txt">
      <p>Look at the mark as a tree and it stays a tree. Look at the middle and an eye opens.</p>
      <ul>
        <li>The <b>lower lid</b> is the top page of the book, an arc of C1.</li>
        <li>The <b>upper lid</b> is a fine arc of C2. It passes through C1's centre, which is exactly where the two big branches split, so the eyelid and the tree share a point.</li>
        <li>The <b>pupil</b> is the orange fruit, at the exact centre of the canvas.</li>
      </ul>
      <p>The reading: you read the book, and the book looks back. Recall, spaced review and the eye sensor are this idea, drawn.</p>
    </div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">05</span><h2>Hidden: the runes</h2></div>
  <div class="split">
    <div class="fig">{fig_runes}</div>
    <div class="txt">
      <p>Above the book, the trunk and its two big branches form <b>ᛉ</b>, Algiz: the rune of life and protection, and the Y that starts the name.</p>
      <p>Below the book, the roots form the same rune turned upside down, <b>ᛦ</b>, Yr: the rune of the <b>yew</b>. Old Norse writers called Yggdrasil an ash, but many scholars think the evergreen yew was meant. The world tree's own rune is hiding in its roots.</p>
      <p>The book is the waterline. What grows above it is reflected below it at half strength, like a tree mirrored in a lake. The book shows you back to yourself.</p>
    </div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">06</span><h2>Hidden: nine worlds</h2></div>
  <div class="split">
    <div class="fig">{fig_nine}</div>
    <div class="txt">
      <p>Yggdrasil holds nine worlds. The mark has exactly nine points, and they sit on three levels, like the myth's heavens, middle world and underworld:</p>
      <ul>
        <li><b>1 to 5</b>, the crown: five leaves on the upper circle.</li>
        <li><b>6</b>, the middle world: the orange pupil at the centre. In the myth that's Midgard, the world of people; here it's the reader.</li>
        <li><b>7 to 9</b>, the roots: three tips on the lower circle, for the three roots the tree stands on.</li>
      </ul>
      <p>Neither circle is drawn. You only see them once you know the points are on them.</p>
    </div>
  </div>
</section>''')

swatches = "".join(
    f'<div class="sw"><div class="chip" style="background:{c}"></div><div class="m">{h}</div><b>{n}</b><p>{d}</p></div>'
    for c, h, n, d in (
        (INDIGO, "#2F45E0", "Indigo", "The tree and the eyelid. The canvas accent colour, already used across the app."),
        (INK, "#14161B", "Ink", "The book. The same ink as the reading text."),
        (EMBER, "#B4460A", "Ember", "The pupil only, never anything else. The canvas colour for “the book is reading you”."),
        ("#FFFFFF", "#FFFFFF", "Paper", "The ground. The icon tile is a blank page."),
    ))

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">07</span><h2>Colour</h2></div>
  <p class="intro">Four colours, all taken from the app's own canvas design, so the icon and the reading screen speak the same language. Orange appears exactly once.</p>
  <div class="sws">{swatches}</div>
  <div class="row" style="margin-top:26px">
    {tile("#fff", MARK, 150)}{tile("#14161B", DARK, 150)}{tile("#2F45E0", mark_svg(ink="#fff", indigo="#fff", ember="#FFB27A"), 150)}
    <div class="txt small" style="max-width:300px">Light tile (the app icon), dark tile (for dark docks and store art) and the reversed indigo tile (for a splash screen). The roots always keep half strength, so the reflection still reads.</div>
  </div>
</section>''')

PAGES.append(f'''
<section>
  <div class="hd"><span class="n">08</span><h2>In use</h2></div>
  <h3 class="sub">Launcher sizes, from the store listing down to a notification</h3>
  <div class="row">{sizes_row}</div>
  <h3 class="sub">Themed icons (Android 13+) and other grounds</h3>
  <div class="row">{mono_row}</div>
  <div class="lock">
    {tile("#fff", MARK, 64, "24%")}
    <div><div class="wm">Yggdrasil</div><div class="wmt">the book that reads you back</div></div>
  </div>
  <p class="foot">Yggdrasil is a fork of Librera Reader by Ivan Ivanenko, free software under the GNU GPL v3.</p>
</section>''')

CSS = '''
@page { size: 297mm 210mm; margin: 0 }
* { box-sizing: border-box }
body { margin: 0; font-family: 'Instrument Sans', sans-serif; color: #14161B; -webkit-print-color-adjust: exact; print-color-adjust: exact }
section { width: 297mm; height: 210mm; padding: 16mm 18mm 14mm; page-break-after: always; position: relative; background: #FAFAFB; overflow: hidden }
section::after { content: "YGGDRASIL · MARK & RATIONALE"; position: absolute; left: 18mm; bottom: 8mm; font: 500 8pt 'JetBrains Mono'; letter-spacing: .12em; color: #8A909B }
.hd { display: flex; align-items: baseline; gap: 14px; border-bottom: 1px solid #E3E5EA; padding-bottom: 10px; margin-bottom: 18px }
.hd .n { font: 500 12pt 'JetBrains Mono'; color: #B4460A }
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
.plus { font: 300 34pt 'Instrument Sans'; color: #B4460A }
.cover { display: flex; align-items: center; gap: 30px; background: #fff }
.cv-l { flex: none; border-radius: 26%; box-shadow: 0 2px 16px rgba(20,22,27,.1); background: #fff }
.kick { font: 500 9pt 'JetBrains Mono'; letter-spacing: .12em; color: #B4460A }
h1 { font: 600 54pt 'Instrument Sans'; margin: 8px 0 0; letter-spacing: -.02em }
.tag { font: italic 400 19pt 'Literata', serif; margin: 2px 0 18px; color: #2F45E0 }
.lede { font-size: 12.5pt; max-width: 120mm }
.toc { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 22px; margin-top: 22px; font-size: 10.5pt }
.toc span { font-family: 'JetBrains Mono'; color: #B4460A; margin-right: 10px; font-size: 9pt }
table.spec { border-collapse: collapse; width: 100%; margin: 4px 0 16px; font-size: 10.5pt }
.spec th { text-align: left; font: 500 8pt 'JetBrains Mono'; letter-spacing: .08em; text-transform: uppercase; color: #5E6570; border-bottom: 1px solid #E3E5EA; padding: 6px 4px }
.spec td { border-bottom: 1px solid #F1F2F5; padding: 6px 4px }
.m { font-family: 'JetBrains Mono'; font-size: 9.5pt } .r { text-align: right }
.sws { display: flex; gap: 18px; margin-top: 20px }
.sw { flex: 1; background: #fff; border: 1px solid #E3E5EA; border-radius: 14px; padding: 12px }
.sw .chip { height: 70px; border-radius: 10px; border: 1px solid #E3E5EA; margin-bottom: 10px }
.sw p { font-size: 9.5pt; margin: 4px 0 0 }
.row { display: flex; gap: 22px; align-items: flex-end }
.lock { display: flex; align-items: center; gap: 16px; margin-top: 26px }
.wm { font: 600 26pt 'Instrument Sans'; letter-spacing: -.01em }
.wmt { font: italic 12pt 'Literata', serif; color: #2F45E0 }
.foot { position: absolute; right: 18mm; bottom: 7mm; font-size: 8.5pt; color: #8A909B; margin: 0 }
'''

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>Yggdrasil mark and rationale</title>
<link href="https://fonts.googleapis.com/css2?family=Instrument+Sans:ital,wght@0,400;0,500;0,600;1,400&family=JetBrains+Mono:wght@400;500;600&family=Literata:ital,wght@0,400;1,400&display=swap" rel="stylesheet">
<style>{CSS}</style></head><body>{"".join(PAGES)}</body></html>'''
open(sys.argv[2], "w").write(html)
