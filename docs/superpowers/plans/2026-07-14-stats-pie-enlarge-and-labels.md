# Stats 饼图放大与标签避让 Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox syntax.

**Goal:** Android 统计页饼图更大、少空白；小扇区标签分层并避让，避免叠字。

**Architecture:** 仍用 MPAndroidChart `PieChart`。布局改正方形宽高；标签用阈值 + 角度邻近降级（&lt;3% / 挤兑时仅图例或仅 `%`）。库无法真正混合 inside/outside 时：多数分组走外侧；大扇区文案完整、小扇区短/`%`/空。

**Tech Stack:** Compose `AndroidView`、MPAndroidChart PieChart、仅 Android。

**Spec:** `docs/superpowers/specs/2026-07-14-stats-pie-enlarge-and-labels-design.md`

---

### Task 1: 尺寸

**Files:** Modify `app/.../ui/stats/StatsScreen.kt`

- [x] 饼图 `fillMaxWidth()` + `aspectRatio(1f)`（去固定过高 height）
- [x] `extraOffsets` ≈ 8–12；`holeRadius` ≈ 26–30
- [x] `oneClickSetup` 后肉眼确认空白减少

### Task 2: 标签分层与避让

- [x] `PieLabelMinPercent` ≈ 3；inside 阈值保持 12
- [x] 按累计角计算相邻小扇区；冲突则较小者空标签或仅 `%`
- [x] Formatter：短名（≤3）+ `%`；外侧优先单行
- [x] 图例逻辑保持；提示文案更新

### Task 3: 验证

- [x] `sh oneClickSetup`
- [ ] 验收对照 spec §3
