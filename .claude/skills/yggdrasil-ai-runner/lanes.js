export const meta = {
  name: 'yggdrasil-ai-runner',
  description: 'One agent per book, N lanes in parallel: write the Yggdrasil reading app AI results on the WebDAV folder',
  phases: [{ title: 'Books', detail: 'each lane runs its books one at a time, one agent per book' }],
}
// args: { skill: <this skill folder>, queue: <work>/queue, counts: [books in q0.json, q1.json, ...] }
// (plan.py prints these args). Pause: create the file <queue>/PAUSE - running agents finish their
// book, queued ones return at once without touching anything.
const REPORT = {
  type: 'object',
  properties: {
    book: { type: 'string' },
    status: { type: 'string', enum: ['done', 'partial', 'skipped', 'failed'] },
    added: { type: 'string' },
    remaining: { type: 'string' },
    notes: { type: 'string' },
  },
  required: ['book', 'status', 'added', 'remaining', 'notes'],
}
phase('Books')
const lane = async (i, n) => {
  const out = []
  for (let j = 0; j < n; j++) {
    const r = await agent(
      `Read ${args.skill}/BRIEF.md and follow it exactly for ONE book. SKILL = ${args.skill}.\n` +
      `Your queue entry: index ${j} (0-based) of the JSON list in ${args.queue}/q${i}.json ` +
      `(queue folder: ${args.queue}).`,
      { label: `lane ${i + 1} book ${j + 1}/${n}`, phase: 'Books', schema: REPORT, agentType: 'general-purpose' })
    out.push(r || { book: `q${i}#${j}`, status: 'failed', added: '', remaining: 'all', notes: 'agent returned nothing' })
    if (r) log(`lane ${i + 1} ${j + 1}/${n} ${r.status}: ${r.book}`)
  }
  return out
}
const res = await parallel(args.counts.map((n, i) => () => lane(i, n)))
return res.flat()
