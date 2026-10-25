# 「不背单词」v5.11.7 背诵逻辑完整逆向分析报告

> 分析对象：不背单词官方 APK `is_cool_lexis_5_11_7_1791969587289.apk`（137MB，2026-10 官网下载）
> 分析方式：静态分析（资源字符串表、DEX 字符串表、类结构、方法调用图、H5 学习页 JS）
> 分析日期：2026-10-25
> 说明：APK 使用爱加密(ijiami)加固，核心业务 DEX 被加密存放于 `assets/ijiami.dat`，本报告的结论来自
> 可见 DEX 的类/方法结构、resources.arsc 全量 UI 文案、H5 页面交互代码与公开资料交叉验证。

---

## 一、总体架构

| 组成 | 证据 | 说明 |
|---|---|---|
| 应用主包 | `cn.com.langeasy.LangEasyLexis.*` | 类名**未混淆**，业务结构清晰可读 |
| 记忆调度引擎 | `coreengine/BBCoreEngine`、`BBCoreEngineInMemory`、`LeitnerCardInMemoryImp` | **莱特纳记忆盒**在内存中的实现 |
| 单词进度模型 | `bean/BBWordProcess` + `database/BBWordProcessDao` | 每词一条进度记录（等级/场景/时间） |
| 学习计划 | `util/LearnPlanManager`、`bean/LearnPlanDataSnap`、`webservice/LearnPlanService` | 学习节奏/复习节奏/计划同步 |
| 学习界面 | `activity/NewLearnActivity`、`NewReviewActivity`、`NewLearnAndReviewParentActivity` | 新版学习/复习合一界面 |
| 拼写检查 | `activity/spellcheck/LRSpellCheckHelper`、`SpellCheckFragment` | 拼写题型 |
| 题型组件 | `widget/card/PhoneticView`、`widget/component/WordChoiceSelView`、`CustomChoiceGroupView` | 选择题/音标组件 |
| 词进度显示 | `NewLearnActivity.getWordMaxLevel / setDotLevel`、`widget/component/ProgressLevelView` | 每词等级小圆点 |
| 数据库 | `lib/arm64-v8a/libsqlcipher.so` + "Android SQLite API based on SQLCipher" | 词库/进度库 SQLCipher 加密 |
| 学习内容 | 例句 H5（`exam.html`、`interpret.html`、`collins.js`柯林斯、`lexis-select.js`） | 情景例句、点词查义 |
| 打卡/统计 | `events/CheckInEvent`、`widget/BBCheckIn`、`util/BBStatistician` | 学习日历与打卡 |

## 二、学习流（新词）

来自 UI 文案 + NewLearnActivity 方法结构：

1. **新词首现**：显示单词（自动发音、音标 `PhoneticView`）。文案：
   - “先回想词义再选择，想不起来「看答案」”
   - “瞬间想起词义，选「认识」” / “思考后想起词义，选「模糊」” / 「忘记了」
   - `lookAnswer()`（看答案）→ 内部调用引擎 `BBCoreEngineInMemory.iDontKnow()`，与「忘记了」同路（文案“已改为「忘记了」”）
2. **三级自评处理**：`doKnownProcess()`（认识）/ `doUnKnownProcess()`（不认识）两条处理链，随后 `showWordProgressTips()` 更新词进度提示。
3. **释义展示**：“选择是否认识后即显示释义”，同时展示双语例句、词组搭配、词根词缀、派生词、柯林斯释义。
   - 例句交互（H5 `lexis-select.js`）：点句中任意单词 → `cmd=touchOnWord/selectedWord` 查义 → “点击例句中的单词查询词义，再点击 + 将其加为生词”。
4. **题型闯关**：选择题（词→义 / 义→词 / 听音选义“听音选义题型”、`isChoiceTypeV2`）+ 拼写测试（`LRSpellCheckHelper`，“辨识后拼写，更能加强记忆哦”）。首次答错有专门处理 `onPostChooseFirstClickWrong()`。
5. **连续答对制通关**（关键算法证据）：
   - “连续答对 2 次，再答对 1 次就学完本词啦”
   - “答对 1 次，连续答对 3 次完成本词学习” / “连续答对 4 次完成本词学习”（设置可选 3 或 4）
   - “答对次数已清零，还需连续答对 3 次” —— **答错即连对清零**
   - “本词最后一关：请在无提示的情况下判断” —— 最后一关无提示
