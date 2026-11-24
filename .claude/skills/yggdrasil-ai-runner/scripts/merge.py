# usage: python3 -I merge.py <tools dir> <work dir> check|dry|write
#  check: report every problem in <work>/out/*.json (quotes not found, bad shapes, unknown keys)
#  dry:   show what would be added to the server files (fill-only)
#  write: GET fresh copies, merge (fill-only), back up, PUT (read back and compared), overwrite .changed
# Marker files in <work dir> (written by plan.py, one feature name per line or comma separated):
#  FEATURES   only these features are merged (default: all of summary book highlights skeleton preq xray updated)
#  REPLACE    the user approved replacing existing results of these features with the drafts
#  UPGRADE_V1 replace old quotes-only key passages (no ideas), keeping every old quote that is found
import sys, json, glob, subprocess, time, copy, os
TOOLS, W, MODE = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, TOOLS)
from norm import load, norm_q
from cfg import ROOT, PROF, MODEL, FEATURES as ALL_FEATURES
idx, T = load(W); FN = idx['file']; PDF = idx['type'] == 'pdf'
CH = {c['key']: c for c in idx['chapters']}
CATS = {'main', 'evidence', 'concept', 'caveat', 'apply', 'example'}
RATES = {'strong', 'fair', 'observation', 'questioned', 'weak'}
problems = []; log = []
UPGRADE = os.path.exists(os.path.join(W, 'UPGRADE_V1'))  # user approved replacing old quotes-only key passages
def marker(name, default):
    p = os.path.join(W, name)
    if not os.path.exists(p): return set(default)
    got = {x.strip() for x in open(p).read().replace(',', '\n').split('\n') if x.strip()}
    bad = got - set(ALL_FEATURES)
    if bad: raise SystemExit(f'{name}: unknown feature names {sorted(bad)}; use {ALL_FEATURES}')
    return got
FEAT = marker('FEATURES', ALL_FEATURES)
REPL = marker('REPLACE', [])
LOCKED = ('del', 'declined', 'locked')  # items the reader removed or declined: never replaced
def is_locked(v): return isinstance(v, dict) and any(v.get(x) for x in LOCKED)
def kind_of(k): return {'preq': 'preq', 'xray': 'xray', 'updated': 'updated', 'skeleton': 'skeleton'}.get(k.partition(':')[0])
def P(msg): problems.append(msg)
def found(key, q): return key in T and T[key].find(q) is not None
def pg(key, q): return T[key].page(q) if PDF and key in T else -1
def lpg(key, q): return T[key].last_page(q) if PDF and key in T else -1

# ---------- collect drafts
S, H, E, A = [], [], [], {}
for f in sorted(glob.glob(f'{W}/out/*.json')):
    try: o = json.load(open(f))
    except Exception as ex: P(f'{os.path.basename(f)}: not valid JSON ({ex})'); continue
    S += o.get('summaries', []); H += o.get('highlights', []); E += o.get('emptyKeys', [])
    for k, v in o.get('assist', {}).items(): A.setdefault(k, v)

