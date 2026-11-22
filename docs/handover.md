# SD Click Assistant（ゴーストタッチ）引き継ぎ書

作成日：2026-11-16（最終更新：2026-11-19）

このドキュメントは、新しいチャットでClaudeに開発を引き継ぐための要約です。**リポジトリの `docs/handover.md` として置き、GitHubからClaudeプロジェクトへ同期して使います。**

**このバージョンの特徴**：コードの内容だけでなく、**「コードの実装方法（ターミナルでの編集）」「Git・GitHubの運用」「現在の状況（どこまで済み、何が未適用か）」** を、確実に引き継げるように書いています。特に**9章「現在の状況」**を、最初に読んでください。

---

## 0. 新しいチャットを始めたら、最初にやること

1. **同期の確認**：最初のメッセージで、次のように頼む。
   > 「docs/handover.md を読んで、引き継ぎしてください。」
   - Claudeが `docs/handover.md` を読めれば、GitHubとの同期は成功。
   - 読めない場合は、プロジェクトの知識欄で、GitHubの「Sync」を押してから、**新しいチャット**で、もう一度試す（**チャットの途中で同期しても、そのチャットには反映されない**。1-3項）。
   - それでも読めない場合は、プロジェクトの知識欄の、GitHubの連携（取り込むファイルの選択）を、確認する。
2. 読めたら、Claudeは**9章（現在の状況）と10章（TODO）** を、ユーザーに要約して見せ、次の作業を相談する。
3. コードを編集する前は、**「Sync」が最新か**を、確認する（`git log` の結果や、`docs/LAST_SYNC.txt` の時刻で判断してもよい。2-4項）。

---

## 1. プロジェクト概要

- アプリ名（表示名）：ゴーストタッチ　※内部プロジェクト名は SD Click Assistant
- パッケージ名：`com.example.sdclickassistant`
- Androidマクロ自動化アプリ。Accessibility Serviceでタップ・長押し・スワイプ・待機・スクリーンショット・ラベル・ジャンプ・条件分岐（ループ制御／色判定／**画像認識**）を自動実行する
- 開発者はプログラミング初心者（アプリは7個ほど作成経験あり）。
- 動作確認用の実機：OPPO Reno11 A（ABIは `arm64-v8a,armeabi-v7a,armeabi`。adbで確認済み）
- 開発環境：Windows、Android Studio。**Android Studioのターミナルは、PowerShell**（プロンプトが `PS C:\Users\ysato\AndroidStudioProjects\SDClickAssistant>`）。
- プロジェクトのフォルダ：`C:\Users\ysato\AndroidStudioProjects\SDClickAssistant`
- GitHub：ユーザー名 `ysatoshi1101-sudo`、リポジトリ `SDClickAssistant`（**非公開**）、ブランチ `main`。リモートURL：`https://github.com/ysatoshi1101-sudo/SDClickAssistant.git`

---

## 2. 開発の進め方（重要）

### 2-1. 基本の流れ

実装前に、次の順で説明してから、コードを提示する。

1. 要件整理 → 2. 実装方法 → 3. Android仕様上の制約 → 4. 必要な権限・API → 5. 既存コードへの影響範囲

- 設計判断が分かれる所は、**選択肢と推奨を表で提示**する。ユーザーは「推奨どおり」と返すことが多い。
- コード提示前に、**新規作成／修正／削除するファイルの一覧**を出す。
- UIを変える時は、**変更前／変更後／理由**を示す。
- 完了報告は、**【追加】【変更】【確認事項】【次の実装候補】** の形式で行う。
- ライブラリを追加する時は、追加理由・メリット・デメリット・代替案を説明し、バージョン帯との互換性を**検索で確認**してから提案する。
- 原因不明の不具合は、**推測で直さない**。Logcatのログ、切り分けの実験で、事実を確認してから直す。
- 動いているコードを大きく変える前は、**変更する場合・しない場合のデメリット**と、**確認用の実験**を先に示す。
- ファイルが大きい変更は、**2回に分けて**渡し、1回目だけでビルドできるようにする。
- 1回の返信を、長くしすぎない。長い作業は、区切って渡す。
- **Claude自身が、コードや、ユーザーの要望を、読み違えることもある**（過去に、ファイルの保存形式を誤って指摘した、ユーザーの要望を取り違えた、が実際にあった）。「食い違い」を指摘する時や、要望が曖昧な時は、**先に、内容を確認してから**進める。

### 2-2. コードの変更方法：ターミナルに貼り付けるコマンド

**ユーザーは、Claudeが出した「PowerShell用のコマンド」を、Android Studioのターミナルに貼り付けて実行し、ファイルを編集する。** Claude Codeなどの自動編集ツールは使わない（ユーザーが明確に否定済み）。他のプロジェクトでも、この方法で進めている。

**使う道具**：リポジトリの `tools/edit-helpers.ps1`（内容は、付録A）。

| 関数 | 用途 |
|---|---|
| `Write-SourceFile -Path <相対パス> -Content <全文>` | ファイルを**丸ごと書き出す**（新規作成、大きな変更）。改行はCRLF、文字コードはBOMなしUTF-8 |
| `Edit-SourceFile -Path <相対パス> -Old <置換前> -New <置換後>` | 文字列を**1か所だけ**置換する（小さな変更）。**置換前の文字列が、ちょうど1か所でなければ、何も変更せずにエラーで止まる**（安全装置） |

**ターミナルを開くたびに、最初に1回、次を実行して、関数を読み込む。**
```powershell
cd C:\Users\ysato\AndroidStudioProjects\SDClickAssistant
iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)
```
（`. .\tools\edit-helpers.ps1` ではなく `iex (…)` にしているのは、Windowsの実行ポリシーで、スクリプトの読み込みが拒否されることがあるため。）

**コマンドの書き方（Claudeが出す形）**

```powershell
# 全文の書き出し
Write-SourceFile -Path "app\src\main\java\com\example\sdclickassistant\service\Foo.kt" -Content @'
（ファイルの全文）
'@

# 部分置換（置換前・置換後を、変数に入れてから渡す）
$P = "app\src\main\java\com\example\sdclickassistant\service\Bar.kt"
$old = @'
（置換前の文字列。ファイル内で、ちょうど1か所だけに一致すること）
'@
$new = @'
（置換後の文字列）
'@
Edit-SourceFile -Path $P -Old $old -New $new
```

**作法と注意点**

