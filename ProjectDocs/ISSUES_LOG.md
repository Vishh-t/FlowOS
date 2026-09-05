# FlowOS — Issues & Fixes Log

> Personal reference only — not tracked in git (see `.gitignore`). This is the "what broke
> and how it got fixed" record. `ROADMAP.md` stays forward-looking (architecture, planning,
> open design decisions) — this file is backward-looking (concrete bugs found, root cause,
> fix, verification).

Last updated: 2026-09-06 — **reorganized by status** (was chronological/topic-grouped
before). Sections now run `OPEN` → `RESOLVED` → `DEFERRED` → `DECIDED / CHECKED — not bugs`
→ `SUGGESTIONS`, in that fixed order, so the current backlog is always at the top and
history doesn't need to be scanned to find it. Entry content is unchanged from before this
reorganization — only the grouping and old topic/chronology sub-headers were removed.
Zero `[OPEN]` entries as of this update.

## How to read this file

- Each entry follows: **what broke → root cause → fix → how it was verified** (dry run /
  Postman / neither yet).
- Within each status section, entries are kept in roughly the order they were originally
  found/fixed (oldest first) — later entries sometimes reference earlier ones by name.

---

## OPEN

*(none currently)*

---

## RESOLVED

### `TaskInstance` had no real calendar-date anchoring — `ROADMAP.md` §2c step 1
- **What was missing:** `TaskInstance.occurrenceDay` was a bare `DayOfWeek` — no way to tell
  "this week's Monday" from "next week's Monday." Root cause underneath several other §2c
  gaps (no "now" lower bound at the day level, no multi-week horizon, `ONE_OFF` unsupported,
  incremental placement not representable) per the roadmap's own dependency ordering.