def clean_highlight(h, report):
    key = h.get('key')
    if key not in T: report(f'highlights: unknown chapter key {key!r}'); return None
    h = copy.deepcopy(h)
    ideas = h.get('ideas', []); ids = [i.get('id') for i in ideas]
    if len(set(ids)) != len(ids): report(f'highlights {key}: duplicate idea ids')
    qs, ps = [], []
    for q in h.get('quotes', []):
        if not found(key, q.get('text')): report(f'highlights {key}: quote not found :: {q.get("text","")[:110]}'); continue
        if q.get('idea') not in ids: report(f'highlights {key}: quote names unknown idea {q.get("idea")}'); continue
        q['page'] = pg(key, q['text']); qs.append(q)
    for q in h.get('paragraphs', []):
        if not found(key, q.get('text')): report(f'highlights {key}: paragraph not found :: {q.get("text","")[:110]}'); continue
        if q.get('idea') not in ids: report(f'highlights {key}: paragraph names unknown idea {q.get("idea")}'); continue
        q['page'] = pg(key, q['text']); ps.append(q)
    backed = {q['idea'] for q in qs + ps}
    keep = []
    for i in ideas:
        if i.get('category') not in CATS: report(f'highlights {key}: idea {i.get("id")} bad category {i.get("category")}')
        if i.get('id') not in backed: report(f'highlights {key}: idea {i.get("id")} has no quote/paragraph that was found'); continue
        keep.append(i)
    kid = {i['id'] for i in keep}
    for i in keep:
        if i.get('parent', -1) not in kid: i['parent'] = -1
        i['marked'] = True
        first = next((q for q in qs + ps if q['idea'] == i['id']), None)
        i['page'] = first['page'] if first else -1
        cat = i.get('category')
        for q in qs + ps:
            if q['idea'] == i['id']: q['category'] = cat
    h['ideas'] = keep; h['quotes'] = [q for q in qs if q['idea'] in kid]; h['paragraphs'] = [q for q in ps if q['idea'] in kid]
    return h if keep else None

def need_rated(h):
    par = {i['parent'] for i in h['ideas']}
    return {i['id'] for i in h['ideas'] if i['parent'] != -1 or i['id'] in par}

def clean_assist(k, v, hl, report):
    kind, _, key = k.partition(':'); v = copy.deepcopy(v)
    if kind == 'preq':
        if key.startswith('@') or key not in T: report(f'{k}: unknown or @ key'); return None
        qs = v.get('questions', [])
        if len(qs) != 3: report(f'{k}: needs exactly 3 questions'); return None
        for i, q in enumerate(qs):
            if len(q.get('options', [])) != 4 or q.get('answer') not in (0, 1, 2, 3): report(f'{k} q{i}: needs 4 options and answer 0-3'); return None
            if not found(key, q.get('quote')): report(f'{k} q{i}: quote not found :: {q.get("quote","")[:110]}'); return None
            q.update(guess=-1, own='', confidence=-1, skipped=False, found=0, page=pg(key, q['quote']), lastPage=lpg(key, q['quote']))
        v.update(state='ready', layout='', locked=0)
    elif kind == 'updated':
        if key not in T: report(f'{k}: unknown key'); return None
        notes = []
        for i, n in enumerate(v.get('notes', [])):
            if not found(key, n.get('quote')): report(f'{k} note{i}: quote not found :: {n.get("quote","")[:110]}'); continue
            n['page'] = pg(key, n['quote']); n['last'] = lpg(key, n['quote']); notes.append(n)
        v['notes'] = notes[:6]
        tw = (v.get('objection') or {}).get('twist')
        if tw and tw.get('quote'):
            if found(key, tw['quote']): tw['page'] = pg(key, tw['quote'])
            else: report(f'{k}: twist quote not found'); tw['quote'] = ''; tw['page'] = -1
        v.update(layout='', model=MODEL)
    elif kind == 'xray':
        h = hl.get(key)
        if not h: report(f'{k}: no key passages for this chapter'); return None
        need = need_rated(h); got = [r.get('id') for r in v.get('ratings', [])]
        bad = [r for r in v.get('ratings', []) if r.get('id') not in need or r.get('rating') not in RATES]
        if bad: report(f'{k}: {len(bad)} ratings with an id that needs no rating or a bad rating value')
        miss = need - set(got)
        if miss: report(f'{k}: {len(miss)} ideas not rated, e.g. {sorted(miss)[:8]}')
        v['ratings'] = [r for r in v.get('ratings', []) if r.get('id') in need and r.get('rating') in RATES]
        if (v.get('weakest') or {}).get('id') not in need: report(f'{k}: weakest id is not a rated idea'); return None
        if not v['ratings']: return None
        v['ideas'] = h['time']; v['model'] = MODEL
    elif kind == 'skeleton':
        v['model'] = MODEL
    else:
        report(f'{k}: not a feature this tool writes'); return None
    return v

