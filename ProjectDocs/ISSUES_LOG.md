# FlowOS — Issues & Fixes Log

> Personal reference only — not tracked in git (see `.gitignore`). This is the "what broke
> and how it got fixed" record. `ROADMAP.md` stays forward-looking (architecture, planning,
> open design decisions) — this file is backward-looking (concrete bugs found, root cause,
> fix, verification). If a bug is still open, it's logged here too, with `[OPEN]`.

Last updated: 2026-08-28

---

## How to read this file

Each entry: what broke → root cause → fix → how it was verified (dry run / Postman / neither
yet). Entries are grouped roughly by when they surfaced, oldest first.

---

## Scheduler core (`generateCandidate`, day-selection)

### [RESOLVED] Midnight-crossing wake/sleep window bug
**What broke:** wake/sleep windows that cross midnight (e.g. a night-owl profile) weren't
handled — bare `LocalTime` arithmetic wraps silently instead of tracking day rollover.
**Fix:** `shift()` helper returns a `ShiftedTime(time, dayOffset)` record, making day-rollover
explicit everywhere it matters instead of relying on `LocalTime` wraparound.
**Verified:** dry-run sandbox, three bug-repro scenarios.

### [RESOLVED] Week-boundary wraparound bug (Sunday → Monday)
**What broke:** same class of bug one level up — day position wrapping incorrectly at the
week boundary.
**Fix:** track day position as a raw non-wrapping integer offset internally; only convert to
`DayOfWeek` at the very end when building the final `TimeAndDayRange`.
**Verified:** dry-run sandbox.

### [RESOLVED] `taskDeadline` not read by the scheduler
**What broke:** deadlines existed as a field but weren't enforced as a search upper bound.
**Fix:** deadline recomputed per candidate day (distance to deadline differs per day); days
where the deadline's occurrence has already passed are skipped outright. Single-week clamp
(`maxSearchDayOffset = startDayOffset + 6`) added so a deadline further out doesn't alias
onto the wrong day (multi-week scheduling still isn't representable — see ROADMAP §2 known
gaps, this is a mitigation not a full fix).
**Verified:** dry-run sandbox.

