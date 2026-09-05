# FlowOS — Issues & Fixes Log

> Personal reference only — not tracked in git (see `.gitignore`). This is the "what broke
> and how it got fixed" record. `ROADMAP.md` stays forward-looking (architecture, planning,
> open design decisions) — this file is backward-looking (concrete bugs found, root cause,
> fix, verification). If a bug is still open, it's logged here too, with `[OPEN]`.

Last updated: 2026-09-05 (second verification pass — confirmed 2 more previously-OPEN
issues fixed against live code: `GET /schedule` read-only endpoint added, and `logIn`'s
timing side-channel closed via a constant-time dummy-hash comparison. Checked every other
remaining [OPEN] entry against current source in the same pass — none of the rest have
changed; the `TimeAndDayRange.overlaps()` week-boundary bug is still live and still
CRITICAL.)

---

## Live review pass (2026-09-05) — verifying prior OPEN issues against current code

> Triggered by checking in on the state of the OPEN backlog. Four previously-OPEN entries
> below (marked RESOLVED with a 2026-09-05 note) turned out to already be fixed in the live
> code. One more, a live break in `getCandidateResult`, turned out to not be a new issue at
> all — see the updated "`postPaddingMinutes` computed independently in two places" entry
> further down, which already covered exactly this risk.

---

## How to read this file

- Each entry follows: **what broke → root cause → fix → how it was verified** (dry run /
  Postman / neither yet).
- Entries are grouped roughly by when they surfaced, oldest first.

---

## Full-codebase review pass (2026-08-30) — found via direct code read, before rescheduler work starts

> Distinct from the sections below (which are chronological, mixed discovery methods). This
> section is a deliberate, systematic read-through of every Task/Scheduler/Profile
> controller/service/helper/entity/DTO, done specifically to catch issues *before* building
> the rescheduler on top of this foundation — not reactive bug-chasing during a feature build.

### [OPEN — CRITICAL] `TimeAndDayRange.overlaps()` breaks across the Sunday→Monday week boundary
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
  Building the rescheduler on top of a silently-broken overlap check means the bug
  propagates into reschedule logic too, and gets harder to isolate the more sits on top of
  it — this is why it's flagged as the top-priority item before that work starts.
- **Not yet fixed** — needs a design decision on the right representation (e.g. a genuinely
  linear "minutes since an anchor point, extending past 10080 for wraparound cases" instead
  of a cyclic `DayOfWeek`-based one, or explicitly detecting and special-casing the wrap).
- **Status:** flagged, not yet fixed or tested against a live Postman repro.

### [RESOLVED] `GlobalExceptionHandler`'s live validation-error handler returns an unhelpful raw message
- **What was broken:** the active `MethodArgumentNotValidException` handler just returned
  `ex.getMessage()` — Spring's default technical dump, not meant for API consumers.
- **Fix confirmed via direct code read:** the live handler now builds a clean, per-field
  error string (`fieldErrors().stream().map(error -> error.getField() + ": " +
  error.getDefaultMessage()).collect(Collectors.joining(", "))`), returned as a `400` —
  matches the intended fix exactly.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman for this specific pass.

### [RESOLVED] `resolveTargetDays`'s `ONE_OFF` branch skips the exclusion check
- **What was broken:** the `DAILY` and `WEEKLY` branches both filtered against
  `excludedDaysOfWeek`; the `ONE_OFF` branch (`result.add(now.getDayOfWeek())`) didn't
  check exclusions at all.
- **Fix confirmed via direct code read:** `ONE_OFF` now reads `if
  (!excluded.contains(now.getDayOfWeek())) { result.add(now.getDayOfWeek()); }` — same
  exclusion guard as the other two branches.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### [OPEN, functional gap not a defect] `ONE_OFF` tasks can only ever be placed "today"
- **What's happening:** `resolveTargetDays`'s `ONE_OFF` branch always uses
  `now.getDayOfWeek()` as the only target day — there's no way to say "place this one-off
  task next Tuesday." Not a code defect, a scoping gap — worth a deliberate decision, not
  urgent.
- **Status:** flagged for a future decision.

### [RESOLVED] N+1 delete pattern in `ScheduleGenerationService`
- **What was broken:** `taskInstanceRepo.deleteAllByTask(task)` was called once per task
  inside the loop instead of one bulk delete up front.
- **Fix confirmed via direct code read:** `generateSchedule` now calls
  `taskInstanceRepo.deleteAllByTask_User(user)` once, before the placement loop, backed by
  the repo's dedicated `deleteAllByTask_User(User user)` method.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### [CHECKED, NOT A BUG] `Profile` double-create doesn't duplicate — confirmed via `@MapsId`
- Initially suspected `ProfileService.createProfile` might create a duplicate row on a
  second call (no existence check before `save`). Checked `Profile.java` directly: `@MapsId`
  gives `Profile` a shared primary key with `User` (`profileId` is derived from the
  associated `User`, not independently generated) — so calling `/profile/create` twice for
  the same user always resolves to the same row and safely overwrites via JPA's merge
  behavior. Confirms the existing ROADMAP note ("calling this twice silently overwrites")
  is accurate, not a discrepancy. No action needed.

---

## Full-codebase review pass, part 2 (2026-08-30) — Auth module, `User`, enum converters,
exceptions — previously unreviewed in this depth

> Direct follow-up after being pushed to check beyond just the areas pointed at. This part
> covers everything under `Auth/`, `User/`, all 8 `AttributeConverter` classes, and the
> `Exceptions` package — none of which had been read end-to-end before this pass.

### [RESOLVED] `/auth/logout` required a valid access token, defeating its own purpose
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
  is still never verified. That's the second half of what this entry originally flagged;
  it's a separate, still-open concern (anyone holding a valid refresh token string can
  revoke it, regardless of who they are), not re-opened here since the original entry's
  primary complaint (the endpoint being unreachable without a live access token) is fixed.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### [OPEN — security] `signInWithGoogle` silently links accounts by email match alone
- **What's happening:** when a Google sign-in's email matches an existing `LOCAL` account,
  `AuthService.signInWithGoogle` does `user.setGoogleId(googleId); repo.save(user);` —
  linking the two accounts with no additional verification (no requirement that the user
  already be logged in as the LOCAL account, no confirmation step).
- **Why it matters:** this is a documented OAuth account-linking anti-pattern. If email
  ownership isn't independently guaranteed to be verified on both sides, matching by email
  alone risks account takeover — whoever controls a Google account with a given email
  gets silently merged into whatever LOCAL account already used that email.
