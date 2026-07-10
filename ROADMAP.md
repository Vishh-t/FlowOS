# FlowOS — Roadmap & Source of Truth

> This file is the persistent project context. Any conversation (this one or a future one)
> should read this file before making architectural suggestions. Update it as decisions
> are made — don't let decisions live only inside a chat transcript.

Last updated: 2026-07-10

---

## 1. Vision (unchanged from original knowledge transfer)

FlowOS is a mobile-first intelligent life planner. Not a calendar, not a to-do list, not a
chatbot. It's a scheduling engine with a mobile interface. Users define **what** they want
(goals, priorities, constraints) — the app decides **when**. Signature feature: dynamic
rescheduling when life doesn't go to plan.

Target audience: college/engineering students, placement aspirants.

Full philosophy and original module/screen list as originally specified in
`PROJECT_CONTEXT.md`. Where this roadmap's data model differs from that document (see §3),
this roadmap is the current, corrected design — `PROJECT_CONTEXT.md` stays as the stable
vision doc and is not rewritten for every schema decision.

---

## 2. Current Actual State (verified by reading the repo, not assumed)

**Built and functionally complete:**
- `Auth` module — signUp, logIn, refresh, logout, Google Sign-In, JWT (access + Redis-backed
  refresh tokens), Spring Security filter chain, global exception handling.
- `User` entity — supports both LOCAL and GOOGLE auth providers on one table.

**In progress:** `Task` entity + nested embeddables (`EventOccurrence` → `EventTime`,
`TaskTimeRange` ×2, `Recurrence`) — see §3.3 for the actual current shape, which evolved
significantly from the original flat sketch. `Profile` entity is done.

**Scheduler Engine — partially started (candidate-generation slice only, see §5 Phase 2
note below):** `SchedulerService.generateCandidate` exists and handles: midnight-crossing
wake/sleep windows and padding (a real bug found and fixed via a night-owl profile —
`shift()` helper tracks day-rollover explicitly instead of relying on bare `LocalTime`
wrap), the same wraparound bug one level up at the week boundary (Sunday→Monday, fixed by
tracking day position as a raw non-wrapping offset until the final `TimeAndDayRange` is
built), and `taskDeadline` as an upper bound on the search (recomputed per candidate day
since a deadline's distance from each day differs; days where the deadline's occurrence has
already passed are skipped outright). Verified via a standalone compiled dry run against the
real algorithm code (not just read-through) covering these cases.

**Known gaps in this slice, not yet addressed:**
- Nothing yet constructs a `GenerateCandidateDTO` and populates `.now` — the deadline logic
  is correct but inert until some caller/orchestrator exists to invoke `generateCandidate`
  at all.
- No lower bound preventing a candidate from being placed before `now` — only the deadline
  (upper bound) is enforced.
- `WeeklyTimeline` is architecturally a single recurring week (no "which week" concept) —
  a deadline pushing the search past 7 days out is now clamped to avoid silently aliasing
  onto the wrong day, but multi-week scheduling isn't actually representable yet. Real
  limitation, not a bug — needs a deliberate decision before it matters (e.g. an assignment
  due in 3 weeks).
- `Recurrence.dayOfMonth`/`monthOfYear`/`timesPerWeek` are still unread by
  `generateCandidate` — `MONTHLY`/`ANNUALLY`/`COUNT_ONLY` recurrence isn't implemented, as
  originally scoped for later.
- Where this work actually sits against the Phase 2.1/2.2 split below is an open question —
  see §10.

**Not started yet:** Import, Calendar, Notifications, Analytics, AI Parser, and the rest of
the Scheduler Engine (naive full-placement pipeline, constraint validation layer beyond what
`generateCandidate` already does, multi-candidate scoring, rescheduling).
No React Native frontend exists yet — backend-only repo so far.

