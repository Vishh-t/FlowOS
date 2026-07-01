# FlowOS — Roadmap & Source of Truth

> This file is the persistent project context. Any conversation (this one or a future one)
> should read this file before making architectural suggestions. Update it as decisions
> are made — don't let decisions live only inside a chat transcript.

Last updated: 2026-07-01

---

## 1. Vision (unchanged from original knowledge transfer)

FlowOS is a mobile-first intelligent life planner. Not a calendar, not a to-do list, not a
chatbot. It's a scheduling engine with a mobile interface. Users define **what** they want
(goals, priorities, constraints) — the app decides **when**. Signature feature: dynamic
rescheduling when life doesn't go to plan.

Target audience: college/engineering students, placement aspirants.

Full philosophy, three activity types (Fixed / Flexible / Leisure), tech stack, module list,
and screen list are as originally specified in the knowledge-transfer doc. Not repeated here
verbatim — treat that doc's content as still valid unless contradicted below.

---

## 2. Current Actual State (verified by reading the repo, not assumed)

**Built and functionally complete:**
- `Auth` module — signUp, logIn, refresh, logout, Google Sign-In, JWT (access + Redis-backed
  refresh tokens), Spring Security filter chain, global exception handling.
- `User` entity — supports both LOCAL and GOOGLE auth providers on one table.

**Not started yet:** Goals, Commitments, Import, Scheduler Engine, Calendar, Notifications,
Analytics, AI Parser. No React Native frontend exists yet — backend-only repo so far.