- **Not yet fixed** — needs a decision: require the user to be already authenticated as the
  LOCAL account to link (explicit "link my Google account" flow instead of implicit
  merge-on-login), or otherwise verify email ownership before linking.
- **Status:** flagged, not yet fixed.

### [RESOLVED] `logIn` had a timing side-channel that could leak whether an email is registered
- **What was broken:** `AuthService.logIn` checked `storedUser == null` and threw
  immediately, *before* ever calling `encoder.matches(...)`. Since bcrypt comparison is
  deliberately slow, a request for a non-existent email returned fast, while a request for a
  real email with a wrong password took measurably longer (the bcrypt comparison actually
  ran). This timing difference was a classic side-channel for enumerating which emails have
  accounts.
- **Fix confirmed via direct code read:** a fixed `DUMMY_HASH` constant (a real bcrypt hash
  of an arbitrary value, not tied to any user) is now always used as the comparison target
  when the user doesn't exist — `hashToCheck = userExists ? storedUser.getHashedPassword() :
  DUMMY_HASH`, and `encoder.matches(dto.getPassword(), hashToCheck)` runs unconditionally
  on every call, existent user or not. The `!userExists || !passwordMatches` check only
  happens after, so response time no longer depends on which branch was hit — both paths
  now pay the same bcrypt cost.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  a live timing measurement.

### [RESOLVED] `SignUpDTO.password` had no strength/length validation
- **What was broken:** only `@NotBlank` — a single-character password passed signup
  validation entirely.
- **Fix confirmed via direct code read:** `@Pattern(regexp =
  "^(?=.*[A-Z])(?=.*[a-z])(?=.*[^A-Za-z]).{8,16}$")` now enforces 8–16 characters with at
  least one uppercase, one lowercase, and one non-alphabet character.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### [RESOLVED] `SignUpDTO.emailId` lacked `@Email` validation, inconsistent with `LogInDTO`
- **What was broken:** `LogInDTO.emailId` had both `@Email` and `@NotBlank`;
  `SignUpDTO.emailId` only had `@NotBlank`.
- **Fix confirmed via direct code read:** `SignUpDTO.emailId` now carries `@Email` alongside
  `@NotBlank`, matching `LogInDTO`.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

### [OPEN, design note] Refresh tokens are never rotated
- **What's happening:** `RefreshTokenService`/`AuthService.refresh` mints a new *access*
  token on each call but reuses the same refresh token for its full 7-day Redis TTL — no
  rotation, no invalidation of the old token when a new access token is issued.
- **Why it's worth knowing, not necessarily "wrong":** non-rotating refresh tokens are a
  common, simpler pattern and not inherently broken — but it means a stolen refresh token
  stays valid for its entire lifetime with no reuse-detection signal (rotating refresh
  tokens let you detect "this token was already used once" as a compromise indicator).
  Worth a deliberate decision, not an oversight to silently fix.
- **Status:** flagged as a design point to revisit, not an active bug.

### [RESOLVED] Enum converters' `fromCode` throws an unhandled `IllegalArgumentException` on unknown codes
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
  Postman against an actual stale/unknown enum code (would need a deliberate bad-data
  repro to fully exercise this path end-to-end).

---

## Functional gaps (missing capability, not broken code) — found while searching for what
could block the rescheduler and future work

### [OPEN — significant] No way to change a `TaskInstance`'s status at all
- **What's missing:** there is no controller, service method, or repository query method
  anywhere that lets a client mark a placed occurrence as done/skipped/in-progress.
  `TaskController` only operates on `Task` (the template) — create/read/update/delete —
  never on `TaskInstance` (the placed occurrence). `TaskInstanceRepo` has exactly one method
  (`deleteAllByTask`) — no `findById`, no way to fetch or mutate a single instance at all.
- **Why this blocks real future work, not just a nice-to-have:**
  - `ROADMAP.md` §6's standing rule ("every completion must record a timestamp") can never
    actually happen — `TaskInstance.timeOfCompletion` exists as a field but nothing anywhere
    can ever set it.
  - `ROADMAP.md` §7's rescheduling trigger taxonomy explicitly includes "task skipped,"
    "done early," "done late" — none of these events can ever be reported to the system,
    since there's no endpoint through which they'd arrive. The rescheduler would have
    nothing to react to.
  - The adherence-tracking / "learns what you'll actually do" thesis (§6b) depends entirely
    on completion data that currently has no path into the database.
- **Not yet fixed** — needs at minimum: a `TaskInstanceController`/service with something
  like `PATCH /instances/{id}/status`, plus `TaskInstanceRepo.findById` (already inherited
  free from `JpaRepository`, just unused) and an ownership check (instance → task → user,
  same pattern as `TaskService`'s existing ownership checks).
- **Status:** flagged as high priority — this is likely worth building before or alongside
  the rescheduler, since the rescheduler's trigger taxonomy directly depends on it.

### [RESOLVED] No way to view an already-generated schedule without regenerating it
- **What was missing:** `SchedulerController` only had `POST /generate` — which deletes and
  fully re-places every task's instances every time it's called. There was no `GET` endpoint
  to simply view what was already placed. Viewing "today's schedule" (the Home Screen's
  core purpose, per `PROJECT_CONTEXT.md`) required triggering a full destructive
  regenerate cycle just to read data that already exists.
- **Fix confirmed via direct code read:** `SchedulerController` now has `GET /schedule`,
  backed by `TaskInstanceRepo.findAllByTask_User(user)` (already added for the
  `ScheduleGenerationService` bulk-delete fix, reused here) — a genuine read-only path,
  no writes, separate from `POST /generate`. Groups instances by `occurrenceDay` the same
  way `POST /generate`'s response does, via the same `ScheduleGeneratorHelperMethods::fromEntity`
  mapper.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman.

### [RESOLVED] No `GET` endpoint for `Profile`
- **What was missing:** `ProfileController` only had `POST /create` — no way for a client
  to fetch the current profile settings to display them.
- **Fix confirmed via direct code read:** `GET /profile/get` now exists, backed by
  `profileService.getProfile(user)`.
- **Verified:** confirmed in code (2026-09-04 review pass); not independently re-tested via
  Postman.

---

## Scheduler core (`generateCandidate`, day-selection)

### [RESOLVED] Midnight-crossing wake/sleep window bug
- **What broke:** wake/sleep windows that cross midnight (e.g. a night-owl profile) weren't
  handled — bare `LocalTime` arithmetic wraps silently instead of tracking day rollover.
- **Fix:** `shift()` helper returns a `ShiftedTime(time, dayOffset)` record, making
  day-rollover explicit everywhere it matters instead of relying on `LocalTime` wraparound.
- **Verified:** dry-run sandbox, three bug-repro scenarios.

### [RESOLVED] Week-boundary wraparound bug (Sunday → Monday)
- **What broke:** same class of bug one level up — day position wrapping incorrectly at the
  week boundary.
- **Fix:** track day position as a raw non-wrapping integer offset internally; only convert
  to `DayOfWeek` at the very end when building the final `TimeAndDayRange`.
- **Verified:** dry-run sandbox.

### [RESOLVED] `taskDeadline` not read by the scheduler
- **What broke:** deadlines existed as a field but weren't enforced as a search upper bound.
- **Fix:**
  - Deadline recomputed per candidate day (distance to deadline differs per day).
  - Days where the deadline's occurrence has already passed are skipped outright.
  - Single-week clamp (`maxSearchDayOffset = startDayOffset + 6`) added so a deadline
    further out doesn't alias onto the wrong day (multi-week scheduling still isn't
    representable — see ROADMAP §2 known gaps, this is a mitigation not a full fix).
- **Verified:** dry-run sandbox.

### [RESOLVED] `generateCandidate` couldn't correctly handle multi-occurrence recurring tasks
- **What broke:** original design had `generateCandidate` free-scanning all 7
  `DayOfWeek.values()` internally and returning the first free slot. Calling it multiple
  times for a task needing several occurrences (e.g. gym 3x/week) had no way to steer each
  call toward a *different* day — all calls would converge on the same day repeatedly since
  nothing tracked "already used this day for this task."
- **Fix:** signature change — `generateCandidate` now takes a required `targetDay` and
  searches only that one day. Day-selection logic moved entirely to the caller
  (`RecurrenceInterpreters.resolveTargetDays` + `SchedulerService.placeTask`'s loop).
- **Verified:** dry-run sandbox, confirmed via the day-distribution check in scenario 2.

### [RESOLVED] NPE in `getCandidateResult` for non-`WEEKLY` tasks
- **What broke:**
  `resultDto.getTaskRecurrence().getWeeklyMode().equals(WeeklyModeEnum.EXACT_DAYS)` called
  unconditionally, no null-check — `weeklyMode` is legitimately `null` for non-`WEEKLY`
  recurrence types. Only surfaced once request validation (see below) stopped blocking such
  requests from reaching this code path.
- **Root cause, and why it wasn't just null-guarded:** this was duplicated, stale
  validation — `RecurrenceInterpreters.resolveTargetDays` already filters valid days per
  recurrence type *before* `getCandidateResult` runs, so `day` arriving here is already
  guaranteed valid. The `dayExcluded`/`notInExactDays` re-check was redundant, not just
  unsafe.
- **Fix:** deleted the redundant check entirely rather than null-guarding it — collapses
  day-validity down to one source of truth (`resolveTargetDays`) instead of two that can
  drift out of sync (which is exactly what had happened).
- **Verified:** Postman testing (surfaced during real testing, not caught by review).

---

## Recurrence / day-spreading

### [RESOLVED] `pickSpreadDays` — old cap silently dropped occurrences past 7/week
- **What broke (early version):** `count = availableDays.size()` clamp meant a
  `timesPerWeek` > 7 request (e.g. "2x/day") silently capped at 7, losing requested
  occurrences with no signal.
- **Fix:** replaced the cap with `index = i % availableDays.size()` (round-robin
  wraparound) — correctly handles `timesPerWeek` > 7 without dropping anything.
- **Verified:** dry-run sandbox.

### [RESOLVED] `pickSpreadDays` didn't actually spread (the wraparound fix above had its own bug)
- **What broke:** `i % availableDays.size()` for `i = 0..timesPerWeek-1`, when
  `timesPerWeek <= 7` (the common case — "3x/week"), always evaluates to `0, 1, 2, ...` —
  literally the first N days in `DayOfWeek.values()` order (Mon/Tue/Wed), never an actual
  spread. Confirmed via real Postman test: "Reading" (`COUNT_ONLY`, 3x/week) landed
  Mon/Tue/Wed instead of spread across the week.
- **Fix:** `index = (i * availableDays.size()) / timesPerWeek` — even distribution across
  the full range instead of sequential indices. 3x/week over 7 days now gives
  Mon(0)/Wed(≈2)/Fri(≈4).
- **Verified:** Postman re-test — Reading now lands Mon/Wed/Fri. Confirmed no regression
  to `EXACT_DAYS` (Gym) or `DAILY` (DSA Practice) tasks, which don't go through this code
  path.

### [RESOLVED] `COUNT_ONLY` tasks had no fallback when a chosen day was fully booked
- **What broke:** `resolveTargetDays` picks specific days upfront; if placement failed on
  one of those days (occupied), `placeTask` just recorded it as failed and moved to the
  next *pre-chosen* day — never tried a different, genuinely-free day, even if one existed.
- **Fix:**
  - Added a fallback mechanism in `placeTask`, scoped to `COUNT_ONLY` only (deliberately
    excludes `EXACT_DAYS` — user explicitly chose those days, falling back would silently
    violate that choice; excludes `DAILY`/`ONE_OFF` — no fallback concept applies).
  - `claimedDays` (seeded with all of the task's own target days) prevents one occurrence's
    fallback from stealing a day already earmarked for a sibling occurrence of the same
    task.
  - On failure, tries the next unclaimed day from
    `RecurrenceInterpreters.getAvailableDays(recurrence)` until placed or the pool is
    exhausted.
- **Rejected alternative:** random day selection for the fallback (proposed, discussed,
  declined — breaks determinism/explainability per ROADMAP §8, and duplicates what
  multi-start greedy is meant to own via varied task *ordering*, not in-task-day
  randomness).
- **Verified:** confirmed present and correctly scoped by direct code read. Not yet
  stress-tested against a real forced-collision scenario via Postman — worth doing once
  Issue 3 (below) is also in.

---

## API layer — `failedDays` visibility

### [RESOLVED] `failedDays` computed correctly, never reached the API response
- **What broke:** `PlacementResult.failedDays` was correct internally, but
  `ScheduleGenerationService.generateSchedule` only ever read `result.placedSlots()` in its
  save loop and returned a bare `List<TaskInstance>` — no structural room to carry failure
  info even if the controller wanted it. Confirmed concretely: a `DAILY` task (DSA
  Practice) blocked by a competing `FIXED` task (College) failed silently on 3/7 days — no
  error, just absent from those days' output, invisible unless you counted entries.
- **Fix:** three additions:
  - `FailedOccurrenceDTO(taskName, day)`
  - `ScheduleGenerationResult` record (carries both `placedInstances` and
    `failedOccurrences`)
  - `ScheduleGenerationResponseDTO` (typed response wrapper with `schedule` + `failed`
    keys, chosen over a quick `Map<String, Object>` for consistency with the rest of the
    codebase's typed-DTO convention)
  - `generateSchedule` gained one extra inner loop over `result.failedDays()` — no change
    to placement logic itself, purely additive.
- **Verified:** Postman re-test — response now returns both `schedule` (unchanged,
  correct) and a populated `failed` array listing exactly `[DSA Practice/MONDAY,
  DSA Practice/WEDNESDAY, DSA Practice/FRIDAY]`.

---

## Search-width design

### [RESOLVED] Blocked `preferredTimeRange` failed outright instead of widening the search
- **What broke:** when a task's `preferredTimeRange` was fully occupied by a competing
  task, `generateCandidate` just failed that occurrence outright — never fell back to
  searching the full wake-sleep window. Confirmed via the DSA-vs-College Postman test: DSA
  (`DAILY`, preferred 09:00–11:00) silently failed to place on 3/7 days (Mon/Wed/Fri,
  blocked by `College`, `FIXED`+`CRITICAL`, 09:00–12:00).
- **Note on a wrong turn during discussion:** this was initially miscast as a
  `movability`/`FlexibilityEnum` issue — corrected: `movability` is exclusively about
  resistance to being moved *during rescheduling* (ROADMAP §3.4b), completely unrelated to
  initial-placement search width. That distinction held, no rework needed there.
- **Fix:** two-phase search, implemented as designed.
  - Extracted the entire single-window search pipeline (padding math,
    `GetCandidateResultDTO` construction, `getCandidateResult` call) out of
    `generateCandidate` into a new private `attemptSearch(GenerateCandidateDTO dto,
    LocalTime baseStart, LocalTime baseLatest)`.
  - `generateCandidate` now: if `preferredTimeRange` is set, tries `attemptSearch` with
    the preferred window first (unchanged behavior when it succeeds); only if that
    returns empty, retries `attemptSearch` with the full wake-sleep window as fallback.
  - Both calls check the same shared `WeeklyTimeline` via `isFree`, so the fallback
    correctly still avoids every already-occupied slot — widening the search window never
    risks walking back into a real conflict, since occupancy is checked against live
    timeline state, not re-derived from `preferredTimeRange`.
  - Unblocked days pay zero extra cost — the fallback call only happens when the first
    attempt genuinely returns `Optional.empty()`.
- **Rejected alternative:** removing the preferred-window constraint entirely and always
  searching the full window — rejected because `generateCandidate` is first-fit, not
  best-fit, so this would risk placing a "prefers evenings" task in a free morning slot
  just because morning is scanned first, even when evenings were genuinely available.
  Two-phase avoids that regression by trying the preferred window fully first.
- **Also discussed, deferred:** "placed via fallback vs. placed inside preferred window"
  as a future scoring signal, once multi-candidate scoring exists (ROADMAP §5 Phase 2
  step 3).
- **Verified:** Postman re-test, full 4-task scenario (College/DSA/Gym/Reading). DSA now
  placed on all 7 days — Tue/Thu/Sat/Sun unchanged (09:00–10:30, inside preferred window,
  first-try success), Mon/Wed/Fri now correctly fall back to 07:00–08:30 (earliest free
  slot found once the search widened, still correctly avoiding College's 09:00–12:00).
  Response `failed` array empty — confirms zero silent failures, not just "looks less
  broken."

### [RESOLVED] Fixed 10-minute search step could skip over a genuinely valid boundary slot
- **What broke:** `attemptSearch`'s candidate loop advanced the cursor by a fixed
  `incrementalStep = 10` minutes. A valid free slot sitting between two grid points (not
  an exact multiple of 10 minutes from the search window's start) could be stepped over
  entirely and never tried, even though it was the correct answer. Not a hypothetical —
  confirmed via a real case: `Reading` (`COUNT_ONLY`, no preferred window, searches from
  wake time 07:00 forward) needed to land at 12:15, but 12:15 is 315 minutes after 07:00 —
  not divisible by 10 — so a step-10 grid starting at 07:00 could never land on it
  exactly, and the search found a later, worse slot (12:20) instead.
- **Fix:** `incrementalStep` changed from `10` to `1`. Since every time field in the
  domain is `LocalTime` at minute granularity (no sub-minute precision used anywhere),
  stepping by 1 minute means the search tries every possible start minute — no boundary
  can be skipped by construction, not just "less likely to be skipped."
- **Cost considered and accepted:** worst case ~10x more iterations per `attemptSearch`
  call (roughly 1,000 vs. 100 over a full wake-sleep window). Each iteration is cheap
  `LocalTime` arithmetic plus one linear scan over `WeeklyTimeline.occupiedSlots` (small —
  single digits to low dozens of entries for one person's week). Negligible for a
  synchronous per-request computation at this scale; not a concern worth a smarter (but
  more complex) fix yet — see Suggestions section for the deferred "jump to obstruction"
  alternative if this ever needs to scale further.
- **Verified:** Postman re-test — `Reading` now correctly lands at 12:15 (previously
  12:20), confirming the finer step does catch previously-skipped valid slots. **Not yet
  independently re-confirmed against a deliberately engineered exact-boundary case** —
  the test built for that purpose (`Post-Gym Call`, expecting a slot to open exactly when
  a competing task's padding ended) turned out to rest on an incorrect hand-calculation of
  the competing task's total padding (see next entry) rather than actually isolating this
  fix. Re-verification against a correctly-designed boundary case is still owed.

---

## Padding-boundary verification

### [RESOLVED — false alarm, test-data assumption error, not a code bug] Task placed inside a
window initially expected to be padding-blocked
- **What appeared to be happening:** placed a task (`Post-Gym Call v2`, preferred window
  19:30–20:00) against `Gym`, expecting it to be blocked until 19:40 based on a
  hand-calculated assumption that Gym had `commuteApplicableWay: BOTH_WAYS` /
  `commuteTimeInMinutes: 15` (carried over from memory of an early-session message, never
  re-verified against what was actually persisted). The task placed at 19:30 instead,
  appearing to contradict the padding logic.
- **Actual root cause, found via `GET /tasks/getAllTasks` ground-truth check:** Gym's real
  persisted values are `commuteApplicableWay: NONE`, `commuteTimeInMinutes: 0` — not
  `BOTH_WAYS`/`15`. With the *correct* values, Gym's real padded range is `[18:00, 19:25)`
  (`bufferTimeInMinutes: 10` + `durationToleranceMinutes: 15`, zero commute contribution).
  Re-checked both observed results against this corrected range:
  - `Post-Gym Call`'s 19:00–19:30 window has no valid start (`19:15`, the latest possible
    start, is still `< 19:25` — correctly blocked entirely, correctly fell back).
  - `Post-Gym Call v2`'s window starts at `19:30`, already past the real occupied end —
    correctly free on the first candidate.
  - **Both results were correct all along; the padding/search logic has no defect here.**
- **Lesson, worth keeping rather than just deleting this entry:** two consecutive
  hand-calculations were built on an unverified assumption about persisted data instead of
  checking it directly — cost real time chasing a phantom bug. The fix wasn't a code
  change, it was going to `GET /tasks/getAllTasks` for ground truth, which should have
  happened before the first hand-calculation, not after two failed ones.
- **Verified:** `GET /tasks/getAllTasks` ground truth confirmed; both prior Postman
  results are fully explained and correct under the real Gym values.

---

## API layer — endpoint verification (2026-08-30, second pass)

> Full CRUD + ownership + edge-case pass across `TaskController`/`SchedulerController`/
> `ProfileController`, run after the Scheduler-core fixes above were already confirmed
> working. Distinct from the Scheduler-core testing above — this round targets the REST
> layer itself, not placement logic.

### [RESOLVED] `/schedule/generate` threw a raw `500` when the user had no `Profile`
- **What broke:** `ScheduleGenerationService.generateSchedule` threw
  `IllegalStateException("No profile found for user " + user.getUserId())` when
  `profileRepo.findById(...)` came back empty. `IllegalStateException` isn't a domain
  exception `GlobalExceptionHandler` has a dedicated handler for, so it fell through to the
  generic catch-all, surfacing as an ugly `500 Internal Server Error` with a raw
  `"Something went wrong: No profile found for user <uuid>"` message instead of a clean,
  actionable error.
- **Fix:** swapped the thrown exception from `IllegalStateException` to the existing
  `NotFoundException` (`org.example.flowos.Exceptions.NotFoundException`) — already used
  elsewhere for exactly this class of problem ("a resource that should exist doesn't"),
  already wired to a clean `404` handler in `GlobalExceptionHandler`. One-line change, no
  new exception class or handler needed — this is a domain-exception-vs-generic-exception
  fix, not a new mechanism.
- **Verified:** Postman re-test with a brand-new user (signed up, logged in, deliberately
  skipped `profile/create`) hitting `/schedule/generate` — now returns a clean `404` with a
  readable message instead of a `500` stack trace.

### [RESOLVED] Ownership enforcement — confirmed correct across `GET`/`PUT`/`DELETE`
- **What was tested:** created a second user (no tasks, no relation to the first), used
  their token to attempt `GET`/`PUT`/`DELETE` against a `taskId` belonging to the first
  user.
- **Result, all as expected by design (not a bug, a genuine verification pass):**
  - `GET /{taskId}/getTask` → `404 Not Found`. Correct per `TaskService.getTaskByUser`'s
    design — `TaskRepo.findByTaskIdAndUser(taskId, user)` scopes the query itself, so a
    mismatched user just looks like "doesn't exist," revealing nothing about whether the
    task belongs to someone else.
  - `PUT /{taskId}/updateTask` → `403 Forbidden`. Correct per `updateTask`'s different
    pattern — fetches by `taskId` alone, then explicitly checks
    `!DBTask.getUser().equals(user)` and throws `UnauthorizedUserException`.
  - `DELETE /{taskId}/deleteTask` → `403 Forbidden`. Same pattern as `updateTask`.
- **Known, deliberately-not-fixed inconsistency (carried over from the earlier code read):**
  `GET` reveals nothing (404 either way), `PUT`/`DELETE` reveal that the task exists just
  not owned by the caller (403, distinct from a genuine 404 on a truly nonexistent
  `taskId`). A minor information-disclosure asymmetry, not a security hole — logged as a
  design note, not something blocking anything.
- **Verified:** Postman, all three cases, with a real second account — not just reviewed in
  code.

### [RESOLVED] `/schedule/generate` with zero tasks — confirmed clean, no fix needed
- **What was tested:** a user with a valid `Profile` but zero created tasks, calling
  `/schedule/generate`.
- **Result:** clean `200 OK`, `{"schedule": {}, "failed": []}` — `placeAll` handles an
  empty task list gracefully by construction (empty input, empty `Map<Task,
  PlacementResult>` output), no special-casing needed anywhere in the pipeline.
- **Verified:** Postman, real empty-task account.

### [RESOLVED — genuinely re-verified, not just re-reviewed] `GET`/`PUT`/`DELETE` single-task ops
- Previously logged as "reviewed but not independently Postman-tested" (see Task CRUD
  section above — the null-check and duplicate-row fixes). Now actually exercised via
  Postman as part of this round: fetched an existing task by ID, updated one field and
  confirmed no duplicate row was created (`getAllTasks` count unchanged), deleted a
  disposable throwaway task and confirmed it no longer appears in `getAllTasks`.
- **Verified:** Postman, all three, this session.

---

## Task CRUD / persistence layer

### [RESOLVED] `TaskRepo repo = new TaskRepo();` — interface instantiation error
- **What broke:** `TaskRepo` is a Spring Data JPA interface (`extends JpaRepository<...>`),
  not a class — can't be instantiated with `new`. Compile error: "'TaskRepo' is abstract;
  cannot be instantiated."
- **Fix:** constructor injection — `private final TaskRepo repo;` +
  `@RequiredArgsConstructor` on `TaskService`, matching the pattern already used in
  `AuthService`. Spring auto-generates and injects the real `TaskRepo` implementation.
- **Verified:** compiles; confirmed via IntelliJ Problems panel before/after.

### [RESOLVED] `updateTask` silently created a duplicate row instead of updating
- **What broke:** `updateTask` fetched the existing `DBTask` only to check ownership, then
  called `createTaskFromDTO(user, dto)` — which builds a **brand new** `Task` entity with
  no `taskId` set — and saved *that*. Since JPA sees a null primary key as "insert," every
  "update" silently created a second, duplicate task instead of modifying the original,
  which stayed untouched forever.
- **A tempting but wrong fix that was considered and rejected:** manually copying
  `DBTask.getTaskId()` onto the new object before saving. This would fix the duplicate-row
  problem but introduces a worse, quieter bug: the new object's `EventOccurrence` is built
  fresh from only what's in `CreateTaskDTO` (which doesn't include scheduler-owned fields
  like `status`/`allottedTimeRange`) — saving it would silently wipe the task's placement
  history on any unrelated edit (e.g. renaming a task would un-schedule it).
- **Actual fix:** added `TaskCreationHelpers.applyDTOToExistingTask(existingTask, dto)` —
  mutates the *existing* `Task`/`EventOccurrence` in place, reusing
  `existingTask.getEvent()` rather than replacing it, so scheduler-owned fields survive.
  Shared private helper (`applyDTOToTaskAndEvent`) used by both the create path and this
  new update path, avoiding duplicated field-mapping logic.
- **Verified:** logic reviewed and rewritten; not yet independently Postman-tested
  specifically for the "edit a task, confirm no duplicate row + confirm allottedTimeRange
  survives" case — worth doing once TaskInstance-based persistence settles further, since
  `allottedTimeRange` itself was later removed from `EventOccurrence` entirely in favor of
  `TaskInstance` (see below), which may have already superseded part of this concern.

### [RESOLVED] `updateTask`/`deleteTask` missing null checks → raw NPE instead of 404
- **What broke:** `repo.findByTaskId(taskId)` returns a plain `Task` (not `Optional`) — if
  the task doesn't exist, the next line (`.getUser()`) threw a raw `NullPointerException`
  instead of a clean, catchable `NotFoundException`.
- **Fix:** explicit null check before use, throwing `NotFoundException` — applied
  consistently across `getTaskByUser` (via `orElseThrow` once that repo method was
  converted to return `Optional<Task>`), `updateTask`, and `deleteTask`.
- **Verified:** reviewed; not independently re-tested via Postman for the "request a
  nonexistent taskId" case specifically.

### [RESOLVED] `getAllTasksByUser` had dead/redundant code
- **What broke:** manually special-cased an empty list (`if (tasks.isEmpty()) { return new
  ArrayList<>(); }`) — pointless, since Spring Data JPA's `List<T>` finder methods never
  return `null`, already returning an empty list when nothing matches.
- **Fix:** removed the redundant branch — `return repo.findAllByUser(user);` alone is
  equivalent.
- **Verified:** reviewed, trivial change, no behavior difference by construction.

### [RESOLVED] `Recurrence` constructor arg-count/order mismatch
- **What broke:** mapping code called `new Recurrence(type, weeklyMode, daysOfWeek,
  timesPerWeek, excludedDaysOfWeek)` — 5 args — but the real
  `@AllArgsConstructor`-generated constructor has 7 fields in this order:
  `recurrenceTypeEnum, weeklyMode, daysOfWeek, timesPerWeek, dayOfMonth, monthOfYear,
  excludedDaysOfWeek`. Wrong arg count/position.
- **Fix:** switched to the no-args constructor + individual setters instead of fighting
  the 7-arg constructor for two fields (`dayOfMonth`/`monthOfYear`) that don't even exist
  in `RecurrenceDTO` yet (`MONTHLY`/`ANNUALLY` are deliberately unimplemented) — avoids
  passing `null, null` for fields with no DTO representation, and won't need touching if
  those fields get added to the DTO later.
- **Verified:** compiles; confirmed via IntelliJ error panel before/after.

### [RESOLVED] Naming — `getCreateTaskDTO` record name violated convention
- **What broke:** a record was named `getCreateTaskDTO` (lowercase-leading) — legal Java,
  but reads like a getter method rather than a type, and sits confusingly next to an
  actual method also named similarly.
- **Fix:** renamed to `CreateTaskResult` (proper PascalCase).
- **Verified:** cosmetic/convention only, no behavior change.

### [RESOLVED] `Task.equals`/`hashCode` unsafe as a `Map` key
- **What broke:** `Task` is `@Data`, which generates `equals`/`hashCode` off *all* fields
  by default — including the mutable `event` field. Using `Task` as a `Map` key (needed
  for `placeAll`'s `Map<Task, PlacementResult>` return type) risked entries becoming
  unreachable if `event` mutated after insertion (hashcode changes, but the map bucket was
  computed from the old hashcode).
- **Fix:** `@EqualsAndHashCode(of = "taskId")` — scopes identity to the immutable,
  JPA-generated UUID only, matching correct entity-identity semantics.
- **Verified:** reviewed and confirmed in code; correctness reasoning is sound (standard
  JPA entity `equals`/`hashCode` best practice), not independently stress-tested with a
  mutation-after-insertion repro.

---

## Persistence hardening (enum & collection mapping)

### [RESOLVED] Enum fields silently corruptible via `EnumType.ORDINAL` (default)
- **What broke:** all 8 enum fields across `Task`/`EventOccurrence`/`Recurrence`/`Profile`
  were on JPA's default `EnumType.ORDINAL` — stores the enum's array index, not its name.
  Reordering or inserting a new constant anywhere in any of these enums would silently
  corrupt every existing row referencing a shifted value, with no error, no warning.
- **Fix:** 8 dedicated `AttributeConverter<Enum, String>` classes (`@Converter(autoApply =
  true)`), each mapping to a stable string code independent of the Java constant's name or
  declared position — survives both reordering *and* renaming (stricter than plain
  `EnumType.STRING`, which only survives reordering).
- **Verified:** applied and confirmed in code during the 2026-08-25 session; not
  independently re-verified via a live reorder-and-check test (would require deliberately
  reordering an enum and confirming existing DB rows still resolve correctly).

### [RESOLVED] `Set<DayOfWeek>` fields had no JPA mapping strategy at all
- **What broke:** `Recurrence.daysOfWeek`/`excludedDaysOfWeek` — bare collection fields
  inside a nested `@Embeddable` aren't mapped by JPA without either `@ElementCollection` or
  a converter. Would have failed at runtime/startup once actually persisted.
- **Fix:** one `AttributeConverter<Set<DayOfWeek>, String>` (comma-joined day names),
  `autoApply = true` binds it to both fields automatically (same generic type). Chose
  converter-to-single-column over `@ElementCollection` (separate join table) — matches the
  actual read pattern (always loaded as a whole set into Java, nothing queries "which
  recurrences include Monday" at the SQL level) and avoids double-nested-embeddable
  join-table complexity. `null` vs. empty set preserved as distinct states, not collapsed.
- **Verified:** applied during the 2026-08-25 session; not independently re-verified
  beyond confirming it compiles and the entities persist without error during later
  Postman testing (implicitly exercised every time a `Recurrence` was saved/loaded in
  subsequent tests).

---

## `TaskInstance` — placement-storage redesign

### [RESOLVED] `EventOccurrence.allottedTimeRange` couldn't represent multiple placements
- **What broke:** `SchedulerService.placeAll` returns `PlacementResult.placedSlots` — a
  task can have *multiple* distinct placed occurrences (e.g. gym on Mon/Wed/Fri,
  independently searched, not guaranteed to land at the same time each day). But
  `EventOccurrence.allottedTimeRange` was a single field — no way to store more than one
  placement per task.
- **A first attempt that was also wrong:** modeling the fix as `@Embeddable` with
  `taskId` as `@Id` — would have capped one task to exactly one occurrence, the same
  underlying problem in a different shape.
- **Actual fix:** new entity `TaskInstance` — one row per placed occurrence, own `@Id
  UUID` (genuine independent identity, not embedded), `@ManyToOne Task`, `occurrenceDay`,
  `time` (`@Embedded TaskTimeRange`), `status`, `timeOfCompletion`.
  `EventOccurrence.status` and `.allottedTimeRange` removed entirely from `Task` — `Task`
  is now purely the template/rule, `TaskInstance` is each concrete placed occurrence.
- **Verified:** confirmed no other code referenced the removed fields before deletion
  (`TaskCreationHelpers` only had a stale comment, no live usage). Exercised extensively
  via every subsequent `/schedule/generate` Postman test in this log.

---

## Minor, flagged but not yet fixed

### [RESOLVED] Deleting a `Task` broke or orphaned its `TaskInstance` rows
- **What was broken:** `TaskInstance.task` is `@ManyToOne` with no `cascade` attribute set
  (defaults to `CascadeType.NONE`). `TaskService.deleteTask` called `repo.delete(storedTask)`
  directly on the `Task`, with nothing deleting or reassigning its `TaskInstance` rows
  first — risking either an FK-constraint `500` or orphaned rows.
- **Fix confirmed via direct code read:** `deleteTask` now calls
  `taskInstanceRepo.deleteAllByTask(storedTask)` immediately before `repo.delete(storedTask)`
  — the exact fix this entry proposed (deleting the task's `TaskInstance` rows first via the
  already-existing repo method, rather than adding a cascade annotation).
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman (would need: generate a schedule for a task with placed instances, delete that
  task, confirm no `500` and no orphaned rows in `task_instance`).

### [OPEN] Zero automated tests — every fix tonight was verified manually, not by a test suite
- No JUnit/integration tests exist anywhere in the project. Every bug found and fixed this
  session (the redundant NPE check, the three-layer conditional-validation gaps, the
  `incrementalStep` boundary bug) was caught by manual Postman testing, not an automated
  regression check — several were themselves regressions introduced by earlier fixes in the
  same session. Nothing currently stops the same class of regression from happening silently
  next session.
- **Not fixed — explicitly deferred per user decision (2026-08-30):** "tests we will put
  ig" — acknowledged as needed, intentionally not started yet.

### [OPEN] No timezone handling anywhere in the data model
- Every time field is `LocalTime`/`LocalDateTime` — no `ZoneId` or offset stored or read
  anywhere. Fine for single-machine testing; a real correctness gap the moment this serves
  users in more than one timezone, or a user travels.

### [OPEN] Editing a `Task` doesn't propagate to its already-placed `TaskInstance` rows
- **What's happening:** `updateTask` changes the `Task` template (duration, priority,
  preferred window, etc.) but nothing re-places or updates existing `TaskInstance` rows tied
  to it — they silently keep reflecting the *old* definition until the next full
  `/schedule/generate` call regenerates everything from scratch.
- **Related to, but distinct from, ROADMAP.md §2c items #4/#5** (no incremental placement,
  destructive wholesale regeneration) — same root cause (no notion of "re-place just the
  affected subset"), different symptom (this one manifests as silent drift between the
  `Task` definition and reality, not data loss on regenerate).

### [OPEN] No concurrency protection on `/schedule/generate`
- Two overlapping requests for the same user could interleave their delete/insert cycles in
  ways `@Transactional` alone doesn't fully guard against across separate, concurrent HTTP
  requests (each gets its own transaction). Not yet a confirmed live bug — flagged as a real
  risk given the delete-then-reinsert pattern, not tested under actual concurrent load.

### [RESOLVED] No visibility into why the scheduler placed something where it did
- **What was broken:** debugging padding-boundary confusion earlier required manually
  re-deriving expected values from `Task` data and comparing against actual placements by
  hand (see the "Padding-boundary verification" section above) — no structured logging of
  placement decisions (why a slot was chosen, why a fallback triggered, why a day failed)
  existed anywhere in `SchedulerService`.
- **Fix confirmed via direct code read:** `placeTask` now logs at each decision point —
  `log.debug("Placed {} on {}", ...)` on a successful placement, `log.debug("{} blocked on
  {}, trying fallback day {}", ...)` when the `COUNT_ONLY` fallback kicks in, and
  `log.warn("{} failed to place on {}", ...)` when a day exhausts every option. Covers all
  three cases the original entry called out: why a slot was chosen (implicitly, via the
  success log), why a fallback triggered, and why a day failed.
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested by
  inspecting live log output during a real placement run.

### [RESOLVED] JWT access token expiry hardcoded in `JwtUtil`
- **What was broken:** expiry wasn't externalized to `application.properties`.
- **Fix confirmed via direct code read:** `JwtUtil` now reads `@Value("${security.jwt.expiration}")` into `expiryTime`, and `application.properties` sets
  `security.jwt.expiration = 1000 * 60 * 15`.
- **Note — worth a follow-up, not urgent:** that value is the literal string `"1000 * 60 *
  15"`, not a pre-computed `900000`. Since it binds to an `int` field, Spring's property
  binder would need to evaluate that as an expression rather than parse it as a plain
  integer for this to actually work at runtime — worth confirming the app actually boots
  and issues tokens with the intended ~15 minute expiry rather than failing to bind or
  parsing unexpectedly. Flagging as a small follow-up check, not re-opening the original
  "hardcoded" complaint, which is resolved.
- **Verified:** confirmed in code (2026-09-04 review pass); binding behavior itself not
  independently tested.

### [OPEN] `JwtFilter` silently swallows all exceptions into a debug log
- Including `NotFoundException` for a deleted user — worth a deliberate decision on
  whether that's the desired behavior (e.g. should a deleted user's still-valid token
  actively fail loudly rather than silently falling through?). Not yet decided either way.