**Known minor issues flagged, not yet fixed (backend is user's learning area — not fixed by Claude):**
- JWT access token expiry (15 min) is hardcoded in `JwtUtil`, not externalized to
  `application.properties`.
- `JwtFilter` silently swallows all exceptions (including `NotFoundException` for a deleted
  user) into a debug log — worth a deliberate decision on whether that's desired.

---

## 3. Data Model — CURRENT DESIGN (supersedes earlier Commitment/Goal split)

This section reflects a real design evolution during Phase 1 planning — recorded here so
the reasoning isn't lost, not just the final answer.

### 3.1 Evolution, briefly

- **v1 idea:** separate `Commitment` (fixed) and `Goal` (flexible) entities, plus a
  `Profile`-level `SoftTimeBlock` list for meals/naps.
- **Problem spotted:** `Commitment` and `Goal` were converging toward needing the same
  timing/frequency/priority shape anyway. `SoftTimeBlock` was duplicating logic that a
  flexible/anchored task would need regardless. Commute duration and a global buffer value
  were incorrectly placed on `Profile` (a global settings object) when they're actually
  per-instance concerns (commute varies per location; buffer varies per task/task-type).
- **Resolution:** unify everything that "happens at a time" into one `Task` entity, with an
  embedded `EventTime` value object describing how rigid its timing is. `Profile` shrinks to
  only what's genuinely global to the user (wake/sleep window, morning-night preference).

### 3.2 `Profile`
- `@OneToOne` with `User`
- `wakeTime`, `sleepTime` (`LocalTime`)
- `morningNightPreference` (enum: `MORNING` / `NIGHT` / `NEUTRAL`)
- Nothing else. No commute field, no buffer field, no soft-block list — all relocated (see
  below). Profile only holds what defines the *boundary* of the user's day, not anything
  that happens *within* it.

### 3.3 `Task` (replaces `Commitment` + `Goal` + `SoftTimeBlock`) — AS ACTUALLY IMPLEMENTED
One entity for anything that occupies time: college, exams, meals, naps, gym, DSA, leisure.

The original plan was a flat `Task` + single embedded `EventTime`. While implementing, this
evolved into a small nested object graph — recorded here as the real, current shape, not the
original sketch:

```
Task
 ├─ taskId, user, taskName, category (TaskCategoryEnum), splittable
 ├─ movability (FlexibilityEnum: FLEXIBLE / ANCHORED / FIXED) — this IS the old
 │   "scheduleType" concept, renamed during implementation
 ├─ priority (TaskPriorityEnum)
 └─ @Embedded EventOccurrence
     ├─ @Embedded EventTime            (taskDurationInMinutes, duration tolerance — see below)
     ├─ bufferTimeInMinutes            (replaces old Profile.defaultBufferMinutes; mental
     │                                  transition gap, independent of location)
     ├─ commuteTimeInMinutes           (replaces old Profile.commuteDurationMinutes; physical
     │                                  travel time, independent of buffer — a task can need
     │                                  either, both, or neither)
     ├─ status (TaskStatusEnum: PENDING / IN_PROGRESS / DONE)
     ├─ preferredTimeRange (@Embedded TaskTimeRange, nullable) — what the user asked for
     ├─ allottedTimeRange (@Embedded TaskTimeRange)             — what the scheduler placed
     ├─ taskDeadline (nullable LocalDateTime)
     └─ @Embedded Recurrence           (see §3.6a)
```

**Why `EventOccurrence` exists as a wrapper rather than flattening everything onto `Task`:**
groups every timing-related concern (duration, buffer, commute, status, ranges, deadline,
recurrence) into one cohesive embeddable, keeping `Task` itself focused on identity/
classification (name, category, priority, movability). Deliberate choice, not accidental
nesting — confirmed during implementation when there was a pull to put tolerance fields
directly on `Task`; rejected because it would flatten a structured, purposeful grouping into
an undifferentiated pile of fields on the root entity.

**Tolerance — consolidated from 3 fields down to 1 distinct concept (design correction made
during implementation):**
Originally, tolerance appeared in three places: `EventTime.errorTolerance`,
`TaskTimeRange.taskTolerance` (on `preferredTimeRange`), and `TaskTimeRange.taskTolerance`
(on `allottedTimeRange`). Resolved as follows:
- `TaskTimeRange.taskTolerance` — **removed entirely, from both usages.** A `TaskTimeRange`
  already stores explicit `taskStartTime`/`taskEndTime` — that pair *is* the flexibility
  window. A separate scalar tolerance on top of an explicit range duplicates information the
  range already carries. (Tolerance-via-scalar is only needed when representing flexibility
  as anchor-point + margin instead of an explicit range — not the representation chosen here.)
- `EventTime.errorTolerance` — **kept, but must be renamed** (pending — not yet applied in
  code as of this writing) to something like `durationToleranceMinutes`, since this field is
  about a genuinely different axis: how much the task's *duration* can flex (e.g. nominally
  40 min, ±5 min acceptable), independent of *when* it's scheduled. Timing flexibility lives
  in the range; duration flexibility lives in this one field. Two distinct, non-overlapping
  concepts, not duplication.

