# Re0 Gboard Patches（Morphe用）

Gboard の「フラグ」を好きな値に書き換えて遊ぶための、[Morphe](https://morphe.software) 互換パッチです。
フラグとは機能のON/OFFや数値設定のことで、Google がサーバーから配信して機能を段階的に出し分けるのに使っています。

> Morphe 公式プロジェクトとは無関係の個人プロジェクトです（[NOTICE](NOTICE)）。

## 🧩 パッチ

| パッチ | 初期状態 | 内容 |
|---|---|---|
| Keep clipboard history forever | ON | クリップボードの履歴を時間で消さない |
| Half-width digits in Japanese input | ON | 日本語入力中の数字を、打った瞬間から半角にする。設定にスイッチも追加 |
| Show all clipboard history | ON | 最新5回分しか出ないクリップボードの表示を、保存されている全件にする |
| Extend clipboard history | OFF | クリップボードの保持期間を最大24時間から選べるようにする |
| Change app name | ON | アプリ名を変える |
| Override flags | OFF | 任意のフラグを上書きする（上級者向け） |

ON/OFF だけで使えるパッチは、Morphe Manager の Simple mode でも、何も入力せずにそのまま効きます。

### Keep clipboard history forever

ピン留めしていないクリップは、通常は1時間で削除されます。
このパッチは保持期間を決めるメソッド（`ClipboardContentProviderUtils.getUnpinnedItemTimeLimitInMilliSeconds`）が常に100年を返すようにするので、自分で消すまで残ります。
件数の上限（ピン留めなしで約100件）はそのままです。
Extend clipboard history と一緒に当てると、こちらが優先されて保持期間の設定は効かなくなります。

### Half-width digits in Japanese input

日本語入力中にQWERTYの数字キーで打った数字は、通常は「１２３」と全角で表示されます。
このパッチを当てると、打った瞬間から「123」と半角になります。変換候補には全角も残ります。

Gboard の **設定 → キー** に **「数字を常に半角で入力」** のスイッチが増えます（初期値はON）。
切り替えたあとは、次に入力欄を開いたときに反映されます。

**仕組み:** 日本語変換エンジン（Mozc）は、文字の種類ごとの「全角・半角のルール」（`character_form_rules`）で打った文字を変換します。
Gboard が渡す設定にはこのルールが無いので、Mozc の初期ルール（数字は入力中も全角）が使われています。
パッチは、エンジンに送る命令を作っている `LocalSessionHandler.evalCommand` にフックして、設定を送る命令（`SET_CONFIG`）の末尾にルールを足します。
protobuf は同じメッセージが2回出てきたら中身を合成するので、既存の設定はそのまま残ります。
ルールが1つでもあると初期ルールは使われなくなるので、Mozc の初期ルール12個をそのまま足したうえで、数字（グループ `"0"`）だけ半角にしています。
スイッチの値は SharedPreferences の `re0_half_width_digits` に保存されます。

### Show all clipboard history

Gboard のクリップボード画面の「最近」には、**最新5回分のコピー**しか表示されません（同じ時刻のクリップは1回分として数えます）。
さらに、表示した中で一番古いクリップの時刻を `clipboard_unpinned_item_threshold_time` に保存し、次からはそれより古いものを検索しなくなります。
そのため、6回目より前のクリップは保存されていても二度と表示されません。
このパッチは5回分の上限（`if (copies >= 5) break;`）を外し、保存された時刻での絞り込みも無視して、保存されている全件（ピン留めなしで約100件まで）を表示します。
同じ上限は古い検索処理にもあるので、Gboard の初期値どおりフラグ `enable_clipboard_query_refactoring` を ON に固定し、新しい方を必ず使わせます。

### Extend clipboard history

ピン留めしていないクリップは、通常は1時間で削除されます。
このパッチを当てると、Gboard の **設定 → クリップボード** に **「クリップボードの履歴を保持する期間」** が出てきて、1・2・3・6・12・18・24時間から選べます。
中身はフラグ `enable_extended_clipboard_history` を ON にしているだけです（Gboard に元から入っている隠し設定）。

### Override flags

Gboard のフラグを上書きします。サーバー側の設定（Phenotype）より優先されます。
設定値の入力が必要なので、Morphe Manager では Expert mode で使ってください。

**仕組み:** Gboard はローカルの初期値、サーバー配信値、OEM値などを1つの最終値にまとめてから使います。
内部パッチ（`FlagOverridesHookPatch`）がその最終値を返すゲッター（難読化名 `odx.g()`）をフックします。
そのため、すべてのフラグ読み取りが extension の `FlagOverridePatch.getFlagValue(name, value)` を通ります。
各パッチが追加した上書きは、全パッチの実行後（`finalize`）にまとめて extension に書き込まれます。
同じフラグなら、このパッチのオプションで入れた値が優先されます。

| オプション | 内容 |
|---|---|
| `flagOverrides` | `name=value` を `;` 区切りで並べたもの。例: `enable_extended_clipboard_history=true;text_clip_item_char_limit=50000` |
| `logFlags` | Gboard が読んだフラグの名前と値を logcat（タグ `GboardFlags`）に出します。上書き候補を探すのに便利です。 |

**再パッチなしで上書きする方法:**
`/sdcard/Android/data/<パッケージ名>/files/flag_overrides.txt` に1行1つ `name=value` を書きます。
`#` で始まる行はコメントです。ファイルの値はパッチオプションより優先されます。

値は元のフラグの型に合わせて変換されます。

| 型 | 書き方 |
|---|---|
| bool | `true` / `false`（`1` / `0` も可） |
| long | 整数 |
| double | 小数 |
| string | そのまま（`;` は使えません） |

protobuf（byte[]）のフラグには対応していません。変換できない値は無視され、logcat に警告が出ます。

### Change app name

アプリ名（＝端末のキーボード一覧に出る名前）を変えます。初期値は `Gboard Re0` です。
Clone app で公式 Gboard と並べて入れたときに見分けるためのものです。

## 🎮 遊んでみたいフラグ

18.4.1 のコードから見つけたフラグの一覧（型と初期値つき）は [flags-18.4.1.txt](flags-18.4.1.txt) にあります。
いくつか例を挙げますが、**どれも名前から推測しただけで動作は未確認です。**

| フラグ | 型 | 初期値 | 推測 |
|---|---|---|---|
| `enable_extended_clipboard_history` | bool | false | クリップボード履歴の拡張 |
| `text_clip_item_char_limit` | long | 20000 | クリップボードに残すテキストの文字数上限 |
| `config_keyboard_max_height` | long | -1 | キーボードの最大の高さ |
| `config_keyboard_height_ratio` | string | "1.0" | キーボードの高さの倍率 |
| `enable_access_points_menu_redesign` | bool | false | ツールバーメニューの新デザイン |
| `enable_custom_sticker_tab` | bool | false | カスタムステッカーのタブ |
| `enable_debug_toast_for_proactive_emoji_kitchen` | bool | false | Emoji Kitchen のデバッグ用トースト |

サーバー側のデータが必要な機能は、フラグを有効にしても動かないことがあります。

## 🚀 使い方

### 1. APK を用意する

Gboard **18.4.1.985164140-release-arm64-v8a** で開発しました。
フラグの仕組みはあまり変わらないので、他のバージョンにも当たる可能性があります（experimental 扱い）。

### 2. パッチを当てる（Morphe CLI）

[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) の jar に CLI が入っています（`morphe-desktop-<version>-all.jar`）。
GUI で使う場合も、ビルドした `.mpp` を他のパッチバンドルと同じように読み込めます。

**スマホだけで作る場合（Morphe Manager）:** **Sources → ＋ → Local** でこの `.mpp` を追加し、ホームの Gboard から普通にパッチします。
Simple mode なら初期状態が ON のパッチがそのまま当たります。
公式 Gboard はアンインストールが必要です（Morphe Manager が案内します）。

**公式 Gboard と並べて入れたい場合:** 公式パッチの **Clone app**（パッケージ名の変更）と **Spoof signature** も一緒に当てます。
Morphe Manager では Expert mode で選べます。
CLI では `-e` と `-O` は直前の `--patches` に対して効く点に注意してください。

```sh
java -jar morphe-desktop-1.18.1-all.jar patch --exclusive \
  --patches=patches/build/libs/patches-1.1.0.mpp \
    -e "Extend clipboard history" \
    -e "Change app name" \
    -e "Override flags" \
    -O logFlags=true \
    -O 'flagOverrides=text_clip_item_char_limit=50000' \
  --patches=patches-1.47.0.mpp \
    -e "Clone app" \
    -e "Spoof signature" \
  -o gboard-patched.apk \
  gboard.apk
```

Clone app の初期設定では、パッケージ名が `com.google.android.inputmethod.latin.morphe` になります。

### 3. インストールして有効にする

```sh
adb install gboard-patched.apk
```

端末の **設定 → システム → キーボード → 画面キーボード** で、追加された Gboard を有効にします。

### 4. フラグを試す

```sh
PKG=com.google.android.inputmethod.latin.morphe

# フラグの読み取りを見る（logFlags=true でパッチした場合）
adb logcat -s GboardFlags

# 上書きファイルを置いて Gboard を再起動する
adb shell mkdir -p /sdcard/Android/data/$PKG/files
adb push flag_overrides.txt /sdcard/Android/data/$PKG/files/
adb shell am force-stop $PKG
```

ファイルはプロセスの起動時に1回だけ読まれます。変更したら `force-stop` してください。
おかしくなったらファイルを消して `force-stop` すれば元に戻ります。
パッチオプションで入れた上書きは、消すのに再パッチが必要です。試行錯誤にはファイルの方が向いています。

## ⚠️ 注意

- **実機ではまだ動かしていません。** 確認済みなのは次の4つです。
  - ビルド
  - 18.4.1 arm64 への適用（Clone app / Spoof signature との組み合わせも含む）
  - 生成された smali の中身
  - extension の JVM 単体テスト
- 署名が変わるので、Google アカウントの同期や音声入力など、Google Play 開発者サービスを使う機能は動かないかもしれません。
- フラグの組み合わせによってはクラッシュします。

## 🛠️ ビルド

必要なもの: JDK 17以上と Android SDK（platform 36）。`ANDROID_HOME` か `local.properties` の `sdk.dir` で SDK の場所を指定してください。

```sh
./gradlew buildAndroid
# → patches/build/libs/patches-1.1.0.mpp
```

Morphe の Gradle プラグインとパッチャーは GitHub Packages にあり、取得には GitHub の認証が必要です。次のどちらかを使ってください。

- `read:packages` 権限の PAT を `~/.gradle/gradle.properties` に `gpr.user=<GitHubユーザー名>` と `gpr.key=<トークン>` として書く（[Morphe のドキュメント](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md)）。
- [morphe-patcher](https://github.com/MorpheApp/morphe-patcher)（v1.15.1）と [morphe-patches-gradle-plugin](https://github.com/MorpheApp/morphe-patches-gradle-plugin)（v1.3.4）をソースから `./gradlew publishToMavenLocal` する。
  `settings.gradle.kts` が `mavenLocal()` を最初に見るので、トークンなしでビルドできます。
  この場合も `-Pgpr.user=x -Pgpr.key=x` のようなダミー値は渡してください（プラグインが値の存在を要求するため）。
  署名用の `signing {}` が gpg を要求したら、ローカル用にそのブロックを外します。

### 新しい Gboard に対応させるとき

fingerprint はフラグ実装クラスの文字列 `"Clearing default value is disallowed ["` と `"Invalid flag: "` を頼りにしています（[Fingerprints.kt](patches/src/main/kotlin/app/re0/patches/gboard/flags/Fingerprints.kt)）。
適用に失敗したら jadx でこの文字列を探してください。
内部名 `com/google/android/libraries/inputmethod/flag/FlagFactory` と `FlagManager` もログ文字列として残っているので、目印になります。

## 📁 構成

```
patches/      パッチ本体（Kotlin）。fingerprint とバイトコード注入
extensions/   Gboard 内で動く Java コード（.mpe として同梱）
flags-18.4.1.txt  フラグ一覧
```

## 📜 ライセンス

[GPLv3](LICENSE)（[Morphe Patches template](https://github.com/MorpheApp/morphe-patches-template) をもとにしています）