### [RESOLVED] No lower bound preventing placement before `now`
- **What was broken:** only the deadline (upper bound) was enforced in `generateCandidate`
  / `getCandidateResult` — nothing stopped a candidate on the current day from being placed
  at a time already in the past relative to `now`.
- **Fix confirmed via direct code read:** `attemptSearch` now clamps the search start
  forward when the target day is today: `if (dto.getTargetDay() ==
  dto.getNow().getDayOfWeek() && startTime.isBefore(dto.getNow().toLocalTime())) { startTime
  = dto.getNow().toLocalTime(); }` — applied after the pre-padding shift, before the
  candidate loop ever runs, so no candidate on today's date can start before the current
  time. Other days are unaffected, matching the original scoping (this only ever mattered
  for "today").
- **Verified:** confirmed in code (2026-09-05 review pass); not independently re-tested via
  Postman (would need a request made mid-day against a task whose earliest natural slot
  falls before the current time).

### [OPEN] `WeeklyTimeline` has no "which week" concept
- Single recurring week only — a deadline pushing the search past 7 days out is clamped to
  avoid aliasing onto the wrong day, but genuine multi-week scheduling (e.g. "assignment
  due in 3 weeks") isn't representable yet. Real limitation, not a bug — needs a
  deliberate design decision before it matters.

### [RESOLVED] `postPaddingMinutes` computed independently in two places in `SchedulerService`
- **What was broken:** the same padding formula (`bufferTimeInMinutes +
  durationToleranceMinutes`, plus `commuteTimeInMinutes` conditionally added when
  `isAfterTask || isBothWay`) was computed twice, in two different methods, with no shared
  source — once in `attemptSearch` (to derive the outer search-window bound), and again
  independently inside `getCandidateResult`'s per-candidate loop (to compute the actual
  occupancy check). Consistent by luck, not by construction — confirmed by a real
  near-miss on 2026-09-05, where unrelated refactoring desynced the second call site from
  the helper's real signature and broke compilation.