**`preferredTimeRange` vs `allottedTimeRange`:** `preferredTimeRange` = what the user asked
for (input, nullable — not every task has an explicit ask). `allottedTimeRange` = what the
Scheduler actually placed (output/result). This distinction is why tolerance only makes
sense on the preferred/input side — the allotted range is already a resolved decision, there's
nothing left to be tolerant about once it's been placed.

`deadline` lives as `taskDeadline` inside `EventOccurrence`, not on `Task` directly —
consistent with grouping timing concerns together. `category` (`TaskCategoryEnum`, see
§3.6) is purely descriptive/analytical — does **not** influence scheduling or scoring;
`priority` is the only field that affects placement.

### 3.6 `TaskCategory` (enum)

Decision: **pre-defined enum, not user-extensible.** A fully user-extensible `Category`
entity was considered and deliberately deferred — nothing consumes `category` yet (not the
scheduler, not Analytics, which is Phase 3), so the extensible version would be scope pulled
forward for a consumer that can't test it yet. Swappable for an FK-based version later
without touching the Scheduler, since the Scheduler never reads this field.

Orthogonal to `EventTime.scheduleType` — category answers "what domain of life," scheduleType
answers "how rigid is the timing." E.g. college (FIXED) and DSA practice (FLEXIBLE) can both
be `ACADEMICS`.

```java
enum TaskCategory {
    ACADEMICS,        // college, classes, exams, assignments
    SKILL_BUILDING,   // DSA, coding practice, personal projects, learning new tech
    FITNESS,          // gym, sports, workouts
    HEALTH,           // meals, sleep-adjacent, medical, self-care
    SOCIAL,           // hangouts, calls, family time
    LEISURE,          // Netflix, gaming, Instagram, YouTube
    CHORES,           // errands, cleaning, admin/life-maintenance tasks
    CAREER,           // placement prep, interviews, resume work, networking
    PERSONAL_GROWTH   // hobbies (guitar, reading, journaling) — self-directed, not
                       // "skill for a job," not pure leisure either
}
```

Why some categories weren't merged: `ACADEMICS`/`SKILL_BUILDING`/`CAREER` look similar (all
"studying") but differ in purpose — obligation vs. self-directed growth vs. deadline-driven
placement prep; splitting lets Weekly Insights surface things a merged "Study" bucket
couldn't. `FITNESS` vs `HEALTH` — active/repeatable habit vs. passive-but-blocking
(meals/medical). `PERSONAL_GROWTH` vs `LEISURE` — self-improvement intent vs. intentionally
sacrificial downtime; lowest-cost one to fold into `LEISURE` later if 9 ever feels like one
too many.

### 3.4 `EventTime` (`@Embeddable`, nested inside `EventOccurrence`) — AS IMPLEMENTED
Holds only duration-related fields — timing/rigidity moved to `Task.movability`
(`FlexibilityEnum`) and to `TaskTimeRange` (see §3.3), not here as originally sketched.

- `taskDurationInMinutes` (int)
- duration tolerance field — pending rename from `errorTolerance` to something explicit like
  `durationToleranceMinutes` (see §3.3 tolerance discussion — not yet applied in code)

### 3.4a `TaskTimeRange` (`@Embeddable`, used twice inside `EventOccurrence`)
- `taskStartTime`, `taskEndTime` (`LocalTime`)
- No separate tolerance field — the `[start, end]` pair already **is** the tolerance/
  flexibility window; a scalar on top would duplicate it (see §3.3).

### 3.4b `Recurrence` (`@Embeddable`, nested inside `EventOccurrence`) — AS IMPLEMENTED

**Clarified relationship between `movability` and `preferredTimeRange` (resolved through
discussion):** these are two independent axes, not one implying the other.
- `preferredTimeRange` — a *wide window* the Scheduler should try hard to place the task
  within (not one exact instant). Nearly any task can carry one, regardless of `movability`.
