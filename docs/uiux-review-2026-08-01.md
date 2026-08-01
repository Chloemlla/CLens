# CLens UI/UX 不合理问题扫描报告

> 扫描日期：2026-08-01
> 扫描范围：android/app/src/main/java/com/chloemlla/clens/ui/ 全部 UI 文件（35 个）
> 项目类型：Kotlin Jetpack Compose Android 应用（MongoDB 管理客户端）

---

## 总体统计

| 严重度 | 数量 |
|--------|------|
| 🔴 高 | 3 类（触摸目标、本地化、无障碍） |
| 🟡 中 | 5 类 |
| 🟢 低 | 3 类 |

---

## 1. 🔴 触摸目标过小（低于 48dp）

| # | 文件 | 行号 | 实际尺寸 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|----------|
| 1.1 | `ui/ClensComponents.kt` | 311 | 32dp | StatusBanner 关闭按钮 `Modifier.size(32.dp)` | 移除 size 限制或使用 48dp |
| 1.2 | `ui/connection/SessionHealthBanner.kt` | 76 | 32dp | 连接健康横幅关闭按钮 | 改为 `Modifier.size(48dp)` |
| 1.3 | `ui/editor/DocumentEditorPanel.kt` | 212-213 | 36dp | 文档编辑器搜索按钮 | 使用默认尺寸 |
| 1.4 | `ui/editor/DocumentEditorPanel.kt` | 674-694 | 32dp | 搜索栏上/下一个/清除按钮 | 移除 size 修饰符 |
| 1.5 | `ui/editor/DocumentDiffPanel.kt` | 385-388 | 28dp | 差异视图并排应用按钮 | 改为 48dp |
| 1.6 | `ui/editor/DocumentDiffPanel.kt` | 530-531 | 24dp | 差异视图内联应用按钮 | 改为 48dp 或使用 TextButton |

---

## 2. 🟡 缺少视觉反馈

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 2.1 | `ui/query/AggregateTemplateSheet.kt` | 197-206 | SwipeToDismissBox 滑动后弹回（confirmValueChange 返回 false），用户看到假动作效果 | 滑动开始时显示删除确认对话框 |

---

## 3. 🟡 布局不合理

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 3.1 | `ui/BrowsePanel.kt` | 105-108 | 关闭标签页使用 `OutlinedButton` + `Text("×")`，与同一行的 FilterChip 高度不一致 | 改用 `IconButton` + `Icons.Filled.Close` |
| 3.2 | `ui/AdminPanel.kt` | 129-132 | 按钮标签根据状态切换显示"会话监控中"或"看历史"，按钮描述状态而非操作 | 使用 `SegmentedButton` 切换模式 |
| 3.3 | `ui/ClensComponents.kt` | 526-531 | FlagRow 中只有 Checkbox 可点击，点击标签文本不触发切换 | 将 Row 设为 clickable 或使用 `Modifier.toggleable` |

---

## 4. 🟡 缺少加载/错误/空状态

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 4.1 | `ui/ConnectionsPanel.kt` | 77-93 | profiles 为空且加载中时，显示"还没有连接"的 InfoCard，可能误导用户 | 添加 `profilesLoading` 标志，加载中显示进度指示器 |
| 4.2 | `ui/QueryPanel.kt` | 325-330 | 查询结果为空时显示"暂无结果"，但无法区分"查询已执行返回零条"和"尚未执行查询" | 添加 `queryExecuted` 标志区分状态 |

---

## 5. 🟡 间距/对齐不一致

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 5.1 | `ui/BrowsePanel.kt` | 92-111 | FilterChip（~32dp）和 OutlinedButton（~40dp）在同一行高度不一致 | 统一使用 FilterChip 或 IconButton |
| 5.2 | `ui/BrowsePanel.kt` | 130-148 | 同一 ActionRow 中"刷新库"有图标，而"刷新集合"等无图标 | 统一所有操作按钮样式 |

---