- **Fix:** `GetCandidateResultDTO` gained a `postPaddingMins` field. `attemptSearch` now
  computes `PostPaddingMins` exactly once and passes it straight into the DTO via
  `generateGetCandidateResultDTO`. `getCandidateResult`'s per-candidate loop no longer
  calls `getPostPaddingMins(...)` at all — it just reads `resultDto.getPostPaddingMins()`,
  the same value computed once upstream. One source of truth, and as a side benefit it's
  no longer recomputing an identical, candidate-independent value on every iteration of the
  search loop.
- **Verified:** applied directly (2026-09-05), reviewed the full file afterward to confirm
  no dangling references to the removed local variables (`isAfterTask`/`isBothWay`/
  `commuteTimeInMinutes` inside the loop) and that `resultDto.isBothWay()`/
  `resultDto.getCommuteTimeInMinutes()` are still read correctly further down in the same
  loop. Not yet re-tested via Postman for a live regression check.

---

## Suggestions / Refinements (not bugs — ideas for later, not yet scoped into `ROADMAP.md`)

> Distinct from both sections above: these aren't things that broke, and they aren't
> committed roadmap scope either. They're improvement ideas surfaced while working on
> something else, parked here until there's a deliberate decision to build them (at which
> point they graduate to `ROADMAP.md`).

