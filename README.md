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
- 关于页显示实际安装版本，比较 GitHub latest release 版本后才提供更高正式版的 APK 下载更新
- 通用设置支持 GitHub 官方、gh-proxy.net、gh-proxy.com 下载镜像选择
- 整理词条时自动补全缺失的例句和译文，有原始语境时由应用保留原文；确认保存后留在当前页面
- AI 推荐词已添加时仅以背景色区分，不显示加号或“已添加”，避免重复添加

## 技术栈

Kotlin、Jetpack Compose、Material 3 fallback 组件。MIUIX 组件接入点保留在 UI 层，待项目确定 MIUIX 版本和 Maven 坐标后替换基础组件，不改变领域和状态层。

## 运行

使用 Android Studio 打开本目录，使用 JDK 17 同步并运行 `app`。当前版本为 `2.0.3`，学习条目和 AI 对话使用 Room 本地持久化，API Key 使用 Android Keystore 加密。

## 滚动性能与键盘验收

- AI 对话使用完整历史的惰性列表，不在上翻时按四条反复追加；后台缓存解析后的消息行，新增消息复用旧行与稳定 key。
- 单词本在后台建立搜索索引和排序结果，行只接收轻量不可变数据；搜索或排序切换不会在 UI 线程重新遍历全部语境。
- AI 顶部操作栏位于滚动区之外。键盘采用 `adjustResize` 与 IME insets，弹出时暂时隐藏底部导航；整条选中和局部选词均使用顶部复制、加入单词本、翻译、引用按钮。
- 请用 **JDK 17** 执行 `gradle :app:testDebugUnitTest :app:assemblePerformance`，或在 Android Studio 的 Build Variants 选择 `performance`。产物为 `app/build/outputs/apk/performance/app-performance.apk`；启用 R8 与资源优化、关闭 debuggable，沿用现有 debug 签名以保持覆盖安装兼容。当前 GitHub APK 采用此完整优化构建，尚未切换正式发行密钥，不用于应用商店生产发布。普通 `debug` 包仍可构建。
- 真机检查：长列表来回滑动、收到 AI 回复时阅读旧消息不被拉回、键盘打开后长按消息并再次长按选词/拖动范围，再点顶部四项操作；覆盖返回键、横屏、不同输入法和较大字体。
- 单元测试和构建不能证明实际帧率。若仍卡顿，应使用 Android Studio System Trace / Perfetto 对优化包记录帧耗时，按 UI 线程、文本测量或 GC 热点继续优化，而不是直接承诺流畅度。

## 分层

- `app/src/main/java/com/roboko/app/MainActivity.kt`：领域模型、ReviewEngine、LearningRepository、ViewModel 和页面
- `app/src/main/java/com/roboko/app/SttEngine.kt`：语音识别引擎抽象（系统识别 / 云端识别）
- `app/src/main/java/com/roboko/app/CloudVoiceInput.kt`：云端语音识别（OpenAI 转写接口 / 对话式音频接口）
- UI 通过 `AppUiState` 单向渲染；页面不直接访问数据层
- 复习进度集中由 `ReviewEngine` 计算，提示后的答题使用较小的进度变化
