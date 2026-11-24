# usage: python3 -I plan.py <tools dir> --work <dir> [options]
# Builds the queue of books for one run and prints the Workflow call that runs it.
#   --book TEXT        only books whose path under Books/ contains TEXT (case-insensitive; repeatable)
#   --features LIST    only these features (comma list of: summary,book,highlights,skeleton,preq,xray,updated)
#   --all              queue every selected book even if nothing is missing (agents re-check them)
#   --replace LIST     REDO: replace existing results of these features (needs the user's explicit approval)
#   --upgrade-v1       replace old quotes-only key passages so X-ray can be written (needs approval)
#   --lanes N          agents running at the same time (default 1, max 4)
#   --limit N          at most N books
#   --refresh          re-download and re-extract books already prepared in --work
#   --include-leftovers  also queue books whose only gaps are in a few chapters earlier agents left out
# Writes <work>/queue/q<i>.json (one per lane) and <work>/queue/plan.json, and the marker files
# FEATURES / REPLACE / UPGRADE_V1 in each book's work dir (stale markers are removed).
import sys, os, json, re, subprocess, time, argparse, shutil, hashlib
from concurrent.futures import ThreadPoolExecutor
TOOLS = os.path.abspath(sys.argv[1]); sys.path.insert(0, TOOLS)
from cfg import ROOT, PROF, FEATURES
ap = argparse.ArgumentParser()
ap.add_argument('--work', required=True); ap.add_argument('--book', action='append', default=[])
ap.add_argument('--features', default=''); ap.add_argument('--replace', default='')
ap.add_argument('--all', action='store_true'); ap.add_argument('--upgrade-v1', action='store_true')
ap.add_argument('--lanes', type=int, default=1); ap.add_argument('--limit', type=int, default=0)
ap.add_argument('--refresh', action='store_true'); ap.add_argument('--include-leftovers', action='store_true')
a = ap.parse_args(sys.argv[2:])
WORK = os.path.abspath(a.work); os.makedirs(WORK, exist_ok=True)
def flist(s):
    got = [x.strip() for x in s.split(',') if x.strip()]
    bad = [x for x in got if x not in FEATURES]
    if bad: raise SystemExit(f'unknown features {bad}; use {FEATURES}')
    return got
FEAT = flist(a.features) or list(FEATURES); REPL = flist(a.replace)
if REPL and not a.features: FEAT = REPL
lanes = max(1, min(4, a.lanes))
def rc(*args, check=True):
    r = subprocess.run(['rclone', *args], capture_output=True, text=True)
    if check and r.returncode != 0: raise SystemExit(f'rclone {args[0]} failed: {r.stderr[-300:]}')
    return r

# 1. is a phone syncing? (a .changed newer than 20 min that this tool did not write)
for e in json.loads(rc('lsjson', ROOT, '--include', 'profile.*/.changed', '-R', '--files-only').stdout or '[]'):
    t = time.mktime(time.strptime(e['ModTime'][:19], '%Y-%m-%dT%H:%M:%S')) - time.timezone
    age = (time.time() - t) / 60
    who = rc('cat', f"{ROOT}/{e['Path']}", check=False).stdout.strip()
    if age < 20 and not who.startswith('ai-data'):
        raise SystemExit(f"STOP: {e['Path']} changed {age:.0f} min ago ({who[:40]!r}): a phone is probably syncing. Try again later.")

# 2. books and their order: books with ai/assist first (most recently changed), then the rest, newest file first
sys.path.insert(0, TOOLS); from fname import app_file
books = [e for e in json.loads(rc('lsjson', f'{ROOT}/Books', '-R', '--files-only').stdout)
         if e['Path'].rsplit('.', 1)[-1].lower() in ('pdf', 'epub')]
seen = {}
for kind in ('ai', 'assist'):
    r = rc('lsjson', f'{ROOT}/{PROF}/{kind}', '--files-only', check=False)
    for e in json.loads(r.stdout or '[]'): seen[e['Name']] = max(seen.get(e['Name'], ''), e['ModTime'])
for b in books: b['fn'] = app_file(os.path.basename(b['Path'])); b['seen'] = seen.get(b['fn'], '')
books.sort(key=lambda b: (b['seen'] == '', '' if not b['seen'] else ''.join(chr(255 - ord(c)) for c in b['seen']),
                          ''.join(chr(255 - ord(c)) for c in b['ModTime'])))
if a.book:
    books = [b for b in books if any(t.lower() in b['Path'].lower() for t in a.book)]
    if not books: raise SystemExit(f'no book under Books/ matches {a.book}')

def wdir(rel): return os.path.join(WORK, 'books', re.sub(r'[^\w.-]+', '_', rel)[:80] + '-' + hashlib.md5(rel.encode()).hexdigest()[:6])

