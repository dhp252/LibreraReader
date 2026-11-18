"""Geometry of the Yggdrasil mark, on the 108-unit Android adaptive-icon canvas.

Everything comes from two equal circles whose centres are one radius apart (a vesica piscis)
and from powers of the golden ratio of that radius.
"""
from math import cos, sin, radians, sqrt

PHI = (1 + sqrt(5)) / 2
O = (54.0, 54.0)          # canvas centre = centre of the eye
R = 18.0                  # radius of both circles
C1 = (54.0, 54.0 - R / 2)  # upper circle: the crown
C2 = (54.0, 54.0 + R / 2)  # lower circle: the roots (C1 mirrored in the waterline y = 54)
H = R * sqrt(3) / 2       # half-width of the eye (where the circles cross)

PUPIL_R = R / PHI ** 3
LEAF_R = R / PHI ** 4 * 0.93  # 2.73 (one step down, trimmed so the crown stays in the safe zone)
TRUNK_W = R / PHI ** 4    # 2.92
BRANCH_W = R / PHI ** 5   # 1.80
LID_W = R / PHI ** 6      # 1.11
BOOK_T = R / PHI ** 3     # 4.72 thickness of the pages
SPINE_GAP = R / PHI ** 6  # 1.11


def on(c, deg, r=R):
    """Point on a circle round c at angle deg (maths angle, y up)."""
    return (c[0] + r * cos(radians(deg)), c[1] - r * sin(radians(deg)))


# Outer rune: stem to the top of C1, arms are radii of C1 at 30 deg above the horizontal.
TOP = on(C1, 90)
ARM_L, ARM_R = on(C1, 150), on(C1, 30)
# Inner rune: the outer one scaled by 1/phi, slid up the stem until its tips land on C1 too.
r_in = R / PHI
dx, dy = r_in * cos(radians(30)), r_in * sin(radians(30))
# solve dx^2 + (d + dy)^2 = R^2 for d (distance of the inner fork above C1's centre)
d_in = sqrt(R ** 2 - dx ** 2) - dy
FORK_IN = (54.0, C1[1] - d_in)
TIP_IL, TIP_IR = (54 - dx, FORK_IN[1] - dy), (54 + dx, FORK_IN[1] - dy)
# Roots: the outer rune mirrored in the waterline, onto C2 (the yew rune).
ROOT_B = on(C2, 270)
ROOT_L, ROOT_R = on(C2, 210), on(C2, 330)
# Eye corners
EYE_L, EYE_R = (54 - H, 54.0), (54 + H, 54.0)

CROWN_TIPS = [ARM_L, TIP_IL, TOP, TIP_IR, ARM_R]
ROOT_TIPS = [ROOT_L, ROOT_B, ROOT_R]


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def p(pt):
    return f"{f(pt[0])},{f(pt[1])}"


def arc_pts(c, r, a0, a1, n=48):
    return [on(c, a0 + (a1 - a0) * i / n, r) for i in range(n + 1)]


BOOK_LAYERS = [  # (radius, half-span in degrees from the spine, stroke width): the page stack
    (R, 67, BOOK_T * 0.62),
    (R + BOOK_T * 0.95, 61, BOOK_T * 0.36),
    (R + BOOK_T * 1.6, 55, BOOK_T * 0.26),
]


def book_strokes():
    """Open book: the top page lies on C1 itself (the lower eyelid); two thinner pages under it.
    Each layer is split at the spine."""
    from math import degrees
    out = []
    for r, span, w in BOOK_LAYERS:
        ga = degrees((SPINE_GAP / 2 + w / 2) / r)
        for a0, a1 in ((270 - span, 270 - ga), (270 + ga, 270 + span)):
            s_, e_ = on(C1, a0, r), on(C1, a1, r)
            out.append((f"M{p(s_)} A{f(r)},{f(r)} 0 0,0 {p(e_)}", w))
    return out


def lid_path():
    """Upper eyelid: arc of C2 from the left corner over C1's centre to the right corner."""
    return f"M{p(EYE_L)} A{f(R)},{f(R)} 0 0,1 {p(EYE_R)}"


def lower_lid_path():
    return f"M{p(EYE_L)} A{f(R)},{f(R)} 0 0,0 {p(EYE_R)}"


def crown_strokes():
    return [
        (f"M54,{f(54 - PUPIL_R - 0.9)} L{p(TOP)}", TRUNK_W),
        (f"M{p(C1)} L{p(ARM_L)} M{p(C1)} L{p(ARM_R)}", BRANCH_W * 1.25),
        (f"M{p(FORK_IN)} L{p(TIP_IL)} M{p(FORK_IN)} L{p(TIP_IR)}", BRANCH_W),
    ]


def root_strokes():
    return [
        (f"M{p(C2)} L{p(ROOT_B)}", TRUNK_W * 0.8),
        (f"M{p(C2)} L{p(ROOT_L)} M{p(C2)} L{p(ROOT_R)}", BRANCH_W),
    ]


def trunk_low():
    return f"M54,{f(54 + PUPIL_R + 0.9)} L54,{f(C2[1])}"


def circle_path(c, r):
    x, y = c
    return f"M{f(x - r)},{f(y)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"


INK, INDIGO, EMBER = "#14161B", "#2F45E0", "#B4460A"


def mark_svg(ink=INK, indigo=INDIGO, ember=EMBER, roots_op=0.5, lid=True, mono=None):
    if mono:
        ink = indigo = ember = mono
    g = []
    if lid:
        g.append(f'<path d="{lid_path()}" fill="none" stroke="{indigo}" stroke-width="{f(LID_W)}" stroke-linecap="round"/>')
    g.append(f'<g opacity="{roots_op}">')
    for d, w in root_strokes():
        g.append(f'<path d="{d}" fill="none" stroke="{indigo}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    for t in ROOT_TIPS:
        g.append(f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="{f(LEAF_R * 0.8)}" fill="{indigo}"/>')
    g.append('</g>')
    for d, w in crown_strokes():
        g.append(f'<path d="{d}" fill="none" stroke="{indigo}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    g.append(f'<path d="{trunk_low()}" fill="none" stroke="{indigo}" stroke-width="{f(TRUNK_W)}" stroke-linecap="round"/>')
    for t in CROWN_TIPS:
        g.append(f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="{f(LEAF_R)}" fill="{indigo}"/>')
    for d, w in book_strokes():
        g.append(f'<path d="{d}" fill="none" stroke="{ink}" stroke-width="{f(w)}" stroke-linecap="round"/>')
    g.append(f'<circle cx="54" cy="54" r="{f(PUPIL_R)}" fill="{ember}"/>')
    return "\n".join(g)


if __name__ == "__main__":
    print("C1", C1, "C2", C2, "H", round(H, 2))
    print("tips", [tuple(round(v, 2) for v in t) for t in CROWN_TIPS])
    print("inner fork", FORK_IN, "roots", [tuple(round(v, 2) for v in t) for t in ROOT_TIPS])
    far = max(sqrt((x - 54) ** 2 + (y - 54) ** 2) + LEAF_R for x, y in CROWN_TIPS + ROOT_TIPS)
    print("farthest from centre", round(far, 2), "(safe zone 33)")
    print(round(PUPIL_R, 2), round(LEAF_R, 2), round(TRUNK_W, 2), round(BRANCH_W, 2), round(LID_W, 2))
