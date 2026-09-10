# Documentation discipline

The rules that keep the KB worth reading. Referenced by `.claude/hooks/README.md` and
`BRANCH.md`; enforced where it can be by `scripts/check-docs.sh`.

## What earns a doc

Something a competent person would get wrong without it, that is not obvious from the code:

- A finding that cost real time to establish, especially a measured one.
- A constraint that is invisible at the call site (a licensing boundary, a type mismatch
  across two files, an API that blocks).
- A decision with a rejected alternative. The alternative is the valuable half.
- A reversal.

Not: what the code already says, a change the diff explains, or a narrative of the session.

## Verify, don't remember

**Every factual claim in a doc is checked against the code at the time of writing.** Not
recalled, not inferred from a filename. The failure mode is confident and specific and wrong,
which is worse than vague.

Two rules that catch most of it:

- **Line references rot.** Pin them: *"references are against `integration` at `432cf375`"*.
  Branches diverge — `filter-monsters` sat at `:1304` on one branch and `:1147` on another in
  the same week.
- **`#_` is not a comment, it discards the next form.** A grep hit inside one looks live.
  `classes.cljc` carries 160 discarded hits for `:name "` against 346 live. Use
  `scripts/clj-grep.py`, which separates them.

## Update in place

A doc is the current state of knowledge, not a log of how it was reached. When something
changes, **edit the claim**. Do not append a second account and leave the reader to work out
which one won.

The exception is a reversal.

## Record reversals

When a doc's conclusion is overturned, the superseded reasoning stays visible — someone will
otherwise re-derive it and reach the same wrong answer.

Keep a `## Revisions` section at the foot: what the doc used to say, what it says now, and why
it changed. Correct the body; explain in Revisions. Both, not either.

## Index it or it is invisible

Every `docs/kb/*.md` is linked from `docs/kb/README.md`. An unlinked doc is not findable, and
`check-docs.sh` fails the commit. Two plan docs sat orphaned for weeks because the hook was
never armed.

## Say what is not known

An open question is content. Write it down with what you tried, so the next person does not
repeat the dead end — *"the 5etools schema was not obtained; GitHub returns 403 through the
sandbox proxy"* is more useful than silence, and stops a second agent spending an hour on it.

**But check the KB before calling something open.** "Why is Circle of the Moon commented out?"
was listed as an open question while the answer sat in `srd-vs-plugin-content.md` in the same
directory.

## Arm the hooks

`core.hooksPath` is not set by cloning. Run `scripts/setup-hooks.sh` once per clone or none of
the enforcement above runs, and the discipline degrades to whatever the agent remembers.