def build(ai, asst, now, report):
    added = []
    # ---- summaries (chapter = 'summary', '@book' = 'book')
    have_s = {s['key']: s for s in ai['summaries']}
    for s in S:
        k0 = s.get('key'); feat = 'book' if k0 == '@book' else 'summary'
        if feat not in FEAT or not s.get('text'): continue
        if k0 != '@book' and k0 not in T: report(f'summary: unknown key {k0!r}'); continue
        item = {'key': k0, 'title': s.get('title', ''), 'text': s['text'], 'model': MODEL, 'time': now}
        if k0 in have_s:
            if feat not in REPL: continue
            i = ai['summaries'].index(have_s[k0]); ai['summaries'][i] = item; added.append('replace-sum:' + k0)
        else:
            ai['summaries'].append(item); added.append('sum:' + k0)
        have_s[k0] = item
    # ---- key passages
    hl = {h['key']: h for h in ai['highlights']}
    empty = set(ai.get('emptyKeys', []))
    xray_drafts = {k.partition(':')[2] for k in A if k.startswith('xray:')}
    replaced_hl = set()
    if 'highlights' in FEAT:
        for h in H:
            k0 = h.get('key')
            old = hl.get(k0)
            if old is not None and not old.get('ideas') and h.get('ideas') and (UPGRADE or 'highlights' in REPL):
                # upgrade of an old quotes-only entry: every old quote that is found must be kept
                c = clean_highlight(h, report)
                if c is None: continue
                have = {norm_q(q['text']) for q in c['quotes'] + c['paragraphs']}
                lost = [q.get('text', '') for q in old.get('quotes', []) + old.get('paragraphs', []) if norm_q(q.get('text')) not in have and found(k0, q.get('text'))]
                if lost: report(f'highlights {k0}: upgrade drops {len(lost)} old quotes, e.g. :: {lost[0][:110]}'); continue
                c.update(model=MODEL, time=now)
                i = ai['highlights'].index(old); ai['highlights'][i] = c; hl[k0] = c; added.append('upgrade:' + k0)
                replaced_hl.add(k0)
                continue
            if (k0 in hl or k0 in empty) and 'highlights' not in REPL: continue
            c = clean_highlight(h, report)
            if c is None: continue
            c.update(model=MODEL, time=now)
            if k0 in hl:
                i = ai['highlights'].index(hl[k0]); ai['highlights'][i] = c; added.append('replace-hl:' + k0)
                replaced_hl.add(k0)
            else:
                if k0 in empty: ai['emptyKeys'].remove(k0); empty.discard(k0)
                ai['highlights'].append(c); added.append('hl:' + k0)
            hl[k0] = c
        for k in E:
            if k in T and k not in hl and k not in empty:
                ai.setdefault('emptyKeys', []).append(k); empty.add(k); added.append('empty:' + k)
    # an X-ray rates idea ids, so a replaced chapter's old X-ray must be replaced too
    for k0 in replaced_hl:
        if f'xray:{k0}' in asst and not is_locked(asst[f'xray:{k0}']) and not ('xray' in FEAT and k0 in xray_drafts and ('xray' in REPL or UPGRADE)):
            report(f'highlights {k0}: replaced, but its existing xray:{k0} rates the old ideas; draft a new xray and allow replacing xray')
    # ---- assist items
    for k, v in A.items():
        kind = kind_of(k)
        if kind not in FEAT: continue
        if k in asst:
            if is_locked(asst[k]): continue  # the reader removed or declined it
            ok = kind in REPL or (kind == 'xray' and k.partition(':')[2] in replaced_hl)
            if not ok: continue
        c = clean_assist(k, v, hl, report)
        if c is None: continue
        c['t'] = now
        if k == 'skeleton:book': c['made'] = now
        if k.startswith('updated:'): c['at'] = now
        added.append(('replace:' if k in asst else '') + k)
        asst[k] = c
    return added