- ヒアストリング（`@'` … `'@`）：開始の `@'` は**行末**に置き、終了の `'@` は**行の先頭**（左端）に置く。内容の最後の改行は、含まれない（`.TrimEnd()` は不要）。
- `Edit-SourceFile` の置換前は、**インデントや空行も、現物と完全に一致**させる。1行だけの短い文字列（行の途中の一部）を、目印にすると、失敗しにくい。複数行の場合、空行に空白が入っていると、一致しない。
- **成功すると、`置換しました` / `書き込みました` と表示される。** Claudeは、各コマンドの**成功の回数**を、ユーザーに伝える（例：「`置換しました` が9回」）。
- コードブロックの印（```）や、前後の説明の文字を、**貼り付けに含めない**。
- 長い貼り付け（目安：300行を超えるファイル）は、途中で崩れることがある。その場合は、**Android Studioのエディタに、直接貼り付ける**。大きな変更は、コマンドを、小さなブロックに分けて渡す。
- 日本語のコメントが入るコードは、初回に、`git diff` で、文字化けがないか確認する。
- 実行後は、`git diff --stat` で、変更されたファイルを確認する。気に入らなければ、`git restore <ファイルのパス>`（コミット前の変更が消える）で戻せる。
- **反映されたかの確認**：`Test-Path <パス>`（ファイルの有無）、`Select-String -Path <パス> -Pattern "<文字列>"`（文字列の有無）。
- **エラーが出たら、エラーの文面（赤い文字）を、そのまま貼ってもらう。** どのコマンドか、PowerShellのバージョン（`$PSVersionTable.PSVersion`）も確認する。
- **「`Edit-SourceFile`が認識されません」と出たら、ターミナルを開き直した後の、読み込み忘れ**（2026-11-19に実際に発生。構文エラーではない）。上の `iex (Get-Content …)` を実行してから、やり直す。赤い文字が出たら、**先頭の1行**を見て、原因を切り分ける。
- 2026-11-14の「構文エラー」は、エラーの文面を確認できなかったが、同じ読み込み忘れだった可能性が高い。

### 2-3. Git・GitHubの運用

**1つの作業のサイクル**

| 順 | 誰が | 内容 |
|---|---|---|
| 1 | ユーザー | 作業の前に、`git status` で、未コミットの変更がないことを確認する（あれば、先にコミットする） |
| 2 | Claude | 要件整理→ターミナル用のコマンドを出す |
| 3 | ユーザー | 貼り付けて実行→`git diff --stat` で確認→**ビルド**→**実機確認** |
| 4 | ユーザー | 問題なければ、コミットして、pushする |
| 5 | ユーザー | Claudeプロジェクトで、GitHubの「Sync」を押す |
| ✗ | ユーザー | 問題があれば、ログを貼る。または、`git restore .` で戻す |

**コミットとpush**
```powershell
git add -A
git status          # 追加されるファイルを確認(build/ や local.properties が含まれていないこと)
git commit -m "Add marker show/hide toggle to placement screen"
git push
```

- **コミットメッセージは、半角英数字（英語）にする。** Windows PowerShell 5.1は、日本語を、Gitへ渡す時に、文字化けすることがあるため。
- `LF will be replaced by CRLF` という警告は、改行コードの自動変換の通知。問題ない。
- ログインを求められたら、GitHubのログインの画面に従う。
- リモートのURLを間違えた場合の直し方：
  ```powershell
  git remote -v
  git remote set-url origin https://github.com/ysatoshi1101-sudo/SDClickAssistant.git
  ```
  （最初のpushで、ブラウザのプロフィールのURL `?tab=repositories` が混ざって、失敗したことがある。URLは、リポジトリのページの「Code」→「HTTPS」から、コピーする。）

**`.gitignore`**
次の行を、追加済み：`/.idea/`、`build`、`.gradle`、`.cxx`、`.externalNativeBuild`、`local.properties`、`*.apk`、`*.aab`、`*.jks`、`*.keystore`。**`.kotlin/` が、まだ入っていない**（9章・10章参照）。

**リポジトリの構成（要点）**
- `app/` 以下：アプリのコード
- `docs/`：`handover.md`（この引き継ぎ書）。`spec.md`（仕様書）は、削除されて存在しない。作り直す予定（10章のTODO 6）
- `tools/`：`edit-helpers.ps1`（編集用の道具）

### 2-4. Claudeプロジェクトとの同期

- プロジェクトの知識欄の「＋」→「GitHub」→ `SDClickAssistant` を選び、必要なファイルを取り込む。**自動では更新されない。** pushのあとは、**手動で「Sync」を押す。**
- 取り込まれるのは、ファイル名・内容・ブランチの内容。**コミット履歴や、プルリクエストは、取り込まれない。**
- 取り込むファイル（トークンの上限があるため、**必要なものだけ**）：
  - `app/src/main/java/` 以下のコード
  - `app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/`
  - `app/build.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`
  - `docs/`、`tools/`
  - 取り込まない：`build/`、`.gradle/`、`.kotlin/`、画像、`local.properties`
- **同期の内容は、新しいチャットでないと、反映されない**（2026-11-12に、同期後の同じチャットで、ファイルが見えなかった。プロジェクトのファイルの置き場所 `/mnt/project` が空だった）。**同期したあとは、新しいチャットを始める。**
- 手動でアップロードした、古いコードや、古い引き継ぎ書・仕様書は、**知識から削除する**（同じファイルが2つあると、古いほうを読む恐れがある）。
- 同期が最新かを確認する小さな工夫（任意）：コミットの前に、時刻を書いたファイルを更新する。
  ```powershell
  Get-Date -Format s | Set-Content docs\LAST_SYNC.txt
  ```
- 同期の不具合（連携済みと表示されるのに、内容が見えない）の報告が、過去にある。連携したら、**実際にファイルが見えているか**を、毎回確認する。

### 2-5. 実機確認と報告の形式

- 実機確認は、**番号付きのテスト表**で渡す。ユーザーは「1〇、2×…」の形で報告する。×の場合は、**何が起きたか**を一言添えてもらい、必要なら、Logcatのログ（検索欄にタグ名を入れて絞り込み）を貼ってもらう。
- 画像認識のログは、Logcatの検索欄に `IMAGE`（`MacroAccessibilityService` のログ）、キャプチャは `ScreenshotCaptureService`、マーカーの診断は `StepMarkerDiag`。

### 2-6. アプリを実機へ入れる方法（重要）

- **Android Studioの▶（実行）、または `adb install -r app\build\outputs\apk\debug\app-debug.apk`** で入れる。`-r` は、データを残して上書きする指定。
- **APKファイルをスマホに送って、スマホ上で入れる方法は、避ける。** 「外部から入れたアプリ」として扱われ、Android 13以降の「制限付きの設定」で、**ユーザー補助（アクセシビリティ）が押せなくなる**（2026-11-14に発生。8章の教訓30）。

---

## 3. 確定している技術スタック・バージョン（重要）

これまで多数のバージョン不整合トラブルを解決してきた結果、以下の組み合わせで安定動作している。**今後もこのバージョン帯を維持すること。むやみに最新版へ上げない。**

| 項目 | バージョン |
|---|---|
| AGP (Android Gradle Plugin) | 8.13.2（手元の`libs.versions.toml`の値） |
| Gradle | 9.4.1（ビルドログより） |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Hilt | 2.52 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Compose BOM | 2024.10.01 |
| androidx-core-ktx | 1.13.1 |
| androidx-activity-compose | 1.9.3 |
| androidx-lifecycle-runtime-ktx | 2.8.7 |
| androidx-navigation-compose | 2.8.4 |
| Room | 2.6.1 |
| androidx-hilt-navigation-compose | 1.2.0 |
| OpenCV | 4.12.0（`org.opencv:opencv`。Maven Central経由） |

- `gradle.properties` に `android.useAndroidX=true` が必須。ルート・app両方の `build.gradle.kts` に `kotlin-android` プラグインの宣言が必須。
- **OpenCV**：`app/build.gradle.kts`の`defaultConfig`内に`ndk { abiFilters += listOf("arm64-v8a") }`を指定し、対応CPUを`arm64-v8a`のみにしている（16KBページサイズ未対応のarmeabi-v7aを除外し、APKサイズも抑える）。**エミュレーターで確認する場合は`x86_64`を追加すること。** OpenCV 5.0系は使わない。
- **ドラッグ&ドロップ（シナリオ一覧・ステップ一覧）は、外部ライブラリを使わず`pointerInput`＋`detectDragGesturesAfterLongPress`による自前実装。**
- **アイコン（★☆⋮≡✕▶など）は`Icons.Filled.xxx`ではなくプレーンテキストの記号、または`GradientDrawable`/`Drawable`による自前描画で実装。** 文字記号は端末のフォントで表示が変わるため、重要なアイコンは、コードで描画する方針。既存で確実に動く`Add`・`Delete`・`PlayArrow`のみ`Icons.Filled`を使用。

---

## 4. アーキテクチャ

MVVM + Repository + StateFlow + Hilt DI + Compose Navigation

```
domain/model/          … ActionType, ConditionType（LOOP_COUNT / COLOR / IMAGE ＋ しきい値の定数）,
                         MacroStep, MacroScenario, Folder,
                         ScreenCaptureRequirement.kt（「画面キャプチャが必要か」の共通関数）
