import re, json, os, bisect
MARK = re.compile(r'\n=== PAGE (\d+) ===\n')
def _norm(t):
    t = re.sub(r'(\w)-\n(?=[a-z])', r'\1', t).replace('­', '')
    return re.sub(r'\s+', ' ', t)
def norm_q(q): return re.sub(r'\s+', ' ', (q or '').replace('­', '')).strip()
class Chapter:
    def __init__(self, raw):
        parts = MARK.split(raw)
        self.text = ''; self.starts = []; self.pages = []
        if len(parts) == 1:
            self.text = _norm(raw)
        else:
            buf = _norm(parts[0])
            for i in range(1, len(parts), 2):
                self.starts.append(len(buf) + 1); self.pages.append(int(parts[i]))
                buf += ' ' + _norm(parts[i + 1])
            self.text = buf
    def find(self, q):
        q = norm_q(q)
        if not q: return None
        i = self.text.find(q)
        return None if i < 0 else i
    def page(self, q):
        i = self.find(q)
        if i is None or not self.pages: return -1
        k = bisect.bisect_right(self.starts, i) - 1
        return self.pages[max(0, k)]
    def last_page(self, q):
        i = self.find(q)
        if i is None or not self.pages: return -1
        k = bisect.bisect_right(self.starts, i + len(norm_q(q)) - 1) - 1
        return self.pages[max(0, k)]
def load(W):
    idx = json.load(open(os.path.join(W, 'ch', 'index.json')))
    T = {c['key']: Chapter(open(os.path.join(W, 'ch', c['file'])).read()) for c in idx['chapters'] if c.get('file')}
    return idx, T
