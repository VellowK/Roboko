# Roboko

Roboko 是一款以真实语境、AI 辅助整理和自适应复习为核心的个人词汇学习应用。

## 当前 MVP

- 首页今日学习概览与快速开始
- 学习条目、词义、语境、分类和复习进度已通过 Room 本地持久化
- 添加单词或短语时调用真实 Provider 生成结构化词义、例句和分类，确认后写入 Room
- 单词本支持按添加时间、首字母、熟练程度排序，并可切换正序/倒序
- 单词详情支持按当前单词本排序上下滑动切换，向上进入下一个词，向下返回上一个词
- 从词条详情追问 AI 时会携带引用卡片、释义和例句上下文
- 设置采用菜单入口，Provider 配置支持预设、自定义、OpenAI/Claude 格式和余额查询入口
- 一个单词页承载多个独立词义分支
- 一次一张卡的遮罩式背诵，支持提示、知道 / 不知道
- 答案展开、词义进度和今日完成统计
- AI 学习空间作为应用默认首屏
- 聊天消息采用 AI 左侧、用户右侧的不同颜色气泡
- AI 输入支持文字和 Android 语音识别，语音结果先回填后发送；录音时显示渐变遮罩和动态声纹动画
- AI Provider 预设：DeepSeek、ModelScope、火山 Agent Plan、火山 Coding Plan
- 设置采用无边框文字列表风格，包含通用设置、Provider 设置和关于软件
- 关于页支持 GitHub 仓库、版本/构建日期、GitHub latest release 检查和 APK 下载更新
- 通用设置支持 GitHub 官方、gh-proxy.net、gh-proxy.com 下载镜像选择

## 技术栈

Kotlin、Jetpack Compose、Material 3 fallback 组件。MIUIX 组件接入点保留在 UI 层，待项目确定 MIUIX 版本和 Maven 坐标后替换基础组件，不改变领域和状态层。

## 运行

使用 Android Studio 打开本目录，使用 JDK 17 同步并运行 `app`。当前版本为 `2.0.0`，学习条目和 AI 对话使用 Room 本地持久化，API Key 使用 Android Keystore 加密。

## 分层

- `app/src/main/java/com/roboko/app/MainActivity.kt`：领域模型、ReviewEngine、MockRepository、ViewModel 和 MVP 页面
- UI 通过 `AppUiState` 单向渲染；页面不直接访问数据层
- 复习进度集中由 `ReviewEngine` 计算，提示后的答题使用较小的进度变化