domain/executor/        … MacroExecutor, StepResult（TAP/LONG_PRESS/SWIPE/WAITのみ担当）
data/local/             … Room Entity (ScenarioEntity, StepEntity, FolderEntity), DAO, AppDatabase（version 7）,
                         Migrations.kt（5→6, 6→7）
data/mapper/             … Entity ⇔ ドメインモデルの変換
data/matcher/            … TemplateMatcher（interface）, MatchResult, OpenCvTemplateMatcher
                         ※インターフェースも実装と同じdata/matcherに置く（ScenarioRepositoryのインターフェースがdata/repositoryにあるのと同じ流儀）
data/template/           … TemplateStorage（テンプレート画像の保存・読み込み・複製・削除・一時保存）
data/repository/         … ScenarioRepository / FolderRepository (interface + impl)
di/                       … DatabaseModule, RepositoryModule, MatcherModule (Hilt)
presentation/scenariolist/      … 一覧画面（フォルダ・お気に入り・並び替え対応）
presentation/scenariodetail/    … 詳細・ステップ編集画面（ドラッグ&ドロップ対応、画像認識の設定ダイアログ）
presentation/executionprep/     … 実行前確認画面（①）
presentation/navigation/         … AppNavHost（Compose Navigation）
service/                  … Accessibility Service関連一式（下記5章）
```

**重要**：`ActionType.LABEL`・`JUMP`・`CONDITION`は`MacroExecutor`（`domain/executor`）では処理しない。`MacroAccessibilityService.startExecution`内の実行ループで直接分岐処理している。

---

## 5. serviceパッケージの各ファイルの役割

| ファイル | 役割 |
|---|---|
| `MacroAccessibilityService.kt` | 中核。配置待機状態の表示、実行ループ、STOP処理、ジェスチャー実行、SCREENSHOT分岐、色判定、**画像認識の判定**（`evaluateImageConditionWithWait` / `evaluateImageCondition` / `tapFoundImage` / `captureScreenWithRetry`）、`showStepMarkers()`、`startColorPicker()`、`startTemplateCapture()`、`startSequenceRecording()`、`replayRecordedStep()`、`updateRepeatSettings()`。Hiltで`ScenarioRepository`・`TemplateStorage`・`TemplateMatcher`を受け取る |
| `FloatingButtonController.kt` | 配置待機のボタン群。**1段目：✕（戻る）・▶（再生）・◎（マーカーの表示/非表示）**、2段目：繰り返し回数パネル（−／回数／＋／∞）。円形ボタン。**`show()`の引数に`onMarkersToggled`が加わっている**（5-1参照）。`areMarkersVisible()` を持つ |
| `ExecutionPanelController.kt` | STOPボタンを赤い正方形アイコンで表示。パネル全体がタップ判定。**再生ボタンを置いた位置に出る** |
| `StepMarkerController.kt` | 登録済みステップの番号マーカー（丸）と、スワイプの線を表示。ドラッグで位置調整可能（`MarkerTarget`で始点/終点を区別）。`show()` / `hide()` |
| `CoordinateCaptureController.kt` / `CaptureResultBus.kt` | ステップ編集ダイアログの「画面から取得」機能 |
| `SequenceRecordingController.kt` | 操作を記録し、対象アプリへ実際に再送する。開始/一時停止/再開・待機時間の自動計測 |
| `RecordingResultBus.kt` | 操作記録の結果をCompose側へ渡すBus |
| `MediaProjectionTokenHolder.kt` | MediaProjection許可結果の一時保持。consume()で一度きり取得 |
| `ScreenshotCaptureService.kt` | スクリーンショット機能本体。Foreground Service。**直近のフレームを保持し、`captureBitmap()`は「新しいフレームを最大0.3秒待ち、来なければ保持している直近のフレームを返す」**（8章23項）。保存形式はPNG（品質100）。MediaStore経由で`Pictures/SD Click Assistant/シナリオ名/`に保存 |
| `ColorPickerController.kt` | 操作モード→「この画面をキャプチャ」→色選択モード（十字マーカー）。ツールバーは記号のみ（✕ ↺ ✓）、色の表示は`R,G,B`の短い表記で幅固定 |
| `ColorPickResultBus.kt` | スポイト結果（座標＋RGB）をCompose側へ受け渡すBus |
| `TemplateCaptureController.kt` | 画像認識のテンプレート作成。操作モード→（操作パネルを隠す）→キャプチャ→ドラッグで範囲選択→決定。最小24px。ツールバーは記号のみ（✕ ↺ ✓） |
| `TemplateCaptureResultBus.kt` | テンプレート作成の結果（一時保存の鍵・サイズ・画面サイズ）をCompose側へ渡すBus |

---

## 6. 主な実装済み機能の要点（詳細は、コードを参照。`docs/spec.md`は作り直し中）

### 6-1. 色判定スポイト
- 操作モード→「この画面をキャプチャ」→色選択モード。ツールバーは記号のみ。
- `show()`のシグネチャ：
  ```kotlin
  fun show(
      onCaptureRequested: (onCaptured: (Bitmap?) -> Unit) -> Unit,
      onDecide: (ColorPickResult) -> Unit,
      onCancel: () -> Unit
  )
  ```

### 6-2. 配置待機画面（FloatingButtonController）と、マーカーの表示/非表示ボタン
- 再生・戻る・「−」・「＋」・マーカーのボタンは、95pxの円形。「∞」のみ62px。
- `show()`のシグネチャ：
  ```kotlin
  fun show(
      initialRepeatCount: Int,
      initialIsInfiniteLoop: Boolean,
      onTap: () -> Unit,
      onBack: () -> Unit,
      onRepeatSettingsChanged: (repeatCount: Int, isInfiniteLoop: Boolean) -> Unit,
      onMarkersToggled: (visible: Boolean) -> Unit
  )
  ```
- **マーカーの表示/非表示ボタン（2026-11-12に追加、実機で確認項目1〜12すべて成功、コミット・push済み）**：
  - 1段目の右端（✕、▶の右）に、3つ目の丸いボタン「◎」。**表示中は青（`#2979FF`）、非表示はグレー（`#616161`）**（「∞」ボタンのオン/オフと同じ見せ方）。
  - **配置待機の画面が出るたびに、非表示から始まる**（STOPで戻った時も同様。`show()`の最初で`markersVisible = false`）。
  - 「表示」にすると、サービスの`showStepMarkers(scenarioId)`が、**DBから最新のステップを読み直して**マーカーを出す。読み込みの間に、非表示に戻された・実行が始まった場合は、`areMarkersVisible()`で確認して、出さない。
  - 「非表示」にすると、`stepMarkerController.hide()`でマーカーを**画面から取り除く**（対象アプリの操作を邪魔しない）。
  - 配置待機の表示時に、マーカーを自動で出す処理は、取り除いてある。