### [RESOLVED] `generateCandidate` couldn't correctly handle multi-occurrence recurring tasks
**What broke:** original design had `generateCandidate` free-scanning all 7 `DayOfWeek.values()`
internally and returning the first free slot. Calling it multiple times for a task needing
several occurrences (e.g. gym 3x/week) had no way to steer each call toward a *different*
day — all calls would converge on the same day repeatedly since nothing tracked "already used
this day for this task."
**Fix:** signature change — `generateCandidate` now takes a required `targetDay` and searches
only that one day. Day-selection logic moved entirely to the caller
(`RecurrenceInterpreters.resolveTargetDays` + `SchedulerService.placeTask`'s loop).
**Verified:** dry-run sandbox, confirmed via the day-distribution check in scenario 2.

### [RESOLVED] NPE in `getCandidateResult` for non-`WEEKLY` tasks
**What broke:** `resultDto.getTaskRecurrence().getWeeklyMode().equals(WeeklyModeEnum.EXACT_DAYS)`
called unconditionally, no null-check — `weeklyMode` is legitimately `null` for non-`WEEKLY`
recurrence types. Only surfaced once request validation (see below) stopped blocking such
requests from reaching this code path.
**Root cause, and why it wasn't just null-guarded:** this was duplicated, stale validation —
`RecurrenceInterpreters.resolveTargetDays` already filters valid days per recurrence type
*before* `getCandidateResult` runs, so `day` arriving here is already guaranteed valid. The
`dayExcluded`/`notInExactDays` re-check was redundant, not just unsafe.
**Fix:** deleted the redundant check entirely rather than null-guarding it — collapses
day-validity down to one source of truth (`resolveTargetDays`) instead of two that can drift
out of sync (which is exactly what had happened).
**Verified:** Postman testing (surfaced during real testing, not caught by review).

---

## Recurrence / day-spreading

### [RESOLVED] `pickSpreadDays` — old cap silently dropped occurrences past 7/week
**What broke (early version):** `count = availableDays.size()` clamp meant a `timesPerWeek`
> 7 request (e.g. "2x/day") silently capped at 7, losing requested occurrences with no signal.
**Fix:** replaced the cap with `index = i % availableDays.size()` (round-robin wraparound) —
correctly handles `timesPerWeek` > 7 without dropping anything.
**Verified:** dry-run sandbox.

### [RESOLVED] `pickSpreadDays` didn't actually spread (the wraparound fix above had its own bug)
**What broke:** `i % availableDays.size()` for `i = 0..timesPerWeek-1`, when `timesPerWeek <=
7` (the common case — "3x/week"), always evaluates to `0, 1, 2, ...` — literally the first N
days in `DayOfWeek.values()` order (Mon/Tue/Wed), never an actual spread. Confirmed via real
Postman test: "Reading" (`COUNT_ONLY`, 3x/week) landed Mon/Tue/Wed instead of spread across
the week.
**Fix:** `index = (i * availableDays.size()) / timesPerWeek` — even distribution across the
full range instead of sequential indices. 3x/week over 7 days now gives Mon(0)/Wed(≈2)/Fri(≈4).
**Verified:** Postman re-test — Reading now lands Mon/Wed/Fri. Confirmed no regression to
`EXACT_DAYS` (Gym) or `DAILY` (DSA Practice) tasks, which don't go through this code path.

### [RESOLVED] `COUNT_ONLY` tasks had no fallback when a chosen day was fully booked
**What broke:** `resolveTargetDays` picks specific days upfront; if placement failed on one of
those days (occupied), `placeTask` just recorded it as failed and moved to the next
*pre-chosen* day — never tried a different, genuinely-free day, even if one existed.
**Fix:** added a fallback mechanism in `placeTask`, scoped to `COUNT_ONLY` only (deliberately
excludes `EXACT_DAYS` — user explicitly chose those days, falling back would silently violate
that choice; excludes `DAILY`/`ONE_OFF` — no fallback concept applies). `claimedDays` (seeded
with all of the task's own target days) prevents one occurrence's fallback from stealing a day
already earmarked for a sibling occurrence of the same task. On failure, tries the next
unclaimed day from `RecurrenceInterpreters.getAvailableDays(recurrence)` until placed or the
pool is exhausted.
**Rejected alternative:** random day selection for the fallback (proposed, discussed,
declined — breaks determinism/explainability per ROADMAP §8, and duplicates what multi-start
greedy is meant to own via varied task *ordering*, not in-task-day randomness).
**Verified:** confirmed present and correctly scoped by direct code read. Not yet stress-tested
against a real forced-collision scenario via Postman — worth doing once Issue 3 (below) is
also in.

---

## API layer — `failedDays` visibility

### [RESOLVED] `failedDays` computed correctly, never reached the API response
**What broke:** `PlacementResult.failedDays` was correct internally, but
`ScheduleGenerationService.generateSchedule` only ever read `result.placedSlots()` in its save
loop and returned a bare `List<TaskInstance>` — no structural room to carry failure info even
if the controller wanted it. Confirmed concretely: a `DAILY` task (DSA Practice) blocked by a
competing `FIXED` task (College) failed silently on 3/7 days — no error, just absent from
those days' output, invisible unless you counted entries.
**Fix:** three additions — `FailedOccurrenceDTO(taskName, day)`, `ScheduleGenerationResult`
record (carries both `placedInstances` and `failedOccurrences`), `ScheduleGenerationResponseDTO`
(typed response wrapper with `schedule` + `failed` keys, chosen over a quick `Map<String,
Object>` for consistency with the rest of the codebase's typed-DTO convention).
`generateSchedule` gained one extra inner loop over `result.failedDays()` — no change to
placement logic itself, purely additive.
**Verified:** Postman re-test — response now returns both `schedule` (unchanged, correct) and
a populated `failed` array listing exactly `[DSA Practice/MONDAY, DSA Practice/WEDNESDAY,
DSA Practice/FRIDAY]`.

---

## Search-width design (open)

### [OPEN] Blocked `preferredTimeRange` fails outright instead of widening the search
**What's happening:** when a task's `preferredTimeRange` is fully occupied by a competing
task, `generateCandidate` just fails that occurrence — it never falls back to searching the
full wake-sleep window. Confirmed via the DSA-vs-College Postman test (now visible thanks to
the `failedDays` fix above, previously invisible).
**Note on a wrong turn during discussion:** this was initially miscast as a `movability`/
`FlexibilityEnum` issue — corrected: `movability` is exclusively about resistance to being
moved *during rescheduling* (ROADMAP §3.4b), completely unrelated to initial-placement search
width. Don't re-litigate that; it's settled.
**Proposed fix (agreed in discussion, not yet implemented):** two-phase search — try
`preferredTimeRange` first (unchanged, preserves today's correct behavior when the preferred
window genuinely is free), only widen to the full wake-sleep window if that fully fails.
Rejected alternative: removing the preferred-window constraint entirely and always searching
the full window — `generateCandidate` is first-fit, not best-fit, so this would risk placing
a "prefers evenings" task in a free morning slot just because morning is scanned first, even
when evenings were genuinely available. Two-phase avoids that regression.
**Also discussed, deferred:** "placed via fallback vs. placed inside preferred window" as a
future scoring signal, once multi-candidate scoring exists (ROADMAP §5 Phase 2 step 3) — good
idea, but the two-phase fallback itself doesn't need to wait for scoring to be built.
**Status:** next up to implement.

---

## Task CRUD / persistence layer

### [RESOLVED] `TaskRepo repo = new TaskRepo();` — interface instantiation error
**What broke:** `TaskRepo` is a Spring Data JPA interface (`extends JpaRepository<...>`), not
a class — can't be instantiated with `new`. Compile error: "'TaskRepo' is abstract; cannot be
instantiated."
**Fix:** constructor injection — `private final TaskRepo repo;` + `@RequiredArgsConstructor`
on `TaskService`, matching the pattern already used in `AuthService`. Spring auto-generates
and injects the real `TaskRepo` implementation.
**Verified:** compiles; confirmed via IntelliJ Problems panel before/after.

### [RESOLVED] `updateTask` silently created a duplicate row instead of updating
**What broke:** `updateTask` fetched the existing `DBTask` only to check ownership, then
called `createTaskFromDTO(user, dto)` — which builds a **brand new** `Task` entity with no
`taskId` set — and saved *that*. Since JPA sees a null primary key as "insert," every "update"
silently created a second, duplicate task instead of modifying the original, which stayed
untouched forever.
**A tempting but wrong fix that was considered and rejected:** manually copying
`DBTask.getTaskId()` onto the new object before saving. This would fix the duplicate-row
problem but introduces a worse, quieter bug: the new object's `EventOccurrence` is built fresh
from only what's in `CreateTaskDTO` (which doesn't include scheduler-owned fields like
`status`/`allottedTimeRange`) — saving it would silently wipe the task's placement history on
any unrelated edit (e.g. renaming a task would un-schedule it).
**Actual fix:** added `TaskCreationHelpers.applyDTOToExistingTask(existingTask, dto)` — mutates
the *existing* `Task`/`EventOccurrence` in place, reusing `existingTask.getEvent()` rather than
replacing it, so scheduler-owned fields survive. Shared private helper
(`applyDTOToTaskAndEvent`) used by both the create path and this new update path, avoiding
duplicated field-mapping logic.
**Verified:** logic reviewed and rewritten; not yet independently Postman-tested specifically
for the "edit a task, confirm no duplicate row + confirm allottedTimeRange survives" case —
worth doing once TaskInstance-based persistence settles further, since `allottedTimeRange`
itself was later removed from `EventOccurrence` entirely in favor of `TaskInstance` (see
below), which may have already superseded part of this concern.

### [RESOLVED] `updateTask`/`deleteTask` missing null checks → raw NPE instead of 404
**What broke:** `repo.findByTaskId(taskId)` returns a plain `Task` (not `Optional`) — if the
task doesn't exist, the next line (`.getUser()`) threw a raw `NullPointerException` instead of
a clean, catchable `NotFoundException`.
**Fix:** explicit null check before use, throwing `NotFoundException` — applied consistently
across `getTaskByUser` (via `orElseThrow` once that repo method was converted to return
`Optional<Task>`), `updateTask`, and `deleteTask`.
**Verified:** reviewed; not independently re-tested via Postman for the "request a nonexistent
taskId" case specifically.

### [RESOLVED] `getAllTasksByUser` had dead/redundant code
**What broke:** manually special-cased an empty list (`if (tasks.isEmpty()) { return new
ArrayList<>(); }`) — pointless, since Spring Data JPA's `List<T>` finder methods never return
`null`, already returning an empty list when nothing matches.
**Fix:** removed the redundant branch — `return repo.findAllByUser(user);` alone is equivalent.
**Verified:** reviewed, trivial change, no behavior difference by construction.

### [RESOLVED] `Recurrence` constructor arg-count/order mismatch
**What broke:** mapping code called `new Recurrence(type, weeklyMode, daysOfWeek,
timesPerWeek, excludedDaysOfWeek)` — 5 args — but the real `@AllArgsConstructor`-generated
constructor has 7 fields in this order: `recurrenceTypeEnum, weeklyMode, daysOfWeek,
timesPerWeek, dayOfMonth, monthOfYear, excludedDaysOfWeek`. Wrong arg count/position.
**Fix:** switched to the no-args constructor + individual setters instead of fighting the
7-arg constructor for two fields (`dayOfMonth`/`monthOfYear`) that don't even exist in
`RecurrenceDTO` yet (`MONTHLY`/`ANNUALLY` are deliberately unimplemented) — avoids passing
`null, null` for fields with no DTO representation, and won't need touching if those fields
get added to the DTO later.
**Verified:** compiles; confirmed via IntelliJ error panel before/after.

### [RESOLVED] Naming — `getCreateTaskDTO` record name violated convention
**What broke:** a record was named `getCreateTaskDTO` (lowercase-leading) — legal Java, but
reads like a getter method rather than a type, and sits confusingly next to an actual method
also named similarly.
**Fix:** renamed to `CreateTaskResult` (proper PascalCase).
**Verified:** cosmetic/convention only, no behavior change.

### [RESOLVED] `Task.equals`/`hashCode` unsafe as a `Map` key
**What broke:** `Task` is `@Data`, which generates `equals`/`hashCode` off *all* fields by
default — including the mutable `event` field. Using `Task` as a `Map` key (needed for
`placeAll`'s `Map<Task, PlacementResult>` return type) risked entries becoming unreachable if
`event` mutated after insertion (hashcode changes, but the map bucket was computed from the
old hashcode).
**Fix:** `@EqualsAndHashCode(of = "taskId")` — scopes identity to the immutable, JPA-generated
UUID only, matching correct entity-identity semantics.
**Verified:** reviewed and confirmed in code; correctness reasoning is sound (standard JPA
entity `equals`/`hashCode` best practice), not independently stress-tested with a mutation-
after-insertion repro.

---

## Persistence hardening (enum & collection mapping)

### [RESOLVED] Enum fields silently corruptible via `EnumType.ORDINAL` (default)
**What broke:** all 8 enum fields across `Task`/`EventOccurrence`/`Recurrence`/`Profile` were
on JPA's default `EnumType.ORDINAL` — stores the enum's array index, not its name. Reordering
or inserting a new constant anywhere in any of these enums would silently corrupt every
existing row referencing a shifted value, with no error, no warning.
**Fix:** 8 dedicated `AttributeConverter<Enum, String>` classes (`@Converter(autoApply =
true)`), each mapping to a stable string code independent of the Java constant's name or
declared position — survives both reordering *and* renaming (stricter than plain
`EnumType.STRING`, which only survives reordering).
**Verified:** applied and confirmed in code during the 2026-08-25 session; not independently
re-verified via a live reorder-and-check test (would require deliberately reordering an enum
and confirming existing DB rows still resolve correctly).

### [RESOLVED] `Set<DayOfWeek>` fields had no JPA mapping strategy at all
**What broke:** `Recurrence.daysOfWeek`/`excludedDaysOfWeek` — bare collection fields inside a
nested `@Embeddable` aren't mapped by JPA without either `@ElementCollection` or a converter.
Would have failed at runtime/startup once actually persisted.
**Fix:** one `AttributeConverter<Set<DayOfWeek>, String>` (comma-joined day names),
`autoApply = true` binds it to both fields automatically (same generic type). Chose
converter-to-single-column over `@ElementCollection` (separate join table) — matches the
actual read pattern (always loaded as a whole set into Java, nothing queries "which
recurrences include Monday" at the SQL level) and avoids double-nested-embeddable join-table
complexity. `null` vs. empty set preserved as distinct states, not collapsed.
**Verified:** applied during the 2026-08-25 session; not independently re-verified beyond
confirming it compiles and the entities persist without error during later Postman testing
(implicitly exercised every time a `Recurrence` was saved/loaded in subsequent tests).

---

## `TaskInstance` — placement-storage redesign

### [RESOLVED] `EventOccurrence.allottedTimeRange` couldn't represent multiple placements
**What broke:** `SchedulerService.placeAll` returns `PlacementResult.placedSlots` — a task can
have *multiple* distinct placed occurrences (e.g. gym on Mon/Wed/Fri, independently searched,
not guaranteed to land at the same time each day). But `EventOccurrence.allottedTimeRange` was
a single field — no way to store more than one placement per task.
**A first attempt that was also wrong:** modeling the fix as `@Embeddable` with `taskId` as
`@Id` — would have capped one task to exactly one occurrence, the same underlying problem in
a different shape.
**Actual fix:** new entity `TaskInstance` — one row per placed occurrence, own `@Id UUID`
(genuine independent identity, not embedded), `@ManyToOne Task`, `occurrenceDay`, `time`
(`@Embedded TaskTimeRange`), `status`, `timeOfCompletion`. `EventOccurrence.status` and
`.allottedTimeRange` removed entirely from `Task` — `Task` is now purely the
template/rule, `TaskInstance` is each concrete placed occurrence.
**Verified:** confirmed no other code referenced the removed fields before deletion
(`TaskCreationHelpers` only had a stale comment, no live usage). Exercised extensively via
every subsequent `/schedule/generate` Postman test in this log.

---

## Minor, flagged but not yet fixed

### [OPEN] JWT access token expiry hardcoded in `JwtUtil`
Not externalized to `application.properties`. Low priority, not blocking anything.

### [OPEN] `JwtFilter` silently swallows all exceptions into a debug log
Including `NotFoundException` for a deleted user — worth a deliberate decision on whether
that's the desired behavior (e.g. should a deleted user's still-valid token actively fail
loudly rather than silently falling through?). Not yet decided either way.

### [OPEN] No lower bound preventing placement before `now`
Only the deadline (upper bound) is enforced in `generateCandidate`. Deferred deliberately —
irrelevant for "regenerate whole week fresh" (the current use case), only matters once
mid-week rescheduling reuses `generateCandidate`. See ROADMAP §2 known gaps for full reasoning.

### [OPEN] `WeeklyTimeline` has no "which week" concept
Single recurring week only — a deadline pushing the search past 7 days out is clamped to
avoid aliasing onto the wrong day, but genuine multi-week scheduling (e.g. "assignment due in
3 weeks") isn't representable yet. Real limitation, not a bug — needs a deliberate design
decision before it matters.
