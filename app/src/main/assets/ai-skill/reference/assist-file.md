# `assist/<book>.json`: skeleton, pre-questions, X-ray, the book updated

One JSON object per book: `{"<item key>": {<item>}, ...}`. Phones merge this file item by item,
the item with the larger `t` winning, so:

- Every item you write has `"t": <now in ms>`, larger than the `t` it replaces.
- Change only the items you write; copy every other item exactly (also `{"del": true, "t": ...}`
  ones: those are removals that must keep syncing).
- An item marked `"del": true` was removed by the reader: do not bring it back unless asked.

The `//` comments below are notes, not part of the file.

## Skeleton: `skeleton:book`

The book's whole argument before reading it, from its parts and chapters (`chapters.md`, Parts).

```json
"skeleton:book": {
  "line": "<the thesis, main claim or story, one plain sentence, at most 20 words>",
  "paragraph": "<the whole argument in 3 to 5 sentences: the claim, how the book gets there, what follows>",
  "note": "<where the conclusion is, e.g. \"Smith saves his conclusion for Book IV.\"; empty when none>",
  "conclusion": "<key of the part holding the conclusion (book without parts: of the chapter); empty when none>",
  "parts": {"<part key>": "<what the part does in the argument, at most 15 words>"},
  "optional": ["<key of a part, or in a book without parts of a chapter, that is background the argument does not need>"],
  "chapters": {"<chapter key>": "<what the chapter is about, one sentence of at most 15 words>"},
  "chars": {"<chapter key>": 41250},           // characters of text in the chapter, roughly
  "model": "Claude (outside the app)",
  "made": 1763520000000,
  "t": 1763520000000
}
```

- A book has parts when any part holds 2 or more chapters; otherwise `parts` is `{}` and
  `conclusion`/`optional` use chapter keys. Chapter keys inside a part are `<part key>/<name>#n`.
- One `chapters` entry per chapter, side matter left out. The conclusion is never optional.

## Pre-questions: `preq:<chapter key>`

Three questions a reader guesses before reading the chapter; reaching the answer later makes it
stick. The app offers them when the reader enters the chapter.

```json
"preq:<chapter key>": {
  "title": "<chapter title>",
  "state": "ready",
  "layout": "",
  "locked": 0,
  "questions": [
    {"q": "<the question>", "short": "<the question in at most 8 words>",
     "options": ["<at most 6 words>", "…", "…", "…"], "answer": 2,
     "number": 48000, "label": "<for a quantity: whose number, at most 3 words; else empty>",
     "why": "<one short sentence: why the answer is so>",
     "quote": "<the one sentence of the chapter that gives the answer, copied exactly>",
     "page": -1, "lastPage": -1,
     "guess": -1, "own": "", "confidence": -1, "skipped": false, "found": 0}
  ],
  "t": 1763520000000
}
```

- Exactly 3 questions, the best first, each answered by a single sentence of the chapter. Ask for
  a surprising number or fact, a cause, or an unexpected claim; never about headings, the title,
  or a story twist a guess would spoil.
- 4 options, exactly one right (`answer` = its index 0 to 3), the others plausible. For a
  quantity, every option is a quantity of a clearly different size and `number` is the right
  value; otherwise leave `number` out.
- Keep `state` `"ready"`, `layout` `""`, `locked` 0 and the reader fields (`guess` -1, `own` "",
  `confidence` -1, `skipped` false, `found` 0): the reader fills them in.
- Never replace an item whose `state` is `"locked"`, `"declined"` or whose questions have a
  `guess`: that is the reader's own work.
- Skip side-matter chapters and keys starting with `@`.

## X-ray ratings: `xray:<chapter key>`

How well each idea of the chapter's key passages is supported. Needs the chapter's entry in
`ai/<book>.json` `highlights` (write it first if missing).

```json
"xray:<chapter key>": {
  "ideas": 1763520000000,          // exactly the "time" of that chapter's highlights entry
  "model": "Claude (outside the app)",
  "ratings": [
    {"id": 3, "rating": "strong", "label": "<1 to 3 words naming the idea>", "why": "<1 to 3 words: numbers, 1 story, observed>"}
  ],
  "weakest": {"id": 7, "title": "<at most 7 words>", "explanation": "<exactly 2 short sentences: what the idea claims, and why its support is thin>"},
  "t": 1763520000000
}
```

- `id` is an idea id of that highlights entry. Rate every idea that supports another and every
  idea others support, judging only from its quotes and paragraphs:
  `strong` (numbers, a study, a measured result, several independent cases), `fair` (sound
  reasoning or a plain observation that holds up), `observation` (the author's own observation,
  plausible but unchecked), `questioned` (the source or fact is open to doubt), `weak` (one story,
  no numbers, or a leap).
- `weakest`: the supported idea whose support is thinnest for how much rests on it; name the
  least supported one even when nothing is weak.
- If `ideas` differs from the highlights entry's `time`, the app shows the ratings as out of date.

## The book, updated: `updated:<chapter key>`

What later research, history and criticism have added or corrected since the chapter was
written, as a careful modern editor would. Honesty comes first.

```json
"updated:<chapter key>": {
  "title": "<chapter title>",
  "notes": [
    {"kind": "newer", "quote": "<the one sentence of the chapter it attaches to, copied exactly>",
     "title": "<at most 8 words>", "body": "<2 to 3 short plain sentences>",
     "settled": "debated", "settled_note": "<at most 8 words, or empty>",
     "source": {"work": "<title>", "author": "<author>", "year": "1832"},
     "page": -1, "last": -1}
  ],
  "objection": {
    "claim": "<the author's main claim, one sentence>", "objection": "<one sentence>", "strength": "moderate",
    "twist": {"head": "<where the author answers it, at most 8 words>", "quote": "", "note": "<one sentence>", "in_chapter": false, "page": -1},
    "pushed": {"who": "<later author>", "work": "<title>", "year": "1890", "text": "<one sentence>"},
    "disagree": {"author": "<the author's side, one sentence>", "other": "<the other side, one sentence>", "text": ""}
  },
  "layout": "",
  "at": 1763520000000,
  "model": "Claude (outside the app)",
  "first_published": 1776,
  "author": "<the book's author>",
  "t": 1763520000000
}
```

- At most 6 notes, only where you know something solid; fewer good notes beat six thin ones; an
  empty list is fine. One note per sentence. `kind`: `source` (where a fact or story really comes
  from, or whether it checks out), `missed` (a cause later work established), `doubtful` (a story
  or fact now doubted or known wrong), `newer` (how later research or events changed the picture).
- `settled`: `well established` (widely accepted among experts), `debated` (experts disagree),
  `speculative` (a guess, one study, a minority view); when in doubt, the weaker one.
- `source`: only when you are sure the work exists and says this; otherwise leave `source` out.
  Never invent a title, author, year, number or quotation.
- `objection`: the single strongest objection to the chapter's main claim; `strength` `strong`,
  `moderate` or `weak`. Leave out `twist`, `pushed` or `disagree` when you have nothing sure, and
  the whole `objection` when the chapter makes no claim.
- `first_published`: the year, only if sure; else 0. Keep `layout` `""`: the app finds the pages.
