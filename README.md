# Cmd/PowerShell GUI

> 一个面向 Windows 的 `cmd.exe` / `powershell.exe` 图形化前端 —— 既能像终端一样直接敲命令，
> 又能像 IDE 一样编辑、检查和补全命令脚本。

[![Language](https://img.shields.io/badge/language-Java-orange.svg)](#)
[![UI](https://img.shields.io/badge/UI-Swing-blue.svg)](#)
[![Platform](https://img.shields.io/badge/platform-Windows-lightgrey.svg)](#)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

---

## 这个项目解决什么问题

平时用 `cmd` 或 PowerShell 时，我们经常遇到这些麻烦：

* 一次只能敲一行，想改上一条命令要按半天方向键；
* 命令太长、太复杂时，写错了只能整行重来；
* `del /f /s /q`、`Remove-Item -Recurse -Force` 这类**危险命令没有二次确认**；
* 想保存一组常用命令，只能另存成 `.bat`，却没有编辑器辅助。

本项目把「终端」和「代码编辑器」合二为一：你面对的是一个带标签页的代码编辑区，
把命令当代码写（支持多行、注释、语法高亮、补全、错误提示），点一下 **运行** 就交给真正的
`cmd.exe` / `powershell.exe` 执行，输出实时回显在下方控制台。

## 功能一览

| 能力 | 说明 | 状态 |
| --- | --- | --- |
| 双引擎 | 原生调用 `cmd.exe` 与 `powershell.exe`，不是模拟器 | ⏳ 计划中 |
| 多标签页 | 每个标签页一个独立会话，互不干扰 | ⏳ 计划中 |
| 单条 / 批量执行 | 一次执行一行，或一次执行整个脚本 | ⏳ 计划中 |
| 常驻会话 | `cd`、`set`、变量等状态在多次执行间保持 | ⏳ 计划中 |
| 语法高亮 | 关键字、字符串、变量、注释、重定向分色显示 | ⏳ 计划中 |
| IntelliSense | 命令 / 参数 / 文件路径补全，参数提示 | ⏳ 计划中 |
| 静态检查 | 边写边检查，问题列表可跳转 | ⏳ 计划中 |
| 危险命令警告 | 删除、格式化、关机等操作执行前弹窗确认 | ⏳ 计划中 |
| 工作区切换 | 图形化选择目录，等价于 `cd /d <路径>` | ⏳ 计划中 |
| 管理员模式 | 一键以管理员身份重启 | ⏳ 计划中 |
| 文件操作 | 新建 / 打开 / 保存 `.cmd`、`.ps1` 脚本 | ⏳ 计划中 |
| 桌面应用 | 纯 JDK 构建，可打包为免安装 `.exe` | 🚧 进行中 |

> 图例：✅ 已完成　🚧 进行中　⏳ 计划中

## 环境要求

* Windows 10 / 11
* **JDK 17 或更高版本**（开发使用 JDK 27 验证）
* 无需 Maven / Gradle，无需联网下载任何第三方依赖

## 构建与运行

```powershell
# 在项目根目录
.\build.ps1 -Clean        # 编译并打包 -> build\cpgui.jar
.\build.ps1 -Run          # 编译后直接启动
```

习惯用 `cmd.exe` 的话：

```bat
build.bat
run.bat
```

构建脚本会自动在 `JAVA_HOME`、`D:\Java`、`C:\Program Files\Java` 等常见位置寻找 JDK，
也可以用环境变量显式指定：

```powershell
$env:JAVA_HOME = "D:\Java\jdk-27"
.\build.ps1
```

## 目录结构

```
cmd-powershell-GUI/
├── build.ps1                 构建脚本（编译 + 打包）
├── build.bat / run.bat       cmd.exe 便捷入口
├── src/main/java/            源码
│   └── com/dogepopcat/cpgui/
│       ├── Main.java         程序入口
│       ├── AppInfo.java      名称 / 版本等元信息
│       └── ui/               界面层
│           ├── MainFrame.java  主窗口
│           └── Icons.java      代码绘制的图标
├── src/main/resources/       资源文件
└── build/                    构建产物（已被 .gitignore 忽略）
```

## 技术选型说明

选择 **Java + Swing** 而不是 Electron 之类方案，原因如下：

1. **真正的桌面端应用**。Swing 直接调用 Win32 原生窗口，不需要内嵌浏览器内核，
   不是"网页套壳"，符合项目"桌面端"的定位。
2. **零外部依赖**。Swing 属于 JDK 自带的 `java.desktop` 模块，项目不需要 Maven 中央仓库、
   不需要 `node_modules`，clone 下来就能编译运行——对使用者最友好。
3. **打包体积小**。用 JDK 自带的 `jpackage` 可以把运行时一起打包成免安装 `.exe`，
   用户机器上不装 Java 也能跑。
4. **文本处理能力强**。词法分析、补全候选、静态检查这些"仿 IDE"功能，
   用 Java 写比用 C++ 更快、更安全，也更容易维护。

## 开发路线图

每一次提交只做一件最小的事，保证历史清晰、每一步都可运行：

- [x] **#1** 项目骨架：构建脚本 + 空窗口
- [ ] **#2** 主界面布局：工具栏 + 标签页 + 控制台
- [ ] **#3** 命令执行：把文本交给 `cmd.exe` 并回显输出
- [ ] **#4** 常驻会话与退出码（`cmd` / PowerShell 双引擎）
- [ ] **#5** 图形化切换工作区（`cd`）
- [ ] **#6** 多行脚本解析与「运行全部 / 运行选中 / 运行当前行」
- [ ] **#7** 语法高亮
- [ ] **#8** 危险命令检测与确认弹窗
- [ ] **#9** IntelliSense：命令与参数补全
- [ ] **#10** IntelliSense：文件路径补全
- [ ] **#11** 新建 / 打开 / 保存脚本，未保存提醒
- [ ] **#12** 管理员模式重启
- [ ] **#13** `jpackage` 打包免安装 exe + 使用文档

## 贡献与提交规范

提交信息统一使用如下前缀，一次提交只聚焦一个最简单的功能：

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
