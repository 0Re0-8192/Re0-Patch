# Re0 Gboard Patches（Morphe用）

Gboard の「フラグ」を好きな値に書き換えて遊ぶための、[Morphe](https://morphe.software) 互換パッチです。
フラグとは機能のON/OFFや数値設定のことで、Google がサーバーから配信して機能を段階的に出し分けるのに使っています。

> Morphe 公式プロジェクトとは無関係の個人プロジェクトです（[NOTICE](NOTICE)）。

## 🧩 パッチ

| パッチ | 初期状態 | 内容 |
|---|---|---|
| Extend clipboard history | ON | クリップボードの保持期間を延ばせるようにする |
| Change app name | ON | アプリ名を変える |
| Override flags | OFF | 任意のフラグを上書きする（上級者向け） |

ON/OFF だけで使えるパッチは、Morphe Manager の Simple mode でも、何も入力せずにそのまま効きます。

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
