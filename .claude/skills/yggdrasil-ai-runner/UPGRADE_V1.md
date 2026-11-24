# Extra task for this book: upgrade old key passages (the user approved this)
Some chapters already have key passages in the app's OLD format: a `highlights` entry in cur/ai.json
with `quotes` but no `ideas`. Because of that, X-ray cannot be written for them. The user has approved
replacing those old entries with new-format key passages. plan.py --upgrade-v1 writes W/UPGRADE_V1, which turns this on in the merge tool.

For every CONTENT chapter (not side matter: leave copyright, about the author, footnotes, cover etc. as they
are) whose existing highlight has no `ideas`:
1. Write new key passages for that chapter in W/out/cNNN.json exactly as for a missing chapter (ideas + quotes).
2. KEEP EVERY OLD QUOTE: each old quote text (from cur/ai.json) that can be found in the chapter text must
   appear again, character for character, as a quote of one of your ideas. You may add more quotes and ideas.
   Old quotes the checker cannot find in the chapter text may be dropped (say how many in your report).
3. Then write `xray:<key>` for that chapter as usual (it rates your new idea ids).
The merge tool replaces the old entry only when all findable old quotes are kept; otherwise it reports
"upgrade drops N old quotes" - fix that and check again. It backs up the original file first.
Everything else stays fill-only: never change any other existing result.
