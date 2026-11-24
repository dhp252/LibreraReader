# usage: python3 -I status.py <tools dir> <work dir> [--no-fetch]
# Needs <work>/ch/index.json from prep.py. Fetches the book's current ai/assist files from the server
# (unless --no-fetch) into <work>/cur/ and writes <work>/status.json:
#   {"skip": "<reason>" | "", "content": [keys], "missing": {feature: [keys]}, "v1": [keys], "total": n}
# Features: summary, book, highlights, skeleton, preq, xray, updated (see cfg.FEATURES).
# "v1" = content chapters whose key passages are in the old quotes-only format (no ideas): X-ray is
# impossible for them until they are upgraded (needs the user's approval, see SKILL.md).
import sys, os, json, re, subprocess
TOOLS, W = sys.argv[1], sys.argv[2]
sys.path.insert(0, TOOLS)
from cfg import ROOT, PROF
from side import is_side_title
idx = json.load(open(os.path.join(W, 'ch', 'index.json')))
FN = idx['file']
os.makedirs(os.path.join(W, 'cur'), exist_ok=True)
skip = ''
if '--no-fetch' not in sys.argv:
    for kind in ('ai', 'assist'):
        p = os.path.join(W, 'cur', f'{kind}.json')
        r = subprocess.run(['rclone', 'cat', f'{ROOT}/{PROF}/{kind}/{FN}'], capture_output=True)
        if r.returncode == 0: open(p, 'wb').write(r.stdout)
        elif b'not found' in r.stderr.lower():
            if os.path.exists(p): os.remove(p)
        else: raise SystemExit(f'GET {kind} failed: {r.stderr[-200:]!r}')

def load(kind):
    global skip
    p = os.path.join(W, 'cur', f'{kind}.json')
    if not os.path.exists(p): return None
    raw = open(p, 'rb').read()
    if not raw.strip(): skip = skip or f'server {kind} file is empty (0 bytes): restore it from a backup first'; return None
    try: return json.loads(raw)
    except Exception as ex: skip = skip or f'server {kind} file cannot be parsed ({ex})'; return None
ai = load('ai') or {}; asst = load('assist') or {}

chs = idx['chapters']
def text(c):
    try: return open(os.path.join(W, 'ch', c['file'])).read()
    except Exception: return ''
if not skip and idx.get('notoc') and idx['type'] != 'pdf':
    skip = 'no table of contents (EPUB): the skill says not to write results'
if not skip:
    sizes = [c.get('chars', 0) for c in chs]
    if not chs or sum(sizes) < 2000 or sum(1 for s in sizes if s < 500) > 0.8 * len(sizes):
        skip = 'no text layer (a scan): needs OCR first'
if not skip:
    sample = ''.join(text(c)[:4000] for c in chs[:40])
    letters = re.findall(r'[^\W\d_]', sample)
    common = re.findall(r'\b(the|and|of|to|in|is|a|la|le|de|und|die|và|của|là)\b', sample.lower())
    if letters and len(common) < len(sample) / 2000:
        latin = sum(1 for ch in letters if ch.isascii())
        if latin < 0.3 * len(letters) or not common:
            skip = 'text layer is garbled (font encoding): needs OCR first'

SIDE_RE = re.compile(r'^(i?ndex|back ?cover|front ?cover|cover|title page|half title|dedication|epigraph|disclaimers?|'
                     r'recommended (resources|reading)|further reading|resources|mentor index|bibliography|references|'
                     r'endnotes|notes|glossary|credits|permissions|also by|about the (author|authors|publisher)|'
                     r'praise for|copyright|contents|table of contents|list of (figures|tables)|errata|updates)\b', re.I)
book_title = re.sub(r'[^a-z0-9]+', ' ', os.path.splitext(idx.get('book', ''))[0].lower()).strip()
def is_side(c):
    t = (c.get('title') or '').strip()
    tn = re.sub(r'[^a-z0-9]+', ' ', t.lower()).strip()
    return (c.get('side') or c['key'] == '@front' or is_side_title(t) or bool(SIDE_RE.match(t))
            or (len(tn) > 8 and book_title.startswith(tn)))  # the title / copyright page carries the book's name
content = [c['key'] for c in chs if not is_side(c)]
sums = {s['key'] for s in ai.get('summaries', [])}
hls = {h['key']: h for h in ai.get('highlights', [])}
empty = set(ai.get('emptyKeys', []))
def has(k): return k in asst  # any state counts, including del/declined/locked/offered
def need_rated(h):
    ideas = h.get('ideas') or []
    par = {i.get('parent', -1) for i in ideas}
    return [i for i in ideas if i.get('parent', -1) != -1 or i.get('id') in par]
miss = {f: [] for f in ('summary', 'book', 'highlights', 'skeleton', 'preq', 'xray', 'updated')}
v1 = []
for k in content:
    if k not in sums: miss['summary'].append(k)
    h = hls.get(k)
    if h is None and k not in empty: miss['highlights'].append(k)
    settled = k in empty  # marked 'nothing worth marking': pre-questions, notes and X-ray are optional
    if not settled and not k.startswith('@') and not has(f'preq:{k}'): miss['preq'].append(k)
    if not settled and not has(f'updated:{k}'): miss['updated'].append(k)
    if h is not None and not h.get('ideas'): v1.append(k)
    elif not has(f'xray:{k}') and (h is None and k not in empty or (h is not None and need_rated(h))):
        miss['xray'].append(k)
if '@book' not in sums: miss['book'].append('@book')
if not has('skeleton:book'): miss['skeleton'].append('skeleton:book')
if not content and not skip: skip = 'no content chapters (the table of contents has only side matter)'
out = {'book': idx.get('rel', idx.get('book')), 'skip': skip, 'content': content, 'missing': miss, 'v1': v1,
       'total': sum(len(v) for v in miss.values()), 'goal': ai.get('goal', '')}
json.dump(out, open(os.path.join(W, 'status.json'), 'w'), ensure_ascii=False, indent=1)
print(json.dumps({'title': out['book'], 'skip': skip, 'total': out['total'], 'v1': len(v1),
                  **{f: len(v) for f, v in miss.items()}}, ensure_ascii=False))
