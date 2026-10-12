# Cmd/PowerShell GUI

> 一个面向 Windows 的 `cmd.exe` / `powershell.exe` 图形化前端 —— 既能像终端一样直接敲命令，
> 又能像 IDE 一样编辑、检查、补全命令脚本。

[![Language](https://img.shields.io/badge/language-Java-orange.svg)](#技术选型说明)
[![UI](https://img.shields.io/badge/UI-Swing-blue.svg)](#技术选型说明)
[![Platform](https://img.shields.io/badge/platform-Windows-lightgrey.svg)](#环境要求)
[![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)](#构建与运行)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

![编辑器与危险命令检查](docs/screenshot-editor.png)

---

## 这个项目解决什么问题

平时用 `cmd` 或 PowerShell，总会遇到这些麻烦：

* 一次只能敲一行，想改上一条命令要按半天方向键；
* 命令长了、复杂了，写错只能整行重来；
* `del /f /s /q`、`Remove-Item -Recurse -Force` 这类**危险命令没有任何二次确认**；
* 想保存一组常用命令，只能另存成 `.bat`，却没有编辑器辅助。

本项目把「终端」和「代码编辑器」合二为一：你面对的是一个带标签页的代码编辑区，
把命令当代码写（多行、注释、语法高亮、补全、错误提示），点一下 **运行** 就交给真正的
`cmd.exe` / `powershell.exe` 执行，输出实时回显在下方控制台。

## 功能一览

| 能力 | 说明 |
| --- | --- |
| **双引擎** | 原生调用 `cmd.exe` 与 `powershell.exe`，不是模拟器；工具栏下拉框随标签页切换 |
| **常驻会话** | 每个标签页一个独立进程，`cd`、`set`、PowerShell 变量在多次运行之间保持 |
| **多标签页** | 每个标签页独立会话；标签标题带 `*` 表示未保存；中键或 × 关闭 |
| **单条 / 批量执行** | `Ctrl+Enter` 运行选中或整个脚本，`Ctrl+Shift+Enter` 只运行全部，`Ctrl+L` 只运行光标所在那条命令 |
| **真实退出码** | 每次运行结束都会报告解释器给出的退出码，失败时标红 |
| **语法高亮** | 命令、关键字、开关、参数、变量、字符串、数字、运算符、标签、注释分色显示 |
| **IntelliSense** | 96 个 cmd 命令及其开关、163 个 PowerShell cmdlet 与关键字、环境变量、自动变量，边打边提示，`Ctrl+Space` 手动触发 |
| **路径补全** | 输入 `build\cp` 之类的前缀时，按工作区目录列出文件和文件夹（含大小与日期） |
| **参数提示** | 光标进入某条命令的参数区时，浮动显示该命令的用途和可用开关 |
| **静态检查** | 40 条规则实时检查，问题列表可双击跳转到对应行 |
| **危险命令二次确认** | 删除、格式化、关机、改权限、绕过策略等操作，运行前弹窗逐条列出并默认「取消」 |
| **工作区切换** | 图形化选目录，等价于 `cd /d`，并同步到所有已开启的会话 |
| **管理员模式** | 一键以管理员身份重启（自动处理引号、未保存提醒、UAC 被拒绝的提示） |
| **文件操作** | 新建 / 打开 / 保存 `.cmd`、`.bat`、`.ps1`，未保存时关闭会提醒 |
| **中文 / English** | 界面默认跟随系统语言，可在「视图 → 语言」中即时切换，无需重启；命令说明也已双语化 |
| **函数签名提示** | 光标在命令参数区时，状态栏显示该命令的用途与可用开关 |
| **单行命令输入** | 可在输出面板底部直接输入单条命令并回车执行，支持上下键调出历史 |
| **字体可选** | 「视图 → 字体」可分别为编辑器与控制台选择字体、字形和字号，带实时预览 |
| **命令拼写检查** | 输入 `exyzxo` 这类不存在的命令时给出波浪线与问题提示 |
| **桌面应用** | 纯 JDK 实现，可用 `jpackage` 打包成免安装 `.exe`，目标机器无需安装 Java |

![IntelliSense 补全](docs/screenshot-intellisense.png)

## 环境要求

* Windows 10 / 11
* **JDK 17 或更高**（开发使用 JDK 27 验证）
* 不需要 Maven / Gradle，不需要联网下载任何第三方依赖

## 构建与运行

```powershell
.\build.ps1 -Clean        # 编译并打包 -> build\cpgui.jar
.\build.ps1 -Run          # 编译后直接启动
```

习惯用 `cmd.exe`：

```bat
build.bat
run.bat
```

构建脚本会在 `JAVA_HOME`、`D:\Java`、`C:\Program Files\Java` 等常见位置寻找 JDK，
也可以显式指定：

```powershell
$env:JAVA_HOME = "D:\Java\jdk-27"
.\build.ps1
```

## 打包成免安装 exe

```powershell
.\package.ps1 -Clean
# 产物：dist\CmdPowerShellGUI\CmdPowerShellGUI.exe（约 75 MB，含精简运行时）
```

`package.ps1` 调用 JDK 自带的 `jpackage`，并且**在打包时用 Java2D 现场画出图标**再转成
`.ico`，所以仓库里不需要存放任何二进制素材。目标机器上不装 Java 也能直接双击运行。

## 快捷键

| 快捷键 | 作用 |
| --- | --- |
| `Ctrl+Enter` | 运行选中内容，没有选中则运行整个脚本 |
| `Ctrl+Shift+Enter` | 运行整个脚本（忽略选中） |
| `Ctrl+L` | 运行光标所在的那条命令（自动识别 `^` / 反引号续行） |
| `F2` | 停止当前标签页的解释器 |
| `Ctrl+Space` | 手动触发补全 |
| `↑` `↓` `PgUp` `PgDn` / `Enter` / `Tab` / `Esc` | 补全弹出时选择、接受、关闭 |
| `Ctrl+T` / `Ctrl+W` | 新建 / 关闭标签页 |
| `Ctrl+O` / `Ctrl+S` / `Ctrl+Shift+S` | 打开 / 保存 / 另存为 |
| `Ctrl+K` | 选择工作区目录 |
| `Ctrl+=` / `Ctrl+-` | 放大 / 缩小编辑器字号 |
| `Ctrl+Shift+F` | 选择编辑器与控制台字体 |
| 回车 | 在编辑器内始终换行；在输出面板底部则执行输入的命令 |
| `↑` `↓`（输出面板） | 调出此前输入过的命令 |

## 目录结构

```
cmd-powershell-GUI/
├── build.ps1 / build.bat / run.bat     构建与运行
├── package.ps1                         打包成免安装 exe（jpackage）
├── src/main/java/com/dogepopcat/cpgui/
│   ├── Main.java                       程序入口
│   ├── AppInfo.java                    名称 / 版本
│   ├── analysis/                       ScriptParser、ShellLexer、DangerAnalyzer
│   ├── completion/                     补全目录、候选计算、路径补全、参数提示
│   ├── io/                             ScriptFile：按解释器选择编码读写脚本
│   ├── shell/                          ShellSession、SessionRegistry、ConsoleCharset
│   ├── util/                           CommandLine：还原当前进程命令行
│   └── ui/                             窗口、标签页、控制台、高亮、诊断、补全弹窗
├── tools/dev/                          开发期验证工具（不参与打包）
├── docs/                               README 用截图
└── build/ dist/                        构建产物（已被 .gitignore 忽略）
```

## 技术选型说明

选择 **Java + Swing** 而不是 Electron 之类方案：

1. **真正的桌面端应用**。Swing 直接调用 Win32 原生窗口，不内嵌浏览器内核，不是「网页套壳」。
2. **零外部依赖**。Swing 属于 JDK 自带的 `java.desktop` 模块，clone 下来就能编译运行，
   不依赖 Maven 中央仓库，也没有 `node_modules`。
3. **打包体积与启动速度**。`jpackage` 可以把运行时一起打包成免安装 exe，
   用户机器上不装 Java 也能跑。
4. **文本处理能力强**。词法分析、补全候选、静态检查这些「仿 IDE」功能，用 Java 实现更清晰、更安全。

## 实现上几个值得一提的细节

这些坑都是先写探针程序验证、再落到代码里的，不是拍脑袋决定的：

* **控制台代码页**。`cmd.exe` / `powershell.exe` 输出到管道时使用**控制台 OEM 代码页**
  （中文系统是 936），而不是 UTF-8。程序启动时执行 `chcp` 读出代码页并映射成 Java 字符集，
  否则中文路径全是乱码。（顺带发现：`chcp 65001` 重定向到管道会让 `cmd` 直接退出，所以不能用。）
* **命令结束标记**。管道里没有提示符可以等待，所以每条命令后面追加一句带序号的标记命令，
  读到标记才算这条命令跑完，同时拿到真实退出码。
* **cmd 的多余空行**。`cmd.exe` 在每次打印提示符**之前**都会先输出一个空行，
  程序会把「紧挨着提示符的空行」丢掉，但保留 `echo.` 这种真正的空行输出。
* **脚本编码**。PowerShell 只有在 `.ps1` 带 BOM 时才按 UTF-8 读取，`cmd` 则按控制台代码页读
  `.bat`。因此 `.ps1` 写成「UTF-8 + BOM」，`.cmd` 写成控制台代码页；遇到代码页表示不了的字符
  会明确提示而不是悄悄替换成 `?`。
* **换行符**。Swing 文档内部统一是 `LF`，保存时统一转回 `CRLF`，因为 `cmd.exe` 是按行解析批处理的。
* **管理员重启**。Windows 无法提升已运行进程的权限，只能重新拉起一份；
  程序会还原当前进程的完整命令行，并把可执行文件与参数通过**环境变量**传给 PowerShell，
  避开所有层的引号转义规则。
* **布局**。`JSplitPane` 的分隔条初始位置来自两侧组件的 preferred size，
  空编辑器时会被挤到很窄，所以在窗口第一次拿到真实尺寸后显式设置一次比例。
* **工作目录**。从快捷方式或开始菜单启动时，Windows 会把工作目录设成 `C:\Windows\System32`。
  程序会拒绝系统目录，改为回退到用户主目录——否则「另存为」和 `cd` 的默认位置就在系统目录里。
* **查询与运行必须区分**。读取会话当前目录也是一条命令，但它属于内部查询：
  如果它也触发「命令完成」回调，而回调又要读取目录，就会无限递归。查询因此不发完成通知。
* **静态初始化顺序**。翻译表由其后声明的数组构建，静态初始化按声明顺序执行，
  结果是一旦用到补全就抛异常。声明顺序在这里是有语义的。

## 开发期验证工具

`tools/dev/` 下是一组开发期工具（**不参与打包**，`src/` 也不依赖它们）：

| 工具 | 验证内容 |
| --- | --- |
| `dev.SessionProbe` | 直接驱动真实解释器，打印原始输出；当年就是靠它确认了代码页、提示符标记与退出码协议 |
| `dev.ParserProbe` | 打印脚本切分结果与命中的危险规则 |
| `dev.CompletionProbe` | 补全候选、路径补全、参数提示的断言 |
| `dev.CompletionGuiProbe` | 通过真实窗口验证补全弹窗与「接受」行为 |
| `dev.DangerProbe` | 用替身回答确认弹窗，验证危险命令拦截确实生效 |
| `dev.FileProbe` | 编码往返、CRLF、脏标记、打开保存 |
| `dev.AdminProbe` | 还原命令行、`Start-Process` 通道 |
| `dev.GuiDemo` | 打开真实窗口并填入示例脚本，用于人工查看与截图 |
| `screenshot.ps1` | 启动、置顶（含模态对话框）并截图 |

用法见 [tools/dev/README.md](tools/dev/README.md)。

> 安全提示：`dev.DangerProbe` 与 `GuiDemo danger` 里的危险命令都指向**不存在的临时路径**，
> 且 `GuiDemo` 不会自动执行示例。修改这些脚本时请保持这一点——确认弹窗一旦被接受，
> 命令是真的会执行的。

## 中文 / English

界面默认跟随系统语言（中文 Windows 上即为中文），可在 **视图 → 语言** 中即时切换。
应用自身显示的所有文字——菜单、工具栏、状态栏、问题列表、对话框、危险命令说明、
命令与开关的帮助文字——都由 `i18n/Msg` 统一提供中英两份，缺少翻译会直接编译报错。

> 说明：`cmd.exe` 与 PowerShell 自身输出的文字随 Windows 语言而定，那部分不属于本程序。

一次提交只做一件最小的事，每一步都可编译、可运行：

| # | 提交 | 内容 |
| --- | --- | --- |
| 1 | `feat: project skeleton…` | 构建脚本 + 空窗口 |
| 2 | `feat: main window with editor tabs…` | 标签页、行号、控制台、状态栏 |
| 3 | `feat: run scripts in a persistent interpreter session` | 常驻会话、运行/停止、退出码 |
| 4 | `feat: graphical workspace selection` | 图形化 `cd` |
| 5 | `feat: split scripts into commands…` | 命令切分、按行/全部运行 |
| 6 | `feat: syntax highlighting…` | 词法分析与高亮 |
| 7 | `feat: detect dangerous commands…` | 危险命令检查 + 问题列表 + 波浪线 |
| 8 | `feat: confirm before running a dangerous command` | 运行前二次确认 |
| 9 | `feat: IntelliSense completion…` | 补全弹窗 |
| 10 | `feat: complete file paths and hint command parameters` | 路径补全 + 参数提示 |
| 11 | `feat: open, save and protect unsaved scripts` | 文件操作与编码处理 |
| 12 | `feat: restart the application with administrator rights` | 管理员模式 |
| 13 | `build: package as a self-contained Windows application` | jpackage 打包与文档 |
| 14 | `fix: stop the hint from taking over the editor` | 提示只在输入命令名时出现、Enter 可换行、虚化提示 |
| 15 | `feat: make the working directory obvious and safe` | 工作目录栏、拒绝 System32 默认值 |
| 16 | `feat: report commands that do not exist` | 未知命令检查 |
| 17 | `style: give the tool bar buttons an even size and a visible edge` | 工具栏等宽按钮 |
| 18 | `feat: administrator mode opens a second window and shows its state` | 管理员模式新开窗口并按状态着色 |
| 19 | `feat: type single commands straight into the console` | 控制台单行输入 |
| 20 | `feat: choose the editor and console font` | 字体选择 |
| 21 | `feat: complete Chinese and English interface` | 双语界面 |
| 22 | `feat: translate the command catalogue as well` | 命令说明双语化 |

## 贡献与提交规范

```
feat:     新功能
fix:      修 bug
docs:     只改文档
style:    格式调整，不影响逻辑
refactor: 重构，不改变行为
build:    构建脚本 / 打包
test:     测试
```

## 许可证

[MIT](LICENSE)