def get(kind):
    r = subprocess.run(['rclone', 'cat', f'{ROOT}/{PROF}/{kind}/{FN}'], capture_output=True)
    if r.returncode != 0:
        if b'not found' in r.stderr.lower(): return None
        raise SystemExit(f'GET {kind} failed: {r.stderr[-200:]!r}')
    return r.stdout
def put(rel, data):
    # upload, then read back and compare: a cut-off upload must never be left as the file
    for i in range(4):
        r = subprocess.run(['rclone', 'rcat', f'{ROOT}/{rel}'], input=data, capture_output=True)
        if r.returncode == 0:
            back = subprocess.run(['rclone', 'cat', f'{ROOT}/{rel}'], capture_output=True)
            if back.returncode == 0 and back.stdout == data: return
        time.sleep(2 ** i)
    raise SystemExit(f'PUT failed {rel}: {r.stderr[-200:]!r}')

if MODE == 'check':
    ai = {'version': 2, 'summaries': [], 'highlights': []}
    try: ai = json.load(open(f'{W}/cur/ai.json'))
    except FileNotFoundError: pass
    build(copy.deepcopy(ai) | {'summaries': [], 'highlights': [h for h in ai.get('highlights', [])]}, {}, 0, P)
    print(f'{len(problems)} problems'); print('\n'.join(problems[:80]))
    sys.exit(0)

raw_ai, raw_as = get('ai'), get('assist')
if raw_ai == b'' or raw_as == b'': raise SystemExit('SKIP BOOK: an existing server file is empty (0 bytes); report it, do not write')
try:
    ai = json.loads(raw_ai) if raw_ai else {'version': 2, 'updated': 0, 'summaries': [], 'highlights': []}
    asst = json.loads(raw_as) if raw_as else {}
except Exception as ex:
    raise SystemExit(f'SKIP BOOK: an existing server file cannot be parsed ({ex})')
ai.setdefault('summaries', []); ai.setdefault('highlights', [])
now = int(time.time() * 1000)
n_ai0 = json.dumps(ai, ensure_ascii=False); n_as0 = json.dumps(asst, ensure_ascii=False)
added = build(ai, asst, now, log.append)
ai_changed = json.dumps(ai, ensure_ascii=False) != n_ai0
as_changed = json.dumps(asst, ensure_ascii=False) != n_as0
if ai_changed: ai['updated'] = now; ai.setdefault('version', 2)
out_ai = json.dumps(ai, ensure_ascii=False).encode(); out_as = json.dumps(asst, ensure_ascii=False).encode()
json.loads(out_ai); json.loads(out_as)
print('ADDED', len(added), added)
if log: print('DROPPED/FIXED:', '\n'.join(log[:60]))
if MODE == 'write' and log and '--force' not in sys.argv:
    raise SystemExit('NOT WRITTEN: fix the problems above first (run check), then write again')
if MODE == 'write' and (ai_changed or as_changed):
    day = time.strftime('%Y-%m-%d', time.gmtime())
    for kind, raw, out, ch in (('ai', raw_ai, out_ai, ai_changed), ('assist', raw_as, out_as, as_changed)):
        if not ch: continue
        if get(kind) != raw: raise SystemExit(f'{kind} file changed on the server during the merge: run write again')
        if raw:
            bk = f'.claude/ai-data-backups/{day}/{PROF}/{kind}/{FN}'
            ex = subprocess.run(['rclone', 'lsf', f'{ROOT}/{bk}'], capture_output=True, text=True)
            if ex.returncode == 0 and ex.stdout.strip(): pass  # keep the day's first backup (the original)
            elif ex.returncode == 0 or 'not found' in ex.stderr.lower(): put(bk, raw)
            else: raise SystemExit(f'cannot check backup {bk}: {ex.stderr[-200:]}')
        put(f'{PROF}/{kind}/{FN}', out)
    put(f'{PROF}/.changed', f'ai-data {time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}'.encode())
    print('WRITTEN')
