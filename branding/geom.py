"""Yggdrasil mark, version 2: Rings + Flip, on the 108-unit adaptive-icon canvas.

A book is open at the bottom. From its spine S, nine pages fan up into a round crown.
Across the pages run growth rings, and every ring is a line of "words".
"""
from math import cos, sin, radians, degrees, sqrt, pi
import random

PHI = (1 + sqrt(5)) / 2
INK, INDIGO, EMBER = "#14161B", "#2F45E0", "#B4460A"
INDIGO_2 = "#5B6CEB"   # every other page, one shade lighter
ROOT = "#97A2EF"       # the roots: indigo at half strength on paper

S = (54.0, 67.4)            # the spine: centre of the rings and of the fan, in the book's gutter
N_PAGES = 9
FAN = (15.0, 165.0)         # the fan opens 150 degrees, symmetric about the vertical
STEP = (FAN[1] - FAN[0]) / N_PAGES
R_OUT = 30.0
N_RINGS = 7
SQ = sqrt(PHI)              # each ring is sqrt(phi) times the one inside it
RINGS = [R_OUT / SQ ** (N_RINGS - 1 - k) for k in range(N_RINGS)]   # 6.0 ... 30
LINE_W = R_OUT / PHI ** 6   # 1.67, the weight of a line of text
GAP = R_OUT / PHI ** 6      # the white gap between two pages (a branch, in negative space)
WORD_GAP = R_OUT / PHI ** 7 * 1.5  # the space between two words
HEART_R = R_OUT / PHI ** 5  # 2.71, the heartwood
TRUNK_W = R_OUT / PHI ** 5

BOOK_Y = 68.2               # the book's spine: the pages fan straight out of it
MIDDLE = N_PAGES // 2       # page 5 of 9: yours


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pol(c, r, deg):
    return (c[0] + r * cos(radians(deg)), c[1] - r * sin(radians(deg)))


def page_bounds(i):
    a0 = FAN[0] + i * STEP
    return a0, a0 + STEP


def arc_d(c, r, a0, a1):
    p0, p1 = pol(c, r, a0), pol(c, r, a1)
    large = 1 if a1 - a0 > 180 else 0
    return f"M{f(p0[0])},{f(p0[1])} A{f(r)},{f(r)} 0 {large},0 {f(p1[0])},{f(p1[1])}"


def words(seed=11):
    """[(page, ring, d)] - every word is an arc of a ring, inside one page, clear of the gaps."""
    rnd = random.Random(seed)
    out = []
    for i in range(N_PAGES):
        a0, a1 = page_bounds(i)
        for k, r in enumerate(RINGS):
            half_gap = degrees((GAP / 2 + LINE_W / 2) / r)
            lo, hi = a0 + half_gap, a1 - half_gap
            if hi - lo < 1:
                continue
            # a line of text on this page: words of 2..7 units, separated by WORD_GAP
            span_len = radians(hi - lo) * r
            if span_len < 3.5:   # the innermost rings: one short word
                out.append((i, k, arc_d(S, r, lo, hi)))
                continue
            a = lo
            while a < hi - 0.5:
                w = rnd.uniform(2.2, 7.0)
                b = min(a + degrees(w / r), hi)
                if hi - b < degrees(1.6 / r):
                    b = hi
                out.append((i, k, arc_d(S, r, a, b)))
                a = b + degrees(WORD_GAP / r)
    return out


WORDS = words()


def page_color(i):
    if i == MIDDLE:
        return EMBER
    return INDIGO if i % 2 == 0 else INDIGO_2


def book_paths():
    # an open book: two pages, each a gentle curve from the outer edge down to the spine
    left = f"M27,{f(BOOK_Y - 3)} Q40.5,{f(BOOK_Y - 5.5)} 52.6,{f(BOOK_Y)}"
    right = f"M55.4,{f(BOOK_Y)} Q67.5,{f(BOOK_Y - 5.5)} 81,{f(BOOK_Y - 3)}"
    left2 = f"M28.5,{f(BOOK_Y + 0.8)} Q41,{f(BOOK_Y - 1.4)} 52.6,{f(BOOK_Y + 3.6)}"
    right2 = f"M55.4,{f(BOOK_Y + 3.6)} Q67,{f(BOOK_Y - 1.4)} 79.5,{f(BOOK_Y + 0.8)}"
    return [(left, TRUNK_W), (right, TRUNK_W), (left2, TRUNK_W / PHI), (right2, TRUNK_W / PHI)]


def trunk_path():
    return f"M54,{f(S[1])} L54,{f(BOOK_Y + 3.4)}"


def roots_paths():
    y0 = BOOK_Y + 4.2
    return [f"M54,{f(y0)} L54,{f(y0 + 9)}",
            f"M54,{f(y0 + 1.6)} Q50.5,{f(y0 + 5.5)} 46,{f(y0 + 6.8)}",
            f"M54,{f(y0 + 1.6)} Q57.5,{f(y0 + 5.5)} 62,{f(y0 + 6.8)}"]