### 6-3. 操作記録
- 記録した操作を対象アプリへ実際に再送する。開始・一時停止・再開。待機時間は自動計測（下限100ms、最後や計測不能時は300ms）。

### 6-4. 画像認識（IMAGE）

**完了済み（実機確認済み）**
- 条件分岐の判定方式`IMAGE`。テンプレート作成（画面キャプチャ→ドラッグで範囲選択→プレビュー）。
- 設定：しきい値（0.50〜1.00、空欄で0.90）、見つかるまで待つ最大秒数（1〜600、空欄で待たず1回だけ判定）、見つかった時にその位置をタップするか（タップ後の待機時間は既存の`waitTime`。空欄で300ms。実行速度の倍率が効く）、反転（見つからなかった時にジャンプ）。
- 実行時の動作：
  - テンプレートは**実行の開始前にまとめて読み込む**。1つでも無ければ**開始前に停止**。終了時（STOP含む）に必ず`recycle`。
  - 画面キャプチャは最大3回（各3秒の上限、間200ms）。**判定できなかった（キャプチャ失敗・照合失敗）場合は「見つからなかった」ではなく停止**（反転ジャンプの誤動作を避けるため）。
  - 「見つかるまで待つ」は、判定→500ms待つ、を繰り返す。時間切れは「見つからなかった」として、既存のジャンプ・反転のルールに従う。最大秒数は実時間で数える。待っている間も、STOPで中断できる。
  - 見つかったら、**反転のON/OFFに関係なく**、`tapOnImageFound`がオンならその位置（領域の中心）をタップする。
  - 照合は`Dispatchers.Default`で実行。テンプレート作成時と画面サイズが違う場合は、**警告ログのみ**で照合は続ける。
  - 1回の照合は、約0.55〜0.6秒（OPPO Reno11 A）。

**保留（ユーザーの判断、2026-11-04）**
- 「しきい値を試すボタン」「探索範囲の指定」「一致位置からのずらし量」。**必要になった時に、あらためて相談する。**

### 6-5. 画面キャプチャの方式
- VirtualDisplay（`AUTO_MIRROR`）は、**画面が変化した時だけ新しいフレームを作る**。画面が止まっていると、新しいフレームは届かない。
- 以前の「次に届くフレームを待つ」方式では、止まった画面で最大3秒待たされ、画像認識の「待つ」が不安定だった（Logcatで確定）。
- 現在は、`ScreenshotCaptureService`が直近のフレーム（`latestImage`）を保持する。`ImageReader`の保持枚数は3。保持したフレームの読み出しは**メインスレッド**（`Dispatchers.Main.immediate`）で行う。解放は`releaseCaptureResources`で行う。

---

## 7. データベーススキーマ

**現在のバージョン：7**（`AppDatabase.kt`の`@Database(version = 7)`）。

- Room DBの実体を提供しているのは`di/DatabaseModule.kt`の`provideAppDatabase()`（Hilt経由）。`.addMigrations(*ALL_MIGRATIONS)`と`.fallbackToDestructiveMigrationFrom(1, 2, 3, 4)`が付いている。**バージョン5以降は、マイグレーションを書き忘れると、全消去ではなくエラーで止まる**（意図した設計）。
- `exportSchema = false`のため、マイグレーションの自動テストはできない。**アンインストールせずに、上書きインストールして、既存シナリオが残っていることを実機で確認する**こと。
- マイグレーションは`data/local/Migrations.kt`にある。**新しいマイグレーションを作ったら、`ALL_MIGRATIONS`に追加する。**
  - 5→6：stepsに`imageMatchThreshold REAL`、`templateScreenWidth INTEGER`、`templateScreenHeight INTEGER`を追加
  - 6→7：stepsに`tapOnImageFound INTEGER NOT NULL DEFAULT 0`、`imageWaitTimeoutSec INTEGER`を追加
- NOT NULLの列を追加する時は、`StepEntity`側にも`@ColumnInfo(defaultValue = "0")`を付けて、マイグレーションのDEFAULTと揃える。

**ScenarioEntity**：`id, name, repeatCount, isInfiniteLoop, speed, createdDate, updatedDate, isFavorite, order, folderId`