## 6. 🟡 缺少无障碍标签（contentDescription）

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 6.1 | `ui/ClensComponents.kt` | 300-301 | StatusBanner 状态图标 `contentDescription = null` | 添加"错误"或"成功" |
| 6.2 | `ui/ClensComponents.kt` | 337-340 | WarningBanner 图标无 contentDescription | 添加"警告" |
| 6.3 | `ui/ClensComponents.kt` | 371-375 | StatusPill 图标 contentDescription = null | 使用 label 作为描述 |
| 6.4 | `ui/ClensComponents.kt` | 453 | SectionTitle 图标无 contentDescription | 提供有意义的描述 |
| 6.5 | `ui/ClensComponents.kt` | 481 | InfoCard 图标无 contentDescription | 提供有意义的描述 |
| 6.6 | `ui/BrowsePanel.kt` | 652-684 | 选择模式删除/分享/编辑图标无 contentDescription | 添加"删除"、"导出"等 |
| 6.7 | `ui/query/AggregateTemplateSheet.kt` | 249-250 | 连接专属指示器图标无 contentDescription | 添加"当前连接"或"全局" |

---

## 7. 🔴 硬编码字符串，无本地化支持

| # | 文件 | 行号 | 示例 |
|---|------|------|------|
| 7.1 | `ui/ClensApp.kt` | 115-121 | `Text("CLens")`、`"已连接"`、`"MongoDB 管理客户端"` |
| 7.2 | `ui/ConnectionsPanel.kt` | 55 | `Text("新建连接")` |
| 7.3 | `ui/ClensComponents.kt` | 116 | `"MongoDB 管理中枢"` |
| 7.4 | `ui/QueryPanel.kt` | 159 | `"可视化"`、`"JSON"`、`"SQL"` |

**影响：** 整个项目几乎所有 composable 都使用硬编码中文简体字符串，没有使用 `strings.xml` 或 `R.string.*` 引用。无法本地化、无法通过 Android 字符串资源系统管理。

**建议修复：** 将所有硬编码字符串迁移到 `res/values/strings.xml`，使用 `stringResource(R.string.xxx)` 引用。

---

## 8. 🟢 缺失键盘支持

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 8.1 | `ui/BrowsePanel.kt` | 150-157 | 新建数据库名字段无 IME 操作 | 添加 `ImeAction.Next` 聚焦到下一个字段 |
| 8.2 | `ui/BrowsePanel.kt` | 194-201 | 新建集合名字段无 IME 操作 | 添加 `ImeAction.Next` |
| 8.3 | `ui/BrowsePanel.kt` | 212-219 | 重命名集合名字段无 IME 操作 | 添加 `ImeAction.Done` 触发重命名 |
| 8.4 | `ui/ConnectionsPanel.kt` | 266-273 | 连接名称字段无 IME 操作 | 添加 `ImeAction.Next` 聚焦到 URI 字段 |
| 8.5 | `ui/QueryPanel.kt` | 270-277 | 查询收藏名称字段无 IME 操作 | 添加 `ImeAction.Done` 触发保存 |

---

## 9. 🟢 对话框/模态框问题

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 9.1 | `ui/BrowsePanel.kt` | 707-709 | BulkUpdateSheet 使用 Dialog 而非 ModalBottomSheet | 改用 ModalBottomSheet |
| 9.2 | `ui/editor/GeoMapPickerDialog.kt` | 42-89 | OSM 地图 WebView 加载外部资源，无网络下显示空白 | 添加 `onReceivedError` 回调，使用 `heightIn(max = 320.dp)` |
| 9.3 | `ui/editor/DocumentEditorPanel.kt` | 736-777 | 10 种类型芯片在横向滚动行中，用户可能不知道可以滚动 | 使用 `FlowRow` 让类型芯片自动换行 |

---

## 10. 🟢 滚动问题

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 10.1 | `ui/browse/SortPicker.kt` | 236-238 | LazyColumn 高度硬编码为 `itemCount * 48dp`，内容换行时可能截断 | 使用 `Modifier.heightIn(max = 480.dp)` |
| 10.2 | `ui/ClensComponents.kt` | 186-190 | PanelColumn 使用 `verticalScroll`，子组件若用 LazyColumn 会触发嵌套滚动警告 | 约束高度或重构为 LazyColumn |

