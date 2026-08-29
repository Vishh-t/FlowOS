# FlowOS — Roadmap & Source of Truth

> This file is the persistent project context. Any conversation (this one or a future one)
> should read this file before making architectural suggestions. Update it as decisions
> are made — don't let decisions live only inside a chat transcript.

Last updated: 2026-08-27

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
- `Task` entity + nested embeddables (`EventOccurrence` → `EventTime`, `TaskTimeRange` ×2,
  `Recurrence`) — see §3.3 for the actual current shape, which evolved significantly from
  the original flat sketch. `Profile` entity is done. `TaskRepo` exists.

**Scheduler Engine — first full end-to-end placement slice complete and tested
(2026-08-23):** what exists now takes a list of tasks and a profile and returns actual
placed weekly slots, honoring priority, recurrence, and constrained-vs-flexible timing —
the first real "input tasks, get a placed week" flow, even though it's still naive
placement (no scoring, no rescheduling yet). Layers, bottom to top:

- **`generateCandidate`** — finds one valid slot on one specific target day (a signature
  change from the original "free-scan across all 7 days" design — day selection was moved
  out to the caller once multi-occurrence recurring tasks exposed a real bug, see below).
  Handles: midnight-crossing wake/sleep windows and padding (`shift()` helper tracks
  day-rollover explicitly instead of relying on bare `LocalTime` wrap), the week-boundary
  wraparound (Sunday→Monday, fixed by tracking day position as a raw non-wrapping offset
  until the final `TimeAndDayRange` is built), and `taskDeadline` as an upper bound on the
  search (recomputed per candidate day; days where the deadline's occurrence has already
  passed are skipped outright).
- **`RecurrenceInterpreters.resolveTargetDays`** — translates a task's `Recurrence` (type +
  weekly mode + day-set/count + exclusions) into a concrete `List<DayOfWeek>` for the caller
  to loop over. `EXACT_DAYS` returns the explicit day-set (minus exclusions). `COUNT_ONLY`
  calls `pickSpreadDays`, which spreads occurrences across distinct available days first and
  only wraps around to double up a day once every available day already has one occurrence
  (`i % availableDays.size()`) — correctly handles `timesPerWeek` exceeding 7 (e.g. 2x/day
  patterns), which an earlier naive-cap version would have silently dropped.
