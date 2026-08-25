# Avatar Cloud Upload Implementation Plan

> **For agentic workers:** Execute task-by-task. Skip git commits (repo rule). Steps use checkbox syntax.

**Goal:** Upload user avatars to the self-hosted server and sync via `/me` across Android and WeChat miniprogram.

**Architecture:** Multipart `POST /me/avatar` stores files under `./data/avatars`; DB keeps relative `avatarUrl`. Clients upload when logged in; `GET /me` / logout follow nickname rules. Public `GET /avatars/**` serves files.

**Tech Stack:** Spring Boot multipart + static resource; Retrofit `@Multipart`; Coil; `wx.uploadFile` + local cache.

**Spec:** `docs/superpowers/specs/2026-08-24-avatar-cloud-upload-design.md`

---

### Task 1: Server avatar API

**Files:**
- Modify: `AuthDtos.kt`, `AuthService.kt`, `MeController.kt`, `SecurityConfig.kt`, `application.yml`, `application-local.yml`
- Create: `AvatarStorageService.kt`, `AvatarController.kt` (or methods on MeController)
- Test: `MeProfileTest.kt` (extend) or `AvatarUploadTest.kt`

- [x] Upload/delete/get tests + implementation
- [x] `/avatars/**` permitAll + resource handler
- [x] `MeResponse` / `AuthResponse` include `avatarUrl`

### Task 2: Android client

**Files:**
- Modify: `ApiModels.kt`, `LedgerApi.kt`, `LedgerSyncRepository.kt`, `UserPrefs.kt`, `ProfileViewModel.kt`, `ProfileAvatar.kt`, `build.gradle.kts` (Coil)
- Possibly: `AvatarStorage.kt` (compress / cache from URL)

- [x] Multipart upload + DELETE
- [x] fetchMe / logout apply avatarUrl
- [x] ProfileAvatar loads URL or file

### Task 3: Miniprogram client

**Files:**
- Modify: `utils/api.js`, `utils/sync.js`, `pages/profile/profile.js`, logout paths clearing avatar

- [x] `uploadFile` + delete + fetchMe/logout
- [x] Cache remote URL to local path for display

### Task 4: Verify

- [x] Server tests pass
- [x] `sh oneClickSetup` on Android（assembleDebug 成功；adb 无设备未装上）