---

## 11. 🟢 文本字段问题

| # | 文件 | 行号 | 问题描述 | 建议修复 |
|---|------|------|----------|----------|
| 11.1 | `ui/AdvancedPanel.kt` | 209-211 | 导入 JSON 字段无实时验证反馈，点击导入后才报错 | 在 `onValueChange` 中解析 JSON，使用 `isError` 和 `supportingText` |
| 11.2 | `ui/ConnectionsPanel.kt` | 292-301 | 主机字段使用默认键盘类型，缺少 `/` 和 `.` 键 | 添加 `KeyboardType.Uri` |

---

## 优先级修复建议

### P0（严重影响用户体验）

| 优先级 | 问题 | 影响范围 |
|--------|------|----------|
| P0 | 触摸目标 < 48dp（6 处，最小仅 24dp） | 移动端操作困难 |
| P0 | 无本地化（所有 UI 字符串硬编码中文） | 无法国际化 |

### P1（影响日常使用）

| 优先级 | 问题 | 影响范围 |
|--------|------|----------|
| P1 | 缺少无障碍标签（8 处） | 屏幕阅读器无法使用 |
| P1 | 加载/空/错误状态不明确 | 连接和查询页面用户体验差 |
| P1 | 布局不一致（按钮高度不统一） | 浏览标签页视觉混乱 |

### P2（长期改进）

| 优先级 | 问题 | 影响范围 |
|--------|------|----------|
| P2 | 文本字段缺少键盘 IME 操作 | 表单填写效率低 |
| P2 | 对话框/模态框实现问题 | 部分场景用户体验差 |
| P2 | 滚动嵌套风险 | 潜在崩溃风险 |
| P2 | 文本字段实时验证缺失 | 错误反馈不及时 |

---

## 设计良好的模块

以下文件经扫描认为设计合理，无明显 UI/UX 问题：

- `ui/ClensTheme.kt` — Material3 主题配置完善，支持深色/浅色
- `ui/ClensUiState.kt` — 状态管理清晰
- `ui/ClensControllers.kt` — 控制器架构合理
- `ui/security/BiometricLockGate.kt` — 生物识别锁实现良好
- `ui/security/BiometricAuthHelper.kt` — 辅助功能正确
- `ui/ClensActionRunner.kt` — 操作执行器设计合理
- `ui/KeyboardShortcutHandler.kt` — 键盘快捷键实现良好
- `ui/VisualFilterBuilder.kt` — 查询构建器界面合理
- `ui/CurrentOpFilters.kt` — 过滤条件 UI 清晰

---

*报告生成日期：2026-08-01*
*扫描工具：Agent（uiux-clens）*


---

# CLens UI/UX Responsive Review — 2026-08-01

## Overview
Automated scan of 23 Kotlin Jetpack Compose UI files for responsive layout issues. Found 18 issues total.

## Critical Issues (Overflow Risks / Hardcoded Sizes)

### android/app/src/main/java/com/chloemlla/clens/ui/ClensComponents.kt:486
- **Element**: `InfoCard -> Column -> lines.forEach { Text(line, bodyMedium) }`
- **Issue**: Text in InfoCard lines loop has no maxLines/overflow, so arbitrary content is fully rendered
- **Why**: Callers pass full JSON documents as lines: AdvancedPanel.kt:143 user.rolesJson, AdvancedPanel.kt:167 role.rolesJson/privilegesJson, BrowsePanel.kt:510 snap.filterJson, BrowsePanel.kt:538 draft.codeText. These are long unbroken strings (no spaces) that wrap character-by-character on a 360dp screen, producing dozens-to-hundreds of lines and enormous cards that bury the panel content.
- **Fix**: Cap each line with maxLines (e.g. 4-6) + overflow = TextOverflow.Ellipsis, or route large JSON through a scrollable JsonField/DocumentSnippet instead of InfoCard lines.