- **Fix, deliberately scoped to translation only — zero changes to Scheduler-core:**
  1. `TaskInstance.occurrenceDay` (`DayOfWeek`) replaced with `occurrenceDate` (`LocalDate`).
     Not stored alongside a separate `DayOfWeek` field — `occurrenceDate.getDayOfWeek()` is
     computed on demand wherever the day-of-week is still needed, so the two values can never
     drift apart the way `postPaddingMinutes` once did (see that entry below).
  2. `ScheduleGenerationService.generateSchedule` computes one `anchorMonday` per run —
     `now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))` — and
     translates each placed slot's `DayOfWeek` into a real `actualDate` (`anchorMonday.plusDays
     (slot.getStartDay().getValue() - 1)`) right before building each `TaskInstance`. This is
     the *only* place a `DayOfWeek` becomes a real date.
  3. `TaskInstanceResponseDTO` gained an `occurrenceDate` field; `ScheduleGeneratorHelperMethods
     .fromEntity` passes it through — this is the actual point of the change, the frontend now
     receives real dates.
  4. `SchedulerController`'s two grouping calls (`POST /generate`, `GET /schedule`) updated from
     `TaskInstance::getOccurrenceDay` to `instance -> instance.getOccurrenceDate().getDayOfWeek
     ()` — response shape (grouped by `DayOfWeek`) intentionally unchanged, only the underlying
     stored field changed.
  - `Task`, `Recurrence`, `resolveTargetDays`, `generateCandidate`, `TimeAndDayRange`,
    `SchedulerService` — all untouched. The placement pattern itself ("gym on Mon/Wed/Fri") is
    genuinely calendar-agnostic and stays `DayOfWeek`-based permanently; only the persisted
    *result* of a placement gets a real date.
- **Follow-up fix, same session:** the first version could tag an instance with an
  already-past date (e.g. `now` = Wednesday, a slot placed on Monday would resolve to *this*
  week's Monday, already gone). Added: if `actualDate.isBefore(now.toLocalDate())`, roll it
  forward by `plusDays(7)` — next week's occurrence of that weekday instead. Purely a
  which-calendar-date-to-tag decision; doesn't touch placement logic, doesn't retire the
  `[DEFERRED]` multi-week-horizon gap below (still only ever resolves to this anchor week or
  one week past it, not a genuine multi-week horizon).
- **Verified:** dry-run sandbox (Python port of both the anchor-date math and the rollover
  check) — 5 cases for the base anchoring logic (Wed→Friday same week, Wed→Monday same week,
  now-is-Monday edge case, Monday→Sunday same week, now-is-Sunday edge case), then 6 more
  cases re-run after the rollover fix confirming the previously-broken Wed→Monday and
  Sunday→Monday cases now roll forward correctly while every already-fine case (today, later-
  in-week) stays unaffected. Not yet re-tested via live Postman against a real mid-week
  regenerate.
- **Status:** `ROADMAP.md` §2c step 1 (real-dates/calendar-anchoring) done. Multi-week
  horizon, `ONE_OFF` support, scoring, and rescheduling (with incremental placement folded
  into it) remain not started — this only unblocks them, it doesn't implement any of them.

### `TimeAndDayRange.overlaps()` breaks across the Sunday→Monday week boundary
- **What's broken:** `toMinutesSinceMonday` maps each day onto a **linear, non-wrapping**
  number line (`(day.getValue() - 1) * 1440 + minutes`; Monday = 0, Sunday ≈ 8640+). But
  `DayOfWeek.plus()` — used everywhere a candidate's end day is derived (e.g.
  `day.plus(actualEndDayOffset)` in `getCandidateResult`) — wraps **cyclically** (Sunday + 1
  day = Monday, not "day 8"). Any candidate that starts Sunday night and ends after midnight
  gets `startDay = SUNDAY, endDay = MONDAY` — and on the linear scale, that makes the *end*
  point numerically *before* the *start* point.
- **Confirmed via symbolic trace, not speculation.** Occupied slot `(SUNDAY 23:00) →
  (MONDAY 00:30)` (wraps midnight) vs. a new candidate `(MONDAY 00:00) → (MONDAY 00:15)`
  (genuinely overlapping — both occupy Monday 00:00–00:15):
  ```
  new.start(0) < occupied.end(30)       → true
  occupied.start(10020) < new.end(15)   → false   (10020 is NOT < 15)
  overlaps = true && false = FALSE      → "not overlapping" (WRONG — they do overlap)
  ```
- **Why it matters more right now:** this breaks the one correctness guarantee
  `WeeklyTimeline` exists to provide — no double-booking. Reachable in practice: any
  night-owl profile (`sleepTime` past midnight, already explicitly handled elsewhere via
  `latestCrossesMidnight`), or padding pushing a candidate past Sunday 23:59, or the search
  cursor itself sliding past the week boundary during the (now 1-minute-step) search.
- **Fix applied directly (2026-09-06), Option A from design discussion — extend the linear
  numbering past the week boundary instead of switching representations entirely:**
  1. New private `startMinutes()` — just `toMinutesSinceMonday(startDay, startTime)`, extracted
     so it isn't recomputed inline everywhere.
  2. New private `endMinutesNormalized()` — computes the raw end-minute value, and if it comes
     out `<=` the range's own start (the signal that `DayOfWeek.plus()` wrapped past Sunday),
     adds one full week's worth of minutes (`MINUTES_PER_WEEK = 10080`) so the end value keeps
     climbing past the nominal 0..10079 bound instead of resetting near zero.
  3. `overlaps()` rewritten to use these normalized values, but a normalized end past 10080
     alone isn't sufficient — the *other* range also needs checking at three positions (shifted
     `-10080`, `0`, `+10080`) since the week is a repeating cycle, not a single line; one of
     those three shifts will always line up correctly regardless of which range (if either)
     wrapped the boundary.
  4. `comparePoints`/`toMinutesSinceMonday` (used by `compareTo` and elsewhere) left completely
     untouched — this fix is fully contained inside `overlaps()` plus two small new private
     helpers, no other call site or method signature changed.
- **Verified:** dry-run sandbox (Python port of the exact algorithm, not guessed) covering: (a)
  the original documented false-negative repro from this entry — occupied `SUNDAY 23:00 →
  MONDAY 00:30` vs. new `MONDAY 00:00 → MONDAY 00:15`, now correctly returns `true`; (b) the
  same non-wrapping same-priority/no-overlap and CRITICAL-vs-LOW cases already covered by the
  Scheduler-core dry runs elsewhere in this log, confirmed unchanged; (c) a true negative — two
  ranges on opposite sides of the week with no wraparound involved, confirmed still `false`.
  Not yet re-tested via a live Postman night-owl-profile repro.
- **Status:** fixed and dry-run verified; live Postman verification still owed.

### `GlobalExceptionHandler`'s live validation-error handler returns an unhelpful raw message
- **What was broken:** the active `MethodArgumentNotValidException` handler just returned
  `ex.getMessage()` — Spring's default technical dump, not meant for API consumers.
- **Fix confirmed via direct code read:** the live handler now builds a clean, per-field
  error string (`fieldErrors().stream().map(error -> error.getField() + ": " +
  error.getDefaultMessage()).collect(Collectors.joining(", "))`), returned as a `400` —
  matches the intended fix exactly.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman for this specific pass.

### `resolveTargetDays`'s `ONE_OFF` branch skips the exclusion check
- **What was broken:** the `DAILY` and `WEEKLY` branches both filtered against
  `excludedDaysOfWeek`; the `ONE_OFF` branch (`result.add(now.getDayOfWeek())`) didn't
  check exclusions at all.
- **Fix confirmed via direct code read:** `ONE_OFF` now reads `if
  (!excluded.contains(now.getDayOfWeek())) { result.add(now.getDayOfWeek()); }` — same
  exclusion guard as the other two branches.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### `ONE_OFF` tasks could only ever be placed "today"
- **What was broken:** `resolveTargetDays`'s `ONE_OFF` branch always used
  `now.getDayOfWeek()` as the only target day — no way to say "place this one-off task next
  Tuesday."
- **Fix confirmed via direct code read, three propagation points:**
  1. `Recurrence.oneOffDay` (`DayOfWeek`) added to the entity.
  2. `RecurrenceDTO` now carries `oneOffDay`, with a new `@AssertTrue`
     (`isOneOffValid`) requiring it non-null when `recurrenceTypeEnum == ONE_OFF` — same
     conditional-validation pattern as the existing `daysOfWeek`/`timesPerWeek`/`weeklyMode`
     checks.
  3. `TaskCreationHelpers.applyDTOToTaskAndEvent` now copies
     `recurrence.setOneOffDay(dto.getRecurrence().getOneOffDay())` onto the entity.
  4. `RecurrenceInterpreters.resolveTargetDays`'s `ONE_OFF` branch now reads
     `recurrence.getOneOffDay()`, falling back to `now.getDayOfWeek()` only if null (belt-
     and-suspenders default, since the DTO validation should already guarantee non-null on
     any request that reaches this code) — still correctly filtered against
     `excludedDaysOfWeek` either way.
- **Known remaining gap, not yet addressed:** no check exists anywhere for `oneOffDay` naming
  a day *earlier in the week* than `now.getDayOfWeek()` (e.g. requesting Monday when today is
  Wednesday). `attemptSearch`'s "don't place before now" clamp only fires when `targetDay ==
  now.getDayOfWeek()`, so an earlier-in-week `oneOffDay` wouldn't be clamped and could search
  a day that's already passed — compounded by the (now-resolved, see above) lack of real date
  anchoring at the time this was found. Worth a deliberate decision (reject at DTO validation
  vs. accept as a documented scoping gap) once real `ONE_OFF` target-date design (`ROADMAP.md`
  §10) happens.
- **Verified:** confirmed in code (2026-09-05); not independently re-tested via Postman.

### N+1 delete pattern in `ScheduleGenerationService`
- **What was broken:** `taskInstanceRepo.deleteAllByTask(task)` was called once per task
  inside the loop instead of one bulk delete up front.
- **Fix confirmed via direct code read:** `generateSchedule` now calls
  `taskInstanceRepo.deleteAllByTask_User(user)` once, before the placement loop, backed by
  the repo's dedicated `deleteAllByTask_User(User user)` method.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### `/auth/logout` required a valid access token, defeating its own purpose
- **What was broken:** `SecurityConfig`'s `permitAll()` list covered `/auth/signUp`,
  `/auth/logIn`, `/auth/refresh`, `/auth/google` — but not `/auth/logout` — so calling
  logout required a currently-valid access token, blocking the most common real-world
  reason to call it (an already-expired access token, client just wants to clean up the
  refresh token).
- **Fix confirmed via direct code read:** `/auth/logout` is now in the `permitAll()` list
  alongside the other unauthenticated auth endpoints.
- **Note — still not fully resolved as a security matter, just the availability bug:**
  `AuthController.logout`/`AuthService.logout` still take the refresh token directly off
  the request body and never check it against `@AuthenticationPrincipal` — token ownership
  is still never verified. Separate, still-open concern (anyone holding a valid refresh
  token string can revoke it, regardless of who they are), not re-opened here since the
  original entry's primary complaint (endpoint unreachable without a live access token) is
  fixed.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### `logIn` had a timing side-channel that could leak whether an email is registered
- **What was broken:** `AuthService.logIn` checked `storedUser == null` and threw
  immediately, *before* ever calling `encoder.matches(...)`. Since bcrypt comparison is
  deliberately slow, a request for a non-existent email returned fast, while a request for a
  real email with a wrong password took measurably longer. This timing difference was a
  classic side-channel for enumerating which emails have accounts.
- **Fix confirmed via direct code read:** a fixed `DUMMY_HASH` constant (a real bcrypt hash
  of an arbitrary value, not tied to any user) is now always used as the comparison target
  when the user doesn't exist — `hashToCheck = userExists ? storedUser.getHashedPassword() :
  DUMMY_HASH`, and `encoder.matches(dto.getPassword(), hashToCheck)` runs unconditionally
  on every call, existent user or not. The `!userExists || !passwordMatches` check only
  happens after, so response time no longer depends on which branch was hit.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  a live timing measurement.

### `SignUpDTO.password` had no strength/length validation
- **What was broken:** only `@NotBlank` — a single-character password passed signup
  validation entirely.
- **Fix confirmed via direct code read:** `@Pattern(regexp =
  "^(?=.*[A-Z])(?=.*[a-z])(?=.*[^A-Za-z]).{8,16}$")` now enforces 8–16 characters with at
  least one uppercase, one lowercase, and one non-alphabet character.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### `SignUpDTO.emailId` lacked `@Email` validation, inconsistent with `LogInDTO`
- **What was broken:** `LogInDTO.emailId` had both `@Email` and `@NotBlank`;
  `SignUpDTO.emailId` only had `@NotBlank`.
- **Fix confirmed via direct code read:** `SignUpDTO.emailId` now carries `@Email` alongside
  `@NotBlank`, matching `LogInDTO`.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### Enum converters' `fromCode` throws an unhandled `IllegalArgumentException` on unknown codes
- **What was broken:** all 8 `AttributeConverter` classes
  (`TaskPriorityConverter`/`FlexibilityConverter`/etc.) call `EnumType.fromCode(dbData)`,
  which throws a raw `IllegalArgumentException("Unknown ... code: " + code)` if the stored
  string doesn't match any known code. `GlobalExceptionHandler` had no handler for
  `IllegalArgumentException`, so it fell through to the generic `Exception.class`
  catch-all, surfacing as an unhandled `500` with the raw message leaked to the client.
- **Fix confirmed via direct code read:** `GlobalExceptionHandler` now has a dedicated
  `@ExceptionHandler(IllegalArgumentException.class)` returning a clean `400` ("Invalid
  Request Parameter" + message) instead of falling through to the generic `500` handler.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman against an actual stale/unknown enum code.

### No way to change a `TaskInstance`'s status at all
- **What was missing:** there was no controller, service method, or repository query method
  anywhere that let a client mark a placed occurrence as done/skipped/in-progress.
  `TaskController` only operated on `Task` (the template) — never on `TaskInstance` (the
  placed occurrence). `TaskInstanceRepo` had exactly one method (`deleteAllByTask`) — no
  `findById`, no way to fetch or mutate a single instance at all.
- **Fix, three new pieces, applied directly (2026-09-05):**
  1. `UpdateTaskInstanceStatusDTO` — request body with a single `@NotNull TaskStatusEnum
     status` field.
  2. `TaskInstanceService.updateStatus(instanceId, newStatus, user)` — fetches via
     `repo.findById(instanceId)`, throws `NotFoundException` if absent, checks ownership via
     `instance.getTask().getUser().equals(user)`, sets `status`, and sets `timeOfCompletion`
     to `now()` when transitioning to `DONE` or clears it to `null` otherwise.
  3. `TaskInstanceController` — new `PATCH /instances/{instanceId}/status`, authenticated
     the same way as every other endpoint.
- **Unblocks, now that this exists:** `ROADMAP.md` §6's "every completion must record a
  timestamp" rule can now actually happen, and §7's rescheduling trigger taxonomy ("task
  skipped," "done early," "done late") now has an endpoint through which those events arrive.
- **Verified:** applied directly (2026-09-05); not yet re-tested via Postman.

### No way to view an already-generated schedule without regenerating it
- **What was missing:** `SchedulerController` only had `POST /generate` — which deletes and
  fully re-places every task's instances every time it's called. There was no `GET` endpoint
  to simply view what was already placed.
- **Fix confirmed via direct code read:** `SchedulerController` now has `GET /schedule`,
  backed by `TaskInstanceRepo.findAllByTask_User(user)` — a genuine read-only path, no
  writes, separate from `POST /generate`. Groups instances by day the same way `POST
  /generate`'s response does, via the same `ScheduleGeneratorHelperMethods::fromEntity`
  mapper.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### No `GET` endpoint for `Profile`
- **What was missing:** `ProfileController` only had `POST /create` — no way for a client
  to fetch the current profile settings to display them.
- **Fix confirmed via direct code read:** `GET /profile/get` now exists, backed by
  `profileService.getProfile(user)`.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### Midnight-crossing wake/sleep window bug
- **What broke:** wake/sleep windows that cross midnight (e.g. a night-owl profile) weren't
  handled — bare `LocalTime` arithmetic wraps silently instead of tracking day rollover.
- **Fix:** `shift()` helper returns a `ShiftedTime(time, dayOffset)` record, making
  day-rollover explicit everywhere it matters instead of relying on `LocalTime` wraparound.
- **Verified:** dry-run sandbox, three bug-repro scenarios.

### Week-boundary wraparound bug in day-selection (Sunday → Monday)
- **What broke:** same class of bug as the `TimeAndDayRange.overlaps()` entry above, but at
  the day-selection level inside `generateCandidate` — day position wrapping incorrectly at
  the week boundary.
- **Fix:** track day position as a raw non-wrapping integer offset internally; only convert
  to `DayOfWeek` at the very end when building the final `TimeAndDayRange`.
- **Verified:** dry-run sandbox.

### `taskDeadline` not read by the scheduler
- **What broke:** deadlines existed as a field but weren't enforced as a search upper bound.
- **Fix:**
  - Deadline recomputed per candidate day (distance to deadline differs per day).
  - Days where the deadline's occurrence has already passed are skipped outright.
  - Single-week clamp (`maxSearchDayOffset = startDayOffset + 6`) added so a deadline
    further out doesn't alias onto the wrong day (multi-week scheduling still isn't
    representable — see `ROADMAP.md` §2c, this is a mitigation not a full fix).
- **Verified:** dry-run sandbox.

### `generateCandidate` couldn't correctly handle multi-occurrence recurring tasks
- **What broke:** original design had `generateCandidate` free-scanning all 7
  `DayOfWeek.values()` internally and returning the first free slot. Calling it multiple
  times for a task needing several occurrences (e.g. gym 3x/week) had no way to steer each
  call toward a *different* day.
- **Fix:** signature change — `generateCandidate` now takes a required `targetDay` and
  searches only that one day. Day-selection logic moved entirely to the caller
  (`RecurrenceInterpreters.resolveTargetDays` + `SchedulerService.placeTask`'s loop).
- **Verified:** dry-run sandbox, confirmed via the day-distribution check in scenario 2.

### NPE in `getCandidateResult` for non-`WEEKLY` tasks
- **What broke:**
  `resultDto.getTaskRecurrence().getWeeklyMode().equals(WeeklyModeEnum.EXACT_DAYS)` called
  unconditionally, no null-check — `weeklyMode` is legitimately `null` for non-`WEEKLY`
  recurrence types.
- **Root cause:** duplicated, stale validation — `RecurrenceInterpreters.resolveTargetDays`
  already filters valid days per recurrence type *before* `getCandidateResult` runs, so the
  re-check was redundant, not just unsafe.
- **Fix:** deleted the redundant check entirely rather than null-guarding it — collapses
  day-validity down to one source of truth.
- **Verified:** Postman testing (surfaced during real testing, not caught by review).

### `pickSpreadDays` — old cap silently dropped occurrences past 7/week
- **What broke (early version):** `count = availableDays.size()` clamp meant a
  `timesPerWeek` > 7 request (e.g. "2x/day") silently capped at 7, losing requested
  occurrences with no signal.
- **Fix:** replaced the cap with `index = i % availableDays.size()` (round-robin
  wraparound) — correctly handles `timesPerWeek` > 7 without dropping anything.
- **Verified:** dry-run sandbox.

### `pickSpreadDays` didn't actually spread (the wraparound fix above had its own bug)
- **What broke:** `i % availableDays.size()` for `i = 0..timesPerWeek-1`, when
  `timesPerWeek <= 7`, always evaluates to `0, 1, 2, ...` — literally the first N days in
  `DayOfWeek.values()` order, never an actual spread. Confirmed via real Postman test:
  "Reading" (`COUNT_ONLY`, 3x/week) landed Mon/Tue/Wed instead of spread across the week.
- **Fix:** `index = (i * availableDays.size()) / timesPerWeek` — even distribution across
  the full range instead of sequential indices. 3x/week over 7 days now gives
  Mon(0)/Wed(≈2)/Fri(≈4).
- **Verified:** Postman re-test — Reading now lands Mon/Wed/Fri. Confirmed no regression
  to `EXACT_DAYS` (Gym) or `DAILY` (DSA Practice) tasks.

### `COUNT_ONLY` tasks had no fallback when a chosen day was fully booked
- **What broke:** `resolveTargetDays` picks specific days upfront; if placement failed on
  one of those days (occupied), `placeTask` just recorded it as failed — never tried a
  different, genuinely-free day, even if one existed.
- **Fix:**
  - Added a fallback mechanism in `placeTask`, scoped to `COUNT_ONLY` only (deliberately
    excludes `EXACT_DAYS`/`DAILY`/`ONE_OFF`).
  - `claimedDays` (seeded with all of the task's own target days) prevents one occurrence's
    fallback from stealing a day already earmarked for a sibling occurrence of the same task.
  - On failure, tries the next unclaimed day from
    `RecurrenceInterpreters.getAvailableDays(recurrence)` until placed or the pool is
    exhausted.
- **Rejected alternative:** random day selection for the fallback — breaks
  determinism/explainability per `ROADMAP.md` §8, and duplicates what multi-start greedy is
  meant to own via varied task *ordering*, not in-task-day randomness.
- **Verified:** confirmed present and correctly scoped by direct code read. Not yet
  stress-tested against a real forced-collision scenario via Postman.

### `failedDays` computed correctly, never reached the API response
- **What broke:** `PlacementResult.failedDays` was correct internally, but
  `ScheduleGenerationService.generateSchedule` only ever read `result.placedSlots()` and
  returned a bare `List<TaskInstance>` — no structural room to carry failure info. Confirmed
  concretely: a `DAILY` task (DSA Practice) blocked by a competing `FIXED` task (College)
  failed silently on 3/7 days.
- **Fix:** `FailedOccurrenceDTO(taskName, day)`, `ScheduleGenerationResult` record (carries
  both `placedInstances` and `failedOccurrences`), `ScheduleGenerationResponseDTO` (typed
  response wrapper with `schedule` + `failed` keys), and one extra inner loop in
  `generateSchedule` over `result.failedDays()`.
- **Verified:** Postman re-test — response now returns both `schedule` (unchanged) and a
  populated `failed` array listing exactly `[DSA Practice/MONDAY, DSA Practice/WEDNESDAY,
  DSA Practice/FRIDAY]`.

### Blocked `preferredTimeRange` failed outright instead of widening the search
- **What broke:** when a task's `preferredTimeRange` was fully occupied by a competing task,
  `generateCandidate` just failed that occurrence outright — never fell back to searching the
  full wake-sleep window. Confirmed via the DSA-vs-College Postman test.
- **Fix:** two-phase search.
  - Extracted the single-window search pipeline out of `generateCandidate` into a new
    private `attemptSearch(GenerateCandidateDTO dto, LocalTime baseStart, LocalTime
    baseLatest)`.
  - `generateCandidate` now: if `preferredTimeRange` is set, tries `attemptSearch` with the
    preferred window first; only if that returns empty, retries with the full wake-sleep
    window as fallback.
  - Both calls check the same shared `WeeklyTimeline` via `isFree`, so widening the search
    window never risks walking back into a real conflict.
- **Rejected alternative:** removing the preferred-window constraint entirely and always
  searching the full window — rejected because `generateCandidate` is first-fit, not
  best-fit, so this would risk placing a "prefers evenings" task in a free morning slot just
  because morning is scanned first.
- **Also discussed, deferred:** "placed via fallback vs. placed inside preferred window" as a
  future scoring signal, once multi-candidate scoring exists.
- **Verified:** Postman re-test, full 4-task scenario (College/DSA/Gym/Reading). DSA now
  placed on all 7 days. Response `failed` array empty.

### Fixed 10-minute search step could skip over a genuinely valid boundary slot
- **What broke:** `attemptSearch`'s candidate loop advanced the cursor by a fixed
  `incrementalStep = 10` minutes. A valid free slot not on an exact multiple of 10 minutes
  from the search window's start could be stepped over entirely. Confirmed via a real case:
  `Reading` needed to land at 12:15 (315 minutes after 07:00, not divisible by 10).
- **Fix:** `incrementalStep` changed from `10` to `1` — every possible start minute is tried,
  no boundary can be skipped by construction.
- **Cost considered and accepted:** worst case ~10x more iterations per `attemptSearch` call,
  negligible at FlowOS's real scale. See "Jump to obstruction" in Suggestions if this ever
  needs to scale further.
- **Verified:** Postman re-test — `Reading` now correctly lands at 12:15. Not yet
  independently re-confirmed against a deliberately engineered exact-boundary case (the test
  built for that purpose rested on an incorrect hand-calculation, see the padding-boundary
  entry below).

### Task placed inside a window initially expected to be padding-blocked — false alarm, test-data assumption error
- **What appeared to be happening:** placed a task (`Post-Gym Call v2`) against `Gym`,
  expecting it blocked based on a hand-calculated assumption about Gym's commute settings
  never re-verified against what was actually persisted. The task placed earlier than
  expected, appearing to contradict the padding logic.
- **Actual root cause, found via `GET /tasks/getAllTasks` ground-truth check:** Gym's real
  persisted values differed from the assumed ones. With the correct values, both prior
  Postman results were fully explained and correct — **the padding/search logic had no
  defect here.**
- **Lesson kept rather than deleted:** two consecutive hand-calculations were built on an
  unverified assumption about persisted data instead of checking it directly — cost real
  time chasing a phantom bug.
- **Verified:** `GET /tasks/getAllTasks` ground truth confirmed both prior results correct.

### `/schedule/generate` threw a raw `500` when the user had no `Profile`
- **What broke:** threw `IllegalStateException`, which `GlobalExceptionHandler` has no
  dedicated handler for, surfacing as an ugly `500` with a raw message.
- **Fix:** swapped to the existing `NotFoundException`, already wired to a clean `404`
  handler. One-line change, no new exception class or handler needed.
- **Verified:** Postman re-test with a brand-new user who skipped `profile/create` — now
  returns a clean `404`.

### Ownership enforcement — confirmed correct across `GET`/`PUT`/`DELETE`
- **What was tested:** a second user's token against a `taskId` belonging to the first user.
- **Result, all as expected by design:** `GET` → `404` (query itself scopes by user, reveals
  nothing). `PUT`/`DELETE` → `403` (fetches by `taskId` alone, then explicitly checks
  ownership).
- **Known, deliberately-not-fixed inconsistency:** `GET` reveals nothing either way, `PUT`/
  `DELETE` reveal the task exists but isn't owned by the caller — a minor information-
  disclosure asymmetry, not a security hole.
- **Verified:** Postman, all three cases, with a real second account.

### `/schedule/generate` with zero tasks — confirmed clean, no fix needed
- **What was tested:** a user with a valid `Profile` but zero created tasks.
- **Result:** clean `200 OK`, `{"schedule": {}, "failed": []}` — `placeAll` handles an empty
  task list gracefully by construction.
- **Verified:** Postman, real empty-task account.

### `GET`/`PUT`/`DELETE` single-task ops — genuinely re-verified, not just re-reviewed
- Previously logged as "reviewed but not independently Postman-tested." Now actually
  exercised: fetched an existing task by ID, updated one field and confirmed no duplicate
  row was created, deleted a disposable task and confirmed it no longer appears in
  `getAllTasks`.
- **Verified:** Postman, all three, this session.

### `TaskRepo repo = new TaskRepo();` — interface instantiation error
- **What broke:** `TaskRepo` is a Spring Data JPA interface, not a class — can't be
  instantiated with `new`.
- **Fix:** constructor injection — `private final TaskRepo repo;` +
  `@RequiredArgsConstructor` on `TaskService`, matching the pattern already used in
  `AuthService`.
- **Verified:** compiles; confirmed via IntelliJ Problems panel before/after.

### `updateTask` silently created a duplicate row instead of updating
- **What broke:** `updateTask` fetched the existing `DBTask` only to check ownership, then
  built a **brand new** `Task` entity with no `taskId` set and saved *that* — JPA sees a
  null primary key as "insert."
- **A tempting but wrong fix that was considered and rejected:** manually copying
  `DBTask.getTaskId()` onto the new object — would fix the duplicate row but silently wipe
  scheduler-owned fields (e.g. `status`) not present in `CreateTaskDTO`.
- **Actual fix:** `TaskCreationHelpers.applyDTOToExistingTask(existingTask, dto)` — mutates
  the *existing* `Task`/`EventOccurrence` in place, reusing `existingTask.getEvent()` rather
  than replacing it. Shared private helper used by both create and update paths.
- **Verified:** logic reviewed and rewritten; not yet independently Postman-tested for this
  specific case — `allottedTimeRange` was later removed from `EventOccurrence` entirely in
  favor of `TaskInstance` (see below), which may have already superseded part of this
  concern.

### `updateTask`/`deleteTask` missing null checks → raw NPE instead of 404
- **What broke:** `repo.findByTaskId(taskId)` returns a plain `Task` (not `Optional`) — a
  nonexistent task threw a raw `NullPointerException` on the next line.
- **Fix:** explicit null check before use, throwing `NotFoundException` — applied
  consistently across `getTaskByUser`, `updateTask`, and `deleteTask`.
- **Verified:** reviewed; not independently re-tested via Postman for this specific case.

### `getAllTasksByUser` had dead/redundant code
- **What broke:** manually special-cased an empty list — pointless, since Spring Data JPA's
  `List<T>` finder methods never return `null`.
- **Fix:** removed the redundant branch.
- **Verified:** reviewed, trivial change, no behavior difference by construction.

### `Recurrence` constructor arg-count/order mismatch
- **What broke:** mapping code called the constructor with 5 args, but the real
  `@AllArgsConstructor`-generated constructor has 7 fields in a different order.
- **Fix:** switched to the no-args constructor + individual setters instead of fighting the
  7-arg constructor for two fields not yet represented in `RecurrenceDTO`.
- **Verified:** compiles; confirmed via IntelliJ error panel before/after.

### Naming — `getCreateTaskDTO` record name violated convention
- **What broke:** a record named `getCreateTaskDTO` (lowercase-leading) reads like a getter
  method, not a type.
- **Fix:** renamed to `CreateTaskResult` (proper PascalCase).
- **Verified:** cosmetic/convention only, no behavior change.

### `Task.equals`/`hashCode` unsafe as a `Map` key
- **What broke:** `Task` is `@Data`, generating `equals`/`hashCode` off *all* fields
  including the mutable `event` field — unsafe as a `Map` key (needed for `placeAll`'s
  `Map<Task, PlacementResult>` return type).
- **Fix:** `@EqualsAndHashCode(of = "taskId")` — scopes identity to the immutable,
  JPA-generated UUID only.
- **Verified:** reviewed and confirmed in code; not independently stress-tested with a
  mutation-after-insertion repro.

### Enum fields silently corruptible via `EnumType.ORDINAL` (default)
- **What broke:** all 8 enum fields across `Task`/`EventOccurrence`/`Recurrence`/`Profile`
  were on JPA's default `EnumType.ORDINAL` — stores the array index, not the name.
  Reordering or inserting a new constant would silently corrupt every existing row.
- **Fix:** 8 dedicated `AttributeConverter<Enum, String>` classes (`@Converter(autoApply =
  true)`), each mapping to a stable string code — survives both reordering *and* renaming.
- **Verified:** applied and confirmed in code (2026-08-25); not independently re-verified
  via a live reorder-and-check test.

### `Set<DayOfWeek>` fields had no JPA mapping strategy at all
- **What broke:** `Recurrence.daysOfWeek`/`excludedDaysOfWeek` — bare collection fields
  inside a nested `@Embeddable` aren't mapped by JPA without `@ElementCollection` or a
  converter. Would have failed at runtime once actually persisted.
- **Fix:** one `AttributeConverter<Set<DayOfWeek>, String>` (comma-joined day names),
  `autoApply = true` binds it to both fields automatically. Chose converter-to-single-column
  over `@ElementCollection` since nothing queries "which recurrences include Monday" at the
  SQL level. `null` vs. empty set preserved as distinct states.
- **Verified:** applied (2026-08-25); implicitly exercised every time a `Recurrence` was
  saved/loaded in subsequent tests.

### `EventOccurrence.allottedTimeRange` couldn't represent multiple placements
- **What broke:** a task can have *multiple* distinct placed occurrences (e.g. gym on
  Mon/Wed/Fri, independently searched) — but `EventOccurrence.allottedTimeRange` was a
  single field.
- **A first attempt that was also wrong:** modeling the fix as `@Embeddable` with `taskId`
  as `@Id` — would have capped one task to exactly one occurrence, the same problem in a
  different shape.
- **Actual fix:** new entity `TaskInstance` — one row per placed occurrence, own `@Id UUID`,
  `@ManyToOne Task`, `occurrenceDay` (later replaced with `occurrenceDate`, see the date-
  anchoring entry above), `time`, `status`, `timeOfCompletion`. `EventOccurrence.status`/
  `.allottedTimeRange` removed entirely — `Task` is now purely the template, `TaskInstance`
  each concrete placed occurrence.
- **Verified:** confirmed no other code referenced the removed fields before deletion.
  Exercised extensively via every subsequent `/schedule/generate` Postman test.

### Deleting a `Task` broke or orphaned its `TaskInstance` rows
- **What was broken:** `TaskInstance.task` is `@ManyToOne` with no `cascade` set — deleting
  a `Task` directly risked either an FK-constraint `500` or orphaned rows.
- **Fix confirmed via direct code read:** `deleteTask` now calls
  `taskInstanceRepo.deleteAllByTask(storedTask)` immediately before `repo.delete(storedTask)`.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### No timezone handling anywhere in the data model — deliberately scoped fix
- **What was broken:** every time field was `LocalTime`/`LocalDateTime` with no `ZoneId`
  stored anywhere. `SchedulerController.generateSchedule` called `LocalDateTime.now()` — the
  **server's** default zone — with no relationship to the user's actual local time.
- **Fix, deliberately scoped — not a full UTC-storage rearchitecture:**
  1. `Profile.timezone` (`String`, IANA zone id) added, converted via `ZoneId.of(...)` at
     each use site.
  2. `CreateProfileDTO` requires `timezone`, validated via `@AssertTrue isTimezoneValid()`.
  3. `ProfileService.createProfile` copies it onto the entity.
  4. `ScheduleGenerationService.generateSchedule` computes `LocalDateTime.now(ZoneId.
     of(profile.getTimezone()))` internally instead of receiving a server-zone `now`.
  5. `TaskInstanceService.updateStatus`'s `timeOfCompletion` updated the same way.
- **Explicitly NOT covered by this fix:** `Instant` fields (already zone-agnostic), DST
  transitions (relies on standard Java behavior), a user traveling to a new zone mid-week
  without updating their profile.
- **Known migration gap:** any `Profile` row created before this change has `timezone =
  NULL` — `ZoneId.of(null)` throws immediately.
- **Verified:** applied directly (2026-09-05); not yet re-tested via Postman.

### Editing a `Task` didn't propagate to its already-placed `TaskInstance` rows
- **What was broken:** `updateTask` changed the `Task` template but nothing touched existing
  `TaskInstance` rows tied to it — they silently kept reflecting the *old* definition.
- **Fix, deliberately minimal:** `updateTask` now calls
  `taskInstanceRepo.deleteAllByTask(DBTask)` right after `applyDTOToExistingTask`, before
  saving. Doesn't re-place immediately — closes the drift-vs-reality gap to "no instances
  until next generate" instead of "wrong instances forever." Genuine incremental
  re-placement deliberately not attempted here — see `ROADMAP.md` §2c/§10 (folded into the
  rescheduling engine, not built standalone).
- **Verified:** applied directly (2026-09-05); not yet re-tested via Postman.

### No concurrency protection on `/schedule/generate`
- **What was broken:** two overlapping requests for the same user could interleave their
  delete/insert cycles — `@Transactional` alone doesn't guard against this across separate
  concurrent HTTP requests.
- **Fix confirmed via direct code read:** `ScheduleGenerationService` now holds a
  `ConcurrentHashMap<UUID, Object>` of per-user lock objects, with a `lockFor(userId)`
  helper. The entire body of `generateSchedule` runs inside `synchronized
  (lockFor(user.getUserId()))`. Different users never block each other.
- **Deliberately scoped, not a distributed-lock solution:** per-JVM in-memory lock, correct
  for the project's current single-Cloud-VM deployment target. Would need Redis if this
  ever runs behind a load balancer with multiple app instances.
- **Verified:** applied directly (2026-09-05); not yet load-tested (would need two
  overlapping requests fired concurrently — hard to trigger via manual Postman clicks).

### No visibility into why the scheduler placed something where it did
- **What was broken:** no structured logging of placement decisions existed anywhere in
  `SchedulerService`.
- **Fix confirmed via direct code read:** `placeTask` now logs at each decision point —
  success, fallback trigger, and day-exhaustion warning.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested by
  inspecting live log output during a real placement run.

### JWT access token expiry hardcoded in `JwtUtil`
- **What was broken:** expiry wasn't externalized to `application.properties`.
- **Fix confirmed via direct code read:** `JwtUtil` now reads
  `@Value("${security.jwt.expiration}")`, set in `application.properties`.
- **Note — worth a follow-up, not urgent:** the property value is the literal string
  `"1000 * 60 * 15"`, not a pre-computed `900000` — worth confirming Spring's property
  binder actually evaluates that as an expression rather than failing to bind.
- **Verified:** confirmed in code (2026-09-04 review pass); binding behavior itself not
  independently tested.

### `JwtFilter` silently swallowed all exceptions into a debug log
- **What was broken:** a token referencing a deleted/missing user was only logged, then the
  filter chain ran anyway with no authentication set — silently downgrading to anonymous.
- **Fix confirmed via direct code read:** the `NotFoundException` branch now calls
  `response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ...)` and returns immediately. The
  generic `catch (Exception ex)` branch (malformed/expired/invalid-signature tokens) is
  unchanged — correct behavior for "no valid credentials supplied."
- **Verified:** applied directly (2026-09-05); not yet re-tested via Postman.

### No lower bound preventing placement before `now`
- **What was broken:** only the deadline (upper bound) was enforced — nothing stopped a
  candidate on the current day from being placed at a time already in the past.
- **Fix confirmed via direct code read:** `attemptSearch` now clamps the search start
  forward when the target day is today, applied after the pre-padding shift, before the
  candidate loop ever runs.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### `postPaddingMinutes` computed independently in two places in `SchedulerService`
- **What was broken:** the same padding formula was computed twice, in two different
  methods, with no shared source — consistent by luck, not by construction, confirmed by a
  real near-miss where unrelated refactoring desynced the second call site and broke
  compilation.
- **Fix:** `GetCandidateResultDTO` gained a `postPaddingMins` field, computed exactly once in
  `attemptSearch` and passed through. `getCandidateResult`'s per-candidate loop no longer
  recomputes it.
- **Verified:** applied directly (2026-09-05), reviewed the full file afterward to confirm
  no dangling references to the removed local variables. Not yet re-tested via Postman.

---

## DEFERRED

### `WeeklyTimeline` has no "which week" concept
- Single recurring week only — a deadline pushing the search past 7 days out is clamped to
  avoid aliasing onto the wrong day, but genuine multi-week scheduling (e.g. "assignment
  due in 3 weeks") isn't representable yet. Real limitation, not a bug.
- **Decision (2026-09-06):** don't bolt a week-index onto the current abstract-recurring-week
  model just to patch this gap — folds into the multi-week generation horizon work
  (`ROADMAP.md` §2c, next up after real-dates/calendar-anchoring, which is done). Once that
  lands, "which week" stops being a missing concept and becomes trivial.
- **Status:** not fixed, deliberately not scheduled as its own task — resolved by design once
  the multi-week generation horizon step is built.

---

## DECIDED / CHECKED — not bugs

### `Profile` double-create doesn't duplicate — confirmed via `@MapsId`
- Initially suspected `ProfileService.createProfile` might create a duplicate row on a
  second call. Checked `Profile.java` directly: `@MapsId` gives `Profile` a shared primary
  key with `User` — calling `/profile/create` twice for the same user always resolves to the
  same row and safely overwrites via JPA's merge behavior. Confirms the existing `ROADMAP.md`
  note is accurate, not a discrepancy. No action needed.

### `signInWithGoogle` links accounts by email match — accepted pattern, not an issue
- **Original framing (now corrected):** this was logged as an OPEN security anti-pattern. On
  review, auto-linking a Google sign-in to an existing `LOCAL` account by matching email is a
  standard, widely-used pattern (Auth0, Firebase, Google's own guidance) — not inherently
  unsafe, provided the email is actually verified. Decision: keep the silent-link behavior.
- **One genuine gap fixed alongside this decision:** `signInWithGoogle` read
  `payload.getEmail()` but never checked `payload.getEmailVerified()`. Added a check that
  throws `InvalidCredentialsException` if the email isn't verified, before any account
  lookup — applies to both the link path and brand-new-account creation.
- **Verified:** applied directly (2026-09-05); not yet re-tested via Postman (would need a
  Google test token with `email_verified: false`).

### Refresh tokens are never rotated — decided not needed at this scale
- **Decision (2026-09-05):** rotation isn't necessary for a solo/student-scale project.
  Full idea and reasoning parked under Suggestions ("Refresh token rotation") in case this
  ever gets revisited.

### Zero automated tests — moved to Suggestions, not dropped
- **Decision (2026-09-05):** kept as a known gap, but treated as a parked improvement rather
  than an open bug. Full detail under Suggestions ("Automated test suite").

---

## SUGGESTIONS

> Not bugs, and not committed roadmap scope — improvement ideas surfaced while working on
> something else, parked here until there's a deliberate decision to build them (at which
> point they graduate to `ROADMAP.md`).

### Automated test suite
- **Context:** no JUnit/integration tests exist anywhere in the project — every fix in this
  log was verified manually via Postman or direct code read, several of them themselves
  regressions introduced by earlier fixes in the same session.
- **Where a first pass would pay off most:** `RecurrenceInterpreters` (day-selection math —
  `pickSpreadDays` has already had two silent bugs caught only by manual testing),
  `GenerateCandidateHelperMethods.shift`/`toRawMinutes` (the midnight/week-boundary
  arithmetic), and `SchedulerService.getCandidateResult`'s deadline-clamping logic — all
  pure functions, cheapest possible unit tests to write, highest bug-density so far.
- **Status:** parked, not scheduled.

### Refresh token rotation
- **Context:** `RefreshTokenService`/`AuthService.refresh` mints a new access token per call
  but reuses the same refresh token for its full 7-day Redis TTL. Decided not necessary at
  current solo/student-project scale.
- **Idea, if this ever gets revisited:** on `refresh`, mint a *new* refresh token alongside
  the new access token, store it in Redis with the same TTL, delete the old one. Optionally
  add reuse-detection as a second step.
- **Status:** parked, not scheduled.

### Closest-fit fallback instead of first-fit, when `preferredTimeRange` search fails — decided: fold into scoring
- **Context:** the resolved two-phase search already falls back from the preferred window to
  the full wake-sleep window when blocked, but that fallback is still first-fit — could land
  far from the original preferred range. Confirmed real by the DSA-vs-College test: DSA's
  fallback landed at 07:00, two hours before its 09:00 preferred start.
- **Idea:** when the search widens, prefer the free slot closest to the original
  `preferredTimeRange` over strictly the first one the scan encounters.
- **Decision (2026-08-30):** not built standalone — "distance from preferred window" becomes
  one weighing factor inside the multi-candidate scoring mechanism (`ROADMAP.md` §5 Phase 2
  step 3) instead. Revisit when that step actually starts.

### "Jump to obstruction" instead of fixed-step search
- **Context:** the resolved `incrementalStep` fix (10 → 1 minute) guarantees correctness by
  brute force. Cost is currently negligible at FlowOS's real scale, so this is explicitly
  not worth building yet.
- **Idea:** instead of a fixed step, when `WeeklyTimeline.isFree(candidate)` returns `false`
  because it overlaps some occupied range, jump the cursor straight to that range's end
  instead of re-trying every intermediate minute.
- **Why it's strictly better, not just a trade-off:** cost would scale with the *number of
  conflicts*, not the window's raw size in minutes — immune to the boundary-skipping bug by
  construction, without paying the 10x iteration cost.
- **Why not built now instead:** genuine refactor — `WeeklyTimeline.isFree` currently only
  returns `boolean`, would need to return *which* range caused the conflict. Reasonable to
  defer until `placeAll` needs to run over much larger data.
- **Status:** parked, not scheduled.