**StepEntity（version 7）**：`id, scenarioId, order, actionType, x, y, endX, endY, duration, waitTime, condition, targetLabel, loopCount, conditionType, colorR, colorG, colorB, colorTolerance, invertCondition, imageMatchThreshold, templateScreenWidth, templateScreenHeight, tapOnImageFound, imageWaitTimeoutSec`
- `waitTime`：通常は各ステップの待機時間(ms)。IMAGEで`tapOnImageFound=true`の場合は、タップ後の待機時間として使う。

**FolderEntity**：`id, name, order, createdDate`

**テンプレート画像のファイル**：DBではなく、`filesDir/templates/<ステップID>.png`（PNG・品質100）。一時保存は`filesDir/templates/tmp/<一時キー>.png`。シナリオのコピー時は新しいステップIDへ複製、ステップ・シナリオの削除時は画像も削除（`ScenarioRepositoryImpl`）。判定方式をIMAGEから変えた時も削除（`ScenarioDetailViewModel`）。

---

## 8. 重要な過去のトラブルと教訓（同じ沼にはまらないために）

1. **AGP/Kotlin/KSPバージョンは絶対にバラバラにしない。** 新規追加ライブラリも、3章の帯に収まるものを選ぶ。迷ったら先に検索で互換性を確認してから提案する。
2. **ルートとappのbuild.gradle.ktsで、プラグイン宣言のスコープを必ず揃える。**
3. **AccessibilityServiceのManifest登録は `<meta-data android:name="android.accessibilityservice" .../>` が正しい。** `"android.accessibilityservice.config"` は誤り（`capabilities=0`になる。`adb shell dumpsys accessibility`で確認）。
4. **MediaProjectionはAndroid 14以降、`startForeground(type=MEDIA_PROJECTION)` → `getMediaProjection()` → `registerCallback()` → `createVirtualDisplay()` の順序を厳守。**
5. **JVM Toolchainの`jvmTarget`はJavaの`compileOptions`と必ず一致させる。**
6. **Composeで`weight()`がinternalエラーになる場合、`Arrangement.SpaceBetween`で代替。**
7. **Accessibility Serviceの重要なUI表示処理を含むCoroutineScopeは`Dispatchers.Main`を明示指定する。**
8. **同種の自動化アプリが同時に有効化されていると原因調査の妨げになる。**
9. **ターミナルから`./gradlew`を直接叩く場合、JAVA_HOMEが必要。**
10. **Room DBの`fallbackToDestructiveMigration…`は、実際にHiltから注入される`di/DatabaseModule.kt`の`provideAppDatabase()`に付いているか確認すること。**
11. **LABEL/JUMP/CONDITIONによるジャンプは、`suspend`を挟まないと`Dispatchers.Main`を専有しANRを起こす危険がある。** ジャンプ処理には必ず`yield()`を挟む。
12. **画面キャプチャを伴う処理（色判定・画像認識など）を追加する場合、「画面キャプチャが必要か」の判定を更新すること。** `domain/model/ScreenCaptureRequirement.kt`の`requiresScreenCapture()`に**1か所にまとめてあり**、判定方式の`when`は`else`なしにしてある。新しい判定方式（OCRなど）を足すと、ここがコンパイルエラーになるので、更新漏れを防げる。
13. **`AlertDialog`内の`Column`は、項目数が増えても自動ではスクロールしない。** `Modifier.verticalScroll(rememberScrollState())`を明示的に付ける。
14. **`enableEdgeToEdge()`使用時、`Scaffold`の`bottomBar`には`Modifier.navigationBarsPadding()`を明示的に付ける必要がある。**
15. **オーバーレイウィンドウ内での描画は、タッチイベントの座標の使い分けに注意すること。** `event.rawX/rawY`は画面全体を基準にした**絶対座標**（Bitmapからのピクセル取得・切り出し・別ウィンドウへの送信用）、`event.x/y`は**ウィンドウ内のローカル座標**（Canvas描画用）。ウィンドウ原点が画面(0,0)と一致しない場合があり、描画にrawを使うとズレる。ColorPickerController・SequenceRecordingController・TemplateCaptureControllerで同じ方針。
16. **MediaProjectionでのスクリーンキャプチャは、画面遷移の直後だと古いフレームを掴むことがある。** キャプチャ前に100〜300ms程度の待機を挟む。
17. **このチャットの作業用コンテナ（サンドボックス）は、セッションの途中でリセットされ、それまでの編集内容が失われることがある。** ファイル編集前は必ず現在の内容を確認する。ビルドエラーが出たら推測で直さず、エラーメッセージと手元の実ファイルをもらってから対応する。
18. **ファイルの内容は、手元の最新版と食い違っていることがある（古い知識、貼り付けの違い）。** 実際に、`libs.versions.toml`のAGP（引き継ぎ書は8.7.3／手元は8.13.2）で食い違いがあった。GitHub同期を使っても、**同期が最新かを確認する**。`Edit-SourceFile`の「1か所でなければ変更しない」安全装置が、古いファイルへの誤った変更を防ぐ。
19. **APKの中身の確認方法。** Android Studioの「Build → Analyze APK...」か、Terminalで `tar -tvf app\build\outputs\apk\debug\app-debug.apk | findstr "lib/"`。
20. **端末のCPU（ABI）の確認方法。** `adb shell getprop ro.product.cpu.abilist`。
21. **Android Studioでファイルをドラッグして別フォルダへ移すと、1行目の`package`が、移動先に合わせて自動で書き換わる。** 設計上の置き場所とずれるとimportが解決できずビルドエラーになる。移動後は、フォルダと`package`の一致を確認する。
22. **Roomのマイグレーション。** NOT NULL列は`DEFAULT`と`@ColumnInfo(defaultValue)`を揃える。確認は、アンインストールせずに上書きインストールして、既存データが残っていることを見る。
23. **VirtualDisplay（`AUTO_MIRROR`）は、画面が変化した時だけフレームを作る。** 「次に届くフレームを待つ」方式は、止まった画面で待たされる。`ScreenshotCaptureService`は直近のフレームを保持し、`captureBitmap()`は新しいフレームを最大300ms待ってから、保持フレームを返す。
24. **画像認識では、「判定できなかった」と「見つからなかった」を区別する。** 判定できなかった場合（キャプチャ失敗など）を「見つからなかった」にすると、反転ジャンプのステップで、誤ってジャンプする。判定できなかった場合は、実行を停止する（`ImageEvaluation.Failed`）。
25. **オーバーレイのツールバーは、文言が長いと画面からはみ出し、「決定」が見えなくなる。** ボタンは記号だけ（✕ ↺ ✓）にして最小幅をなくし、表示は短く・幅固定にする（`createSymbolButton`。`TemplateCaptureController`と`ColorPickerController`に同じ形で2つある。3か所目が出たら共通化する）。
26. **実行中のSTOPパネルは、再生ボタンを置いた位置に出る。** 画像認識で「位置をタップ」を使う時は、再生ボタンをタップ位置から離して置く。
27. **Composeの`DisposableEffect`の`onDispose`では、ローカルの`by remember { mutableStateOf }`を直接読む。** `rememberUpdatedState`は再コンポーズされないと更新されない。
28. **原因が不明な不具合は、Logcatのログと、切り分けの実験で確定させてから直す。**
29. **Hiltの設定などは、新規ファイルだけを先に渡して、ビルドできる状態を作ってから、本体の変更を渡す。**
30. **【新規】Android 13以降の「制限付きの設定」。** APKファイルをスマホに送って、スマホ上で入れると、「外部から入れたアプリ」として扱われ、ユーザー補助（アクセシビリティ）が、グレーで押せなくなる（画面に「アプリはアクセスを拒否されました」）。解除の手順（OPPO）：①設定→ユーザー補助→ダウンロードしたアプリ→ゴーストタッチを一度タップして、警告を出す→「閉じる」 ②設定→アプリ→アプリ管理→ゴーストタッチ→右上の「︙」（または下へスクロール）→**「制限付きの設定を許可」**（本人確認が出ることがある） ③ユーザー補助で、ゴーストタッチを**オン**にする。**予防：** 実機へは、Android Studioの▶か、`adb install -r`で入れる。項目が出ない場合は、「︙」の中身と、AndroidとColorOSのバージョンを確認する。
31. **【新規】GitHubに、不要なファイルが入ることがある。** 最初のコミットで、Kotlinのコンパイラの一時ファイル（`.kotlin/sessions/*.salive`）が追跡されてしまった。`.gitignore`の確認に使う `git check-ignore -v <名前>` と、コミット前の `git status` の一覧確認を、**毎回**行う。
32. **【新規】PowerShell（Windows PowerShell 5.1）の注意。** 日本語をGitのコミットメッセージで使うと文字化けすることがある→英語にする。日本語を含む`.ps1`は、**BOM付きUTF-8**で保存する。スクリプトの読み込みは、実行ポリシーを避けるため`iex (Get-Content …)`。
33. **【新規】プロジェクトの知識（GitHub同期）は、新しいチャットでないと反映されない。** 同期したら、新しいチャットで、`docs/handover.md`を読めるかを確認する。
34. **【新規】ユーザーの質問の意味を取り違えることがある。** 「ターミナルを使ってコードを変更したい」を、Claude Code（自動編集ツール）の話と誤解した。意味が複数に取れる時は、**先に確認する**。
35. **【新規】オーバーレイの窓の原点は、画面の(0,0)と一致しないことがある（OPPO Reno11 Aで、約107px下）。** 症状は、マーカーが目的の位置より下に出る、ドラッグで保存した座標が、見た目より上にずれる、など。原因の確認は、診断ログで、`getLocationOnScreen`の値と、`WindowManager.LayoutParams`の`x,y`の差（`diff`）を見る。マーカーのような小さい窓（`WRAP_CONTENT`／固定サイズ）は、`FLAG_LAYOUT_IN_SCREEN`を付けると、`diff=(0,0)`になった。
36. **【新規】`MATCH_PARENT`の窓（スワイプの線）は、`FLAG_LAYOUT_IN_SCREEN`や`FLAG_LAYOUT_NO_LIMITS`を付けても、原点が107px下のまま、高さも約107px足りなかった。** フラグを重ねて試すのをやめ、**描画側で、`onDraw`のたびに`getLocationOnScreen`で窓の原点を実測し、`canvas.translate(-x, -y)`で打ち消す**方式で解決した（端末が違っても追従する）。ドラッグ中に線を動かすには、線の座標を「更新できる表」（`swipeLines`）で持ち、`invalidate()`で再描画する。
37. **【新規】`Edit-SourceFile`が「認識されません」と出たら、ターミナルを開き直した後の、読み込み忘れ。** 先頭の1行を見て、構文エラーと区別する。読み込みは、`iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)`。
38. **【新規】PowerShell 5.1の`git diff`は、日本語が化けて見えることがあるが、ファイルは壊れていない場合が多い。** 確認は、`Select-String -Path <パス> -Pattern "<日本語>" -Encoding UTF8`、`Get-Content <パス> -Encoding UTF8`、先頭バイト（`ReadAllBytes`）で行う。`git diff`がページャー（`(END)`）で止まったら`q`を押す。`git --no-pager diff`なら、止まらない。