### android/app/src/main/java/com/chloemlla/clens/ui/AdminPanel.kt:106
- **Element**: `Index Card -> Column -> Text(index.keysJson, Monospace, bodySmall)`
- **Issue**: Monospace Text of index.keysJson has no maxLines/overflow
- **Why**: A compound/multi-key index produces a long single-line keys JSON; in monospace on a 360dp screen it wraps into many lines with no cap, ballooning each index card in the admin list.
- **Fix**: Add maxLines (e.g. 4-6) + overflow = TextOverflow.Ellipsis (optionally a '查看全部' toggle) to the keysJson Text.


### android/app/src/main/java/com/chloemlla/clens/ui/AdminPanel.kt:257
- **Element**: `currentOp Card -> Column -> Text('op: '+op.op / ns / secsRunning / client, Monospace)`
- **Issue**: Monospace currentOp Texts (op/ns/secsRunning/client) have no maxLines/overflow
- **Why**: op.op is the full query/command string and can be very long; rendered in monospace with no line cap it wraps character-by-character into a tall card per currentOp, flooding the server section on narrow screens.
- **Fix**: Add maxLines (e.g. 2-4) + overflow = TextOverflow.Ellipsis on each monospace line (op especially).


### android/app/src/main/java/com/chloemlla/clens/ui/ClensComponents.kt:482
- **Element**: `InfoCard -> Row(icon + Text(title, titleSmall))`
- **Issue**: InfoCard title Text has no maxLines/overflow and no Modifier.weight(1f) in its Row
- **Why**: Titles are often long runtime values (AdvancedPanel.kt:201 '导入源: '+filename, BrowsePanel.kt:534 db.collection + docLabel, BrowsePanel.kt:507 snapshot names). With no weight/maxLines the title wraps without bound and the icon+title row has no ellipsis path, producing inconsistent, oversized cards on 360dp.
- **Fix**: Add Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis to the title Text.


### android/app/src/main/java/com/chloemlla/clens/ui/ClensApp.kt:116
- **Element**: `CenterAlignedTopAppBar title -> Row(Icon + Column { Text('CLens'); Text(subtitle, labelSmall) })`
- **Issue**: TopAppBar title Column has no weight and subtitle Text has no maxLines/overflow
- **Why**: The subtitle is 'connectedProfile.name · 已连接 · 只读' — profile names can be long. The unweighted Column mis-measures in the app bar Row, and the unbounded Text wraps, growing the top bar height on 360dp and pushing content down.
- **Fix**: Add Modifier.weight(1f) to the title Column and maxLines = 1 (or 2) + overflow = TextOverflow.Ellipsis on the subtitle Text.