- `movability` (`FlexibilityEnum`) — purely about **resistance to being moved during
  rescheduling**, not initial-placement flexibility: `FLEXIBLE` gives way first when
  something needs to shift, `ANCHORED` gives way only once `FLEXIBLE` options are exhausted,
  `FIXED` never moves, full stop. A `FLEXIBLE` task can absolutely have a `preferredTimeRange`
  — it's just a soft suggestion, first to be overridden when rescheduling needs room. An
  `ANCHORED` task's `preferredTimeRange` is a much stronger signal, respected until there's
  genuinely no other option.

- `recurrenceTypeEnum` — `RecurrenceTypeEnum`: `ONE_OFF` / `DAILY` / `WEEKLY` / `MONTHLY` /
  `ANNUALLY`
- `weeklyMode` — `WeeklyModeEnum`: `EXACT_DAYS` / `COUNT_ONLY` (explicit discriminator, not
  inferred from which field is null — same reasoning as `scheduleType`/`movability`: null
  must not have to mean two different things)
- `daysOfWeek` (`Set<DayOfWeek>`) — used when `weeklyMode = EXACT_DAYS`
- `timesPerWeek` (`Integer`) — used when `weeklyMode = COUNT_ONLY`, scheduler picks the days
- `dayOfMonth`, `monthOfYear` (`Integer`) — stubbed for `MONTHLY`/`ANNUALLY`; not fully
  designed yet, deliberately deferred since almost nothing in the actual use case (gym, DSA,
  meals, classes) needs them. `DAILY`/`WEEKLY`/`ONE_OFF` are the ones that matter for v1.
- `excludedDaysOfWeek` (`Set<DayOfWeek>`, nullable/empty = no exclusions) — **new field,
  added to solve a real gap:** a hard, categorical "never place this on these days" rule
  (e.g. "gym is closed on Sunday"). Distinct in kind from `daysOfWeek` (an inclusion
  whitelist, only meaningful in `EXACT_DAYS` mode) — this is a blacklist that applies
  regardless of `WeeklyMode`. Matters most for `COUNT_ONLY` mode (Scheduler is free to pick
  any day for "5x/week" and must skip excluded days) and for `DAILY` recurrence ("daily
  except Sunday" is a normal real case). Redundant-but-harmless under `EXACT_DAYS` (user
  already only listed allowed days). No-op under `ONE_OFF`. Candidate-generation logic in
  the Scheduler must filter these days out before checking `WeeklyTimeline.isFree`.

### 3.5 What got removed / renamed from the earlier design
- `Commitment` entity — merged into `Task` (`movability = FIXED`).
- `Goal` entity — merged into `Task` (`movability = ANCHORED` or `FLEXIBLE`).
- `SoftTimeBlock` / `RecurringSoftBlock` entity — merged into `Task` (meals/naps are just
  `ANCHORED` tasks with a title like "Lunch").
- `EventTime.scheduleType` (original plan) → became `Task.movability` (`FlexibilityEnum`),
  a field directly on `Task` rather than nested inside the embeddable. Values renamed
  `FLEXIBLE`/`ANCHORED`/`FIXED` — same three-way split, same reasoning: explicit
  discriminator so nullability of dependent fields is never ambiguous.
- `Profile.commuteDurationMinutes` → `EventOccurrence.commuteTimeInMinutes` (per-instance,
  not global — a 45-min commute to college and a 15-min commute to the gym can't share one
  number).
- `Profile.defaultBufferMinutes` → `EventOccurrence.bufferTimeInMinutes` (per-instance;
  deliberately not asked at onboarding — set per-task, naturally, rather than as an
  abstract upfront question).
- `EventTime.recurrence` (vague, original plan) → dedicated `Recurrence` embeddable (§3.4b),
  designed once the weekly count-vs-exact-days fork was spotted.

---

## 4. Module Ownership Boundaries (updated for the Task unification)

| Module | Owns | Does NOT own |
|---|---|---|
| Auth ✅ | Identity, tokens | Anything about the user's schedule |
| User/Profile | Wake/sleep window, morning/night pref | Anything time-instance-specific (moved to Task) |
| **Task** (was Goals + Commitments) | All schedulable items — fixed, anchored, or flexible, via `movability` + `EventOccurrence` | Placement/timing decisions — it's data, not logic |
| Import | Parsing PDFs/screenshots/Google Calendar → `Task` rows with `movability = FIXED` | Any scheduling logic |
| **Scheduler Engine** | Turns `Task` rows + `Profile` into a placed weekly schedule; owns rescheduling | Task data itself — it's a consumer |
| Calendar | Read-facing projection of the scheduler's output | Does not generate placements |
| Notifications | Reacting to scheduler events | No scheduling logic |
| Analytics | Historical record of planned-vs-actual, derived insights | Does not feed back into scheduling decisions yet (deferred — see §6) |
| AI Parser | NLU only: sentence → structured `Task`/`EventOccurrence` fields | Never touches placement logic directly — calls the same APIs any client would |

