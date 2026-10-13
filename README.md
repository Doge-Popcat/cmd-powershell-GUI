# Cmd/PowerShell GUI

> 一个面向 Windows 的 `cmd.exe` / `powershell.exe` 图形化前端 —— 既能像终端一样直接敲命令，
> 又能像 IDE 一样编辑、检查、补全命令脚本。

[简体中文](README.md) | **[English](README.en.md)**

[![Language](https://img.shields.io/badge/language-Java-orange.svg)](#技术选型说明)
[![UI](https://img.shields.io/badge/UI-Swing-blue.svg)](#技术选型说明)
[![Platform](https://img.shields.io/badge/platform-Windows-lightgrey.svg)](#环境要求)
[![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)](#构建与运行)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

![编辑器与危险命令检查](docs/screenshot-editor.png)

---

## ⚠️ 关于本项目的生成方式

**本项目的全部代码、文档与资源均由 AI 生成。**

- 代码：由 AI 编写并逐轮修改，人类负责提出需求、反馈问题与验收。
- 文档：本 `README.md` 及 `docs/` 下的图片说明同样由 AI 生成。
- 验证方式：项目内自带 **20 个开发者检查**（见[测试](#测试)），其中 19 个输出明确的通过/失败判定，
  第 20 个（`SessionProbe`）打印真实解释器会话的报告。所有功能变更都必须先通过这些检查再提交，
  输出都是真实运行结果，不是推测。

请据此评估代码的适用性，并在生产环境使用前自行审查。

---

## 下载与安装

### 方式一：免安装压缩包（推荐）

下载 [`dist/CmdPowerShellGUI-0.1.0-win-x64.zip`](dist/CmdPowerShellGUI-0.1.0-win-x64.zip)（约 25 MB），
解压到任意目录，双击其中的 `CmdPowerShellGUI.exe` 即可运行。

* 自带裁剪过的 Java 运行时，**目标机器无需安装 Java**。
* 不写注册表、不装服务、不产生用户目录外的文件；删除文件夹即完全卸载。
* 支持 Windows 10 / 11 64 位。

### 方式二：安装程序（需要 WiX）

```powershell
.\package.ps1 -Clean -Installer
```

`jpackage` 生成 msi 安装包需要机器上装有 **WiX Toolset 3.0 或更高版本**。
未安装时脚本会明确提示并跳过，压缩包本身已是完整可用的发行形式。
WiX 下载：<https://github.com/wixtoolset/wix3/releases>

### 方式三：从源码运行

见下方[构建与运行](#构建与运行)。

---

## 这个项目解决什么问题

平时用 `cmd` 或 PowerShell，总会遇到这些麻烦：

* 一次只能敲一行，想改上一条命令要按半天方向键；
* 命令长一点、复杂一点，写错只能整行重来；
* `del /f /s /q`、`Remove-Item -Recurse -Force` 这类**危险命令没有任何二次确认**；
* 想把一组常用命令存下来，只能存成 `.bat`，却没有编辑器辅助；
* 中文环境下命令输出和脚本编码经常对不上，出现乱码。

本项目把「终端」和「代码编辑器」合二为一：你面对的是一个带标签页的代码编辑区，
把命令当代码写（多行、注释、语法高亮、补全、错误提示），点一下 **运行** 就交给真正的
`cmd.exe` / `powershell.exe` 执行，输出实时回显在下方控制台。

---

## 功能一览

| 能力 | 说明 |
| --- | --- |
| **双引擎** | 原生调用 `cmd.exe` 与 `powershell.exe`，不是模拟器；工具栏下拉框随标签页切换 |
| **常驻会话** | 每个标签页一个独立进程，`cd`、`set`、PowerShell 变量在多次运行之间保持 |
| **两种控制台模式** | **分块**：每次运行之间有分隔线，停止会丢弃解释器；**终端**：输出连续，解释器一直保留，停止只放弃本次运行，只有手动「重启会话」才换新解释器 |
| **交互式命令** | `time`、`set /p`、`pause`、`choice` 等会提问的命令可以正常应答：会话检测到它在等待输入后，下方输入的行直接作为回答送入 |
| **多标签页** | 每个标签页独立会话；标签标题带 `*` 表示未保存；中键或 × 关闭 |
| **单条 / 批量执行** | `Ctrl+Enter` 运行选中或整个脚本，`Ctrl+Shift+Enter` 只运行全部，`Ctrl+L` 只运行光标所在那条命令 |
| **单行模式** | 工具栏开关：每次只运行光标所在行并下移一行，成功的行整行标绿、失败标红 |
| **真实退出码** | 每次运行结束都报告解释器给出的退出码，失败时标红 |
| **语法高亮** | 命令、关键字、开关、参数、变量、字符串、数字、运算符、标签、注释分色显示，不是单一颜色 |
| **IntelliSense** | 96 个 cmd 命令及其开关、163 个 PowerShell cmdlet 与关键字、环境变量、自动变量；单候选显示虚化提示，多候选弹出列表，`Ctrl+Space` 手动触发 |
| **虚化提示 + Tab** | 灰字提示显示在当前光标后，**不区分大小写**；提示内容与列表高亮项始终一致，按 Tab 插入的正是屏幕上显示的那一项 |
| **路径补全** | 支持相对与绝对路径：`build\cp` 按工作区列出，`cd C:\Win` 补全为 `C:\Windows\` |
| **参数提示** | 光标进入某条命令的参数区时，状态栏显示该命令的用途和可用开关 |
| **静态检查** | 40 条规则实时检查，问题列表可双击跳转到对应行 |
| **危险命令二次确认** | 删除、格式化、关机、改权限、绕过策略等操作，运行前弹窗逐条列出并默认「取消」 |
| **撤销 / 重做** | `Ctrl+Z` / `Ctrl+Y`，工具栏另有 ↶ ↷ 按钮；语法高亮的颜色变化不会污染撤销栈 |
| **工作区切换** | 图形化选目录，等价于 `cd /d`，并同步到所有已开启的会话与左侧文件树 |
| **文件管理器** | 左侧文件树，按需展开目录；双击文件打开、双击目录展开；右键新建 / 删除 / 设为工作目录 |
| **语法参考** | `F1` 打开：可搜索的命令与参数说明，一键在控制台运行 `/?` 或 `Get-Help`，并可跳转官方文档（中英） |
| **管理员模式** | 一键再开一个管理员权限的窗口，免安装版同样可用；启动命令自动还原、引号自动处理，失败时弹窗给出 Windows 报出的原因 |
| **文件操作** | 新建 / 打开 / 保存 `.cmd`、`.bat`、`.ps1`，未保存时关闭会提醒 |
| **中文 / English** | 界面默认跟随系统语言，可在「视图 → 语言」中即时切换，无需重启；命令说明也已双语化 |
| **深色 / 浅色主题** | 默认深色，可在「视图 → 主题」切换浅色或跟随系统；按钮带立体边框 |
| **字体可选** | 「视图 → 字体」可分别为编辑器与控制台选择字体、字形和字号，带实时预览，并可一键恢复默认；无法显示中文的字体会被标注 |
| **命令拼写检查** | 输入 `exyzxo` 这类不存在的命令时给出波浪线与问题提示 |
| **桌面应用** | 纯 JDK 实现，可用 `jpackage` 打包成免安装 `.exe`，目标机器无需安装 Java |

![IntelliSense 补全](docs/screenshot-intellisense.png)

---

## 环境要求

* Windows 10 / 11
* **JDK 17 或更高**（开发使用 JDK 27 验证）
* 不需要 Maven / Gradle，不需要联网下载任何第三方依赖

---

## 构建与运行

```powershell
.\build.ps1 -Clean        # 编译并打包 -> build\cpgui.jar
.\build.ps1 -Run          # 编译后直接启动
```

习惯用 `cmd.exe`：

```bat
build.bat
run.bat
run-latest.bat            # 固定使用 D:\Java\jdk-27，并打印 jar 时间戳与 SHA256 便于比对
```

`run-latest.bat` 会在启动前打印 **JDK 路径、jar 构建时间与大小、SHA256、当前提交**，
用于确认「运行的是哪一个版本」。

### 打包

```powershell
.\package.ps1 -Clean -Zip            # 应用镜像 + 免安装压缩包
.\package.ps1 -Clean -Zip -Installer # 额外尝试生成 msi（需要 WiX）
```

产物：

| 文件 | 说明 |
| --- | --- |
| `dist\CmdPowerShellGUI\CmdPowerShellGUI.exe` | 免安装应用镜像（约 74 MB，自带运行时） |
| `dist\CmdPowerShellGUI-0.1.0-win-x64.zip` | 对外发布的压缩包（约 25 MB） |
| `dist\*.msi` | 安装包，仅在装有 WiX 时生成 |

注意两点：

* **目标目录已存在时 jpackage 会失败，必须带 `-Clean`。**
* 打包前**关闭正在运行的程序**，否则 `build\cpgui.jar` 被占用会导致打包失败。

### 管理员模式排错

免安装版没有控制台，管理员模式失败时看不到原因。让程序把这次启动的信息写成一份报告：

```powershell
dist\CmdPowerShellGUI\CmdPowerShellGUI.exe --elevation-report report.txt
type report.txt
```

报告里的 `relaunch executable` 就是按下「管理员权限」后会启动的程序，`relaunch executable exists`
必须是 `true`。免安装版的 `java.home/java.exe` **一定是 `false`**：jpackage 的运行时用
`jlink --strip-native-commands` 生成，不带 `java.exe`，只认它自己的启动器——早期版本正是
去启动那个并不存在的 `java.exe`，才导致免安装版的管理员模式打不开。

---

## 快捷键

| 快捷键 | 功能 |
| --- | --- |
| `Ctrl+Enter` | 运行选中内容，没有选中则运行整个脚本 |
| `Ctrl+Shift+Enter` | 运行整个脚本（忽略选中） |
| `Ctrl+L` | 只运行光标所在行 |
| `Ctrl+Space` | 手动触发补全 |
| `Tab` | 接受补全 / 虚化提示 |
| `↑` `↓` | 补全列表导航；列表未打开时在控制台输入行回溯历史 |
| `Ctrl+Z` / `Ctrl+Y` | 撤销 / 重做 |
| `Ctrl+N` | 新建标签页 |
| `Ctrl+O` / `Ctrl+S` | 打开 / 保存 |
| `Ctrl+K` | 选择工作目录 |
| `Ctrl+Shift+E` | 显示 / 隐藏文件管理器 |
| `F1` | 打开语法参考 |
| `Ctrl+=` / `Ctrl+-` | 放大 / 缩小编辑区字号 |

---

## 项目结构

```
src/main/java/com/dogepopcat/cpgui/
├── Main.java                    入口：设置外观、解析 --workspace 与 --elevation-report
├── AppInfo.java                 名称与版本
├── analysis/                    静态分析
│   ├── ScriptParser.java        按行切分脚本，注释识别，精确偏移
│   ├── ShellLexer.java          词法分类（命令/关键字/开关/参数/变量/字符串/数字/运算符/注释/标签）
│   ├── DangerAnalyzer.java      40 条危险与可疑规则
│   └── CommandResolver.java     命令是否存在（catalogue + 别名 + PATH×PATHEXT）
├── completion/                  补全
│   ├── CommandCatalog.java      cmd 命令/开关、PowerShell cmdlet/参数，双语描述
│   ├── CompletionProvider.java  上下文判定、候选筛选与替换范围
│   ├── PathCompleter.java       相对与绝对路径补全
│   └── ParameterHint.java       参数签名提示
├── shell/                       与解释器进程打交道
│   ├── ShellSession.java        常驻进程、结束标记协议、交互式输入、提交队列
│   ├── SessionRegistry.java     按标签页复用会话
│   ├── ShellKind.java           cmd / PowerShell 的显示名与可执行文件
│   ├── ConsoleCharset.java      读取 chcp 并映射到 Java 字符集
│   └── ConsolePalette.java      color 命令的 16 色表
├── io/
│   ├── ScriptFile.java          读写 .cmd/.bat/.ps1，处理 BOM 与编码不可表示字符
│   └── WorkspacePolicy.java     拒绝系统目录作为工作区
├── i18n/                        中英双语（Msg 枚举缺翻译会编译失败）
└── ui/                          Swing 界面（见下）
```

界面包关键类：`MainFrame`（窗口与动作）、`EditorTabs`/`EditorTab`（标签页与文本区）、
`CompletionController`/`CompletionPopup`/`GhostTextPane`（补全三件套）、
`SyntaxHighlighter`/`LineNumberView`/`DiagnosticsOverlay`/`SquigglePainter`（显示）、
`ConsolePane`（控制台与输入行）、`FileExplorer`（文件树）、`ProblemsPanel`（问题列表）、
`Theme`/`FontSupport`/`FontChooserDialog`（配色与字体）、`HelpWindow`（语法参考）、
`ConsoleMode`（分块 / 终端）。

---

## 测试

项目自带 20 个开发者检查，位于 `tools/dev/dev/`（不参与打包）。其中 19 个会打印 `RESULT: PASS/FAIL`，`SessionProbe` 打印会话报告。编译并运行：

```powershell
$javac = "D:\Java\jdk-27\bin\javac.exe"
& $javac "-J-Duser.language=en" -encoding UTF-8 -cp "build\classes" -d "build\tools" `
    (Get-ChildItem -Recurse "tools\dev" -Filter *.java | Select-Object -ExpandProperty FullName)

# 单个探针，例如
& "D:\Java\jdk-27\bin\java.exe" -cp "build\classes;build\tools" dev.AcceptanceProbe
```

| 探针 | 覆盖内容 |
| --- | --- |
| `AcceptanceProbe` | **总验收**：25 种 cmd 命令、14 种 PowerShell 命令（内置命令、开关、变量、`if/else`、`for`、`call`、`goto`、`type`、`findstr`、`more`、`sort`、`copy`、`ren`、`del`、`mkdir`、`attrib`、`whoami`、转义、管道、表达式、异常）外加全部功能面 |
| `CompletionProbe` / `CompletionGuiProbe` | 补全候选、虚化提示、Tab 接受、列表导航 |
| `GhostProbe` | 大小写不敏感、虚化提示与列表高亮一致 |
| `ConsoleProbe` | 单行输入、回显、历史、分块分隔线 |
| `HistoryProbe` | 历史回溯（含编辑器运行的命令）与草稿保留 |
| `InteractiveProbe` | `set /p` 端到端应答 |
| `TerminalModeProbe` | 终端模式跨「停止」保留会话、分块模式丢弃、重启会话 |
| `SingleLineProbe` | 单行模式逐行执行与行结果着色 |
| `UndoProbe` | 撤销 / 重做不受高亮污染 |
| `DangerProbe` / `LintProbe` | 危险规则与静态检查 |
| `ParserProbe` / `FileProbe` | 脚本解析与文件读写编码 |
| `WorkspaceProbe` / `AdminProbe` | 工作区策略；管理员启动通道（命令还原、免安装版启动器回退、引号传递、`Start-Process` 通道） |
| `FontProbe` / `ThemeProbe` | 字体可用性、三套配色与热切换 |
| `FixProbe` | 已修复缺陷的回归 |
| `SessionProbe` | 真实解释器会话（字符集、未完成运行） |

约定：

* GUI 探针必须显式 `System.exit`，否则不会退出。
* 断言文本的探针会先 `Messages.setLanguage(Language.ENGLISH)` 固定语言。
* 探针只操作 `D:\cpgui-*` 这类临时目录，不会碰你的真实文件。
* **需要比对界面时，探针必须先调用 `Main.applyLookAndFeel()`** —— 程序真正运行的是
  Windows 外观，直接 `new MainFrame()` 拿到的是跨平台外观，两者绘制行为不同。

---

## 实现要点

* **结束标记协议**。每次提交脚本时追加一条带序号的 `echo` 作为结束标记，读到它才算本次运行结束，
  从而拿到真实退出码。读取工作目录这类内部查询走同一通道但不触发「运行结束」回调，
  否则会在回调里再查询而无限递归。
* **交互式命令不能预置标记**。交互式命令从同一个输入流读取回答，预置的标记会被当成回答
  （这就是 `time` 报「系统无法接受输入的时间」的原因）。因此识别到会提问的命令时不预置标记，
  等回答处理完再补发一条用于收尾。
* **提交必须排队**。读取工作目录也占用解释器，若此时用户按下运行而程序直接以「忙」为由返回，
  这次运行就凭空消失了。会话把提交排队依次执行。
* **会话复用时必须换监听器**。会话跨多次运行复用，若一直持有创建时的监听器，
  第一次运行之后的每个回调都会发往第一次的接收者。
* **外观决定绘制行为**。Windows 外观会用系统主题直接绘制文件选择器的标签、按钮与快捷栏，
  设在组件上的颜色不被读取；因此文件选择器保持系统配色（两种主题下都清晰可读），
  并放在程序自己创建的窗口里，避免走外观自建对话框那条路径。
* **字体必须能显示当前语言**。物理字体（如 Consolas）不带中文回退，把默认字体换成它会
  让中文全变方框；默认字体要求同时覆盖中英文，中文 Windows 上即「新宋体」。
* **刷新外观会清空按键映射**。`updateComponentTreeUI` 会替换组件的 input/action map，
  所以只在主题真正变化时执行，并在之后重新安装补全与控制台的按键绑定。

---

## 已知限制

* 文件选择器在深色主题下仍是**浅色系统外观**——它的文字由系统绘制，无法改色；
  强行改成深色会导致黑底黑字，因此选择保持可读。
* 交互式命令目前只保证**单次提问**正确（`time`、`set /p`、`pause`、`choice`）。
  需要连续多次回答的命令，补发的收尾标记可能被第二个问题读走。
* 控制台是**单一连续输出**，没有多窗格分栏。
* 只支持 Windows；`cmd.exe` 与 `powershell.exe` 是硬依赖。
* 管理员模式不验证 UAC 弹窗本身（自动化无法点击「是」）；此外的链路——启动命令还原、
  免安装版启动器回退、引号传递、`Start-Process` 通道、传入的工作目录——都由 `AdminProbe`
  和 `--elevation-report` 覆盖。

---

## 技术选型说明

* **Java + Swing**：需求明确要求桌面应用且不使用 JavaScript。Java 标准库自带
  `ProcessBuilder`、Swing、`jpackage`，可以做到**零第三方依赖**，并且一次打包即可在
  没有安装 Java 的机器上运行。
* **不引入构建工具**：项目规模用 `javac` + `jar` 足够，`build.ps1` 约 120 行，
  避免为了编译一个不到 50 个源文件的项目引入 Maven/Gradle 生态。

---

## 许可

[MIT](LICENSE)

---

**English documentation: [README.en.md](README.en.md)**

---