### android/app/src/main/java/com/chloemlla/clens/ui/ClensComponents.kt:343
- **Element**: `WarningBanner -> Row(Icon(18dp) + Text(cleartextWarning, bodySmall))`
- **Issue**: WarningBanner Text has no weight and no maxLines/overflow inside its Row
- **Why**: A long cleartext/network warning wraps unboundedly and, because the Text is unweighted, the Row doesn't reserve/limit space properly — on a 360dp screen long banners grow without cap and can push the icon/dismiss affordances out of alignment.
- **Fix**: Add Modifier.weight(1f), maxLines (e.g. 3), overflow = TextOverflow.Ellipsis to the banner Text.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/QueryPanel.kt:316
- **Element**: `Query history restore button content: OutlinedButton(Modifier.fillMaxWidth()) { Text(item.title) }`
- **Issue**: Text(item.title) inside the history OutlinedButton has no maxLines and no overflow handling
- **Why**: History titles store the actual executed find/aggregate filter JSON or SQL (e.g. {"age":{"$gt":18}} with no word-break opportunities). Inside a fillMaxWidth button on a 336dp-wide phone panel, a long unbreakable string is clipped mid-string (default TextOverflow.Clip) or wraps into many lines, producing tall buttons that dominate the panel. The other scrollable rows in this file are fine; this Row relies on weight/fillMaxWidth only for the layout, not the text.
- **Fix**: Add maxLines = 2 and overflow = TextOverflow.Ellipsis to Text(item.title) so each history row stays a compact, readable button.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/QueryPanel.kt:297
- **Element**: `Favorite restore button title: OutlinedButton(Modifier.weight(1f)) { Text(item.title, maxLines = 1) } inside a fillMaxWidth Row with a '删除' button`
- **Issue**: Text(item.title, maxLines = 1) has maxLines but no overflow = TextOverflow.Ellipsis
- **Why**: item.title is arbitrary user-entered text saved as a favorite name and can be long. In the weight(1f) button on a 336dp row the title is truncated mid-character with the default Clip overflow and no '…' indicator, so the user cannot tell the title was cut off. The Row itself does not overflow (weight absorbs it), but the text renders clipped.
- **Fix**: Add overflow = TextOverflow.Ellipsis to the Text so truncated titles show an ellipsis.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/browse/DocumentCardStream.kt:100
- **Element**: `Document card header title: Text(titlePrefix + " #" + (startIndex + index), modifier = Modifier.weight(1f)) in a Row with a fixed 22dp select-mode icon`
- **Issue**: Card header title Text has Modifier.weight(1f) but no maxLines/overflow
- **Why**: The header Row reserves fixed space for the 22dp select-mode Icon; when titlePrefix is long the title wraps to multiple lines and the vertically-centered icon floats out of alignment with the first line, making card headers uneven. In practice prefixes are short today, but nothing constrains the parameter, so a longer prefix or a large startIndex number breaks the header layout on narrow (360-412dp) screens.
- **Fix**: Add maxLines = 1 and overflow = TextOverflow.Ellipsis to the header title Text.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/browse/SortPicker.kt:237
- **Element**: `LazyColumn(modifier = Modifier.height((sortableFields.size.coerceAtMost(10) * 48).dp)) listing ListItem(RadioButton + field + type)`
- **Issue**: LazyColumn height is hardcoded as (sortableFields.size.coerceAtMost(10) * 48).dp. Material3 ListItem with headline + supporting text has a ~72dp min height, so the fixed 48dp-per-item figure underestimates content by ~33%.
- **Why**: Anti-pattern #1: a magic 48dp per row does not adapt to real item height or larger system font scale. The field list is clipped into a too-short box (needing pointless scrolling even for 2-3 items) and rows near the bottom are cut off with no ellipsis.
- **Fix**: Drop the fixed height and let the bottom-sheet content size naturally (ModalBottomSheet content already scrolls), or use heightIn(max = ...) computed from a realistic item height, or measure the ListItem content instead of the 48dp constant.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/connection/ConnectionImportSection.kt:119
- **Element**: `Row { OutlinedButton(weight(1f)) { Icon; Spacer(6.dp); Text("从剪贴板导入") }; OutlinedButton(weight(1f)) { Text("扫描二维码") } }`
- **Issue**: Row of two weight(1f) OutlinedButtons, each holding Icon(16dp) + Spacer(6dp) + Text with no maxLines/overflow. Inside the Card's 14dp padding the buttons are only ~161dp wide at 360dp; M3 button content padding (~48dp) + icon/spacer leaves ~91dp for the label.
- **Why**: Anti-pattern #4: '从剪贴板导入' (6 CJK chars @ labelLarge 14sp ≈ 84dp) fits with only ~7dp to spare, so any system font scale above ~1.1 (common accessibility setting) or a narrower parent makes the label wrap to two lines, producing uneven-height, clipped buttons in the row.
- **Fix**: Add maxLines = 1 + overflow = TextOverflow.Ellipsis to both button labels, and/or switch to a vertically stacked layout (two full-width buttons in a Column) below a width threshold (e.g. BoxWithConstraints width < 380dp).