**Standing rule (unchanged):** AI Parser is a client of FlowOS's own APIs, never a special
backdoor into scheduling logic.

---

## 5. Build Order (phased, not versioned — full scope intended, just sequenced)

### Phase 1 — Minimal data backbone (IN PROGRESS)
1. `Profile` entity — done.
2. `Task` + nested embeddables (`EventOccurrence`, `EventTime`, `TaskTimeRange`,
   `Recurrence`) — in progress, see §3.3 for actual shape. Remaining cleanup: rename
   `EventTime.errorTolerance` → `durationToleranceMinutes`; fix `Task.even` field name typo
   (→ `eventOccurrence` or similar).
3. `ProfileRepo`, `TaskRepo` — standard Spring Data repos, same pattern as `UserRepo`.

No controller/service layer yet — no endpoint needs this until onboarding/task-creation flow
exists, and that's not blocking Phase 2. Just get entities + repos compiling and persisting.

### Phase 2 — Scheduler Engine (the real learning starts here)
1. **Naive placement** — place all `FIXED` tasks first (they define the skeleton), then
   greedily place `ANCHORED`/`FLEXIBLE` tasks by priority into remaining free time. No
   scoring, no candidates yet — goal is a working end-to-end pipeline.
2. **Constraint validation layer** — sleep protection (respect `Profile.wakeTime`/
   `sleepTime`), no double-booking, respecting `ANCHORED` preferred time range +
   `preferredTimeRange`, respecting `commuteTimeInMinutes` around `FIXED` tasks, respecting
   `bufferTimeInMinutes` between tasks.
3. **Multi-candidate + scoring** — generate several candidate schedules, score on: goal
   completion, sleep preservation, workload balance, context-switch minimization, preference
   alignment. Real algorithm-design discussion (greedy vs. backtracking vs. CSP vs. local
   search) happens when this phase is reached, not before.
4. **Rescheduling engine** — reacts to trigger types (see §7). Architecturally distinct from
   initial generation: partial re-optimization, not full regeneration.