---

## 9. 現在の状況（スナップショット：2026-11-19）

### 9-1. Git・GitHub

- ブランチ `main` は、`origin/main` と同期済み（`Your branch is up to date`）。
- **`.kotlin/` の追跡外しは完了済み**（`.gitignore` の24行目に `.kotlin/` があり、`git ls-files .kotlin` は空）。下の手順は、記録として残している（実行は不要）。
- 過去の状況：`.kotlin/sessions/kotlin-compiler-….salive` が、追跡されていた。
  - 直す手順（10章のTODO 2）：
    ```powershell
    $wanted = @('.kotlin/')
    $existing = if (Test-Path .gitignore) { Get-Content .gitignore } else { @() }
    $missing = $wanted | Where-Object { $existing -notcontains $_ }
    if ($missing) { Add-Content .gitignore ("`r`n" + ($missing -join "`r`n")) }
    git rm -r --cached .kotlin
    git status
    git add -A
    git commit -m "Stop tracking .kotlin directory"
    git push
    ```

### 9-2. コードの状況

| 項目 | 状態 |
|---|---|
| マーカーの表示/非表示ボタン（◎、最初は非表示） | **実装済み・実機確認済み（1〜12すべて成功）・コミットとpush済み** |
| ボタンを、◎から「目のアイコン」に変更 | **実装済み・実機確認済み・push済み。** `service/EyeIconDrawable.kt`（斜線入り＝非表示、開いた目＝表示中）。前回の構文エラーの原因は、ターミナルを開き直した後に`edit-helpers.ps1`の読み込みを忘れたこと（`Edit-SourceFile`が認識されない）だった可能性が高い |
| 診断ログ（`StepMarkerDiag`） | 使用後に**削除済み**（確認のためだけに入れたもの） |
| マーカーのずれ | **修正済み・実機確認済み・push済み。** 8章の教訓35〜38を参照。①（軽く触れると見た目が動く）は再現せず、保留 |
| スワイプの線 | マーカーのドラッグ中に、線が**追従する**ようにした（実機確認済み・push済み）。線のデータは`swipeLines`（ステップIDごとの座標の表）で、`updateLine()`で更新する |
| 画像認識の第1段階・第2段階の前半 | 実機確認済みで完了 |
| `docs/spec.md`（仕様書） | **存在しない**（ユーザーが削除した）。現在のコードと、この引き継ぎ書から、新しく作り直す（10章のTODO 6） |

