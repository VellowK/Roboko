# Roboko

Roboko 是一款以真实语境、AI 辅助整理和自适应复习为核心的个人词汇学习应用。

## 当前 MVP

- 首页今日学习概览与快速开始
- 学习条目添加（本地 Mock AI 生成词义、语境和分类）
- 单词本按最近加入时间倒序展示
- 搜索单词、词义、中文翻译和语境
- 一个单词页承载多个独立词义分支
- 一次一张卡的遮罩式背诵，支持提示、知道 / 不知道
- 答案展开、词义进度和今日完成统计
- AI 学习空间的上下文追问 Mock

## 技术栈

Kotlin、Jetpack Compose、Material 3 fallback 组件。MIUIX 组件接入点保留在 UI 层，待项目确定 MIUIX 版本和 Maven 坐标后替换基础组件，不改变领域和状态层。

## 运行

使用 Android Studio 打开本目录，使用 JDK 17 同步并运行 `app`。当前版本为 `2.0.0`，数据使用内存 Mock Repository，应用重启后重置示例数据。

## 分层

- `app/src/main/java/com/roboko/app/MainActivity.kt`：领域模型、ReviewEngine、MockRepository、ViewModel 和 MVP 页面
- UI 通过 `AppUiState` 单向渲染；页面不直接访问数据层
- 复习进度集中由 `ReviewEngine` 计算，提示后的答题使用较小的进度变化