### android/app/src/main/java/com/chloemlla/clens/ui/monitor/OpsCounterChartPanel.kt:253
- **Element**: `Text("当前 " + formatQps(current) + " · 峰值 " + formatQps(peak)) inside MetricRow (lines 253-258)`
- **Issue**: Right-side Text in MetricRow has no weight/maxLines/overflow and sits in a SpaceBetween Row; its monospace content '当前 X · 峰值 Y' can exceed available width
- **Why**: formatQps can emit ~300dp of labelMedium monospace text (e.g. '当前 999999.00/s · 峰值 999999.00/s'). Card padding (12dp each side) leaves only ~336dp on a 360dp screen, so the label wraps to multiple lines (or overflows with font scale > 1), breaking the SpaceBetween row height and misaligning the metric name label.
- **Fix**: Give the Text Modifier.weight(1f) plus maxLines = 1 and overflow = TextOverflow.Ellipsis, or split current/peak onto two lines with separate Text composables.


## Moderate Issues

### android/app/src/main/java/com/chloemlla/clens/ui/AdminPanel.kt:244
- **Element**: `currentOp Card -> Row(fillMaxWidth, SpaceBetween) -> Text(opId) + Text('慢操作')`
- **Issue**: Row(SpaceBetween) with Text('opid '+opId) and optional '慢操作' badge — Text has no weight/maxLines, so wide content overlaps on narrow screens
- **Why**: A large NumberLong opId (up to ~19 digits) in titleMedium SemiBold plus the 慢操作 badge can exceed the card inner width (~324dp at 360dp screen after 12dp panel + 24dp card padding). With SpaceBetween the two Texts then overlap/clip instead of wrapping or scrolling.
- **Fix**: Give the opId Text Modifier.weight(1f) + maxLines = 1 + overflow = TextOverflow.Ellipsis so it truncates instead of colliding with the badge.


### android/app/src/main/java/com/chloemlla/clens/ui/ClensComponents.kt:729
- **Element**: `ResultViewModeToggle -> Row(spacedBy(8.dp)) with FilterChip JSON/表格/卡片`
- **Issue**: ResultViewModeToggle Row of 3 FilterChips has no horizontalScroll
- **Why**: Although the 3 short chips fit at default font, at 360dp with system font scale (>=1.3x, common for CJK users) the row exceeds the available width and clips/overflows instead of scrolling, and there is no way to reach the off-screen chip.
- **Fix**: Add Modifier.horizontalScroll(rememberScrollState()) to the Row (or wrap chips in a FlowRow).


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/editor/DocumentDiffPanel.kt:250
- **Element**: `SideBySideDiffView Row(Modifier.fillMaxWidth()) with Column(weight(1f)) panes containing SideBySideFieldRow`
- **Issue**: Side-by-side diff mode renders two equal-weight(1f) columns in a Row inside an AlertDialog. On a 360dp phone the M3 dialog is ~312dp wide, the dialog text slot adds ~24dp padding each side, and the 4dp spacer leaves each pane only ~130dp wide.
- **Why**: Anti-pattern #2/#4: the two-pane layout has no width to work with on a phone. Inside each ~130dp monospace pane, diff.path uses maxLines=1 with default TextOverflow.Clip (no ellipsis) and values (up to 60 chars, maxLines=3) are hard-clipped, so field paths and values are cut off and the diff is effectively unreadable on 360-412dp devices.
- **Fix**: Auto-fallback to InlineDiffView on narrow widths — use BoxWithConstraints or LocalConfiguration.current.screenWidthDp and only render SideBySideDiffView when width >= 600dp; also add overflow=TextOverflow.Ellipsis to the path Text in SideBySideFieldRow.


