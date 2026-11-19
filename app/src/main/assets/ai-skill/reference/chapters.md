# Chapters and their keys

Every result is stored under the chapter's **key**. A result under a key the app does not use
for that book is never shown, so get the keys right.

## Keys the app already wrote

If `ai/<book>.json` has `chapterKeys`, it is the app's own list of the book's chapter keys, in
order: use it. Keys already used in `summaries[].key`, `highlights[].key` or in `assist/` item
names (`preq:<key>`, `xray:<key>`...) are right too. Make keys yourself only for the chapters
none of these name.

## Making a key from the table of contents

The app takes the book's table of contents (EPUB: the nav or NCX; PDF: its outline/bookmarks):

1. Keep the entries that point into the book.
2. Take the top level. If the top level has a single entry and there are deeper entries (often
   just the book's title above the real chapters), take the level below instead.
3. Order them by where they start. Entries that start on the same page count once (the first); in an EPUB this means entries pointing into the same XHTML file before any text of their own.
4. A chapter's key is its title with runs of white space made one space, trimmed, in lower case,
   then `#` and a number: `#0` for the first chapter with that title, `#1` for the second with the
   same title, and so on. `Chapter 1: The Division of Labour` → `chapter 1: the division of labour#0`.
5. The pages before the first chapter (cover, preface without its own entry) are `@front`.

A book without a table of contents is cut into blocks of pages whose keys are `@pages:<first
page>`; those depend on the reader's screen and font in an EPUB, so do not write results for such
an EPUB. In a PDF the pages are fixed: blocks of 20 pages, `@pages:0`, `@pages:20`, ...

The whole book is `@book` (book summary).

## Side matter

Skip chapters that are side matter (contents, copyright, index, acknowledgments, about the
author, bibliography, notes, glossary, ads) and `@front`: the app writes nothing for them.

## Parts (skeleton only)

The skeleton groups chapters into parts. A top-level chapter (rule 2's level) whose own entries
one level below are 2 or more is a **part**; its chapters are those entries, keyed
`<part key>/<entry title in lower case, spaces collapsed>#<n>` (n counts repeats inside the part).
A top-level chapter without 2 sub-entries is a part of one chapter: itself, same key.