# ---- v2b: solid pages (Flip) cut by white growth rings (Rings) ----
CROWN_C = (54.0, 49.0)      # hidden circle the page tips sit on
CROWN_R = 24.0
PAGE_FAN = (22.0, 158.0)
TRUNK_W = CROWN_R / PHI ** 5        # 2.16: trunk, roots, the top pages of the book
RING_GAP = CROWN_R / PHI ** 6       # 1.34: the white line of a ring
RING_R = [r for r in (4.6 * SQ ** k for k in range(12)) if r < 37] + [80.0]  # grows by sqrt(phi); the last band runs out past the tips


def page_tip(a):
    dx, dy = cos(radians(a)), -sin(radians(a))
    bx, by = S[0] - CROWN_C[0], S[1] - CROWN_C[1]
    bd = bx * dx + by * dy
    t = -bd + sqrt(bd * bd - (bx * bx + by * by - CROWN_R ** 2))
    return (S[0] + t * dx, S[1] + t * dy), t


def page_shape(i):
    a = PAGE_FAN[1] - (PAGE_FAN[1] - PAGE_FAN[0]) * i / (N_PAGES - 1)
    T, L = page_tip(a)
    w = L / PHI ** 3
    nx, ny = sin(radians(a)), cos(radians(a))
    M = ((S[0] + T[0]) / 2, (S[1] + T[1]) / 2)
    c1, c2 = (M[0] + nx * w, M[1] + ny * w), (M[0] - nx * w, M[1] - ny * w)
    return (f"M{f(S[0])},{f(S[1])} Q{f(c1[0])},{f(c1[1])} {f(T[0])},{f(T[1])} "
            f"Q{f(c2[0])},{f(c2[1])} {f(S[0])},{f(S[1])} Z"), T


def ring_bands():
    """The heart and the annuli between consecutive rings, as one even-odd path: what is left of
    a page once the white ring lines are cut out of it."""
    x, y = S

    def c(r):
        return f"M{f(x - r)},{f(y)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"

    d = [c(RING_R[0] - RING_GAP / 2)]          # the heart: solid, so every page starts at the spine
    for k in range(len(RING_R) - 1):
        d += [c(RING_R[k + 1] - RING_GAP / 2), c(RING_R[k] + RING_GAP / 2)]
    return " ".join(d)


ORDER = [0, 8, 1, 7, 2, 6, 3, 5, 4]   # outer pages first, so the middle one lies on top


def mark2_svg(mono=None, ink=INK, ember=EMBER, indigo=INDIGO, indigo2=INDIGO_2, root=ROOT, uid="m", paper="#fff"):
    g = ["<defs>"]
    for i in range(N_PAGES):
        d, _ = page_shape(i)
        g.append(f'<clipPath id="{uid}p{i}"><path d="{d}"/></clipPath>')
    g.append("</defs>")
    for dd in roots_paths():
        g.append(f'<path d="{dd}" fill="none" stroke="{mono or root}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"{" opacity=\".5\"" if mono else ""}/>')
    bands = ring_bands()
    for i in ORDER:
        col = mono or (ember if i == MIDDLE else (indigo if i % 2 == 0 else indigo2))
        d, _ = page_shape(i)
        # a white outline under each page keeps overlapping pages apart
        g.append(f'<path d="{d}" fill="{paper}" stroke="{paper}" stroke-width="1.2" stroke-linejoin="round"/>' if not mono else "")
        g.append(f'<path clip-path="url(#{uid}p{i})" d="{bands}" fill="{col}" fill-rule="evenodd"/>')
    g.append(f'<path d="{trunk_path()}" fill="none" stroke="{mono or ink}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"/>')
    for dd, w in book_paths():
        g.append(f'<path d="{dd}" fill="none" stroke="{mono or ink}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    return "\n".join(g)


def mark_svg(mono=None, ink=INK, ember=EMBER, indigo=INDIGO, indigo2=INDIGO_2, root=ROOT):
    g = []
    for d in roots_paths():
        g.append(f'<path d="{d}" fill="none" stroke="{mono or root}" stroke-width="{f(TRUNK_W * 0.75)}" stroke-linecap="round"{" opacity=\".5\"" if mono else ""}/>')
    g.append(f'<path d="{trunk_path()}" fill="none" stroke="{mono or ink}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"/>')
    for i, k, d in WORDS:
        col = mono or (ember if i == MIDDLE else (indigo if i % 2 == 0 else indigo2))
        g.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{f(LINE_W)}" stroke-linecap="round"/>')
    for d, w in book_paths():
        g.append(f'<path d="{d}" fill="none" stroke="{mono or ink}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    g.append(f'<circle cx="{f(S[0])}" cy="{f(S[1])}" r="{f(HEART_R)}" fill="{mono or ink}"/>')
    return "\n".join(g)


if __name__ == "__main__":
    print("rings", [round(r, 2) for r in RINGS], "words", len(WORDS))
    far = max(sqrt((pol(S, R_OUT + LINE_W / 2, a)[0] - 54) ** 2 + (pol(S, R_OUT + LINE_W / 2, a)[1] - 54) ** 2) for a in (15, 90, 165))
    print("farthest crown point from centre", round(far, 2))
