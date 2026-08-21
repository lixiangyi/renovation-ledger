# Nickname Follows Account Implementation Plan

> **For agentic workers:** Execute task-by-task. Skip git commits (repo rule). Finish with Android `sh oneClickSetup`.

**Goal:** Nickname syncs with logged-in account via `GET/PATCH /me` on server + Android + miniprogram.

**Architecture:** `users.nickname` is source of truth. Clients cache locally; login/fetch overwrite; settings PATCH when logged in; logout resets nickname to「我」.

**Tech Stack:** Spring Boot Kotlin, Android Retrofit/Room prefs, WeChat miniprogram sync.js

**Spec:** `docs/superpowers/specs/2026-08-21-nickname-follows-account-design.md`

---

### Task 1: Server GET/PATCH /me
### Task 2: Android API + sync + settings + logout
### Task 3: Miniprogram API + sync + settings + logout
### Task 4: Restart server if needed; Android oneClickSetup
