---
name: yggdrasil-ai-data
description: Write or update the AI results of the Yggdrasil / Librera reading app (chapter and book summaries, key passages, skeleton, pre-questions, X-ray ratings, "the book, updated" notes) directly in the app's synced WebDAV folder, for one book or all books, so phones pick them up at their next sync. Use when asked to generate, fill in, redo or fix the app's AI results for books in this folder.
---

# Writing the reading app's AI results

The reading app keeps the AI results of each book in JSON files in this synced folder. Phones
sync these files and show what they find, so a file you write here works like one made by
another phone: the app shows it and never asks its own AI API for it again. Your job is to
read the books and write those results correctly.

This skill folder is put here by the app and replaced when a newer app changes a format. Do not
edit it.

## The folder

```
<root>/                                 the folder the app syncs (this skill is in <root>/.claude/skills/)
  Books/                                the book files (EPUB, PDF, FB2...); read the text from here
  profile.<name>/                       one per reader profile, usually one
    .changed                            "something changed": overwrite after you write (step 7)
    .shared/app-AI.json                 the app's AI settings; "answerLanguage" = language of results
    ai/<book>.json                      summaries and key passages of a book    → reference/ai-file.md
    assist/<book>.json                  the other features' results of a book   → reference/assist-file.md
```

`<book>` is the book's file name with its extension (`Wealth of Nations.epub` →
`ai/Wealth of Nations.epub.json`), with `\ / : * ? " < > |` and control characters replaced by
`_`. The folder may hold other files (marks, study, map...): never touch them.

## What the user can ask for

- **Which books:** one book (match its title or file name), several, or all books in `Books/`.
- **Which results:** any of the features below; "everything" means all of them.
- **Fill or overwrite:** by default only fill what is missing. Replace an existing result only
  when the user says so for that book or feature. Never remove results you were not asked to.

| Feature | File, key | Needs first |
|---|---|---|
| Chapter summary | `ai/`, `summaries[]` key = chapter key | |
| Book summary | `ai/`, `summaries[]` key = `@book` | |
| Key passages | `ai/`, `highlights[]` key = chapter key | |
| Skeleton | `assist/`, `skeleton:book` | |
| Pre-questions | `assist/`, `preq:<chapter key>` | |
| X-ray ratings | `assist/`, `xray:<chapter key>` | key passages of that chapter |
| The book, updated | `assist/`, `updated:<chapter key>` | |

Other features of the app (lens, semantic zoom, pictures, fiction companion, book map, recap,
recall, teach-back, debate, review, Use it, paths, edition) are made in the app only: do not
write their items.

## Steps

1. Read `reference/chapters.md`, then the reference file of each feature asked for.
2. Ask the user to sync their phones first and to keep the app closed while you work: the app
   replaces a whole `ai/` file with the newer copy, so a phone's unsent change could be lost.
3. For each book: get its text from `Books/` (EPUB: the XHTML files in spine order; PDF: its text
   layer, page by page). Work out its chapters and their keys (`reference/chapters.md`). If the
   book's `ai/` file has `chapterKeys`, those are the app's own keys for it: use them.
4. Read the book's current `ai/` and `assist/` files (they may not exist yet). Decide what is
   missing, or what you were asked to replace.
5. Write each result from the chapter's own text, in `answerLanguage` (empty: the book's
   language). Quotes are copied from the book character for character.
6. Write the files back: keep everything you did not change exactly as it was (other chapters,
   other items, unknown fields), validate the JSON, then upload.
7. Overwrite `profile.<name>/.changed` with any short text (e.g. `ai-data <time>`), so phones
   know to pull.
8. Tell the user what you wrote, per book and feature, and to open the app (it pulls a book's
   files when the book opens or an AI feature starts).

## Rules

- Quotes (key sentences, answer sentences, note sentences) are whole sentences copied exactly
  from the chapter's text: no changes, no ellipses, no added quotation marks, never headings,
  captions, page numbers or table-of-contents lines. The app finds each one by its words; a quote
  it cannot find is not shown.
- Page numbers: write `-1` (unknown) unless the book is a PDF, where pages are fixed (0-based).
  The app finds the pages itself.
- Times are milliseconds since 1970 (UTC). `model` names who wrote it, e.g. `"Claude (outside the app)"`.
- Leave out side matter: copyright, contents, index, author bios, acknowledgments, dedications,
  bibliographies, glossaries, praise, ads and calls to action.
- Write valid UTF-8 JSON only. If a file cannot be parsed, stop and tell the user; never replace
  a file you could not read.
