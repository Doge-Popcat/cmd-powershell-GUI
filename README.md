# Cmd/PowerShell GUI

> 涓€涓潰鍚?Windows 鐨?`cmd.exe` / `powershell.exe` 鍥惧舰鍖栧墠绔?鈥斺€?鏃㈣兘鍍忕粓绔竴鏍风洿鎺ユ暡鍛戒护锛?> 鍙堣兘鍍?IDE 涓€鏍风紪杈戙€佹鏌ャ€佽ˉ鍏ㄥ懡浠よ剼鏈€?
[![Language](https://img.shields.io/badge/language-Java-orange.svg)](#鎶€鏈€夊瀷璇存槑)
[![UI](https://img.shields.io/badge/UI-Swing-blue.svg)](#鎶€鏈€夊瀷璇存槑)
[![Platform](https://img.shields.io/badge/platform-Windows-lightgrey.svg)](#鐜瑕佹眰)
[![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)](#鏋勫缓涓庤繍琛?
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

![缂栬緫鍣ㄤ笌鍗遍櫓鍛戒护妫€鏌(docs/screenshot-editor.png)

---

## 杩欎釜椤圭洰瑙ｅ喅浠€涔堥棶棰?
骞虫椂鐢?`cmd` 鎴?PowerShell锛屾€讳細閬囧埌杩欎簺楹荤儲锛?
* 涓€娆″彧鑳芥暡涓€琛岋紝鎯虫敼涓婁竴鏉″懡浠よ鎸夊崐澶╂柟鍚戦敭锛?* 鍛戒护闀夸簡銆佸鏉備簡锛屽啓閿欏彧鑳芥暣琛岄噸鏉ワ紱
* `del /f /s /q`銆乣Remove-Item -Recurse -Force` 杩欑被**鍗遍櫓鍛戒护娌℃湁浠讳綍浜屾纭**锛?* 鎯充繚瀛樹竴缁勫父鐢ㄥ懡浠わ紝鍙兘鍙﹀瓨鎴?`.bat`锛屽嵈娌℃湁缂栬緫鍣ㄨ緟鍔┿€?
鏈」鐩妸銆岀粓绔€嶅拰銆屼唬鐮佺紪杈戝櫒銆嶅悎浜屼负涓€锛氫綘闈㈠鐨勬槸涓€涓甫鏍囩椤电殑浠ｇ爜缂栬緫鍖猴紝
鎶婂懡浠ゅ綋浠ｇ爜鍐欙紙澶氳銆佹敞閲娿€佽娉曢珮浜€佽ˉ鍏ㄣ€侀敊璇彁绀猴級锛岀偣涓€涓?**杩愯** 灏变氦缁欑湡姝ｇ殑
`cmd.exe` / `powershell.exe` 鎵ц锛岃緭鍑哄疄鏃跺洖鏄惧湪涓嬫柟鎺у埗鍙般€?
## 鍔熻兘涓€瑙?
| 鑳藉姏 | 璇存槑 |
| --- | --- |
| **鍙屽紩鎿?* | 鍘熺敓璋冪敤 `cmd.exe` 涓?`powershell.exe`锛屼笉鏄ā鎷熷櫒锛涘伐鍏锋爮涓嬫媺妗嗛殢鏍囩椤靛垏鎹?|
| **甯搁┗浼氳瘽** | 姣忎釜鏍囩椤典竴涓嫭绔嬭繘绋嬶紝`cd`銆乣set`銆丳owerShell 鍙橀噺鍦ㄥ娆¤繍琛屼箣闂翠繚鎸?|
| **澶氭爣绛鹃〉** | 姣忎釜鏍囩椤电嫭绔嬩細璇濓紱鏍囩鏍囬甯?`*` 琛ㄧず鏈繚瀛橈紱涓敭鎴?脳 鍏抽棴 |
| **鍗曟潯 / 鎵归噺鎵ц** | `Ctrl+Enter` 杩愯閫変腑鎴栨暣涓剼鏈紝`Ctrl+Shift+Enter` 鍙繍琛屽叏閮紝`Ctrl+L` 鍙繍琛屽厜鏍囨墍鍦ㄩ偅鏉″懡浠?|
| **鐪熷疄閫€鍑虹爜** | 姣忔杩愯缁撴潫閮戒細鎶ュ憡瑙ｉ噴鍣ㄧ粰鍑虹殑閫€鍑虹爜锛屽け璐ユ椂鏍囩孩 |
| **璇硶楂樹寒** | 鍛戒护銆佸叧閿瓧銆佸紑鍏炽€佸弬鏁般€佸彉閲忋€佸瓧绗︿覆銆佹暟瀛椼€佽繍绠楃銆佹爣绛俱€佹敞閲婂垎鑹叉樉绀?|
| **IntelliSense** | 96 涓?cmd 鍛戒护鍙婂叾寮€鍏炽€?63 涓?PowerShell cmdlet 涓庡叧閿瓧銆佺幆澧冨彉閲忋€佽嚜鍔ㄥ彉閲忥紝杈规墦杈规彁绀猴紝`Ctrl+Space` 鎵嬪姩瑙﹀彂 |
| **璺緞琛ュ叏** | 杈撳叆 `build\cp` 涔嬬被鐨勫墠缂€鏃讹紝鎸夊伐浣滃尯鐩綍鍒楀嚭鏂囦欢鍜屾枃浠跺す锛堝惈澶у皬涓庢棩鏈燂級 |
| **鍙傛暟鎻愮ず** | 鍏夋爣杩涘叆鏌愭潯鍛戒护鐨勫弬鏁板尯鏃讹紝娴姩鏄剧ず璇ュ懡浠ょ殑鐢ㄩ€斿拰鍙敤寮€鍏?|
| **闈欐€佹鏌?* | 40 鏉¤鍒欏疄鏃舵鏌ワ紝闂鍒楄〃鍙弻鍑昏烦杞埌瀵瑰簲琛?|
| **鍗遍櫓鍛戒护浜屾纭** | 鍒犻櫎銆佹牸寮忓寲銆佸叧鏈恒€佹敼鏉冮檺銆佺粫杩囩瓥鐣ョ瓑鎿嶄綔锛岃繍琛屽墠寮圭獥閫愭潯鍒楀嚭骞堕粯璁ゃ€屽彇娑堛€?|
| **宸ヤ綔鍖哄垏鎹?* | 鍥惧舰鍖栭€夌洰褰曪紝绛変环浜?`cd /d`锛屽苟鍚屾鍒版墍鏈夊凡寮€鍚殑浼氳瘽 |
| **绠＄悊鍛樻ā寮?* | 涓€閿互绠＄悊鍛樿韩浠介噸鍚紙鑷姩澶勭悊寮曞彿銆佹湭淇濆瓨鎻愰啋銆乁AC 琚嫆缁濈殑鎻愮ず锛?|
| **鏂囦欢鎿嶄綔** | 鏂板缓 / 鎵撳紑 / 淇濆瓨 `.cmd`銆乣.bat`銆乣.ps1`锛屾湭淇濆瓨鏃跺叧闂細鎻愰啋 |
| **涓枃 / English** | 鐣岄潰榛樿璺熼殢绯荤粺璇█锛屽彲鍦ㄣ€岃鍥?鈫?璇█銆嶄腑鍗虫椂鍒囨崲锛屾棤闇€閲嶅惎锛涘懡浠よ鏄庝篃宸插弻璇寲 |
| **鍑芥暟绛惧悕鎻愮ず** | 鍏夋爣鍦ㄥ懡浠ゅ弬鏁板尯鏃讹紝鐘舵€佹爮鏄剧ず璇ュ懡浠ょ殑鐢ㄩ€斾笌鍙敤寮€鍏?|
| **鍗曡鍛戒护杈撳叆** | 鍙湪杈撳嚭闈㈡澘搴曢儴鐩存帴杈撳叆鍗曟潯鍛戒护骞跺洖杞︽墽琛岋紝鏀寔涓婁笅閿皟鍑哄巻鍙?|
| **瀛椾綋鍙€?* | 銆岃鍥?鈫?瀛椾綋銆嶅彲鍒嗗埆涓虹紪杈戝櫒涓庢帶鍒跺彴閫夋嫨瀛椾綋銆佸瓧褰㈠拰瀛楀彿锛屽甫瀹炴椂棰勮 |
| **鍛戒护鎷煎啓妫€鏌?* | 杈撳叆 `exyzxo` 杩欑被涓嶅瓨鍦ㄧ殑鍛戒护鏃剁粰鍑烘尝娴嚎涓庨棶棰樻彁绀?|
| **妗岄潰搴旂敤** | 绾?JDK 瀹炵幇锛屽彲鐢?`jpackage` 鎵撳寘鎴愬厤瀹夎 `.exe`锛岀洰鏍囨満鍣ㄦ棤闇€瀹夎 Java |

![IntelliSense 琛ュ叏](docs/screenshot-intellisense.png)

## 鐜瑕佹眰

* Windows 10 / 11
* **JDK 17 鎴栨洿楂?*锛堝紑鍙戜娇鐢?JDK 27 楠岃瘉锛?* 涓嶉渶瑕?Maven / Gradle锛屼笉闇€瑕佽仈缃戜笅杞戒换浣曠涓夋柟渚濊禆

## 鏋勫缓涓庤繍琛?
```powershell
.\build.ps1 -Clean        # 缂栬瘧骞舵墦鍖?-> build\cpgui.jar
.\build.ps1 -Run          # 缂栬瘧鍚庣洿鎺ュ惎鍔?```

涔犳儻鐢?`cmd.exe`锛?
```bat
build.bat
run.bat
```

鏋勫缓鑴氭湰浼氬湪 `JAVA_HOME`銆乣D:\Java`銆乣C:\Program Files\Java` 绛夊父瑙佷綅缃鎵?JDK锛?涔熷彲浠ユ樉寮忔寚瀹氾細

```powershell
$env:JAVA_HOME = "D:\Java\jdk-27"
.\build.ps1
```

## 鎵撳寘鎴愬厤瀹夎 exe

```powershell
.\package.ps1 -Clean
# 浜х墿锛歞ist\CmdPowerShellGUI\CmdPowerShellGUI.exe锛堢害 75 MB锛屽惈绮剧畝杩愯鏃讹級
```

`package.ps1` 璋冪敤 JDK 鑷甫鐨?`jpackage`锛屽苟涓?*鍦ㄦ墦鍖呮椂鐢?Java2D 鐜板満鐢诲嚭鍥炬爣**鍐嶈浆鎴?`.ico`锛屾墍浠ヤ粨搴撻噷涓嶉渶瑕佸瓨鏀句换浣曚簩杩涘埗绱犳潗銆傜洰鏍囨満鍣ㄤ笂涓嶈 Java 涔熻兘鐩存帴鍙屽嚮杩愯銆?
## 蹇嵎閿?
| 蹇嵎閿?| 浣滅敤 |
| --- | --- |
| `Ctrl+Enter` | 杩愯閫変腑鍐呭锛屾病鏈夐€変腑鍒欒繍琛屾暣涓剼鏈?|
| `Ctrl+Shift+Enter` | 杩愯鏁翠釜鑴氭湰锛堝拷鐣ラ€変腑锛?|
| `Ctrl+L` | 杩愯鍏夋爣鎵€鍦ㄧ殑閭ｆ潯鍛戒护锛堣嚜鍔ㄨ瘑鍒?`^` / 鍙嶅紩鍙风画琛岋級 |
| `F2` | 鍋滄褰撳墠鏍囩椤电殑瑙ｉ噴鍣?|
| `Ctrl+Space` | 鎵嬪姩瑙﹀彂琛ュ叏 |
| `鈫慲 `鈫揱 `PgUp` `PgDn` / `Enter` / `Tab` / `Esc` | 琛ュ叏寮瑰嚭鏃堕€夋嫨銆佹帴鍙椼€佸叧闂?|
| `Ctrl+T` / `Ctrl+W` | 鏂板缓 / 鍏抽棴鏍囩椤?|
| `Ctrl+O` / `Ctrl+S` / `Ctrl+Shift+S` | 鎵撳紑 / 淇濆瓨 / 鍙﹀瓨涓?|
| `Ctrl+K` | 閫夋嫨宸ヤ綔鍖虹洰褰?|
| `Ctrl+=` / `Ctrl+-` | 鏀惧ぇ / 缂╁皬缂栬緫鍣ㄥ瓧鍙?|
| `Ctrl+Shift+F` | 閫夋嫨缂栬緫鍣ㄤ笌鎺у埗鍙板瓧浣?|
| 鍥炶溅 | 鍦ㄧ紪杈戝櫒鍐呭缁堟崲琛岋紱鍦ㄨ緭鍑洪潰鏉垮簳閮ㄥ垯鎵ц杈撳叆鐨勫懡浠?|
| `鈫慲 `鈫揱锛堣緭鍑洪潰鏉匡級 | 璋冨嚭姝ゅ墠杈撳叆杩囩殑鍛戒护 |

## 鐩綍缁撴瀯

```
cmd-powershell-GUI/
鈹溾攢鈹€ build.ps1 / build.bat / run.bat     鏋勫缓涓庤繍琛?鈹溾攢鈹€ package.ps1                         鎵撳寘鎴愬厤瀹夎 exe锛坖package锛?鈹溾攢鈹€ src/main/java/com/dogepopcat/cpgui/
鈹?  鈹溾攢鈹€ Main.java                       绋嬪簭鍏ュ彛
鈹?  鈹溾攢鈹€ AppInfo.java                    鍚嶇О / 鐗堟湰
鈹?  鈹溾攢鈹€ analysis/                       ScriptParser銆丼hellLexer銆丏angerAnalyzer
鈹?  鈹溾攢鈹€ completion/                     琛ュ叏鐩綍銆佸€欓€夎绠椼€佽矾寰勮ˉ鍏ㄣ€佸弬鏁版彁绀?鈹?  鈹溾攢鈹€ io/                             ScriptFile锛氭寜瑙ｉ噴鍣ㄩ€夋嫨缂栫爜璇诲啓鑴氭湰
鈹?  鈹溾攢鈹€ shell/                          ShellSession銆丼essionRegistry銆丆onsoleCharset
鈹?  鈹溾攢鈹€ util/                           CommandLine锛氳繕鍘熷綋鍓嶈繘绋嬪懡浠よ
鈹?  鈹斺攢鈹€ ui/                             绐楀彛銆佹爣绛鹃〉銆佹帶鍒跺彴銆侀珮浜€佽瘖鏂€佽ˉ鍏ㄥ脊绐?鈹溾攢鈹€ tools/dev/                          寮€鍙戞湡楠岃瘉宸ュ叿锛堜笉鍙備笌鎵撳寘锛?鈹溾攢鈹€ docs/                               README 鐢ㄦ埅鍥?鈹斺攢鈹€ build/ dist/                        鏋勫缓浜х墿锛堝凡琚?.gitignore 蹇界暐锛?```

## 鎶€鏈€夊瀷璇存槑

閫夋嫨 **Java + Swing** 鑰屼笉鏄?Electron 涔嬬被鏂规锛?
1. **鐪熸鐨勬闈㈢搴旂敤**銆係wing 鐩存帴璋冪敤 Win32 鍘熺敓绐楀彛锛屼笉鍐呭祵娴忚鍣ㄥ唴鏍革紝涓嶆槸銆岀綉椤靛澹炽€嶃€?2. **闆跺閮ㄤ緷璧?*銆係wing 灞炰簬 JDK 鑷甫鐨?`java.desktop` 妯″潡锛宑lone 涓嬫潵灏辫兘缂栬瘧杩愯锛?   涓嶄緷璧?Maven 涓ぎ浠撳簱锛屼篃娌℃湁 `node_modules`銆?3. **鎵撳寘浣撶Н涓庡惎鍔ㄩ€熷害**銆俙jpackage` 鍙互鎶婅繍琛屾椂涓€璧锋墦鍖呮垚鍏嶅畨瑁?exe锛?   鐢ㄦ埛鏈哄櫒涓婁笉瑁?Java 涔熻兘璺戙€?4. **鏂囨湰澶勭悊鑳藉姏寮?*銆傝瘝娉曞垎鏋愩€佽ˉ鍏ㄥ€欓€夈€侀潤鎬佹鏌ヨ繖浜涖€屼豢 IDE銆嶅姛鑳斤紝鐢?Java 瀹炵幇鏇存竻鏅般€佹洿瀹夊叏銆?
## 瀹炵幇涓婂嚑涓€煎緱涓€鎻愮殑缁嗚妭

杩欎簺鍧戦兘鏄厛鍐欐帰閽堢▼搴忛獙璇併€佸啀钀藉埌浠ｇ爜閲岀殑锛屼笉鏄媿鑴戣鍐冲畾鐨勶細

* **鎺у埗鍙颁唬鐮侀〉**銆俙cmd.exe` / `powershell.exe` 杈撳嚭鍒扮閬撴椂浣跨敤**鎺у埗鍙?OEM 浠ｇ爜椤?*
  锛堜腑鏂囩郴缁熸槸 936锛夛紝鑰屼笉鏄?UTF-8銆傜▼搴忓惎鍔ㄦ椂鎵ц `chcp` 璇诲嚭浠ｇ爜椤靛苟鏄犲皠鎴?Java 瀛楃闆嗭紝
  鍚﹀垯涓枃璺緞鍏ㄦ槸涔辩爜銆傦紙椤哄甫鍙戠幇锛歚chcp 65001` 閲嶅畾鍚戝埌绠￠亾浼氳 `cmd` 鐩存帴閫€鍑猴紝鎵€浠ヤ笉鑳界敤銆傦級
* **鍛戒护缁撴潫鏍囪**銆傜閬撻噷娌℃湁鎻愮ず绗﹀彲浠ョ瓑寰咃紝鎵€浠ユ瘡鏉″懡浠ゅ悗闈㈣拷鍔犱竴鍙ュ甫搴忓彿鐨勬爣璁板懡浠わ紝
  璇诲埌鏍囪鎵嶇畻杩欐潯鍛戒护璺戝畬锛屽悓鏃舵嬁鍒扮湡瀹為€€鍑虹爜銆?* **cmd 鐨勫浣欑┖琛?*銆俙cmd.exe` 鍦ㄦ瘡娆℃墦鍗版彁绀虹**涔嬪墠**閮戒細鍏堣緭鍑轰竴涓┖琛岋紝
  绋嬪簭浼氭妸銆岀揣鎸ㄧ潃鎻愮ず绗︾殑绌鸿銆嶄涪鎺夛紝浣嗕繚鐣?`echo.` 杩欑鐪熸鐨勭┖琛岃緭鍑恒€?* **鑴氭湰缂栫爜**銆侾owerShell 鍙湁鍦?`.ps1` 甯?BOM 鏃舵墠鎸?UTF-8 璇诲彇锛宍cmd` 鍒欐寜鎺у埗鍙颁唬鐮侀〉璇?  `.bat`銆傚洜姝?`.ps1` 鍐欐垚銆孶TF-8 + BOM銆嶏紝`.cmd` 鍐欐垚鎺у埗鍙颁唬鐮侀〉锛涢亣鍒颁唬鐮侀〉琛ㄧず涓嶄簡鐨勫瓧绗?  浼氭槑纭彁绀鸿€屼笉鏄倓鎮勬浛鎹㈡垚 `?`銆?* **鎹㈣绗?*銆係wing 鏂囨。鍐呴儴缁熶竴鏄?`LF`锛屼繚瀛樻椂缁熶竴杞洖 `CRLF`锛屽洜涓?`cmd.exe` 鏄寜琛岃В鏋愭壒澶勭悊鐨勩€?* **绠＄悊鍛橀噸鍚?*銆俉indows 鏃犳硶鎻愬崌宸茶繍琛岃繘绋嬬殑鏉冮檺锛屽彧鑳介噸鏂版媺璧蜂竴浠斤紱
  绋嬪簭浼氳繕鍘熷綋鍓嶈繘绋嬬殑瀹屾暣鍛戒护琛岋紝骞舵妸鍙墽琛屾枃浠朵笌鍙傛暟閫氳繃**鐜鍙橀噺**浼犵粰 PowerShell锛?  閬垮紑鎵€鏈夊眰鐨勫紩鍙疯浆涔夎鍒欍€?* **甯冨眬**銆俙JSplitPane` 鐨勫垎闅旀潯鍒濆浣嶇疆鏉ヨ嚜涓や晶缁勪欢鐨?preferred size锛?  绌虹紪杈戝櫒鏃朵細琚尋鍒板緢绐勶紝鎵€浠ュ湪绐楀彛绗竴娆℃嬁鍒扮湡瀹炲昂瀵稿悗鏄惧紡璁剧疆涓€娆℃瘮渚嬨€?* **宸ヤ綔鐩綍**銆備粠蹇嵎鏂瑰紡鎴栧紑濮嬭彍鍗曞惎鍔ㄦ椂锛學indows 浼氭妸宸ヤ綔鐩綍璁炬垚 `C:\Windows\System32`銆?  绋嬪簭浼氭嫆缁濈郴缁熺洰褰曪紝鏀逛负鍥為€€鍒扮敤鎴蜂富鐩綍鈥斺€斿惁鍒欍€屽彟瀛樹负銆嶅拰 `cd` 鐨勯粯璁や綅缃氨鍦ㄧ郴缁熺洰褰曢噷銆?* **鏌ヨ涓庤繍琛屽繀椤诲尯鍒?*銆傝鍙栦細璇濆綋鍓嶇洰褰曚篃鏄竴鏉″懡浠わ紝浣嗗畠灞炰簬鍐呴儴鏌ヨ锛?  濡傛灉瀹冧篃瑙﹀彂銆屽懡浠ゅ畬鎴愩€嶅洖璋冿紝鑰屽洖璋冨張瑕佽鍙栫洰褰曪紝灏变細鏃犻檺閫掑綊銆傛煡璇㈠洜姝や笉鍙戝畬鎴愰€氱煡銆?* **闈欐€佸垵濮嬪寲椤哄簭**銆傜炕璇戣〃鐢卞叾鍚庡０鏄庣殑鏁扮粍鏋勫缓锛岄潤鎬佸垵濮嬪寲鎸夊０鏄庨『搴忔墽琛岋紝
  缁撴灉鏄竴鏃︾敤鍒拌ˉ鍏ㄥ氨鎶涘紓甯搞€傚０鏄庨『搴忓湪杩欓噷鏄湁璇箟鐨勩€?
## 寮€鍙戞湡楠岃瘉宸ュ叿

`tools/dev/` 涓嬫槸涓€缁勫紑鍙戞湡宸ュ叿锛?*涓嶅弬涓庢墦鍖?*锛宍src/` 涔熶笉渚濊禆瀹冧滑锛夛細

| 宸ュ叿 | 楠岃瘉鍐呭 |
| --- | --- |
| `dev.SessionProbe` | 鐩存帴椹卞姩鐪熷疄瑙ｉ噴鍣紝鎵撳嵃鍘熷杈撳嚭锛涘綋骞村氨鏄潬瀹冪‘璁や簡浠ｇ爜椤点€佹彁绀虹鏍囪涓庨€€鍑虹爜鍗忚 |
| `dev.ParserProbe` | 鎵撳嵃鑴氭湰鍒囧垎缁撴灉涓庡懡涓殑鍗遍櫓瑙勫垯 |
| `dev.CompletionProbe` | 琛ュ叏鍊欓€夈€佽矾寰勮ˉ鍏ㄣ€佸弬鏁版彁绀虹殑鏂█ |
| `dev.CompletionGuiProbe` | 閫氳繃鐪熷疄绐楀彛楠岃瘉琛ュ叏寮圭獥涓庛€屾帴鍙椼€嶈涓?|
| `dev.DangerProbe` | 鐢ㄦ浛韬洖绛旂‘璁ゅ脊绐楋紝楠岃瘉鍗遍櫓鍛戒护鎷︽埅纭疄鐢熸晥 |
| `dev.FileProbe` | 缂栫爜寰€杩斻€丆RLF銆佽剰鏍囪銆佹墦寮€淇濆瓨 |
| `dev.AdminProbe` | 杩樺師鍛戒护琛屻€乣Start-Process` 閫氶亾 |
| `dev.GuiDemo` | 鎵撳紑鐪熷疄绐楀彛骞跺～鍏ョず渚嬭剼鏈紝鐢ㄤ簬浜哄伐鏌ョ湅涓庢埅鍥?|
| `screenshot.ps1` | 鍚姩銆佺疆椤讹紙鍚ā鎬佸璇濇锛夊苟鎴浘 |

鐢ㄦ硶瑙?[tools/dev/README.md](tools/dev/README.md)銆?
> 瀹夊叏鎻愮ず锛歚dev.DangerProbe` 涓?`GuiDemo danger` 閲岀殑鍗遍櫓鍛戒护閮芥寚鍚?*涓嶅瓨鍦ㄧ殑涓存椂璺緞**锛?> 涓?`GuiDemo` 涓嶄細鑷姩鎵ц绀轰緥銆備慨鏀硅繖浜涜剼鏈椂璇蜂繚鎸佽繖涓€鐐光€斺€旂‘璁ゅ脊绐椾竴鏃﹁鎺ュ彈锛?> 鍛戒护鏄湡鐨勪細鎵ц鐨勩€?
## 涓枃 / English

鐣岄潰榛樿璺熼殢绯荤粺璇█锛堜腑鏂?Windows 涓婂嵆涓轰腑鏂囷級锛屽彲鍦?**瑙嗗浘 鈫?璇█** 涓嵆鏃跺垏鎹€?搴旂敤鑷韩鏄剧ず鐨勬墍鏈夋枃瀛椻€斺€旇彍鍗曘€佸伐鍏锋爮銆佺姸鎬佹爮銆侀棶棰樺垪琛ㄣ€佸璇濇銆佸嵄闄╁懡浠よ鏄庛€?鍛戒护涓庡紑鍏崇殑甯姪鏂囧瓧鈥斺€旈兘鐢?`i18n/Msg` 缁熶竴鎻愪緵涓嫳涓や唤锛岀己灏戠炕璇戜細鐩存帴缂栬瘧鎶ラ敊銆?
> 璇存槑锛歚cmd.exe` 涓?PowerShell 鑷韩杈撳嚭鐨勬枃瀛楅殢 Windows 璇█鑰屽畾锛岄偅閮ㄥ垎涓嶅睘浜庢湰绋嬪簭銆?
涓€娆℃彁浜ゅ彧鍋氫竴浠舵渶灏忕殑浜嬶紝姣忎竴姝ラ兘鍙紪璇戙€佸彲杩愯锛?
| # | 鎻愪氦 | 鍐呭 |
| --- | --- | --- |
| 1 | `feat: project skeleton鈥 | 鏋勫缓鑴氭湰 + 绌虹獥鍙?|
| 2 | `feat: main window with editor tabs鈥 | 鏍囩椤点€佽鍙枫€佹帶鍒跺彴銆佺姸鎬佹爮 |
| 3 | `feat: run scripts in a persistent interpreter session` | 甯搁┗浼氳瘽銆佽繍琛?鍋滄銆侀€€鍑虹爜 |
| 4 | `feat: graphical workspace selection` | 鍥惧舰鍖?`cd` |
| 5 | `feat: split scripts into commands鈥 | 鍛戒护鍒囧垎銆佹寜琛?鍏ㄩ儴杩愯 |
| 6 | `feat: syntax highlighting鈥 | 璇嶆硶鍒嗘瀽涓庨珮浜?|
| 7 | `feat: detect dangerous commands鈥 | 鍗遍櫓鍛戒护妫€鏌?+ 闂鍒楄〃 + 娉㈡氮绾?|
| 8 | `feat: confirm before running a dangerous command` | 杩愯鍓嶄簩娆＄‘璁?|
| 9 | `feat: IntelliSense completion鈥 | 琛ュ叏寮圭獥 |
| 10 | `feat: complete file paths and hint command parameters` | 璺緞琛ュ叏 + 鍙傛暟鎻愮ず |
| 11 | `feat: open, save and protect unsaved scripts` | 鏂囦欢鎿嶄綔涓庣紪鐮佸鐞?|
| 12 | `feat: restart the application with administrator rights` | 绠＄悊鍛樻ā寮?|
| 13 | `build: package as a self-contained Windows application` | jpackage 鎵撳寘涓庢枃妗?|
| 14 | `fix: stop the hint from taking over the editor` | 鎻愮ず鍙湪杈撳叆鍛戒护鍚嶆椂鍑虹幇銆丒nter 鍙崲琛屻€佽櫄鍖栨彁绀?|
| 15 | `feat: make the working directory obvious and safe` | 宸ヤ綔鐩綍鏍忋€佹嫆缁?System32 榛樿鍊?|
| 16 | `feat: report commands that do not exist` | 鏈煡鍛戒护妫€鏌?|
| 17 | `style: give the tool bar buttons an even size and a visible edge` | 宸ュ叿鏍忕瓑瀹芥寜閽?|
| 18 | `feat: administrator mode opens a second window and shows its state` | 绠＄悊鍛樻ā寮忔柊寮€绐楀彛骞舵寜鐘舵€佺潃鑹?|
| 19 | `feat: type single commands straight into the console` | 鎺у埗鍙板崟琛岃緭鍏?|
| 20 | `feat: choose the editor and console font` | 瀛椾綋閫夋嫨 |
| 21 | `feat: complete Chinese and English interface` | 鍙岃鐣岄潰 |
| 22 | `feat: translate the command catalogue as well` | 鍛戒护璇存槑鍙岃鍖?|

## 璐＄尞涓庢彁浜よ鑼?
```
feat:     鏂板姛鑳?fix:      淇?bug
docs:     鍙敼鏂囨。
style:    鏍煎紡璋冩暣锛屼笉褰卞搷閫昏緫
refactor: 閲嶆瀯锛屼笉鏀瑰彉琛屼负
build:    鏋勫缓鑴氭湰 / 鎵撳寘
test:     娴嬭瘯
```

## 璁稿彲璇?
[MIT](LICENSE)
