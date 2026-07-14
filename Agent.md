# Agent 约束（renovation-ledger）

本仓库 Agent 必须遵守以下硬规则。

## 1. 需求变更同步小程序

每次对产品/功能需求的修改（含 UI、交互、数据模型、导入导出、预算逻辑等），在改 Android 的同时**必须同步**到小程序仓库：

- 路径：`/Users/beike/Projects/renovation-ledger-miniprogram`
- 保持两端行为与文案一致；仅平台特有能力可做差异实现，但产品语义不可单端遗漏
- 回复中需简要说明小程序侧已同步哪些改动；若本次无法同步，须明确阻断原因，不得默认只改 Android

## 2. 禁止 Git 命令（除非明确点名）

**不得**执行任何 git / `gh` 相关命令（status、diff、log、add、commit、push、branch、checkout、PR 等）。

唯一例外：用户在**当前这条消息**里明确写出要执行的某一项 git 操作（例如「帮我 git commit」）。未点名一律禁止，也不要主动提议 commit / PR。

## 3. 尽量使用 DSL

业务与基础设施代码优先使用本仓库已接入的 DSL，而不是手写等价样板：

- 包路径：`com.renovation.ledger.dsl`
- 说明：`app/src/main/java/com/renovation/ledger/dsl/README.md`
- 优先场景：控制流（`yes`/`no`、`Boolean.invoke`、`catchException*`）、日志（`logV/D/I/W/E`）、JSON（`gson` / `jsonToMap`）、正则（`regex { }`）、字符串/URL 辅助等

能用 DSL 覆盖的写法，不要退回冗长的手工 if/try/解析样板。
