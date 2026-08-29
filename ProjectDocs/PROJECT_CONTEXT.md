# FlowOS — Project Knowledge Transfer

> IMPORTANT: This document is the established project context and source of truth,
> unless explicitly changed later in conversation. Do not assume features outside this
> document unless discussed later. See `ROADMAP.md` in this same directory for the living
> build plan, current implementation state, and open decisions — that file evolves; this
> one is the stable reference.

---

## Project Vision

FlowOS is a mobile-first intelligent life planner.

It is **NOT**:
- A calendar
- A to-do list
- An AI chatbot

Instead, it is an intelligent scheduling system that automatically builds and continuously
adapts a user's schedule according to their goals, priorities, constraints, and real-life
changes.

Instead of asking *"When do you want to study?"* — the application asks *"How much do you
want to study every day?"*

Users define **what** they want to achieve. The application decides **when** those
activities should happen.

**Core promise:** "A planner that adapts when life doesn't go according to plan — and
learns what you'll actually do, so it stops generating plans you were never going to
follow."

*(Updated 2026-08-22 — see `ROADMAP.md` §6b for the reasoning: dynamic rescheduling alone
repairs a broken day but doesn't stop the underlying plan from being unrealistic week after
week. Adherence tracking + realistic-goal correction is the added feedback layer. Same
engine, sharper thesis — no architectural change.)*

---

## Target Audience

- College students
- Engineering students
- Placement aspirants
- Students balancing studies, gym, projects, clubs and hobbies

The product is intentionally optimized for students first.

---

## Core Philosophy

Users should spend very little time planning. The application should do most of the
thinking.

The user defines: Goals, Priorities, Preferences, Fixed commitments.

The application determines: Task placement, Schedule generation, Dynamic rescheduling,
Conflict resolution, Time balancing.

---

## Three Activity Types

**Fixed Commitments** — cannot move. Examples: college, exams, meetings, doctor
appointments. These create the skeleton of the schedule.

**Flexible Tasks** — must be completed, timing may change. Examples: coding, DSA, gym,
reading, assignment work, personal projects.

**Leisure Activities** — can be postponed or shortened. Examples: Netflix, gaming,
Instagram, YouTube. These should be sacrificed before important work.

---

## Smart Onboarding

Collect: wake-up time, sleep time, morning/night preference, meal timings, commute
duration (optional).

---

## Smart Import

Users should not manually recreate their timetable. Support: timetable PDF import,
timetable screenshot import, Google Calendar import, manual entry (fallback). The
application should automatically generate recurring fixed commitments.

---

## Goal-Based Planning

Users should never manually assign exact timings — instead they define goals.

Examples: "Code 2 hours daily", "Gym 5 days/week", "Read 30 minutes/day", "Guitar 3
days/week". The scheduling engine decides where these fit.

---

## Automatic Weekly Schedule

Generate an optimized weekly plan considering: fixed commitments, user goals, priorities,
preferences, deadlines, available time.

---

## Dynamic Rescheduling

**This is the signature feature.**

Examples: overslept, running late, finished early, skipped task, added event, cancelled
event. The application should automatically reorganize the remaining schedule. Users
should never manually rebuild their day.

---

## Home Screen

The application focuses on TODAY, not a monthly calendar. Display: current task, next
task, remaining timeline, daily progress.

---

## Weekly Insights

Display: goal completion, missed tasks, time spent, productivity patterns, weekly summary.

---

## Artificial Intelligence

**AI is NOT responsible for scheduling.** It is only responsible for Natural Language
Understanding.

Example: "I have exams next week so reduce gym and increase DSA." → Structured
preferences → Scheduler Engine → Updated schedule.

Scheduling decisions always come from the application's algorithms. Never from AI.

---

## Scheduler Engine

This is the heart of the application — the most important part of the project.

Responsibilities: generate schedules, resolve conflicts, manage priorities, distribute
goals, check constraints, balance workload, protect sleep, dynamically reschedule.

The scheduler should solve an optimization problem. It should not simply place tasks into
empty slots.

### Scheduler Philosophy

The scheduler should generate multiple candidate schedules. Each schedule receives a
score. Factors include: goal completion, sleep preservation, balanced workload, minimal
context switching, minimal unnecessary movement, user preferences, priorities.

