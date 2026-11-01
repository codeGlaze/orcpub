# Keeping a long chat's expertise cheaply

Long chats do better than new agents with handoffs, but reopening one is expensive: 642k tokens of
cache writes in the chat this was worked out in, and about 70% of that context was tool traffic. A
handoff records state; the value is the owner's corrections, failed approaches and traps.

## The routine (once per chat)

1. Write `~/.claude/expertise/<name>.md`, first line `session: <session id>`, under 3,000 words:
   corrections and why, what failed, what a new agent gets wrong first, settled decisions, open work.
   It points at KB pages; shared findings go in the KB, not here.
2. Audit it: extract the owner's typed messages from the transcript, and have a fresh subagent report
   what is in none of the expertise file, `CLAUDE.md`, memory or the KB. The owner cannot quiz for
   what they do not know is missing, so their own history is the answer key. This cost 215k tokens
   for a four-month chat, so run it once, not routinely.
3. Run a plain `/compact`. `/compact` instructions are a request to the summarizer, not a guarantee.

## What keeps it working

Two user-level hooks, so no agent has to choose to use them: `~/.claude/hooks/expertise.sh`
(SessionStart) prints the file into the session, and `expertise-nudge.sh` (Stop) asks at most once an
hour for anything worth recording. A tool an agent must remember goes unused here: what must happen
needs a hook or a failing test; what should happen needs a `CLAUDE.md` line naming the moment.
Compact before leaving a large chat: idle gaps and model switches force a full cache rewrite.
