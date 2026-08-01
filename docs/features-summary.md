# CLens 功能总结

> 项目类型：Android Kotlin Jetpack Compose 应用
> 用途：移动端 MongoDB 运维控制台（手机端 Mongo ops console）
> 最低 SDK：Android 26+
> 仓库：https://github.com/Chloemlla/CLens

---

## 一、总体架构

```
ClensApp (主入口)
├── ConnectionsPanel     — 连接管理
│   ├── ConnectionImportSection — 导入
│   └── SessionHealthBanner     — 会话健康
├── BrowsePanel          — 浏览/数据管理
│   ├── BrowseBreadcrumb — 路径导航
│   ├── DocumentCardStream — 文档流
│   ├── CollectionStatsQuickPanel — 集合统计
│   └── SortPicker       — 排序
├── QueryPanel           — 查询
│   ├── VisualQueryBuilder — 可视化查询构建
│   └── AggregateTemplateSheet — 聚合模板
├── AdminPanel           — 管理
│   ├── OpsCounterChartPanel — 操作计数
│   └── CurrentOpFilters — 当前操作过滤
├── AdvancedPanel        — 高级功能
├── SettingsPanel        — 设置
│   └── security/BiometricLockGate — 生物识别锁
├── editor/
│   ├── DocumentEditorPanel — 文档编辑器
│   ├── DocumentDiffPanel   — 文档差异对比
│   ├── RawJsonEditor       — 原始 JSON 编辑
│   ├── GeoMapPickerDialog  — 地理坐标选择
│   └── JsonCodeAssist      — JSON 代码辅助
├── ClensComponents      — 通用 UI 组件
├── ClensViewModel       — 全局 ViewModel
├── ClensControllers     — 控制器注册
├── ClensTheme           — 主题
└── ClensActionRunner    — 操作执行器
```

---

## 二、功能模块详解

### 1. 连接管理（ConnectionsPanel）

| 功能 | 说明 |
|------|------|
| 加密连接档案 | 保存 MongoDB 连接配置，支持加密存储 |
| URI 连接 | 直接输入 MongoDB URI 连接 |
| 主机表单连接 | 逐字段填写主机/端口/认证信息 |
| 测试连接 | 连接前校验配置是否可达 |
| 只读连接档案 | 标记为只读，防止误操作 |
| 剪贴板/QR 导入 | 从剪贴板或二维码导入连接配置 |
| 会话健康监控 | 实时显示连接状态，断线自动重连条 |
| 本地网络权限 | Android 本地网络发现权限管理 |

### 2. 浏览/数据管理（BrowsePanel + BrowseController）

| 功能 | 说明 |
|------|------|
| 浏览数据库/集合 | 树形浏览数据库和集合层级 |
| 创建/重命名/删除 | 数据库和集合的 CRUD |
| 文档插入 | 添加新文档 |
| 文档替换 | 替换整个文档 |
| 文档更新 | 部分字段更新 |
| 文档删除 | 删除文档，含批量选择 |
| 结构化编辑器 | 移动端友好的字段级编辑 |
| 路径导航（Breadcrumb） | 数据库 > 集合 > 文档的层级导航 |
| 文档卡片流 | 卡片式文档浏览 |
| 集合统计速览 | 文档数、索引大小等统计信息 |
| 排序选择器 | 字段排序支持 |
| 批量选择操作 | 多选文档后批量操作 |

### 3. 查询（QueryPanel + QueryController）

| 功能 | 说明 |
|------|------|
| Find 查询 | 标准 MongoDB find 查询 |
| Aggregate 聚合管道 | 聚合管道构建与执行 |
| Explain 执行计划 | 查询执行计划分析 |
| 可视化查询构建器 | 图形化构建查询条件 |
| 聚合模板 | 预置聚合管道模板 |
| 查询历史 | 自动保存查询历史 |
| 查询收藏夹 | 保存常用查询 |
| 分页 | 查询结果分页浏览 |

### 4. 管理（AdminPanel + AdminController）

| 功能 | 说明 |
|------|------|
| 服务器概览 | 服务器基本信息 |
| 索引管理 | 创建/列表/删除索引 |
| currentOps | 查看当前运行的操作 |
| killOp | 终止指定操作 |
| 操作计数器图表 | 操作频率可视化图表 |
| 当前操作过滤 | 按条件过滤 currentOps |
| 集合 Validator 管理 | 管理集合文档验证规则 |

### 5. 高级功能（AdvancedPanel + AdvancedController）

