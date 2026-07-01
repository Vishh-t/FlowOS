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

**In progress:** `Profile` entity, unified `Task` entity + `EventTime` embeddable (see §3 —
this replaced the earlier separate `Commitment`/`Goal`/`SoftTimeBlock` design).

**Not started yet:** Import, Scheduler Engine, Calendar, Notifications, Analytics, AI Parser.
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

### 3.3 `Task` (replaces `Commitment` + `Goal` + `SoftTimeBlock`)
One entity for anything that occupies time: college, exams, meals, naps, gym, DSA, leisure.
Distinguished by `EventTime.scheduleType`, not by separate tables.

Fields (draft — refine while implementing, not gospel):
- `id`, `@ManyToOne User`
- `title` (String)
- `category` (enum or free-text — TBD while building; not load-bearing for the scheduler yet)
- `priority` (enum or int — used for scoring/conflict resolution)
- `splittable` (boolean — can a multi-hour duration be split into sessions?)
- `deadline` (nullable `LocalDate`/`LocalDateTime`)
- `travelTimeMinutes` (nullable int — replaces the old `Profile.commuteDurationMinutes`;
  lives per-Task since travel time genuinely varies by location, not globally)
- `bufferAfterMinutes` (nullable int — replaces the old `Profile.defaultBufferMinutes`;
  lives per-Task since a 5-min break after gym isn't the same as after a desk task. Set
  either explicitly by the user or parsed from natural language later via AI Parser, e.g.
  "give me a 5 min break after gym.")
- `eventTime` — `@Embedded EventTime`

