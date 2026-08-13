# 支付清单：按空间分组与筛选 UI 调整

**日期：** 2026-07-22  
**状态：** 待实现  
**端：** Android App + 微信小程序（双端对等）  
**关联：** 延续 `2026-07-22-payment-list-and-home-optimize-design.md`（该文档曾将「按空间」列为非目标；以本文为准覆盖该项）  
**会话偏好：** 改码收尾执行 `sh oneClickSetup`

## 1. 目标

1. 支付清单支持按 **空间** 分组（与阶段、分类并列）  
2. 状态 Tab 与「分组 / 列表形态」筛选在视觉上区分开  
3. 页面顺序调整为：**状态 Tab 在上，筛选区在下**

## 2. 推荐方案

**最小改动：** 扩展现有 `PaymentListGroupBy`；调换 UI 顺序；筛选区改为两行描边分段控件。不做底部 Sheet、不做下拉菜单。

## 3. 布局（自上而下）

1. 标题栏「支付清单」+ 新增  
2. **状态 Tab**（实心/浅底胶囊，展示条数 · 金额）  
   - 全部 / 待购买 / 付款中 / 已结清  
3. **筛选区**（描边分段条，与胶囊 Tab 明显不同）  
   - 第 1 行：按阶段 | 按分类 | 按空间（三选一）  
   - 第 2 行：二级列表 | 单列表  
4. 列表内容（大类头指标口径不变）

## 4. 分组规则

| 维度 | 枚举 / prefs 值 | 分组键 | 空值显示 |
|------|-----------------|--------|----------|
| 阶段（默认） | `STAGE` / `stage` | `stage` | 未分类 |
| 分类 | `CATEGORY` / `category` | `category`，空则回退 `stage` | 未分类 |
| 空间 | `SPACE` / `space` | `space` | **未指定** |

- 偏好持久化：`payment_list_group_by`、`payment_list_layout`（现有键扩展 `space`）  
- 状态筛选与分组/列表形态相互独立，互不重置  
- 「按空间」时大类头图标解析 `TaxonomyKind.SPACE`（有图标则显示）

## 5. 筛选区视觉

- **状态 Tab：** 保持现有胶囊 FilterChip 风格（含数量·金额）  
- **分组行：** 描边分段控件（选中段填充主色，未选中透明底 + 主色字）  
- **列表形态行：** 另一条描边分段（可用中性描边色，与分组行略作层次区分）  
- 禁止筛选区复用与状态 Tab 相同的实心胶囊样式，避免两套控件看起来像同一级 Tab

## 6. 列表形态（不变）

- **二级列表（默认）：** 大类可折叠展开  
- **单列表：** 组间可插分隔条，项始终展开  

大类头：实际支付 / 预算 / 预计要支付；已支付·待支付条数+金额；「新增」角标规则均沿用既有口径。

## 7. 实现提示

### Android

- `PaymentListGroupBy` 增加 `SPACE`  
- `PaymentListAggregator.group`：SPACE 分支用 `item.space.ifBlank { "未指定" }`（或 blank →「未指定」）  
- `UserPrefs`：`space` ↔ `SPACE`  
- `BudgetListViewModel`：`TaxonomyKind.SPACE` 解析图标  
- `BudgetListScreen`：状态 Tab 移到筛选上方；`ListControlsRow` 改为两行描边分段并加入「按空间」  
- 单测：按空间分组；空空间 →「未指定」

### 小程序

- `utils/paymentList.js` 对齐 SPACE  
- `store` prefs 支持 `space`  
- `pages/list`：顺序与描边样式对等；taxonomy 图标 kind 含 spaces

## 8. 非目标

- 不改金额口径、未付自动算、搜索、首页、标签图标体系本身  
- 不采用筛选 Sheet / 下拉按钮方案  

## 9. 验收

1. 可切换「按空间」，同空间项归入同一大类；空空间显示「未指定」  
2. 页面顺序：状态 Tab → 筛选区 → 列表  
3. 筛选为描边分段，与胶囊状态 Tab 视觉区分明显  
4. 分组含阶段 / 分类 / 空间；默认阶段；偏好可记住  
5. Android + 小程序行为一致  
6. `sh oneClickSetup` BUILD SUCCESSFUL 且安装成功  