| 功能 | 说明 |
|------|------|
| GridFS 上传/下载/删除 | 大文件存储管理 |
| Change Stream 监听 | 实时变更流监听 |
| 用户 CRUD | 数据库用户创建/删除/修改 |
| 角色 CRUD | 数据库角色管理 |
| 文件导入 | 导入 JSON / CSV 文件 |
| 字段映射 | 导入时字段映射配置 |
| 文件导出 | 导出为 JSON / CSV / Extended-JSON lines (.jsonl) |
| 本地暂存队列 | 网络失败时暂存操作，自动重试 |
| 离线快照 | 保存当前过滤结果，离线只读浏览 |

### 6. 文档编辑器（editor/）

| 功能 | 说明 |
|------|------|
| 文档编辑器 | 结构化字段编辑（支持 10+ 数据类型） |
| 差异对比 | 文档修改前后对比，逐字段展示差异 |
| 原始 JSON 编辑 | 直接编辑 JSON 文本 |
| 地理坐标选择器 | 地图选取经纬度（OSM WebView） |
| JSON 代码辅助 | 字段建议、代码补全 |
| 文档节点模型 | 树形文档节点表示 |

### 7. 数据导出（export/）

| 功能 | 说明 |
|------|------|
| JSON 导出 | 标准 JSON 格式 |
| CSV 导出 | 表格格式 |
| Extended-JSON lines | .jsonl 格式，支持 MongoDB 扩展类型 |
| Android Share Sheet | 通过系统分享导出 |

### 8. 数据导入（importdata/）

| 功能 | 说明 |
|------|------|
| JSON 解析导入 | 标准 JSON 文件解析并批量插入 |
| CSV 解析导入 | 含表头解析 |
| 字段映射 | 预览字段并映射到目标集合字段 |
| 批量插入 | 分块导入（50 条/块），支持重试 |

### 9. 本地存储（storage/）

| 功能 | 说明 |
|------|------|
| 加密连接档案 | 连接配置加密存储 |
| 文档草稿 | 编辑中的文档自动保存草稿 |
| 离线快照 | 查询结果离线保存 |
| 操作计数归档 | 操作计数器历史归档 |
| 暂存队列 | 失败的写入操作暂存，网络恢复后自动重试 |
| 安全偏好设置 | 加密的偏好设置存储 |

### 10. 安全（security/）

| 功能 | 说明 |
|------|------|
| 生物识别应用锁 | 指纹/面部解锁 |
| 分级危险确认 | 不同级别的操作需要不同确认 |
| 明文传输风险提示 | 检测非 TLS 连接并警告 |
| 密钥转换 | 密钥文件格式转换 |
| 本地审计日志 | 记录破坏性操作 |

### 11. 设置（SettingsPanel）

| 功能 | 说明 |
|------|------|
| 主题切换 | 浅色/深色/跟随系统 |
| 生物识别锁 | 开关应用锁 |
| 列表密度 | 文档列表显示密度调整 |

### 12. 通用组件（ClensComponents）

| 组件 | 说明 |
|------|------|
| StatusBanner | 连接状态横幅 |
| WarningBanner | 警告横幅 |
| StatusPill | 状态标签 |
| SectionTitle | 区块标题 |
| InfoCard | 信息卡片 |
| FlagRow | 复选框行 |
| PanelColumn | 面板列容器 |

---

## 三、核心能力矩阵

| 能力分类 | 具体能力 | 状态 |
|---------|---------|------|
| **连接** | URI 连接、表单连接、加密存储、测试连接、只读模式 | ✅ |
| **CRUD** | 增删改查、批量操作、结构化编辑器 | ✅ |
| **查询** | Find、Aggregate、Explain、可视化构建、历史/收藏 | ✅ |
| **索引** | 创建、列表、删除 | ✅ |
| **管理** | 服务器概览、currentOps、killOp、Validator | ✅ |
| **安全** | 生物识别锁、TLS 检测、审计日志、加密存储 | ✅ |
| **高级** | GridFS、Change Stream、用户/角色管理 | ✅ |
| **导入** | JSON、CSV、字段映射、批量分块 | ✅ |
| **导出** | JSON、CSV、JSONL、系统分享 | ✅ |
| **离线** | 离线快照、暂存队列、自动重试 | ✅ |
| **编辑** | 结构化编辑、差异对比、原始 JSON、地理坐标 | ✅ |
| **平台** | 本地网络权限、生物识别、文件存储适配 | ✅ |

---

## 四、技术栈

| 层 | 技术 |
|----|------|
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose + Material3 |
| 数据库 | MongoDB（通过 MongoJavaDriver） |
| 本地存储 | Room + DataStore + 加密存储 |
| 网络 | MongoJavaDriver + SSH Tunnel |
| 安全 | Android Biometric API + 加密存储 |
| 构建 | Gradle Kotlin DSL |
| CI | GitHub Actions（禁止本地构建） |
| 测试 | JUnit + Compose Test |

---

*整理日期：2026-08-01*