# shared_uilib DSL 接入说明（方案 2）

本包从 `shared_uilib` **移植**控制流 + 数据 DSL，**不**包含 View Layout / Drawable / Span / Dialog（与 Compose UI 不匹配）。

## 包路径

`com.renovation.ledger.dsl`

## 已包含

| 文件 | 能力 |
|------|------|
| `Conditional` / `ControlFlow` / `ExceptionDsl` | `yes/no`、`Boolean.invoke`、`catchException*` |
| `LogDsl` | `logV/D/I/W/E`、`logTime` |
| `StringDsl` | throw helpers、`jsonToMap`、URL/query、长度裁剪 |
| `GsonDsl` / `JsonExt` | `gson { }` / `gsonArray { }`、安全解析 |
| `RegexDsl` | `regex { digit()… }` 语法构建 |

## 相对原仓的裁剪

- 去掉 Beike 监控弹窗、`UrlUtil`、`ColorUtil`、`LJImageLoader`、富文本/View 扩展
- `log*` 以 `BuildConfig.DEBUG` 门控
- `addUrlParams` 改为本地 URLEncoder 实现

## 用法示例

```kotlin
import com.renovation.ledger.dsl.*

val label = (overBudget) yes "超支" nonNull "正常"
val map = rawJson.jsonToMap<Any?>()
val amountPattern = regex { digit().literally(".").digit() }
```