### [SUGGESTION — decided: fold into scoring, not built standalone] Closest-fit fallback instead of first-fit, when `preferredTimeRange` search fails
- **Context:** the resolved two-phase search (see "Blocked `preferredTimeRange` failed
  outright ..." above) already falls back from the preferred window to the full
  wake-sleep window when the preferred window is fully blocked. That fallback is still
  first-fit — it returns the first free slot found in the full window, which could end up
  far from the original preferred range (e.g. a task that preferred 9–11am could fall back
  to a 9pm slot, if that's what the linear scan hits first). Confirmed as a real, not
  hypothetical, gap by the DSA-vs-College test: DSA's fallback landed at 07:00, two hours
  before its 09:00 preferred start, purely because the linear scan starts at `wakeTime`.
- **Idea:** when the preferred-window attempt fails and the search widens, prefer the
  free slot closest to the original `preferredTimeRange` (by start-time distance) over
  strictly the first one the scan encounters.
- **Decision (2026-08-30):** not built as a standalone fix. "Distance from preferred
  window" becomes one weighing factor inside the multi-candidate scoring mechanism
  already planned in `ROADMAP.md` §5 Phase 2 step 3 (generate several candidates, score
  each, pick the best — alongside other factors like minimizing context-switching and
  balancing workload), rather than a bespoke bidirectional-search patch built into
  `attemptSearch` now. Reasoning: building it standalone today risks throwaway work the
  moment real scoring lands, since scoring would naturally subsume "closeness to
  preferred" as one more weighted factor. Revisit when Phase 2 step 3 actually starts —
  this entry is the seed of that factor's design, not a separate task.

### [SUGGESTION] "Jump to obstruction" instead of fixed-step search
- **Context:** the resolved `incrementalStep` fix (10 → 1 minute) guarantees correctness —
  no valid boundary slot can be skipped, since every possible start minute is tried — but
  it does so by brute force. Cost is currently negligible at FlowOS's real scale (a few
  hundred thousand cheap operations per `placeAll` call, well under a second), so this is
  explicitly not worth building yet — logged so the option exists if that ever changes.
- **Idea:** instead of advancing the search cursor by a fixed step regardless of what it
  just found, advance it directly to the end of whatever occupied range just blocked the
  current candidate. Concretely: when `WeeklyTimeline.isFree(candidate)` returns `false`
  because it overlaps some occupied `TimeAndDayRange`, jump the cursor straight to that
  range's end instead of re-trying every intermediate minute one at a time.
- **Why it's strictly better, not just a different trade-off:** cost would scale with the
  *number of conflicts* in the search window, not the window's raw size in minutes — a
  mostly-empty day would resolve in a handful of jumps regardless of whether the window is
  1 hour or 16 hours wide. It's also immune to the boundary-skipping bug by construction,
  the same guarantee `step = 1` provides, but without paying the 10x iteration cost to get
  there.
- **Why not built now instead of the simpler `step = 1` fix:** genuine refactor, not a
  one-line change — `WeeklyTimeline.isFree` currently only returns `boolean`, it would need
  to return *which* range caused the conflict (or the caller would need a new method) for
  the cursor-jump logic to know where to jump to. Reasonable to defer until `placeAll`
  actually needs to run over much larger data (many users, much longer search windows)
  where the current linear cost would start to matter — not needed at current scale.
