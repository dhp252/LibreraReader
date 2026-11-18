"""Yggdrasil mark: Rings + Flip, on the 108-unit adaptive-icon canvas.

A book is open at the bottom. From its spine S, nine pages of equal length fan up into a round
crown. Across the pages run nine growth rings around S, evenly spaced, cut out in white.
"""
from math import cos, sin, radians, sqrt

PHI = (1 + sqrt(5)) / 2

# colour: one green for the tree and the book, one gold for your page
GREEN = "#2E6B4F"
GOLD = "#D9961A"
ROOT = "#A9C8B8"            # the roots: green at a third of its strength on paper

S = (54.0, 64.4)            # the spine: centre of the fan, of the rings and of the crown
BOOK_Y = 65.2               # the book's spine: the pages fan straight out of it
N_PAGES = 9
MIDDLE = N_PAGES // 2       # page 5 of 9: yours
ORDER = [0, 8, 1, 7, 2, 6, 3, 5, 4]   # outer pages first, so the middle one lies on top

CROWN_R = 34.0              # every page is this long: the tips sit on one circle around S
PAGE_FAN = (22.0, 158.0)    # the outer pages leave the book just above its top pages

# nine rings, evenly spaced. A white ring line is G wide, the band of page between two lines is
# phi x G, and the last band ends exactly at the tips: 10 pitches - G/2 = CROWN_R.
N_RINGS = 9
RING_GAP = CROWN_R / (10 * PHI ** 2 - 0.5)   # 1.32: the white line of a ring
RING_BAND = PHI * RING_GAP                     # 2.14: the page between two rings
RING_PITCH = RING_GAP + RING_BAND              # 3.47 = phi^2 x G
RING_R = [RING_PITCH * k for k in range(1, N_RINGS + 1)] + [200.0]   # the last band runs out past the tips

TRUNK_W = RING_BAND         # trunk, roots and the top pages of the book: as wide as a band
BOOK_W2 = RING_GAP          # the page under them: as wide as a ring line


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pol(c, r, deg):
    return (c[0] + r * cos(radians(deg)), c[1] - r * sin(radians(deg)))


def page_angle(i):
    return PAGE_FAN[1] - (PAGE_FAN[1] - PAGE_FAN[0]) * i / (N_PAGES - 1)


def page_shape(i):
    """A leaf from S to its tip on the crown circle, as wide as its length / phi^3."""
    a = page_angle(i)
    T = pol(S, CROWN_R, a)
    w = CROWN_R / PHI ** 3
    nx, ny = sin(radians(a)), cos(radians(a))
    M = ((S[0] + T[0]) / 2, (S[1] + T[1]) / 2)
    c1, c2 = (M[0] + nx * w, M[1] + ny * w), (M[0] - nx * w, M[1] - ny * w)
    return (f"M{f(S[0])},{f(S[1])} Q{f(c1[0])},{f(c1[1])} {f(T[0])},{f(T[1])} "
            f"Q{f(c2[0])},{f(c2[1])} {f(S[0])},{f(S[1])} Z"), T


def book_paths():
    # an open book: two pages, each a gentle curve from the outer edge down to the spine
    left = f"M27,{f(BOOK_Y - 3)} Q40.5,{f(BOOK_Y - 5.5)} 52.6,{f(BOOK_Y)}"
    right = f"M55.4,{f(BOOK_Y)} Q67.5,{f(BOOK_Y - 5.5)} 81,{f(BOOK_Y - 3)}"
    left2 = f"M28.5,{f(BOOK_Y + 0.8)} Q41,{f(BOOK_Y - 1.4)} 52.6,{f(BOOK_Y + 3.6)}"
    right2 = f"M55.4,{f(BOOK_Y + 3.6)} Q67,{f(BOOK_Y - 1.4)} 79.5,{f(BOOK_Y + 0.8)}"
    return [(left, TRUNK_W), (right, TRUNK_W), (left2, BOOK_W2), (right2, BOOK_W2)]


def trunk_path():
    return f"M54,{f(S[1])} L54,{f(BOOK_Y + 3.4)}"


def roots_paths():
    y0 = BOOK_Y + 4.2
    return [f"M54,{f(y0)} L54,{f(y0 + 9)}",
            f"M54,{f(y0 + 1.6)} Q50.5,{f(y0 + 5.5)} 46,{f(y0 + 6.8)}",
            f"M54,{f(y0 + 1.6)} Q57.5,{f(y0 + 5.5)} 62,{f(y0 + 6.8)}"]


def ring_bands():
    """The heart and the annuli between consecutive rings, as one even-odd path: what is left of
    a page once the white ring lines are cut out of it."""
    x, y = S

    def c(r):
        # four quarter arcs, not two half arcs: a half arc whose rounded end points sit a hair
        # less than 2r apart is redrawn by the renderer around a shifted centre (by up to 0.5 u)
        q = f"{f(r)},{f(r)} 0 0,0"
        return (f"M{f(x - r)},{f(y)} A{q} {f(x)},{f(y + r)} A{q} {f(x + r)},{f(y)} "
                f"A{q} {f(x)},{f(y - r)} A{q} {f(x - r)},{f(y)} Z")

    d = [c(RING_R[0] - RING_GAP / 2)]          # the heart: solid, so every page starts at the spine
    for k in range(len(RING_R) - 1):
        d += [c(RING_R[k + 1] - RING_GAP / 2), c(RING_R[k] + RING_GAP / 2)]
    return " ".join(d)


def page_color(i, tree=GREEN, gold=GOLD):
    return gold if i == MIDDLE else tree


def mark2_svg(mono=None, tree=GREEN, gold=GOLD, root=ROOT, uid="m", paper="#fff"):
    g = ["<defs>"]
    for i in range(N_PAGES):
        d, _ = page_shape(i)
        g.append(f'<clipPath id="{uid}p{i}"><path d="{d}"/></clipPath>')
    g.append("</defs>")
    for dd in roots_paths():
        g.append(f'<path d="{dd}" fill="none" stroke="{mono or root}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"{" opacity=\".5\"" if mono else ""}/>')
    bands = ring_bands()
    for i in ORDER:
        d, _ = page_shape(i)
        # a paper-coloured outline under each page keeps overlapping pages apart
        if not mono:
            g.append(f'<path d="{d}" fill="{paper}" stroke="{paper}" stroke-width="1.2" stroke-linejoin="round"/>')
        g.append(f'<path clip-path="url(#{uid}p{i})" d="{bands}" fill="{mono or page_color(i, tree, gold)}" fill-rule="evenodd"/>')
    g.append(f'<path d="{trunk_path()}" fill="none" stroke="{mono or tree}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"/>')
    for dd, w in book_paths():
        g.append(f'<path d="{dd}" fill="none" stroke="{mono or tree}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    return "\n".join(g)


if __name__ == "__main__":
    print("gap", round(RING_GAP, 3), "band", round(RING_BAND, 3), "pitch", round(RING_PITCH, 3))
    print("rings", [round(r, 2) for r in RING_R[:-1]])
    print("tips", [tuple(round(v, 1) for v in page_shape(i)[1]) for i in range(N_PAGES)])