**Known minor issues flagged, not yet fixed (backend is user's learning area — not fixed by Claude):**
- JWT access token expiry (15 min) is hardcoded in `JwtUtil`, not externalized to
  `application.properties`.
- `JwtFilter` silently swallows all exceptions (including `NotFoundException` for a deleted
  user) into a debug log — worth a deliberate decision on whether that's desired.

---

## 3. Module Ownership Boundaries

| Module | Owns | Does NOT own |
|---|---|---|
| Auth ✅ | Identity, tokens | Anything about the user's schedule |
| User/Profile | Wake/sleep, meal windows, morning/night pref, commute buffer | Goals themselves |
| Goals | Goal definitions: target, frequency, priority, splittability, deadline | Placement/timing |
| Commitments | Fixed, immovable events (recurring or one-off) | Flexible task placement |
| Import | Parsing PDFs/screenshots/Google Calendar → Commitments | Any scheduling logic |
| **Scheduler Engine** | Turns Goals + Commitments + Preferences into a placed weekly schedule; owns rescheduling | Goal/commitment data itself — it's a consumer of both |
| Calendar | Read-facing projection of the scheduler's output | Does not generate placements |
| Notifications | Reacting to scheduler events | No scheduling logic |
| Analytics | Historical record of planned-vs-actual, derived insights | Does not feed back into scheduling decisions yet (explicitly deferred — see §6) |
| AI Parser | NLU only: sentence → structured intent object | Never touches placement logic directly — calls the same APIs any client would |

**Standing rule:** AI Parser is a client of FlowOS's own APIs, never a special backdoor into
scheduling logic. This is non-negotiable per the original vision doc.

---

## 4. Build Order (phased, not versioned — full scope intended, just sequenced)

### Phase 1 — Minimal data backbone
1. `Profile` — wake/sleep/meal times, morning-night pref. Attaches to `User`.
2. `Commitment` — fixed events + recurrence rule. Gives the scheduler a skeleton to work around.
3. `Goal` — flexible-task template (duration, frequency, priority, splittable, deadline).
   - **Decision made:** Leisure is modeled as a low-priority `Goal` type for now, not a
     separate entity. Split it out later only if it proves to need genuinely different
     behavior — don't design for a distinction not yet felt.

### Phase 2 — Scheduler Engine (the centerpiece; sub-phased deliberately)
1. **Naive placement** — greedy, highest-priority-first into earliest free slot. No scoring,
   no candidates yet. Goal: working end-to-end pipeline.
2. **Constraint validation layer** — sleep protection, no double-booking, preferred
   time-of-day, bolted on as rules the greedy placer must satisfy.
3. **Multi-candidate + scoring** — generate several candidate schedules, score on: goal
   completion, sleep preservation, workload balance, context-switch minimization, preference
   alignment. Real algorithm-design discussion happens here (greedy vs. backtracking vs. CSP
   vs. local search) — deferred until this phase is reached.
4. **Rescheduling engine** — reacts to trigger types (see §5). Architecturally distinct from
   initial generation: partial re-optimization, not full regeneration.

### Phase 3 — Consumers of the scheduler's output
- `Calendar` (today view, week view)
- `Import` (can be built in parallel with Phase 2 if variety is wanted — fully decoupled)
- `Notifications` (FCM wiring)
- `Analytics` (needs Phase 2's history to have anything real to analyze)

### Phase 4 — AI Parser
Deliberately last — least architecturally risky, translates into a stable target instead of
a moving one.

---

## 5. Rescheduling Trigger Taxonomy (for Phase 2.4)

- **Time-shift events:** overslept, running late, finished early.
- **Completion events:** task skipped, done early, done late.
- **Structural events:** new fixed commitment added, one cancelled, new goal added.
- **Passive drift:** task not started by scheduled time but not explicitly skipped — needs a
  decision: wait, nudge, or auto-reschedule after a grace period?

Open design question (revisit at Phase 2.4): one general re-optimize function, or several
specialized fast-path handlers per trigger type? Real perf/complexity trade-off once this is
live on a phone hitting the API frequently.

---

## 6. Engagement & Retention Strategy

FlowOS should be more than "correct" — it needs to become a daily habit, not just a tool
people abandon once the novelty wears off.

**Design philosophy:** favor intrinsic reinforcement (evidence the system works for you)
over heavy extrinsic gamification (streaks/points/badges). Heavy gamification decays and can
backfire (streak anxiety → users quit entirely after one break). Light-touch extrinsic
mechanics are fine; they shouldn't be the primary hook, since it undermines the "serious tool"
positioning for this audience.

**Planned mechanisms (design detail deferred to Phase 3, Analytics):**
- **Adherence tracking per-goal**, not one fragile global streak. Reschedules *caused by the
  system* (not the user) must not break streaks — this only works if reschedule reason codes
  exist (see standing rule below).
- **Reflective weekly insights**: trend lines, not just totals. E.g. surfacing "adherence
  dropped when exams started — auto-lower priority during exam weeks?" — the app noticing a
  pattern and offering to codify it as a preference.
- **"Time reclaimed" framing**: e.g. finishing early → reclaimed minutes shown back to the
  user, reframing the scheduler as working *for* them.
- **Light positive reinforcement on completion** (frontend/UI territory — cheap, high payoff).
- **Milestone moments**, not daily grind counters (e.g. "50 gym sessions logged" instead of a
  daily point tally) — milestones don't create the anxiety daily streaks do.
- **Adaptive pattern nudges** (rule-based, not ML — see §7): "You've moved gym off 6am 4
  times — try 6pm instead?" This is the strongest differentiator: the app *feels* like it's
  paying attention.
- **Parked for later, not now:** social/accountability features (shared goals, study-with
  presence, leaderboards). High potential for this audience, but a whole extra subsystem
  (real-time, privacy, moderation) that would pull focus from the scheduler. Explicitly out
  of scope until the core loop is proven.

**Standing rule (applies starting Phase 2, cannot be retrofitted cheaply):**
> Every reschedule event must record a reason code (`USER_LATE`, `USER_SKIPPED`,
> `SYSTEM_CONFLICT`, `USER_LOCKED`, etc.) and every completion must record a timestamp.
> This is the one piece of data-model design that must happen now, even though the
> engagement features themselves are built much later.

---

## 7. Position on Machine Learning

**Decision:** No ML in the core Scheduler Engine — permanent architectural boundary, not a
"not yet." Scheduling is a constraint-satisfaction/optimization problem, not a prediction
problem. Reasons: determinism (explainability — "why did gym move?" needs a real answer),
debuggability, and zero cold-start data on day one.

| Feature | ML? |
|---|---|
| Core scheduling/placement | **No — never.** Algorithmic (CSP/greedy/local search), by design. |
| "You keep moving gym off 6am" nudges | No — simple frequency counting over reschedule reason codes. |
| Auto-lower priority during exam weeks | No — rule-based, triggered by deadline proximity. |
| Predicting real task duration vs. estimate | Borderline — start with a simple statistical average, not ML. |
| Learning implicit priority weights across many behavior signals | Yes, genuinely ML — but v-much-later, only after rule-based heuristics are proven insufficient. |
| NLU (AI Parser) | Yes — already correctly delegated to Gemini API, not something to train. |

**Principle:** earn the right to use ML by first proving simple heuristics/rules aren't
enough — don't reach for it upfront.

---

## 8. Working Agreement (how Claude should behave across all chats on this project)

- Backend = user's learning area. Explain, hint, point out mistakes. Never rewrite backend
  code unless explicitly asked. Backend is read-only by default.
- Frontend = Claude can edit directly via filesystem tools when needed.
- Scheduler Engine gets extra care: algorithm trade-offs, complexity discussion, multiple
  approaches — always before code.
- Before referencing or suggesting anything about existing code, read the actual files at
  `E:\computer science\Java\FlowOS` — never guess or assume structure.
- Push back on design choices when warranted. Don't rubber-stamp. Don't overengineer just to
  use more technology.
- This is a learning + portfolio project, primarily the former. Understanding every decision
  matters more than shipping fast.

---

## 9. Open Decisions Log

Track unresolved questions here as they come up, and their eventual resolution, so context
isn't lost between chats.

- [ ] One general re-optimize function vs. specialized fast-path handlers per reschedule
      trigger type? (Revisit at Phase 2.4)
- [ ] Grace period behavior for "passive drift" (task not started, not explicitly skipped)?
- [x] Leisure as separate entity vs. Goal subtype? → **Resolved: Goal subtype for now.**
