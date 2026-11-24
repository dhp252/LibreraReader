# usage: python3 -I tally.py <journal.jsonl or workflow transcript dir> [...]
# Summarises the per-book reports of finished runs: counts per status and the books not done.
import sys, os, json, collections
c = collections.Counter(); rows = []
for p in sys.argv[1:]:
    if os.path.isdir(p): p = os.path.join(p, 'journal.jsonl')
    for line in open(p):
        try: o = json.loads(line)
        except Exception: continue
        if o.get('type') != 'result': continue
        r = o.get('value') or o.get('result') or {}
        if isinstance(r, str):
            try: r = json.loads(r)
            except Exception: continue
        if not isinstance(r, dict): continue
        c[r.get('status')] += 1
        if r.get('status') != 'done':
            rows.append(f"{r.get('status'):8} {r.get('book', '')[:80]} :: {(r.get('remaining') or r.get('notes') or '')[:160]}".replace('\n', ' '))
print(dict(c))
print('\n'.join(rows))