The highest-scoring valid schedule is selected.

---

## Technical Stack

- **Mobile:** React Native, TypeScript
- **Backend:** Java 21, Spring Boot, Spring Security, JWT Authentication, Spring Data JPA
- **Database:** PostgreSQL
- **Cache:** Redis
- **Real-time:** WebSockets
- **Notifications:** Firebase Cloud Messaging
- **AI:** Gemini API — only for Natural Language Understanding, never scheduling
- **Deployment:** Docker, Nginx, Cloud VM

---

## Backend Modules

Authentication, Users, Goals, Import, Scheduler Engine, Calendar, Notifications,
Analytics, AI Parser.

All remain inside one Spring Boot project (**Modular Monolith**).

---

## Main Screens

Splash, Login, Onboarding, Import Timetable, Goals, Today's Timeline, Week View, Task
Details, Weekly Insights, Profile.

---

## UI Philosophy

Mobile-first, minimal, one screen = one purpose, no information overload, large touch
targets, modern animations. Today's timeline is the application's primary focus.

---

## System Architecture

```
React Native
     ↓
Spring Boot REST API
     ↓
Authentication / AI Parser / Scheduler Engine / Import / Analytics
     ↓
PostgreSQL
     ↓
Redis
```

---

## AI Usage

AI is only called for understanding natural language. Examples: "I want to gym five days
a week.", "I have exams next week.", "Move coding to tomorrow."

AI converts English into structured information. Scheduling is entirely handled by the
Scheduler Engine.

---

## Development Philosophy

This project is **primarily a learning project** and secondarily a portfolio project. The
objective is not simply to finish the application but to deeply understand every
architectural and engineering decision.

### Teaching Style

Always prioritize teaching over solving. Whenever introducing a concept:
- Explain why it exists.
- Explain alternative approaches.
- Explain why one approach is preferred.
- Relate concepts back to backend engineering, OOP, algorithms, or system design whenever
  possible.

Assume the goal is to become a strong backend engineer.

### Code Generation Rules

Do NOT immediately generate complete implementations. Instead: give hints, explain
concepts, suggest approaches, encourage thinking first. Only provide complete code when
explicitly asked.

The goal is for the user to write the backend themselves.

### Backend Rules

Treat the backend as the user's learning area. Never rewrite backend code unless
explicitly requested. When reviewing backend code: point out mistakes, suggest
improvements, explain why, encourage the user to implement fixes themselves.

**Backend is read-only unless explicitly requested otherwise.**

### Frontend Rules

Frontend is not the primary learning objective. Frontend files may be modified directly
using filesystem tools whenever required. Focus on producing an excellent mobile
experience.

---

## Project Source & Access Rules

The project source of truth is the local FlowOS project located at:

```
E:\computer science\Java\FlowOS
```

This directory must always be treated as the authoritative implementation.

Before suggesting anything:
- Always read the relevant files from this project.
- Never guess implementation details.
- Never assume structure, logic, or behavior.

Whenever referencing code, mention the exact file path.

If access to the project is available: always inspect the actual code before giving
suggestions — suggestions must be based on real code, not assumptions.

If access is not available: clearly state that it's a general suggestion, not a fact.

---

## Architecture Discussions

Whenever discussing architecture, do not simply recommend technologies. Always explain:
why a component exists, what problem it solves, alternatives, trade-offs, and why one
design is preferred.

---

## Scheduler Engine Guidance

This is the most important component. Focus on: algorithm design, design discussions,
trade-offs, time complexity where relevant, multiple possible approaches.

Do not immediately provide implementations. Help the user think first.

---

## Development Style

Treat the user like a junior backend engineer being mentored. Challenge design decisions
when appropriate. If something can be improved, explain why. Do not agree for the sake of
agreeing. Do not overengineer simply to use more technologies. Recommend technologies only
when they solve a genuine problem.

---

## Overall Goal

Help the user become a significantly better software engineer while building FlowOS.
Understanding every architectural decision is more important than simply finishing the
application.

The scheduler should become the centerpiece of the project. Think of FlowOS not as a
planner, but as an **intelligent scheduling engine with a mobile interface**.
