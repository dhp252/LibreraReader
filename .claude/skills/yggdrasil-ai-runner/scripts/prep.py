# usage: python3 -I prep.py <tools dir> <server path under Books/> <work dir>
# Downloads one book, works out the app's chapter keys (BookChapters.java rules) and writes
# <work>/ch/NN.txt per chapter plus <work>/ch/index.json. Also fetches the book's current ai/assist files.
import sys, os, json, re, subprocess, zipfile, html, posixpath, unicodedata
from html.parser import HTMLParser
from xml.etree import ElementTree as ET
TOOLS, REL, W = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, TOOLS)
from fname import app_file
from side import is_side_title
from cfg import ROOT, PROF
os.makedirs(W + '/ch', exist_ok=True); os.makedirs(W + '/out', exist_ok=True); os.makedirs(W + '/cur', exist_ok=True)
base = posixpath.basename(REL); ext = base.rsplit('.', 1)[-1].lower()
FN = app_file(base)
src = f'{W}/book.{ext}'
if not os.path.exists(src):
    subprocess.run(['rclone', 'copyto', f'{ROOT}/Books/{REL}', src], check=True)
for kind in ('ai', 'assist'):
    r = subprocess.run(['rclone', 'cat', f'{ROOT}/{PROF}/{kind}/{FN}'], capture_output=True)
    if r.returncode == 0:
        open(f'{W}/cur/{kind}.json', 'wb').write(r.stdout)

def keys_for(starts):
    seen = {}; out = []
    for title in starts:
        name = re.sub(r'\s+', ' ', (title or '').strip()).lower()
        n = seen.get(name, -1) + 1; seen[name] = n
        out.append(f'{name}#{n}')
    return out

def pick_level(entries):
    top = min(e['level'] for e in entries)
    at_top = sum(1 for e in entries if e['level'] == top)
    deeper = any(e['level'] != top for e in entries)
    return top + 1 if at_top < 2 and deeper else top

info = {'book': base, 'rel': REL, 'type': ext, 'file': FN, 'chapters': [], 'notoc': False}

if ext == 'pdf':
    import pypdf
    rd = pypdf.PdfReader(src)
    N = len(rd.pages); info['pages'] = N
    ents = []
    def walk(o, d):
        for x in o:
            if isinstance(x, list): walk(x, d + 1)
            else:
                try: p = rd.get_destination_page_number(x)
                except Exception: p = -1
                if p is not None and 0 <= p < N: ents.append({'title': x.title or '', 'level': d, 'page': p})
    try: walk(rd.outline, 0)
    except Exception: ents = []
    txt = subprocess.run(['pdftotext', '-raw', src, '-'], capture_output=True, text=True).stdout.split('\f')
    def ptext(a, b): return ''.join(f'\n=== PAGE {p} ===\n' + txt[p] for p in range(a, b) if p < len(txt) and txt[p].strip())
    chs = []
    if ents:
        lvl = pick_level(ents)
        picked = sorted([e for e in ents if e['level'] <= lvl], key=lambda e: e['page'])  # stable sort like Java
        starts = []
        for e in picked:
            if not starts or starts[-1]['page'] != e['page']: starts.append(e)
        if starts[0]['page'] > 0: chs.append({'key': '@front', 'title': '', 'from': 0, 'to': starts[0]['page'], 'subs': []})
        ks = keys_for([e['title'] for e in starts])
        for i, e in enumerate(starts):
            to = starts[i + 1]['page'] if i + 1 < len(starts) else N
            # sub-entries one level below inside this chapter (for skeleton parts)
            idx = ents.index(e); subs = []
            for f in ents[idx + 1:]:
                if f['level'] <= e['level']: break
                if f['level'] == e['level'] + 1: subs.append(f['title'])
            chs.append({'key': ks[i], 'title': e['title'].strip(), 'from': e['page'], 'to': to, 'subs': subs})
    else:
        info['notoc'] = True
        for a in range(0, N, 20):
            chs.append({'key': f'@pages:{a}', 'title': '', 'from': a, 'to': min(N, a + 20), 'subs': []})
    for i, c in enumerate(chs):
        t = ptext(c['from'], c['to']); c['file'] = f'{i:03d}.txt'; c['chars'] = len(re.sub(r'\s+', ' ', t))
        open(f"{W}/ch/{c['file']}", 'w').write(t)
    info['chapters'] = chs
