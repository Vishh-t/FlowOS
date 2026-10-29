# FlowOS

Personal productivity backend: auth (incl. Google sign-in), user profile, tasks and a scheduler.
**Read `ProjectDocs/PROJECT_CONTEXT.md` first** for goals and design. `ProjectDocs/ROADMAP.md` = planned work, `ProjectDocs/ISSUES_LOG.md` = known issues and past fixes (search it before debugging).

## Stack
Java 21, Spring Boot 4.1, Spring Web, Data JPA (**PostgreSQL**), **Redis** (`spring-boot-starter-data-redis`), Security + JJWT, Google API client, Validation, Lombok, devtools.

## Layout
Feature packages under `src/main/java/org/example/flowos/`, each with its own `Controller`, `Service(s)`, `Repo/Repository`, `Entity`/`Model`, `DTO(s)`, `Enums`, `Helper(s)`:
`Auth/` (incl. `Config`), `Profile/`, `Task/`, `Scheduler/`, `User/`, plus shared `Exceptions/`. Keep new code inside the matching feature package and follow its existing folder names (they are not uniform — match the neighbour, don't rename).

## Commands
- Compile: `./mvnw -q compile` (PowerShell: `.\mvnw.cmd -q compile`)
- Test: `./mvnw -q test` — needs PostgreSQL and Redis reachable as configured in `application.properties`.

## Gotchas
- Active branch is often a feature branch (e.g. `basicScheduling`), not `main` — check `git branch --show-current` before committing.
- Only one test (`FlowOsApplicationTests`); add service tests with each change, especially scheduler logic.
- Secrets (`jwt.secret`, DB password, Google client id) live in `application.properties` — never print or commit real values.