### 9-3. 実機（スマホ）の状況

- 直近の更新で、**APKファイルをスマホに送って入れた**ため、ユーザー補助の「ゴーストタッチ」が、**グレーで押せない状態**になった（教訓30）。
- **解除済み。** ユーザー補助は、オンに戻っている（2026-11-18に確認）。
- 今後は、Android Studioの▶か、`adb install -r`で入れる。

### 9-4. 目のアイコンの仕様（適用する時に使う）

- 3つ目のボタンの記号「◎」を、**目のアイコン**に変える（文字記号は、端末のフォントで表示が変わるため、コードで描画する）。
- **非表示（グレーの丸）は、斜線入りの目。表示中（青い丸）は、開いた目。**
- 実装方法：新規ファイル `service/EyeIconDrawable.kt`（`internal class EyeIconDrawable(private val crossed: Boolean) : Drawable()`）を作り、ボタン（`TextView`）の **`foreground`** に設定する。`FloatingButtonController.kt`は2か所を変える：
  1. ボタンの作成で、`text = "◎"` の行を、`foreground = EyeIconDrawable(crossed = true)` に置き換える（最初は非表示）。
  2. `refreshMarkerToggle()`の`val color = ...`の次の行に、`markerToggleView?.foreground = EyeIconDrawable(crossed = !markersVisible)` を足す。
- 描画の要点（ボタンの幅`w`、高さ`h`、中心`cx,cy`）：
  - 白の`Paint`（アンチエイリアス）。輪郭は`STROKE`（太さ`w*0.06`、端は丸）、黒目は`FILL`。
  - 目の輪郭（アーモンド型）：`halfWidth = w*0.26`、`lidHeight = h*0.17`。`moveTo(cx-halfWidth, cy)` → `quadTo(cx, cy-lidHeight*2, cx+halfWidth, cy)` → `quadTo(cx, cy+lidHeight*2, cx-halfWidth, cy)` → `close()`。
  - 黒目：中心に、半径`w*0.075`の円。
  - `crossed = true`の時：`(cx-halfWidth*0.85, cy-halfWidth*0.75)` から `(cx+halfWidth*0.85, cy+halfWidth*0.75)` へ、斜線を引く。
  - `setAlpha` / `setColorFilter` / `getOpacity`（`TRANSLUCENT`）を実装する。
- `foreground`は、`minSdk 26`で使える（API 23以降）。

### 9-5. 診断ログの仕様（ずれの確認に使う。確認後は、`git restore`で戻す）

`StepMarkerController.kt`の`addMarker`の、`markerViews.add(marker)`の次に、次のブロックを足す。

```kotlin
// 【診断用】ウィンドウの位置と、実際の画面上の位置の差を確認する(確認後に削除する)
marker.post {
    val onScreen = IntArray(2)
    marker.getLocationOnScreen(onScreen)
    android.util.Log.d(
        "StepMarkerDiag",
        "label=$label params=(${params.x},${params.y}) " +
                "onScreen=(${onScreen[0]},${onScreen[1]}) " +
                "diff=(${onScreen[0] - params.x},${onScreen[1] - params.y})"
    )
}
```

---

## 10. 未解決・TODO（優先順）

1. **【最初】新しいチャットで、`docs/handover.md`の同期を確認する**（0章）。
2. ~~`.kotlin/`をGitの追跡から外す~~ **完了済み**。
3. ~~スマホのユーザー補助を、オンに戻す~~ **完了**（更新は、▶か`adb install -r`で入れること）。
4. ~~**目のアイコンと診断ログを適用する。**~~ **完了**（目のアイコンは適用済み。診断ログは、確認後に削除済み）。以下は、当時の記録： PowerShellで構文エラーが出た原因を、**エラーの文面を確認して**から直す（文面が分からなければ、先に、小さなブロックに分けて、1つずつ実行してもらう）。長いコマンドが原因なら、**Android Studioのエディタに直接貼り付ける**（`EyeIconDrawable.kt`は、エディタで新規作成して貼る。2か所の変更は、1行ずつの書き換えなので、エディタで可能）。
5. ~~**マーカーのずれの確認と、修正**~~ **完了**（窓の原点107pxのずれ、スワイプの線の追従。①は再現せず保留）。
6. **`docs/spec.md`の新規作成**（仕様書は削除されて存在しない）。現在のコードと、この引き継ぎ書から、章ごとに（1章ずつ）作り直す。意図が分からない箇所は、推測せず、ユーザーに確認する。
7. 小さな整理：`createSymbolButton`の共通化（3か所目が出たら）、`templates/tmp`の掃除の仕組み（強制終了で残る可能性）。
8. 保留中の機能（ユーザーの判断で保留）：「しきい値を試すボタン」「探索範囲の指定」「一致位置からのずらし量」、OCR。
9. 将来、Google Playで公開する場合：16KBページサイズへの整列の確認（OpenCVの`libopencv_java4.so`は、約23MB、`arm64-v8a`のみで同梱を確認済み）。
10. その他（軽微）：`MacroExecutor`の古いコメント（「SCREENSHOT等は次フェーズで対応」）、シナリオ一覧のタップ反応（保留）、`OpenCvTemplateMatcher.kt`のエディタ警告1件（内容未確認）、`CoordinateCaptureController.kt`など一部のファイルは、GitHub同期後に、現物を確認する。

### マーカーのずれ（TODO 5の詳細）

**ユーザーの要望**：配置待機の画面の、操作用のアイコン（マーカー）が、画面の操作でずれてしまうことがある。まず、**表示/非表示ボタンを付ける**（済み）。**そのあと、ずれを確認する。**

**`StepMarkerController`のコードを読んで分かったこと（確定）**

| # | 内容 | 起きる現象 |
|---|---|---|
| ① | マーカーを指で触ると、**10px（`tapMoveThreshold`）以上動かさなくても、見た目は動く**（`ACTION_MOVE`で、常に`params`を更新）。ところが、保存されるのは、**10px以上動かした時だけ**（`ACTION_UP`で、`isDragging`の時のみ`onPositionChanged`） | 軽く触れて離すと、**マーカーの見た目だけ、最大10px弱ずれる**。実際のタップ位置は、変わっていない |
| ② | スワイプの**線**（`lineView`）は、`show()`で1回だけ描かれ、マーカーをドラッグしても更新されない。位置の保存（`updateStepPosition`）も、再表示はしない | 始点・終点を動かしたあと、**線が、古い位置に残る** |

**未確認の可能性**