6. **完成学习**：“本词学习完成，已进入复习队列”。

## 三、复习流（莱特纳记忆盒）

核心类 `LeitnerCardInMemoryImp`（名字即证据）结合文案：

1. 每个单词对应一张“卡片”，处于某个**盒/等级**（`BBWordProcess.getLevel`，UI 上为 `ProgressLevelView` 小圆点，`getWordMaxLevel` 为满级）。
2. 复习到期进入「今日复习」（`近日复习/今日学习&复习`），到期顺序即队列。
3. 复习题：回忆自评（认识/模糊/忘记了）→ 选择/拼写验证；答对升盒拉长间隔，答错回炉首盒当日重现。
4. **毕业机制**：“通过多轮复习达到掌握标准后，系统自动判断单词为复习完成，不再安排复习”（满级自动完成）。
5. **间隔阶梯**（设置项“新学单词首次复习时间”存在 + 艾宾浩斯宣传）：精确常量在加密 DEX 中不可见，公开行为表现为当日首次重现 → 按天扩张（与经典艾宾浩斯 1/2/4/7/15 曲线一致的再体验）。
6. **标熟**：“标熟后不再安排学习&复习”（`已标熟`、`确定将选中的 %d 个单词标为熟词吗`）。
7. **换词书不丢进度**：“更换词书时，已学习的单词进度不受影响，系统会继续安排复习”（进度按词全局存储，`BBWordProcessDao`）。

## 四、节奏与分组

- “学习节奏”：每日新词量设置（`%1$d 词/组`、`%1$d 词/小结`）。
- “复习节奏”：每日复习量上限（可选“无限”）。
- **组学习**：新词按组切入（默认约 4 词/组），组间穿插复习与小结。
- “更改设置后将重新开启一组单词学习/复习”。

## 五、生词本与自定义词书（与本项目新功能对应）

- 原版生词来源文案：
  1. “点击例句中的单词查询并加为生词”
  2. “首页下滑查词并加入生词本”
  3. “从配套应用轻听英语中添加生词”
- “生词本也能当词书学”、“是否同时学习生词本内的单词？” —— 生词本作为特殊词书进入同一套学习/复习引擎。
- “了解如何自制词书” —— 官方支持自制词书（Web 工具）。
- 本项目将此能力泛化为**通用导入**：文件/粘贴导入任意词表生成自定义词书，进入同一 `SrsEngine`。

## 六、其他机制（本报告记录、Lite 版未全部复刻）

- 模式：预习、沉浸刷词、随身听（听写/听发音/听释义）、拼错词复习
- 内容增强：柯林斯星级释义、词根词缀图记、派生串记、真题词组
- 激励：酷币、打卡、晒成绩、学习日历、学习提醒
- 数据：SQLCipher 本地库 + 账号同步（`SynDataService`/`SyncChainService`）

## 七、逆向方法附记

| 步骤 | 工具/方法 | 产出 |
|---|---|---|
| APK 获取 | 官网 `langeasy.com.cn/open.action?go=6` 302 → 金山CDN | v5.11.7 原包 |
| 加固识别 | assets `ijiami.ajm/dat`、`libijmDataEncryption` | 爱加密加固 |
| 资源文案 | 自写 Python 解析 `resources.arsc` 全部字符串池 | 24,638 条 UI 文案（本报告引号内均为原文） |
| 类结构 | 自写 Python 解析 DEX（string_ids/type_ids/class_defs/encoded_method，注意 method_idx 增量编码） | 262 个业务类、方法名 |
| 调用图 | DEX code_item 指令扫描（const/const-wide/const-string/invoke） | `iDontKnow`、`getLevel` 等调用关系 |
| H5 逻辑 | `assets/js/lexis-select.js`、`lexis-func.js` | 例句点词查义交互 |

> 复刻实现映射见仓库 README 表格。间隔常量等未在静态分析中直接可见的参数，按公开资料与再体验取经典艾宾浩斯阶梯，并在代码中集中定义为 `LADDER`，便于按需调整。