### 3.4 `EventTime` (`@Embeddable`, not its own table — no independent identity/lifecycle)
- `scheduleType` — enum: `FIXED` / `ANCHORED` / `FLEXIBLE`
  - `FIXED` — immovable, exact start/end required (college, exams, meetings). Scheduler
    never moves these; they're placed first and everything else works around them.
  - `ANCHORED` — has a preferred time with some drift tolerance (lunch ~1pm ±30min, gym
    "around 6pm"). Requires `preferredTime` + `flexibilityMinutes` filled.
  - `FLEXIBLE` — no time preference, only duration + frequency (DSA "2hrs/day, whenever
    fits"). `preferredTime`/`flexibilityMinutes` intentionally null — null here means
    "flexible by design," not "data missing," because `scheduleType` makes that explicit.
- `startTime` / `endTime` (for `FIXED`)
- `preferredTime` / `flexibilityMinutes` (for `ANCHORED`)
- `durationMinutes`
- `daysOfWeek` (nullable set — null/empty = any day; otherwise restricts which days this
  applies)
- `recurrence` (daily / specific days / one-off — exact representation TBD while building)

**Why nullability is disambiguated by `scheduleType` and not left to mean one thing
everywhere:** without the explicit enum, a null `preferredTime` could mean either "flexible
on purpose" or "user hasn't set this yet," which is an unresolvable ambiguity once the UI
needs to distinguish "incomplete setup" from "valid flexible task." The enum makes intent
explicit.

**Known accepted trade-off:** a single `Task` table means some columns are only meaningful
for some `scheduleType` values (e.g. `splittable`/`priority` matter less for `FIXED`
entries; `startTime`/`endTime` don't apply to `FLEXIBLE` ones). This is a recognized pattern
("table sprawl via nullable columns") — accepted here because it keeps the Scheduler's
placement logic operating over one entity type instead of merging two, which is a bigger win
for this project than the schema purity cost. Revisit only if it actually becomes painful in
practice, not preemptively.

### 3.5 What got removed from the earlier design
- `Commitment` entity — merged into `Task` (`scheduleType = FIXED`).
- `Goal` entity — merged into `Task` (`scheduleType = ANCHORED` or `FLEXIBLE`).
- `SoftTimeBlock` / `RecurringSoftBlock` entity — merged into `Task` (meals/naps are just
  `ANCHORED` tasks with a title like "Lunch").
- `Profile.commuteDurationMinutes` — moved to `Task.travelTimeMinutes` (per-instance, not
  global — a 45-min commute to college and a 15-min commute to the gym can't share one
  number).
- `Profile.defaultBufferMinutes` — moved to `Task.bufferAfterMinutes` (per-instance; also
  deliberately not asked at onboarding — it's set per-task, naturally, rather than as an
  abstract upfront question that would frustrate onboarding UX).

---

## 4. Module Ownership Boundaries (updated for the Task unification)

| Module | Owns | Does NOT own |
|---|---|---|
| Auth ✅ | Identity, tokens | Anything about the user's schedule |
| User/Profile | Wake/sleep window, morning/night pref | Anything time-instance-specific (moved to Task) |
| **Task** (was Goals + Commitments) | All schedulable items — fixed, anchored, or flexible, via `EventTime` | Placement/timing decisions — it's data, not logic |
| Import | Parsing PDFs/screenshots/Google Calendar → `Task` rows with `scheduleType = FIXED` | Any scheduling logic |
| **Scheduler Engine** | Turns `Task` rows + `Profile` into a placed weekly schedule; owns rescheduling | Task data itself — it's a consumer |
| Calendar | Read-facing projection of the scheduler's output | Does not generate placements |
| Notifications | Reacting to scheduler events | No scheduling logic |
| Analytics | Historical record of planned-vs-actual, derived insights | Does not feed back into scheduling decisions yet (deferred — see §6) |
| AI Parser | NLU only: sentence → structured `Task`/`EventTime` fields | Never touches placement logic directly — calls the same APIs any client would |

**Standing rule (unchanged):** AI Parser is a client of FlowOS's own APIs, never a special
backdoor into scheduling logic.

---

## 5. Build Order (phased, not versioned — full scope intended, just sequenced)

### Phase 1 — Minimal data backbone (IN PROGRESS)
1. `Profile` entity — being built now.
2. `Task` + `EventTime` — being built now (replaces the earlier separate `Commitment`/`Goal`
   plan from §3).
3. `ProfileRepo`, `TaskRepo` — standard Spring Data repos, same pattern as `UserRepo`.

No controller/service layer yet — no endpoint needs this until onboarding/task-creation flow
exists, and that's not blocking Phase 2. Just get entities + repos compiling and persisting.

### Phase 2 — Scheduler Engine (the real learning starts here)
1. **Naive placement** — place all `FIXED` tasks first (they define the skeleton), then
   greedily place `ANCHORED`/`FLEXIBLE` tasks by priority into remaining free time. No
   scoring, no candidates yet — goal is a working end-to-end pipeline.
2. **Constraint validation layer** — sleep protection (respect `Profile.wakeTime`/
   `sleepTime`), no double-booking, respecting `ANCHORED` preferred time + flexibility
   window, respecting `travelTimeMinutes` around `FIXED` tasks, respecting
   `bufferAfterMinutes` between tasks.
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
gym") get parsed here into the appropriate `Task.bufferAfterMinutes` /
`EventTime.preferredTime` fields — never handled as special-cased scheduling logic.

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

- Backend = user's learning area. Explain, hint, point out mistakes. Never rewrite backend
  code unless explicitly asked. Backend is read-only by default.
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

- [ ] One general re-optimize function vs. specialized fast-path handlers per reschedule
      trigger type? (Revisit at Phase 2.4)
- [ ] Grace period behavior for "passive drift" (task not started, not explicitly skipped)?
- [ ] `Task.category` — enum or free-text? Not load-bearing for scheduler yet, decide while
      implementing.
- [ ] `EventTime.recurrence` exact representation (daily/specific days/one-off) — decide
      while implementing `Task`.
- [x] Leisure as separate entity vs. own type? → **Resolved: just a `Task` with low priority,
      no special entity.**
- [x] Meals/naps as separate `SoftTimeBlock` entity? → **Resolved: removed, unified into
      `Task` with `scheduleType = ANCHORED`.**
- [x] Commute duration — global `Profile` field or per-instance? → **Resolved: per-instance,
      moved to `Task.travelTimeMinutes`.**
- [x] Buffer between tasks — global `Profile` default or per-task? → **Resolved: per-task,
      `Task.bufferAfterMinutes`, set explicitly or via NLP, never asked at onboarding.**
- [x] `Commitment`/`Goal` as separate entities? → **Resolved: unified into single `Task`
      entity with `EventTime` embeddable distinguishing rigidity via `scheduleType`.**