| # | 内容 | 確認方法 |
|---|---|---|
| ③ | マーカーの窓は、`gravity = TOP\|START`、`flags = FLAG_NOT_FOCUSABLE`のみ（`FLAG_LAYOUT_IN_SCREEN`などが無い）。窓の原点が、状態バーの高さなどの分だけ、**画面の(0,0)とずれている**可能性がある（教訓15と同種）。もしそうなら、マーカーを目的の場所へ置いても、**保存される座標は、見た目より、その分だけ上にずれる** | 診断ログ（9-5）で、`diff`を見る |
| ④ | マーカーは、64pxの固定サイズで小さい。指で隠れて、**正確な位置に置きにくい** | 使用感 |

**確認の手順（ユーザーが行う。診断ログを適用してから）**

確認用のシナリオ「ずれテスト」：タップ（ホーム画面の「設定」アイコンの真ん中を、「画面から取得」で取る）＋スワイプ（適当な始点・終点）。Logcatの検索欄に `StepMarkerDiag`。

| # | 確認 | 操作 | 見ること |
|---|---|---|---|
| 1 | 目のアイコン | 配置待機の画面を出す | 3つ目のボタンが、グレーの丸に斜線入りの目。押すと、青い丸に開いた目 |
| 2 | 診断ログ | 「ずれテスト」で配置待機を出し、マーカーを表示 | Logcatの`StepMarkerDiag`の行（3行ほど）を貼る |
| 3 | 画面から取得との比較 | マーカー①を見る | 「設定」アイコンの真ん中に重なっているか。ずれるなら、方向と大きさ |
| 4 | 軽く触れる（候補①） | マーカー①に、ほとんど動かさずに触れて離す | マーカーが、少し動いてしまうか |
| 5 | スワイプの線（候補②） | スワイプのマーカーを動かす | 線が、古い位置に残るか |
| 6 | 動かして実行 | マーカー①を、別のアプリのアイコンの真ん中へドラッグ→表示し直す→「▶」を、アイコンから離れた所に置いて実行 | 置いた位置のままか。狙ったアイコンが、実際に開くか |

**結果の読み方**

| 結果 | 意味 | 次の対応 |
|---|---|---|
| 2の`diff=(0,0)`、3でずれなし | 窓の原点のずれは、なし | 4・5の結果から、①②の修正へ |
| 2の`diff`が`(0, 数字)`など、3でその方向にずれ | 窓の原点がずれている（③が確定） | 窓の配置の設定を直す（`FLAG_LAYOUT_IN_SCREEN`の追加など。ログで、ずれの量を確認してから） |
| 4で動く | ①が確定 | 不具合の修正 |
| 5で線が残る | ②が確定 | 不具合の修正（ドラッグ中に線も動かす） |

**改善のアイディア（推奨の順番）**

| # | アイディア | 効果 |
|---|---|---|
| 1 | **不具合の修正**：①（動かしたら必ず保存、または10px未満は見た目も動かさない）、②（ドラッグ中に線も動かす） | ずれの原因（確定分）を解消 |
| 2 | **窓の原点のずれの修正**（③が確認できたら） | 保存される座標と、見た目が一致する |
| 3 | **座標の表示＋十字線**：ドラッグ中に`(x, y)`と中心の十字線を出す | 指で隠れても、位置が分かる |
| 4 | **微調整ボタン**：マーカーを選ぶと、小さな「▲▼◀▶」（1px／5px） | 細かい位置合わせ |
| 5 | **編集ロック**：初期は動かせず、「編集」の時だけ動かせる | 誤操作を防ぐ |
| 6 | **元に戻す** | 失敗してもやり直せる |
| 7 | 座標を画面の比率で保存する（変更が大きい。保留を推奨） | 解像度・向きが変わっても対応しやすい |

---

## 11. 次にやること

1. 新しいチャットで、同期を確認する（0章）→ 9章・10章を、ユーザーに要約する。
2. `docs/spec.md`を、章ごとに新規作成する（TODO 6）。
3. マーカーのずれの改善アイディア3〜6（座標表示・十字線、微調整ボタン、編集ロック、元に戻す）は、必要になった時に相談する。
4. コミット、push、Sync（新しいチャットで、同期を確認する）。

---

## 付録A：`tools/edit-helpers.ps1`（リポジトリにあるものと同じ内容）

BOM付きUTF-8で保存すること。読み込み：`iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)`

```powershell
# tools/edit-helpers.ps1
# 使い方(プロジェクトのルートで):
#   iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# ファイルを丸ごと書き出す(なければ作る)。改行はCRLFにそろえる。
function Write-SourceFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Content
    )
    $full = Join-Path (Get-Location).Path $Path
    $dir = Split-Path $full -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $text = $Content -replace "`r?`n", "`r`n"
    [System.IO.File]::WriteAllText($full, $text, $Utf8NoBom)
    Write-Host "書き込みました: $Path"
}

# 文字列を1か所だけ置換する。1か所でなければ、何も変更しない。
function Edit-SourceFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Old,
        [Parameter(Mandatory)][string]$New
    )
    $full = Join-Path (Get-Location).Path $Path
    if (-not (Test-Path $full)) { Write-Error "ファイルがありません: $Path"; return }

    $text = [System.IO.File]::ReadAllText($full)
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $oldN = $Old -replace "`r?`n", $eol
    $newN = $New -replace "`r?`n", $eol

    $count = ([regex]::Matches($text, [regex]::Escape($oldN))).Count
    if ($count -ne 1) {
        Write-Error "置換前の文字列が $count 箇所ありました(1箇所である必要があります)。変更しません: $Path"
        return
    }

    [System.IO.File]::WriteAllText($full, $text.Replace($oldN, $newN), $Utf8NoBom)
    Write-Host "置換しました: $Path"
}
```

---

## 付録B：Claudeプロジェクトの指示文に加える内容（案）

プロジェクトの指示文に、次を加えると、この進め方が、毎回引き継がれる。

- **コードの変更は、PowerShell用のコマンド（`Write-SourceFile` / `Edit-SourceFile`）で渡す。** 使い方は `docs/handover.md` の2-2項に従う。ターミナルを開くたびに、`iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)` を、先に実行してもらう。
- 作業の前に、`docs/handover.md` の**9章（現在の状況）**と**10章（TODO）**を読み、ユーザーに要約する。
- 編集の前は、GitHubの同期が最新かを確認する。コマンドは、`Edit-SourceFile`の安全装置（置換前が1か所でなければ変更しない）に頼る。
- 変更のたびに、コマンドの**成功の回数**を伝え、`git diff --stat`、ビルド、実機確認（番号付きの表）の順で進める。コミットメッセージは、英語にする。
- 実機へは、Android Studioの▶か`adb install -r`で入れる（APKを送らない）。
- 意味が複数に取れる要望は、**先に確認する**。
- 大きなコマンドは、**小さなブロックに分けて**渡す。