else:  # epub
    z = zipfile.ZipFile(src)
    def rd(p):
        try: return z.read(p)
        except KeyError: return z.read(next(n for n in z.namelist() if n.lower() == p.lower()))
    def strip_ns(t):
        for el in t.iter():
            if isinstance(el.tag, str) and '}' in el.tag: el.tag = el.tag.split('}', 1)[1]
            for k in list(el.attrib):
                if '}' in k: el.attrib[k.split('}', 1)[1]] = el.attrib.pop(k)
        return t
    cont = strip_ns(ET.fromstring(rd('META-INF/container.xml')))
    opf_path = cont.find('.//rootfile').get('full-path'); od = posixpath.dirname(opf_path)
    opf = strip_ns(ET.fromstring(rd(opf_path)))
    man = {i.get('id'): i for i in opf.find('manifest')}
    J = lambda d, h: posixpath.normpath(posixpath.join(d, h.split('#')[0])) if h else ''
    spine = [J(od, man[r.get('idref')].get('href')) for r in opf.find('spine') if r.get('idref') in man]
    class P(HTMLParser):
        BLOCK = {'p', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'li', 'br', 'tr', 'blockquote', 'section', 'pre', 'dd', 'dt', 'figcaption', 'td', 'th'}
        def __init__(s): super().__init__(convert_charrefs=True); s.out = []; s.ids = {}; s.skip = 0
        def handle_starttag(s, tag, a):
            a = dict(a)
            for k in ('id', 'name'):
                if a.get(k) and a[k] not in s.ids: s.ids[a[k]] = sum(map(len, s.out))
            if tag in ('script', 'style', 'head'): s.skip += 1
            if tag in s.BLOCK: s.out.append('\n')
        def handle_startendtag(s, tag, a): s.handle_starttag(tag, a)
        def handle_endtag(s, tag):
            if tag in ('script', 'style', 'head'): s.skip = max(0, s.skip - 1)
            if tag in s.BLOCK: s.out.append('\n')
        def handle_data(s, d):
            if not s.skip: s.out.append(d)
    docs = {}
    for p in spine:
        pr = P()
        try: pr.feed(rd(p).decode('utf-8', 'replace'))
        except Exception: pass
        docs[p] = (''.join(pr.out), pr.ids)
    # TOC: nav (EPUB3) or NCX
    ents = []
    nav = next((i for i in man.values() if 'nav' in (i.get('properties') or '').split()), None)
    def add(title, href, level, d):
        f = J(d, href); frag = href.split('#', 1)[1] if '#' in href else ''
        if f in docs: ents.append({'title': re.sub(r'\s+', ' ', title).strip(), 'level': level, 'file': f, 'frag': frag})
    if nav is not None:
        nd = posixpath.dirname(J(od, nav.get('href')))
        try:
            nt = strip_ns(ET.fromstring(rd(J(od, nav.get('href')))))
            tocnav = next((n for n in nt.iter('nav') if n.get('type') == 'toc'), None) or next(nt.iter('nav'), None)
            def walkol(ol, lvl):
                for li in ol.findall('li'):
                    a = li.find('a') if li.find('a') is not None else li.find('span')
                    if a is not None and a.get('href'): add(''.join(a.itertext()), a.get('href'), lvl, nd)
                    sub = li.find('ol')
                    if sub is not None: walkol(sub, lvl + 1)
            if tocnav is not None and tocnav.find('ol') is not None: walkol(tocnav.find('ol'), 0)
        except Exception: ents = []
    if not ents:
        ncx = next((i for i in man.values() if i.get('media-type') == 'application/x-dtbncx+xml'), None)
        if ncx is not None:
            nd = posixpath.dirname(J(od, ncx.get('href')))
            nt = strip_ns(ET.fromstring(rd(J(od, ncx.get('href')))))
            def walkn(el, lvl):
                for np_ in el.findall('navPoint'):
                    t = np_.find('navLabel/text'); c = np_.find('content')
                    if c is not None: add(t.text if t is not None and t.text else '', c.get('src', ''), lvl, nd)
                    walkn(np_, lvl + 1)
            nm = nt.find('navMap')
            if nm is not None: walkn(nm, 0)
    # global positions
    offs = {}; full = []; pos = 0
    for p in spine:
        offs[p] = pos; t = docs[p][0]; full.append(t); pos += len(t)
    full = ''.join(full)
    for e in ents:
        t, ids = docs[e['file']]
        e['pos'] = offs[e['file']] + (ids.get(e['frag'], 0) if e['frag'] else 0)
    chs = []
    if not ents:
        info['notoc'] = True
    else:
        lvl = pick_level(ents)
        picked = sorted([e for e in ents if e['level'] <= lvl], key=lambda e: e['pos'])
        starts = []
        for e in picked:  # same "page": no text of its own between the two entries
            if starts and not full[starts[-1]['pos']:e['pos']].strip(): continue
            if starts and (spine.index(e['file']) == spine.index(starts[-1]['file'])) and len(full[starts[-1]['pos']:e['pos']].strip()) < 1: continue
            starts.append(e)
        if full[:starts[0]['pos']].strip(): chs.append({'key': '@front', 'title': '', 'a': 0, 'b': starts[0]['pos'], 'subs': []})
        ks = keys_for([e['title'] for e in starts])
        for i, e in enumerate(starts):
            b = starts[i + 1]['pos'] if i + 1 < len(starts) else len(full)
            idx = ents.index(e); subs = []
            for f in ents[idx + 1:]:
                if f['level'] <= e['level']: break
                if f['level'] == e['level'] + 1: subs.append(f['title'])
            chs.append({'key': ks[i], 'title': e['title'], 'a': e['pos'], 'b': b, 'subs': subs})
        for i, c in enumerate(chs):
            t = re.sub(r'\n[ \t]*\n+', '\n\n', full[c['a']:c['b']]); c['file'] = f'{i:03d}.txt'; c['chars'] = len(re.sub(r'\s+', ' ', t))
            c['from'] = c['to'] = -1
            open(f"{W}/ch/{c['file']}", 'w').write(t)
    info['chapters'] = chs
for c in info['chapters']:
    c['side'] = c['key'] == '@front' or is_side_title(c['title']) or c['chars'] < 200
# compare with the app's own keys
try:
    ai = json.load(open(f'{W}/cur/ai.json'))
    ck = ai.get('chapterKeys') or []
    mine = [c['key'] for c in info['chapters']]
    used = set(ck) | {s['key'] for s in ai.get('summaries', [])} | {h['key'] for h in ai.get('highlights', [])}
    info['app_keys_missing_here'] = sorted(k for k in used if k not in mine and k != '@book')
except FileNotFoundError: pass
except Exception as ex: info['ai_parse_error'] = str(ex)
try: json.load(open(f'{W}/cur/assist.json'))
except FileNotFoundError: pass
except Exception as ex: info['assist_parse_error'] = str(ex)
json.dump(info, open(f'{W}/ch/index.json', 'w'), ensure_ascii=False, indent=1)
try: os.remove(src)  # keep disk small; text is extracted
except OSError: pass
print(json.dumps({k: v for k, v in info.items() if k != 'chapters'}, ensure_ascii=False))
for c in info['chapters']: print(c['file'] if 'file' in c else '-', c['key'], c['chars'], 'SIDE' if c['side'] else '', len(c['subs']))
