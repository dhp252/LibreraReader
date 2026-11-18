---
name: yggdrasil-reading
description: Write the AI reading aids (key passages, summaries, skeleton, pre-questions, X-ray ratings, Lens bridges, semantic zoom, "the book, updated", pictures) for books in this Yggdrasil / Librera WebDAV folder, as answer files the reading app picks up at its next sync. Use when asked to prepare, generate or redo AI features for a book in this folder.
---

# Writing AI reading aids for the Yggdrasil reader

The reading app on the user's phone usually asks an AI API for these aids. Here you write the
same answers ahead, from the book's text, into the folder the app syncs. The app then uses your
answer instead of calling the API, checks it, finds every quote on its page and stores it.

This skill folder is written by the app ({{APP}}). Do not edit it: the app rewrites it.

## The folder

```
<this folder>/
  .claude/skills/yggdrasil-reading/   this skill: SKILL.md, formats.json, features/<feature>.md
  Books/                              book files (not needed: use the desk's text.txt)
  profile.<name>/                     one reader profile each (usually one)
    claude/books/<book>/              one "desk" per book the reader prepared
      book.json                       the book, its chapters, the reader's page and goals
      text.txt                        the book's text as the app reads it
      prompts/<feature>.txt           the system prompt the app would send for that feature
      answers/                        YOUR answer files go here, and only here
      answers/used/                   answers the app took (read only)
      answers/rejected/               answers the app could not use, each with <file>.reason.txt
    assist/<book>.json, ai/<book>.json  the app's stored data: NEVER write these
```

## Steps

1. Read `formats.json` in this skill folder: the format number of each feature.
2. Find the desks: `profile.*/claude/books/*/book.json`. Pick the book the user named (match its
   `title`, `author` or `book` file name). No desk for it: tell the user to open the book once in
   the app with AI settings > "Work with Claude through WebDAV" on, then sync.
3. Read `book.json`. Its `features` lists what you may write for this book; `chapters` lists the
   chapters with their `n`, `key` and `title`. Skip chapters with `side: true` (copyright pages,
   index...) and chapters with `chars: 0`.
4. For each feature asked for, read `features/<feature>.md` in this skill folder (what text to take
   and what the user message looks like) and the desk's `prompts/<feature>.txt` (your instructions
   and the exact reply format). Treat that prompt as your system prompt and the user message as
   the input, and write the reply the prompt asks for, nothing around it.
5. Take each chapter's text from `text.txt`: the lines between `=== chapter <n>: ... ===` and the
   next chapter line. Drop the `--- page <p> ---` lines when you build the user message.
6. Write one answer file per feature and chapter (see below).
7. Look at `answers/rejected/` from earlier runs: each `.reason.txt` says why; write a new answer.

## Answer files

`answers/<feature>~<n>.json`, where `<n>` is the chapter's `n` from book.json, or `book` for an
answer about the whole book (book summary, skeleton). One JSON object, UTF-8:

```json
{
  "format": 1,
  "feature": "prequestions",
  "book": "<book.json's book>",
  "chapter": "<the chapter's key from book.json, exactly; \"@book\" for the whole book>",
  "by": "Claude Code",
  "made": "2026-11-18T20:10:00Z",
  "answer": <your reply to the prompt>
}
```

- `format` is the feature's number from `formats.json`. An answer with another number is rejected.
- `answer` holds the reply exactly as the prompt asks for it: a JSON string for a text reply (a
  summary), or the JSON object itself when the prompt asks for JSON (no Markdown fence).
- Writing a new answer for a chapter replaces the old one: the newest `made` wins.

## Rules

- Quotes are copied character for character from `text.txt`, in the book's language. The app looks
  each one up in the book; a quote it cannot find is shown as "not located".
- Use only the text of the chapter you answer for (and for book-wide answers, the whole book).
- Write nothing but files in `answers/`. Never change book.json, text.txt, prompts, `used/`,
  `rejected/`, or anything outside the desk.
- If book.json's `prepared` is older than the book's `source`, or the chapters look wrong, ask the
  user to open the book in the app again so it refreshes the desk.

## When the app takes an answer

- Key passages, the book summary and the skeleton: when the book is next opened, or when a sync
  brings the answer while it is open.
- Every other feature: the first time the reader opens that feature on that chapter. A waiting
  answer needs no API key in the app.

The app then moves the file to `answers/used/` (taken) or `answers/rejected/` (with the reason).
