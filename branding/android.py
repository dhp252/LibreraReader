"""Writes the Yggdrasil launcher icon (adaptive vectors + legacy PNG sources) from geom.py."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geom import *

res = sys.argv[1]
ROOT_ALPHA = "0.5"


def stroke(d, w, color, alpha=None):
    a = f'\n        android:strokeAlpha="{alpha}"' if alpha else ""
    return f'''    <path
        android:pathData="{d}"
        android:strokeWidth="{f(w)}"
        android:strokeColor="{color}"{a}
        android:strokeLineCap="round" />'''


def fill(d, color, alpha=None):
    a = f'\n        android:fillAlpha="{alpha}"' if alpha else ""
    return f'''    <path
        android:pathData="{d}"
        android:fillColor="{color}"{a} />'''


def layer(ink, indigo, ember, root_color):
    out = [stroke(lid_path(), LID_W, indigo)]
    # roots: solid half-tone colour on the icon (no overlap darkening), alpha on the themed icon
    ra = ROOT_ALPHA if root_color is None else None
    rc = root_color or indigo
    for d, w in root_strokes():
        out.append(stroke(d, w, rc, ra))
    for t in ROOT_TIPS:
        out.append(fill(circle_path(t, LEAF_R * 0.8), rc, ra))
    for d, w in crown_strokes():
        out.append(stroke(d, w, indigo))
    out.append(stroke(trunk_low(), TRUNK_W, indigo))
    for t in CROWN_TIPS:
        out.append(fill(circle_path(t, LEAF_R), indigo))
    for d, w in book_strokes():
        out.append(stroke(d, w, ink))
    out.append(fill(circle_path((54, 54), PUPIL_R), ember))
    return "\n".join(out)


HEAD = '''<?xml version="1.0" encoding="utf-8"?>
<!-- {what}
     Generated from the construction in the Yggdrasil mark rationale: two circles of radius
     {r} one radius apart (C1 = crown, C2 = roots), sizes in powers of the golden ratio. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''

open(f"{res}/drawable/ic_yggdrasil_fg.xml", "w").write(
    HEAD.format(what="Yggdrasil launcher icon, foreground: a tree growing through an open book; the eye, the runes and the nine points are hidden in it.", r=f(R))
    + layer(INK, INDIGO, EMBER, "#97A2EF") + "\n</vector>\n")
open(f"{res}/drawable/ic_yggdrasil_mono.xml", "w").write(
    HEAD.format(what="Yggdrasil themed (monochrome) icon: the same mark in one colour; Android tints it.", r=f(R))
    + layer("#FFFFFFFF", "#FFFFFFFF", "#FFFFFFFF", None) + "\n</vector>\n")

# legacy PNG source (API 24-25): white rounded tile / circle with the mark, as SVG
mark = mark_svg()
for name, bg in (("ic_yggdrasil", '<rect x="19" y="19" width="70" height="70" rx="16" fill="#fff"/>'),
                 ("ic_yggdrasil_round", '<circle cx="54" cy="54" r="35" fill="#fff"/>')):
    open(f"{sys.argv[2]}/{name}.svg", "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="17 17 74 74">{bg}{mark}</svg>')