### F:/Repositories/GitHub/CLens/android/app/src/main/java/com/chloemlla/clens/ui/editor/DocumentDiffPanel.kt:137
- **Element**: `Column(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 560.dp)) inside the AlertDialog text slot`
- **Issue**: Dialog content is forced to Column.heightIn(max = 560.dp) with inner panes heightIn(min=200, max=440). Title (~72dp) + 560dp content + buttons (~52dp) + dialog padding (~64dp) is ~733dp tall, exceeding small phones like 360x640.
- **Why**: Material3 AlertDialog does not scroll its content and does not cap the dialog height, so on short/small phones the top or bottom of the dialog (title or 关闭 button) is clipped off-screen and unreachable.
- **Fix**: Derive the max height from the available screen space (e.g. BoxWithConstraints with maxHeight = availableHeight * 0.8), make the diff content vertically scrollable, or use a full-screen dialog on small heights.


### android/app/src/main/java/com/chloemlla/clens/ui/monitor/OpsCounterChartPanel.kt:104
- **Element**: `Row containing OpsSeries.entries FilterChips (lines 104-122)`
- **Issue**: Row of 4 FilterChips (insert/query/update/delete, each with an 8dp leading-icon Spacer) has no horizontalScroll and no wrapping layout, inside a fillMaxWidth Row with spacedBy(8.dp)
- **Why**: At 360dp width the four chips plus spacers sum to roughly 380-410dp (FilterChip default horizontal padding + leading icon + ~14sp label). The Row is non-scrollable and clips/overlaps the trailing 'delete' chip, and FilterChip has no way to shrink, so the toggle UI becomes unusable on small phones.
- **Fix**: Add Modifier.horizontalScroll(rememberScrollState()) to the chips Row, or replace the Row with a FlowRow (ExperimentalLayoutApi) so chips wrap to a second line instead of overflowing.


### android/app/src/main/java/com/chloemlla/clens/ui/query/AggregateTemplateSheet.kt:257
- **Element**: `Text(template.name, maxLines = 1, overflow = Ellipsis) in SwipeableTemplateItem's inner Row (lines 257-262)`
- **Issue**: Template name Text has maxLines=1/ellipsis but no weight modifier inside the weight(1f) inner Row, so it consumes all remaining width and squeezes the sibling '[连接专属]' badge
- **Why**: In a Row, a non-weighted Text with ellipsis is measured at the full remaining max width. For a long template name it fills the whole row (minus the Star icon), leaving ~0dp for the '[连接专属]' badge, which then wraps into a thin vertical column of characters or is clipped behind the IconButton on narrow screens.
- **Fix**: Add Modifier.weight(1f, fill = false) (or weight(1f)) to the name Text so the badge keeps its intrinsic width and the name ellipsizes within the leftover space.


## Files Scanned
- android/app/src/main/java/com/chloemlla/clens/ui/AdminPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/AdvancedPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/BrowsePanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/ClensApp.kt
- android/app/src/main/java/com/chloemlla/clens/ui/ClensComponents.kt
- android/app/src/main/java/com/chloemlla/clens/ui/ClensTheme.kt
- android/app/src/main/java/com/chloemlla/clens/ui/ConnectionsPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/QueryPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/SettingsPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/browse/BrowseBreadcrumb.kt
- android/app/src/main/java/com/chloemlla/clens/ui/browse/CollectionStatsQuickPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/browse/DocumentCardStream.kt
- android/app/src/main/java/com/chloemlla/clens/ui/browse/SortPicker.kt
- android/app/src/main/java/com/chloemlla/clens/ui/connection/ConnectionImportSection.kt
- android/app/src/main/java/com/chloemlla/clens/ui/connection/SessionHealthBanner.kt
- android/app/src/main/java/com/chloemlla/clens/ui/editor/DocumentDiffPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/editor/DocumentEditorPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/editor/GeoMapPickerDialog.kt
- android/app/src/main/java/com/chloemlla/clens/ui/editor/RawJsonEditor.kt
- android/app/src/main/java/com/chloemlla/clens/ui/monitor/OpsCounterChartPanel.kt
- android/app/src/main/java/com/chloemlla/clens/ui/query/AggregateTemplateSheet.kt
- android/app/src/main/java/com/chloemlla/clens/ui/query/VisualQueryBuilder.kt
- android/app/src/main/java/com/chloemlla/clens/ui/security/BiometricLockGate.kt