### Phase 3 — Consumers of the scheduler's output
- `Calendar` (today view, week view)
- `Import` (can be built in parallel with Phase 2 — fully decoupled)
- `Notifications` (FCM wiring)
- `Analytics` (needs Phase 2's history to have anything real to analyze)

### Phase 4 — AI Parser
Deliberately last — least architecturally risky, translates into a stable target instead of
a moving one. Natural-language buffer/preference requests ("give me a 5 min break after
gym") get parsed here into the appropriate `EventOccurrence.bufferTimeInMinutes` /
`preferredTimeRange` fields — never handled as special-cased scheduling logic.

---

## 6. Engagement & Retention Strategy

FlowOS should be more than "correct" — it needs to become a daily habit, not just a tool
people abandon once the novelty wears off.

**Design philosophy:** favor intrinsic reinforcement (evidence the system works for you)
over heavy extrinsic gamification (streaks/points/badges). Heavy gamification decays and can
backfire (streak anxiety → users quit entirely after one break). Light-touch extrinsic
mechanics are fine; they shouldn't be the primary hook.

**Planned mechanisms (design detail deferred to Phase 3, Analytics):**
- Adherence tracking per-task, not one fragile global streak. Reschedules *caused by the
  system* must not break streaks.
- Reflective weekly insights — trend lines, not just totals (e.g. "adherence dropped when
  exams started — auto-lower priority during exam weeks?").
- "Time reclaimed" framing for early completions.
- Light positive reinforcement on completion (frontend/UI territory).
- Milestone moments, not daily grind counters.
- Adaptive pattern nudges (rule-based, not ML — see §8): "You've moved gym off 6am 4 times —
  try 6pm instead?"
- Parked for later, not now: social/accountability features (shared goals, leaderboards) —
  explicitly out of scope until the core loop is proven.

**Standing rule (applies starting Phase 2, cannot be retrofitted cheaply):**
> Every reschedule event must record a reason code (`USER_LATE`, `USER_SKIPPED`,
> `SYSTEM_CONFLICT`, `USER_LOCKED`, etc.) and every completion must record a timestamp.
> This is the one piece of data-model design that must happen now, even though the
> engagement features themselves are built much later.

---

## 6a. AI Parser vs. Scheduler — Where the Line Actually Sits (clarified through discussion)

This boundary came up repeatedly from different angles (priority assignment, time-of-day
phrases) — consolidated here as one clear reference instead of scattered across chat history.

**The single governing rule:** AI Parser turns fuzzy human language into structured `Task`/
`EventOccurrence` field values. It never decides placement, timing, or conflict resolution —
that's 100% the Scheduler's job, always, no exceptions. The test for "is this AI's job": does
this fill in a data field from words, or does this decide where/when something actually
happens / who wins a conflict? The former is fine for AI; the latter never is.

**Concrete cases resolved under this rule:**

- **Priority:** AI may infer `priority` from phrasing ("I really need to nail DSA" → higher
  priority) — this is extraction, identical in kind to inferring `category` from "gym." It is
  NOT the AI deciding which task wins a scheduling conflict; that's still entirely the
  Scheduler's job, using whatever priority value it's handed. Layered fallback so the user
  is never forced to set this themselves:
  1. **Default from `category`** — a simple static lookup (e.g. `ACADEMICS`/`CAREER` skew
     higher, `LEISURE` skews lower). Zero user effort, zero AI needed, covers most cases.
  2. **AI refines it from phrasing**, when parsing natural language, if wording carries a
     clear signal. Optional enhancement, not a requirement.
  3. **User can always override explicitly** — opt-in, never forced.
  Fixed/hardcoded default table for now (not per-user tunable) — same reasoning as deferring
  `Category`-as-entity and ML: don't add configurability before there's evidence a fixed
  default is wrong often enough to justify it.

- **Fuzzy time-of-day phrases ("gym in the evening"):** AI may convert this into a rough
  candidate window (e.g. `preferredTimeRange = 4pm–8pm`) — this is the same extraction move
  as converting "five days a week" into `Recurrence`. AI does **not** pick the exact slot
  within that window — the Scheduler looks at what's actually free inside 4–8pm, checks
  buffer/commute/competing tasks, and decides the real placement (e.g. 5:30–6:30). If nothing
  in that window is free, deciding what happens next (widen search, flag unplaceable, etc.)
  is also the Scheduler's call, never the AI's.

**Why this matters enough to write down:** it would be easy, while building the AI Parser in
Phase 4, to let it creep into "helpfully" resolving something that looks like simple
classification but is actually a scheduling call in disguise. The test above ("field-filling
vs. deciding placement/conflicts") is the check to run whenever that temptation shows up.

---

## 7. Rescheduling Trigger Taxonomy (for Phase 2.4)

- **Time-shift events:** overslept, running late, finished early.
- **Completion events:** task skipped, done early, done late.
- **Structural events:** new `FIXED` task added, one cancelled, new `ANCHORED`/`FLEXIBLE`
  task added.
- **Passive drift:** task not started by scheduled time but not explicitly skipped — needs a
  decision: wait, nudge, or auto-reschedule after a grace period?

Open design question (revisit at Phase 2.4): one general re-optimize function, or several
specialized fast-path handlers per trigger type?

---

## 8. Position on Machine Learning

**Decision:** No ML in the core Scheduler Engine — permanent architectural boundary.
Scheduling is a constraint-satisfaction/optimization problem, not a prediction problem.
Reasons: determinism (explainability), debuggability, zero cold-start data on day one.

| Feature | ML? |
|---|---|
| Core scheduling/placement | **No — never.** Algorithmic (CSP/greedy/local search), by design. |
| "You keep moving gym off 6am" nudges | No — frequency counting over reschedule reason codes. |
| Auto-lower priority during exam weeks | No — rule-based, triggered by deadline proximity. |
| Predicting real task duration vs. estimate | Borderline — start with a simple statistical average, not ML. |
| Learning implicit priority weights across many behavior signals | Yes, genuinely ML — but v-much-later, only after rule-based heuristics prove insufficient. |
| NLU (AI Parser) | Yes — already correctly delegated to Gemini API, not something to train. |

**Principle:** earn the right to use ML by first proving simple heuristics/rules aren't
enough — don't reach for it upfront.

---

## 9. Working Agreement (how Claude should behave across all chats on this project)

- Backend = user's learning area. Explain, hint, point out mistakes. **Backend `.java` files
  are never edited directly via filesystem tools — no exceptions, even if it seems faster.**
  Only markdown docs (`ROADMAP.md`, `PROJECT_CONTEXT.md`) may be written directly. All
  backend code changes are made by the user, after discussion.
- Frontend = Claude can edit directly via filesystem tools when needed.
- Scheduler Engine gets extra care: algorithm trade-offs, complexity discussion, multiple
  approaches — always before code.
- Before referencing or suggesting anything about existing code, read the actual files at
  `E:\computer science\Java\FlowOS` — never guess or assume structure.
- Push back on design choices when warranted. Don't rubber-stamp. Don't overengineer just to
  use more technology.
- Default mode is: help build, don't over-discuss. Design conversation happens when a real
  ambiguity is hit during implementation, not preemptively for every field. If the user says
  "let's just build," stop proposing new design tangents and support implementation instead.
- This is a learning + portfolio project, primarily the former. Understanding every decision
  matters more than shipping fast — but not at the cost of momentum once a decision is made.

---

## 10. Open Decisions Log

- [ ] `generateCandidate`'s current scope (buffer/commute/preferred-range/deadline handling,
      midnight- and week-boundary correctness) already does what §5 Phase 2 describes as
      step 2, "Constraint validation layer" — while step 1, "Naive placement," was meant to
      come first with no such validation. Does the Phase 2.1/2.2 boundary need redrawing to
      match how the code actually evolved, or should a genuinely-naive placement pass still
      be built first/separately, with `generateCandidate` folded in afterward as the 2.2
      layer? Revisit before writing whatever calls `generateCandidate` for the first time.
- [ ] One general re-optimize function vs. specialized fast-path handlers per reschedule
      trigger type? (Revisit at Phase 2.4)
- [ ] Grace period behavior for "passive drift" (task not started, not explicitly skipped)?
- [x] Rename `EventTime.errorTolerance` → `durationToleranceMinutes` — **done, verified in
      code** (this log just hadn't been checked off).
- [x] Fix `Task.even` field name typo (leftover from initial wiring) — **done, verified in
      code** (`Task.event`, correctly named; this log just hadn't been checked off).
- [ ] Add `TaskRepo` once `Task` entity is finalized.
- [x] `Task.category` — enum or free-text? → **Resolved: pre-defined `TaskCategoryEnum`,
      9 values, not user-extensible (see §3.6).**
- [x] Recurrence exact representation → **Resolved: dedicated `Recurrence` embeddable with
      `RecurrenceTypeEnum` + `WeeklyModeEnum` fork for weekly count-vs-exact-days (§3.4b).**
- [x] Leisure as separate entity vs. own type? → **Resolved: just a `Task` with low priority,
      no special entity.**
- [x] Meals/naps as separate `SoftTimeBlock` entity? → **Resolved: removed, unified into
      `Task` with `movability = ANCHORED`.**
- [x] Commute duration — global `Profile` field or per-instance? → **Resolved: per-instance,
      `EventOccurrence.commuteTimeInMinutes`.**
- [x] Buffer between tasks — global `Profile` default or per-task? → **Resolved: per-task,
      `EventOccurrence.bufferTimeInMinutes`, set explicitly or via NLP, never asked at
      onboarding.**
- [x] `Commitment`/`Goal` as separate entities? → **Resolved: unified into single `Task`
      entity; rigidity now lives in `Task.movability` (`FlexibilityEnum`), not nested in
      `EventTime` as first sketched.**
- [x] Tolerance appearing in 3 places (`EventTime.errorTolerance`,
      `TaskTimeRange.taskTolerance` ×2) → **Resolved: `TaskTimeRange.taskTolerance` removed
      entirely (the start/end range already is the tolerance window); only
      `EventTime`'s duration-tolerance field survives, pending rename (see above).**
- [x] Tolerance fields on `Task` itself vs. nested? → **Resolved: stays nested inside
      `EventOccurrence`/`EventTime` — flattening onto `Task` would lose the structured
      grouping and reintroduce ambiguity.**
