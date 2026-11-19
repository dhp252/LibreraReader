# `ai/<book>.json`: summaries and key passages

One JSON object per book (the `//` comments below are notes, not part of the file). A new file starts as `{"version": 2, "updated": <now>, "summaries": [], "highlights": []}`.

```json
{
  "version": 2,
  "updated": 1763520000000,
  "goal": "…",                      // the reader's goal (optional): never change it
  "summaries": [
    {"key": "<chapter key or @book>", "title": "<chapter title, or the book's title>",
     "text": "<the summary>", "model": "Claude (outside the app)", "time": 1763520000000}
  ],
  "highlights": [
    {"key": "<chapter key>", "title": "<chapter title>", "model": "…", "time": 1763520000000,
     "ideas": [
       {"id": 1, "text": "<the idea in one short sentence of your own>", "category": "main",
        "parent": -1, "marked": true, "page": -1}
     ],
     "quotes": [
       {"text": "<a key sentence, copied exactly>", "page": -1, "category": "main", "idea": 1}
     ],
     "paragraphs": [
       {"text": "<a whole must-read paragraph, copied exactly>", "page": -1, "category": "evidence", "idea": 2}
     ]}
  ],
  "chapterKeys": ["…"],              // written by the app: never change
  "emptyKeys": ["…"]                 // chapters with nothing to mark: you may add a key here instead of a highlight
}
```

- `version` is 2. Set `updated` to now whenever you change the file.
- One entry per key in `summaries` and in `highlights`: replace the entry, never add a second.
- Keep every entry, field and key you did not change.

## Summaries

- **Chapter** (`key` = the chapter key): 6 to 10 sentences: what happens or what is argued, the
  people or ideas that matter, and why it matters for the rest of the book. No "This chapter
  describes". Plain prose in short paragraphs, no headings or Markdown. Only from that chapter's text.
- **Book** (`key` = `@book`, `title` = the book's title): its central conflict or thesis, the main
  characters or ideas and what drives them, how it develops and ends, and 3 to 5 themes.

## Key passages (`highlights`)

Map every idea of the chapter so that a reader who reads only what is marked misses nothing
important. Go section by section in order; list every main idea (a claim, a step of the argument,
a conclusion) and under it the evidence, concepts, caveats and examples that matter. Leave out
filler and repetition.

- `ideas`, in reading order. `id`: 1, 2, 3... unique in the chapter. `parent`: the id of the idea
  it supports, `-1` for none. `marked`: true when at least one of its quotes or paragraphs is
  copied exactly. `page`: -1 (a PDF: the 0-based page of its first quote).
- `category`, the one whose test the idea passes best:
  - `main`: the author's core claim or thesis for a section.
  - `evidence`: data, studies or reasoning that support a claim.
  - `concept`: a new term, definition, framework or model.
  - `caveat`: limitations, exceptions, counterarguments, a "but".
  - `apply`: something the reader could act on, judged against their goal. Only when the file has
    a non-empty `goal`.
  - `example`: stories, anecdotes, cases.
- `quotes`: for each idea the 1 to 3 whole sentences that carry it; `idea` = its id, `category` =
  its category.
- `paragraphs`: only where one sentence is not enough, the whole paragraph a reader must read;
  same `idea` and `category` fields.
- Every idea has at least one quote or paragraph.
- A chapter with nothing to mark (side matter only): add its key to `emptyKeys` instead.
- After writing a chapter's key passages, its X-ray ratings (`xray:<key>` in assist) are out of
  date: write them again too if the user asked for X-ray.