# 3. prepare (download + chapter text, cached) and compute what is missing
def one(b):
    rel = b['Path']; W = wdir(rel)
    if a.refresh and os.path.exists(os.path.join(W, 'ch')): shutil.rmtree(os.path.join(W, 'ch'))
    if not os.path.exists(os.path.join(W, 'ch', 'index.json')):
        r = subprocess.run([sys.executable, '-I', f'{TOOLS}/prep.py', TOOLS, rel, W], capture_output=True, text=True)
        if r.returncode != 0: return {'rel': rel, 'W': W, 'error': 'prep failed: ' + r.stderr[-300:]}
    r = subprocess.run([sys.executable, '-I', f'{TOOLS}/status.py', TOOLS, W], capture_output=True, text=True)
    if r.returncode != 0: return {'rel': rel, 'W': W, 'error': 'status failed: ' + r.stderr[-300:]}
    st = json.load(open(os.path.join(W, 'status.json')))
    return {'rel': rel, 'W': W, 'status': st}
with ThreadPoolExecutor(4) as ex: res = list(ex.map(one, books))

queue, report = [], {'skipped': [], 'complete': [], 'leftovers': [], 'errors': [], 'v1': []}
for r in res:
    if 'error' in r: report['errors'].append(f"{r['rel']}: {r['error']}"); continue
    st = r['status']; W = r['W']
    if st['skip']: report['skipped'].append(f"{r['rel']}: {st['skip']}"); continue
    if st['v1'] and not a.upgrade_v1: report['v1'].append(f"{r['rel']}: {len(st['v1'])} chapters (X-ray blocked)")
    miss = {f: st['missing'][f] for f in FEAT}
    n = sum(len(v) for v in miss.values()) + (len(st['v1']) if a.upgrade_v1 else 0)
    chap = {k for f, v in miss.items() if f not in ('book', 'skeleton') for k in v}
    # gaps in only a few chapters of an otherwise finished book: usually chapters an earlier agent judged
    # to be side matter (notes, ads, a title page) - not worth a new agent unless asked
    leftovers = (n > 0 and not miss.get('book') and not miss.get('skeleton') and not st['v1']
                 and len(chap) <= max(2, len(st['content']) // 20))
    if REPL: why = 'redo'
    elif a.all: why = 'all'
    elif n == 0: report['complete'].append(r['rel']); continue
    elif leftovers and not a.include_leftovers:
        report['leftovers'].append(f"{r['rel']}: {sorted(chap)[:4]}"); continue
    else: why = 'missing'
    for name, val in (('FEATURES', FEAT if set(FEAT) != set(FEATURES) else None), ('REPLACE', REPL or None),
                      ('UPGRADE_V1', ['approved'] if a.upgrade_v1 else None)):
        p = os.path.join(W, name)
        if val: open(p, 'w').write('\n'.join(val) + '\n')
        elif os.path.exists(p): os.remove(p)
    out = os.path.join(W, 'out')
    if REPL and os.path.isdir(out) and os.listdir(out):  # a redo starts from fresh drafts
        os.rename(out, out + time.strftime('.prev-%Y%m%d%H%M%S'))
    queue.append({'rel': r['rel'], 'W': W, 'why': why, 'features': FEAT, 'replace': REPL, 'upgrade_v1': a.upgrade_v1,
                  'missing': {f: len(v) for f, v in miss.items() if v}, 'v1': len(st['v1'])})
if a.limit: queue = queue[:a.limit]

QD = os.path.join(WORK, 'queue'); os.makedirs(QD, exist_ok=True)
for f in os.listdir(QD):
    if re.match(r'q\d+\.json$', f) or f == 'PAUSE': os.remove(os.path.join(QD, f))
sl = [queue[i::lanes] for i in range(lanes)]
for i, s in enumerate(sl): json.dump(s, open(os.path.join(QD, f'q{i}.json'), 'w'), ensure_ascii=False, indent=1)
json.dump({'queue': queue, 'report': report, 'made': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())},
          open(os.path.join(QD, 'plan.json'), 'w'), ensure_ascii=False, indent=1)

print(f'books on server: {len(books)}; queued: {len(queue)}; complete: {len(report["complete"])}; '
      f'skipped: {len(report["skipped"])}; leftovers only: {len(report["leftovers"])}; errors: {len(report["errors"])}')
for k in ('skipped', 'v1', 'leftovers', 'errors'):
    if report[k]: print(f'\n{k}:'); print('\n'.join('  ' + x for x in report[k][:40]))
print('\nqueue:'); print('\n'.join(f"  {q['rel']}  {q['missing'] or q['why']}" for q in queue[:60]))
if queue:
    SKILL = os.path.dirname(TOOLS)
    print('\nRUN WITH: Workflow scriptPath=' + os.path.join(SKILL, 'lanes.js') + ' args=' +
          json.dumps({'skill': SKILL, 'queue': QD, 'counts': [len(s) for s in sl if s]}))