- **`SchedulerService.placeTask`** — orchestrates one task's full placement: resolves target
  days, calls `generateCandidate` once per day, occupies the shared `WeeklyTimeline` on
  success, and returns a `PlacementResult(requested, placed, placedSlots, failedDays)`.
  Deliberate product decision: **partial success is not rolled back** — whatever occurrences
  place, stay placed; `failedDays` reports the rest so the caller/UI can surface it, rather
  than an all-or-nothing failure. Matches the adherence-tracking philosophy in §6b (a task
  that can't fully place is itself a signal, not just an error).
- **`PriorityInterpreter.sortForPlacement`** — decides placement order across multiple tasks:
  primary key `TaskPriorityEnum.ordinal()` descending (CRITICAL first), secondary key
  "has a `preferredTimeRange`" before "no range" within the same priority tier — a
  most-constrained-first heuristic so a flexible task doesn't accidentally claim the one slot
  a constrained task actually needed.
- **`SchedulerService.placeAll`** — the top-level entry point: sorts the task list via
  `PriorityInterpreter`, then loops `placeTask` over one shared `WeeklyTimeline` so every
  task sees prior placements as occupied. Returns `Map<Task, PlacementResult>` (not a
  parallel list) for direct per-task lookup by the caller. Required scoping `Task`'s
  `equals`/`hashCode` to `taskId` only (`@EqualsAndHashCode(of = "taskId")`) — Lombok's
  default all-fields `@Data` equality was unsafe as a map key since `event` mutates after
  insertion.

**Verification:** both layers were dry-run tested via standalone compiled sandboxes (not
just read-through) — `generateCandidate` against three bug-repro scenarios (see git history
for the day-1 bugs), and this slice against three integration scenarios: same-priority
no-overlap (baseline), CRITICAL vs LOW competing for an identical slot (confirmed sort order,
not input order, decides the winner), and same-priority ranged-vs-plain (confirmed
most-constrained-first tie-breaking). All passed as designed.

**Known gaps, not yet addressed:**
- No lower bound preventing a candidate from being placed before `now` — only the deadline
  (upper bound) is enforced. Deferred: irrelevant for "regenerate whole week fresh" (the
  current use case), only matters once mid-week rescheduling (§7) reuses `generateCandidate`.
- `WeeklyTimeline` is architecturally a single recurring week (no "which week" concept) —
  a deadline pushing the search past 7 days out is clamped to avoid silently aliasing onto
  the wrong day, but multi-week scheduling isn't representable yet.
- `Recurrence.dayOfMonth`/`monthOfYear` still unread — `MONTHLY`/`ANNUALLY` recurrence
  throws `UnsupportedOperationException` in `resolveTargetDays`, as originally scoped for
  later.
- `FlexibilityEnum` (`FIXED`/`ANCHORED`/`FLEXIBLE`) is stored but not yet read anywhere —
  confirmed via discussion that it governs rescheduling behavior (what's allowed to move
  when a trigger fires), not initial placement, so this is correctly inert until §7 is built,
  not a bug.
- No constraint-validation/scoring/multi-candidate layer beyond first-fit — `generateCandidate`
  returns the first valid slot found, not the best of several candidates. This is still
  "naive placement," consistent with where Phase 2 currently stands.
- Where this work sits against the Phase 2.1/2.2 split is now more resolved in practice
  (`generateCandidate` + `placeTask`/`placeAll` together cover both "naive placement" and
  "constraint validation" as originally scoped) — worth a final explicit decision to close
  out §10's open item.

**Not started yet:** Import, Calendar, Notifications, Analytics, AI Parser, and the rest of
the Scheduler Engine (naive full-placement pipeline, constraint validation layer beyond what
`generateCandidate` already does, multi-candidate scoring, rescheduling).
No React Native frontend exists yet — backend-only repo so far.

**Known minor issues flagged, not yet fixed:** see `ISSUES_LOG.md` ("Minor, flagged but not
yet fixed" section) for the full list (JWT expiry hardcoding, `JwtFilter` exception
swallowing, etc.) — kept out of this file now that a dedicated bug log exists.

---

## 2a. Session Update (2026-08-25 → 2026-08-27) — Persistence hardening, REST plumbing, and real testing

This closes out most of Phase 2.3 (§5) and resolves several of the open items logged below.
Covers three distinct chunks of work, in order.

### 2a.1 Persistence hardening (2026-08-25)
- **Enum persistence fixed.** All 8 enum fields across `Task`, `EventOccurrence`,
  `Recurrence`, `Profile` were on default `EnumType.ORDINAL` (stores array index — silently
  corrupts data on reorder). Fixed via 8 dedicated `AttributeConverter<Enum, String>` classes
  (`@Converter(autoApply = true)`), each mapping to a stable string code independent of the
  Java constant's name or position — survives both reordering and renaming, not just
  reordering (which `EnumType.STRING` alone would have covered).
- **`Set<DayOfWeek>` persistence fixed.** `Recurrence.daysOfWeek`/`excludedDaysOfWeek` had no
  mapping strategy at all (bare collection fields inside a nested `@Embeddable` aren't
  mapped by JPA without `@ElementCollection` or a converter). Resolved via one
  `AttributeConverter<Set<DayOfWeek>, String>` (comma-joined names), `autoApply = true`
  binds it to both fields automatically since they share the exact same generic type.
  Deliberately chose converter-to-single-column over `@ElementCollection`
  (separate join table) — matches the actual read pattern (`RecurrenceInterpreters` always
  loads the whole set into Java, nothing queries "which recurrences include Monday" at the
  SQL level) and avoids double-nested-embeddable join-table complexity. `null` vs. empty set
  preserved as distinct states (unset vs. deliberately-empty), not collapsed.
- **`SchedulerService`, `RecurrenceInterpreters`, `PriorityInterpreter`** — wired into Spring
  DI properly (`@Service`/`@Component` + constructor injection), replacing bare `new X()`
  field initializers from the original dry-run-only version.
- Working agreement (§9) was temporarily relaxed for this one session to let Claude write
  these files directly, then explicitly re-scoped back to the standing restriction
  afterward — see the history note already in §9.

### 2a.2 New entity: `TaskInstance` — the placement-storage gap, resolved
**The gap:** `SchedulerService.placeAll` returns `Map<Task, PlacementResult>`, and
`PlacementResult.placedSlots` is a `List<TimeAndDayRange>` — one task can have multiple
distinct placed occurrences (e.g. gym on Mon/Wed/Fri, independently searched, not guaranteed
to land at the same time). But `EventOccurrence.allottedTimeRange` was a single field —
nowhere to store more than one placement per task.

**Resolution:** new entity `TaskInstance` (`Task/Entity/TaskInstance.java`) — one row per
placed occurrence: own `@Id UUID` (own identity, deliberately not `@Embeddable`, since it
needs independent rows — first attempt wrongly used `@Embeddable` + `taskId` as `@Id`, which
would have capped one task to exactly one occurrence, defeating the purpose), `@ManyToOne
Task task`, `occurrenceDay` (`DayOfWeek`), `time` (`@Embedded TaskTimeRange`), `status`
(`TaskStatusEnum`), `timeOfCompletion` (nullable `LocalDateTime`, unset until marked done).
Also satisfies §6's standing per-occurrence completion-timestamp rule for free, since it was
built with that field from the start.

**Consequence for `Task`:** `EventOccurrence.status` and `EventOccurrence.allottedTimeRange`
removed entirely — both were single-value fields trying to represent what's now correctly
per-occurrence data on `TaskInstance`. `Task` stays the template/rule ("gym, 3x/week,
prefers evenings"); `TaskInstance` is each concrete instance that rule produced
("this week's Wednesday instance, 6–7pm, not yet done"). Verified no other code referenced
the removed fields before deletion (`TaskCreationHelpers` only had a stale comment
mentioning them, no live usage).

**Repo:** `TaskInstanceRepo` (standard `JpaRepository<TaskInstance, UUID>`), plus
`deleteAllByTask(Task task)` (Spring Data derived query) for the overwrite-on-regenerate step.

### 2a.3 New service: `ScheduleGenerationService` — the orchestration layer
Nothing previously called `placeAll` outside a dry-run sandbox. New class
`Scheduler/Service/ScheduleGenerationService.java` — the "impure shell" around
`SchedulerService`'s pure computation core (pure-core/imperative-shell split, deliberate):
fetches the user's tasks (`TaskRepo`) and profile (`ProfileRepo`), calls
`schedulerService.placeAll(...)`, then per task: `deleteAllByTask` (overwrite, not merge —
see resolved open item below) + saves new `TaskInstance` rows from `placedSlots`. Wrapped in
`@Transactional` (multi-task delete+insert must commit or roll back as one unit). Takes
`LocalDateTime now` as a parameter rather than calling `LocalDateTime.now()` internally —
standard clock-injection pattern, keeps the only real "now" call at the controller boundary
and keeps this method testable with a fixed fake time later.

Conversion of `TaskInstance` → API-facing shape lives in a separate static helper
(`Task/Helper/ScheduleGeneratorHelperMethods.fromEntity`) — deliberately in the `Task`
module, not `Scheduler`, since it's pure data-shaping with zero scheduling logic, reusable by
any future consumer (e.g. Calendar's "what's today" query) independent of a generation run.

### 2a.4 REST endpoints — Phase 2.3's open item, resolved
- `TaskController` (`/tasks`) — full CRUD wired to the already-complete `TaskService`:
  `POST /createTask`, `GET /getAllTasks`, `GET /{taskId}/getTask`, `PUT
  /{taskId}/updateTask`, `DELETE /{taskId}/deleteTask`. All take
  `@AuthenticationPrincipal User user` (works directly since `JwtFilter` puts the full `User`
  entity as the security principal).
- `ProfileController` (`/profile`) — `POST /create`, minimal but necessary: onboarding had no
  way to create a `Profile` at all, which blocked testing `/schedule/generate` entirely
  (`ScheduleGenerationService` throws if none exists). Known gap, not fixed: calling this
  twice silently overwrites — fine for now, needs a real decision once onboarding UX exists.
- `SchedulerController` (`/schedule`) — `POST /generate`, no request body (reads the
  authenticated user's tasks server-side rather than accepting a client-supplied list —
  the DB is the source of truth, not a second copy the client has to keep in sync). Response
  is `Map<DayOfWeek, List<TaskInstanceResponseDTO>>` — grouped by day, not a flat list,
  matching the Home Screen's day-first "today's timeline" requirement from
  `PROJECT_CONTEXT.md` directly. Built via `Collectors.groupingBy(occurrenceDay,
  Collectors.mapping(fromEntity, toList()))`.
- **Resolves the REST endpoint contract open item below** (raw task list vs. `TaskRepo`: read
  from `TaskRepo`, server-side, always) **and the save-semantics open item** (overwrite
  wholesale on every regenerate — `deleteAllByTask` before inserting; merge semantics
  explicitly deferred until mid-week rescheduling (§7) needs to distinguish "freshly
  generated" from "already placed, don't touch").

### 2a.5 Request validation added (Bean Validation, `jakarta.validation`)
`CreateTaskDTO`/`RecurrenceDTO`/`TimeRangeDTO` now validated via `@Valid` +
`jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Positive`, `@PositiveOrZero`),
caught by a new handler in `GlobalExceptionHandler` for `MethodArgumentNotValidException` →
clean `400` with field-level messages, instead of an unhandled 500.

**Conditional-validation problem, hit and fixed three times over during testing — worth
understanding as one pattern, not three separate bugs:** `RecurrenceDTO`'s fields depend on
each other (`daysOfWeek` only matters if `weeklyMode = EXACT_DAYS`; `timesPerWeek` only if
`COUNT_ONLY`; `weeklyMode` only matters at all if `recurrenceTypeEnum = WEEKLY`). A blanket
`@NotNull` on all of them rejects legitimate requests (e.g. a `DAILY` task correctly omitting
`weeklyMode` entirely). Fixed with three `@AssertTrue`-annotated methods on `RecurrenceDTO`,
each checking one conditional relationship — the standard Bean Validation pattern for
cross-field rules that a single field-level annotation can't express. All three fields
dropped their unconditional `@NotNull` in favor of this.

### 2a.6 Bugs found via actual Postman testing (not just review)
See `ISSUES_LOG.md` for full detail (root cause, fix, verification status) on every bug
found this session, including the ones surfaced here. Full narrative moved out of this file
deliberately — `ROADMAP.md` stays forward-looking; `ISSUES_LOG.md` is the backward-looking
bug record. As of 2026-08-28: all bugs found in this testing round are resolved except the
blocked-`preferredTimeRange` search-width question, still open (see §10 and `ISSUES_LOG.md`).

---

## 2b. Bug Fixes for §2a.6 Issues — see `ISSUES_LOG.md`

Full narrative (proposed fix, rationale, rejected alternatives, verification) for each of the
three bugs surfaced in §2a.6 now lives entirely in `ISSUES_LOG.md`, not here — this section
kept only as a status pointer so §5/§10 references still resolve to *something* in this file.

- `pickSpreadDays` doesn't spread → **Resolved 2026-08-28.** See `ISSUES_LOG.md`.
- `COUNT_ONLY` fallback-on-failure ("reserved days") → **Resolved**, implemented alongside
  the above. See `ISSUES_LOG.md`.
- `failedDays` never reaches the API response → **Resolved 2026-08-28.** See `ISSUES_LOG.md`.
- Blocked-`preferredTimeRange` search-width fallback → **Still open**, two-phase search
  proposed but not yet implemented. See `ISSUES_LOG.md` for the full design discussion
  (including the rejected "just always search the full window" alternative).

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
1. **Naive placement + constraint validation — DONE (2026-08-23).** `generateCandidate` +
   `resolveTargetDays` + `placeTask` + `sortForPlacement` + `placeAll` together cover what
   this step and step 2 below originally described separately — see §2 for the full
   breakdown and §10 for the resolved 2.1/2.2 boundary question.
2. ~~Constraint validation layer~~ — folded into step 1 above, see §10.
3. **Multi-candidate + scoring — NOT STARTED, next up.** Concrete decisions made in
   discussion, recorded here so they're not re-litigated:
   - **Multi-start greedy first, not backtracking.** Run the existing `placeAll` several
     times with different task orderings (current priority+constraint order; also
     shortest-duration-first; also earliest-deadline-first), producing several distinct full
     `WeeklyTimeline`s instead of one. Each run reuses the current greedy algorithm as-is —
     no undo/backtracking logic needed. Score each resulting timeline, keep the best.
   - **Real backtracking/CSP search is explicitly deferred, not built preemptively.** It
     solves a real failure mode of greedy (a placement choice that's individually valid can
     make a later task unplaceable, when a different valid choice wouldn't have) — but that
     failure mode hasn't been observed yet, and one person's weekly task count is small
     enough that multi-start greedy will likely cover it. Only escalate to backtracking with
     concrete evidence multi-start greedy fails to find arrangements that provably exist.
   - **Scoring function** — hand-written weighted objective (not ML — see §8), over: goal
     completion (`PlacementResult.placed / requested`, aggregated across tasks), sleep
     preservation, workload balance, minimal fragmentation/context-switching — per the
     original §5 factor list. Pick the highest-scoring valid candidate.
4. **Rescheduling engine** — reacts to trigger types (see §7). Architecturally distinct from
   initial generation: partial re-optimization, not full regeneration. This is the first
   point where `FlexibilityEnum` (`FIXED`/`ANCHORED`/`FLEXIBLE`) actually gets read —
   confirmed through discussion it governs *resistance to being moved during rescheduling*,
   not initial-placement flexibility, so it's correctly unused until this phase.

### Phase 2.3 — Make the scheduler reachable (plumbing, before more algorithm work)
Realized during review: nothing outside a dry-run test can currently invoke `placeAll` —
this is the actual next concrete action, ahead of scoring/multi-start work, since it's what
turns tested-but-inert logic into something a frontend or API client can use.
1. **REST endpoint(s)** — e.g. `POST /schedule/generate`, taking a task list (or reading via
   `TaskRepo` for the logged-in user) + `Profile`, returning the `Map<Task, PlacementResult>`
   (or a DTO projection of it).
2. **Persist the result** — currently `placeAll` computes placements in memory only; nothing
   writes back to `EventOccurrence.allottedTimeRange` or saves via `TaskRepo`. Needs a
   decision on save semantics (e.g. does regenerating overwrite prior placements wholesale,
   or merge?) — open, see §10.

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

## 6b. Plan Update — Adherence-Aware Scheduling (added 2026-08-22)

**Why:** Core insight surfaced during a planning discussion — the primary reason people
abandon timetables isn't disruption (which the Scheduler already handles via
rescheduling), it's that plans are unrealistic from the start and nobody course-corrects
when they keep failing. Dynamic rescheduling alone doesn't fix this; it repairs a broken
day but doesn't stop the underlying plan from being wrong week after week.

**What changes:** FlowOS's scope is unchanged architecturally — this is an addition to the
existing plan, not a pivot. The Scheduler Engine (candidate generation, constraint
validation, scoring, rescheduling — §5 Phase 2) stays exactly as scoped. What's added is a
feedback layer on top:

1. **Adherence tracking as a first-class signal** — already logged as a standing rule in §6
   ("every reschedule event must record a reason code, every completion must record a
   timestamp") but wasn't yet tied to anything actionable. Now: this data should feed back
   into future candidate generation/scoring, not just sit in Analytics as a historical
   record.
2. **Realistic-goal correction** — when a task/goal type shows a persistent low completion
   rate, the system should surface that and suggest adjusting the goal (frequency,
   duration, or timing) rather than continuing to generate plans the user has demonstrably
   not been following. Rule-based (frequency counting over reason codes), not ML —
   consistent with the existing §8 position.
3. **Product framing update** — FlowOS is not just "a scheduler that repairs itself when
   disrupted," it's "a scheduler that repairs itself when disrupted and learns what you'll
   actually do, so it stops generating plans you were never going to follow." Same engine,
   sharper thesis.

**Architectural impact:** Minimal at this stage. No new modules — this slots into existing
Phase 2 (scoring can eventually weight by adherence history) and Phase 3 Analytics (which
already owns "historical record of planned-vs-actual"). Only concrete near-term addition:
make sure `Task` completion status + reschedule reason codes are captured from the start
(already planned per §6), since this is the one piece that's expensive to retrofit later.

**Sequencing:** Does not change the current build order. Still: close Phase 1 → naive
placement/orchestration → constraint hardening → adherence data model → scoring →
rescheduling. Adherence-awareness in scoring itself is a Phase 2.3+/Phase 3 concern, not
something to build now.

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
  **History note (2026-08-25):** this rule was temporarily relaxed for one session (enum
  `EnumType.STRING` conversion + `AttributeConverter` boilerplate for enums and
  `Set<DayOfWeek>`) after explicit, repeated user insistence under end-of-night fatigue.
  The user explicitly re-scoped it back down to that one session only on 2026-08-25 and
  confirmed the original restriction stands going forward. Treat any future request to
  relax this again as a fresh decision requiring the same explicit discussion — not as
  precedent set by this one-off.
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

- [ ] Adherence-aware scoring (§6b) — data model for surfacing "realistic-goal correction"
      suggestions to the user not yet designed; revisit once Phase 2 scoring (§5 Phase 2
      step 3) is reached.
- [x] `generateCandidate`'s scope vs. the Phase 2.1/2.2 split → **Resolved (2026-08-23):**
      `generateCandidate` + `resolveTargetDays` + `placeTask` + `sortForPlacement` +
      `placeAll` together cover both "naive placement" and "constraint validation" as
      originally scoped as two separate steps — no redraw needed, the code just ended up
      doing both in one integrated slice. See §2 and §5 Phase 2 step 1.
- [ ] REST endpoint contract for `placeAll` (§5 Phase 2.3) — request/response shape not yet
      decided (raw task list vs. reading from `TaskRepo`; full `PlacementResult` map vs. a
      simplified DTO).
- [ ] Save semantics for placement results (§5 Phase 2.3) — does regenerating a week
      overwrite prior `allottedTimeRange` values wholesale, or merge/preserve unaffected
      tasks? Matters once rescheduling (§7) needs to distinguish "freshly generated" from
      "already placed, don't touch."
- [ ] Backtracking/CSP escalation trigger (§5 Phase 2 step 3) — deferred until multi-start
      greedy is built and tested; revisit only with concrete evidence of unfindable valid
      arrangements, not preemptively.
- [x] `pickSpreadDays` doesn't actually spread (§2a.6) → **Resolved (2026-08-28):** fixed and
      verified via Postman re-test, see §2b.1. Even-distribution formula
      (`(i * availableDays.size()) / timesPerWeek`) replaces the old `i % size` clustering bug.
- [x] `failedDays` never reaches the API response (§2a.6) → **Resolved (2026-08-28):** fixed
      and verified via Postman re-test, see §2b.3. New `ScheduleGenerationResult`/
      `ScheduleGenerationResponseDTO`/`FailedOccurrenceDTO` carry `failedDays` through to a
      `failed` array in the `/schedule/generate` response, alongside the existing `schedule`.
- [ ] Flexible-task search-width design question (§2a.6) — when a `FLEXIBLE` task's
      `preferredTimeRange` is fully blocked by a competing task, the search currently just
      fails that day rather than widening to the full wake-sleep window. `movability` doesn't
      currently affect search width at all — only rescheduling resistance (§3.4b). Needs a
      deliberate decision on whether/how flexibility should widen initial-placement search.
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
