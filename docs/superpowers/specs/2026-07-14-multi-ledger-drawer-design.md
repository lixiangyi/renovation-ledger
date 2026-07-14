# 多账本（Drawer 切换）设计

**日期：** 2026-07-14  
**状态：** 已按用户「使用推荐方案处理」落地实现  
**端：** Android App + 微信小程序

## 目标

- 支持**多个账本**并存，切换查看  
- 首页用 **Drawer（侧滑抽屉）** 切换 / 新建  
- **导入 CSV** 时提示并**新建账本**再写入，避免污染当前账本  

## 推荐方案（已定）

**`currentProjectId` + 多 Project 存储 + 首页 Modal/侧滑抽屉。**

| 方案 | 说明 | 取舍 |
|------|------|------|
| **A（推荐）** | prefs 存当前账本 ID；库内多 project 行 / storage 多 project | 与现有 `projectId` FK 一致，切换成本低 |
| B | 每账本独立 DB / storage key | 迁移、备份更重 |
| C | 仅「标签分组」假多账本 | 无法真隔离导入/备份 |

## 数据

### Android

- Room `projects` 已可多行；去掉「仅 LIMIT 1」为唯一入口  
- DataStore：`current_project_id`  
- `observeProjectWithItems()` 跟随 `current_project_id`  

### 小程序

- `renovation_ledger_v1` → `{ prefs, projects[], currentProjectId, items[] }`  
- 启动迁移：旧 `{ project }` → `projects: [project]`  

## UX

### 首页抽屉

- 汉堡菜单打开抽屉  
- 列表：账本名（当前高亮）  
- 「新建账本」：默认名「新账本」或可改简名，空账本（不种默认模板，与首账号种子区分）  
- 点账本 → 切换 → 关抽屉 → 总览刷新  

### 导入

- 用户发起导入时弹窗：  
  **「导入将新建一个账本并切换过去，当前账本保留。是否继续？」**  
- 确认后：创建账本（名如 `导入账本 M/d`）→ 设为当前 → 写入草稿项  

## 非目标（本期不做）

- 删除账本、合并账本、跨账本搜索  
- 抽屉放到底部导航壳层全 Tab 共享（本期仅首页总览，够用）  
- Autosave CSV 多文件拆分（仍备份**当前**账本）  

## 验收

1. 可新建第二本账本并切换，数据互不串  
2. 导入前有新建提示，导入项落在新账本  
3. 旧数据启动后仍在默认账本  
4. Android `oneClickSetup` 可装机；小程序重新编译可见抽屉  
