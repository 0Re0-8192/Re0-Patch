# ビルドと開発

友達に配るだけなら、ビルドは要りません。[releases/](../releases) の `.mpp` をそのまま使えます。
ここは、パッチを自分で直す人向けです。

## ビルド

必要なもの: JDK 17以上と Android SDK（platform 36）。`ANDROID_HOME` か `local.properties` の `sdk.dir` で SDK の場所を指定してください。

```sh
./gradlew buildAndroid
# → patches/build/libs/patches-<version>.mpp
```

Morphe の Gradle プラグインとパッチャーは GitHub Packages にあり、取得には GitHub の認証が必要です。次のどちらかを使ってください。

- `read:packages` 権限の PAT を `~/.gradle/gradle.properties` に `gpr.user=<GitHubユーザー名>` と `gpr.key=<トークン>` として書く（[Morphe のドキュメント](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md)）。
- [morphe-patcher](https://github.com/MorpheApp/morphe-patcher)（v1.15.1）と [morphe-patches-gradle-plugin](https://github.com/MorpheApp/morphe-patches-gradle-plugin)（v1.3.4）をソースから `./gradlew publishToMavenLocal` する。
  `settings.gradle.kts` が `mavenLocal()` を最初に見るので、トークンなしでビルドできます。
  この場合も `-Pgpr.user=x -Pgpr.key=x` のようなダミー値は渡してください（プラグインが値の存在を要求するため）。
  署名用の `signing {}` が gpg を要求したら、ローカル用にそのブロックを外します。

### 新しい Gboard に対応させるとき

fingerprint はフラグ実装クラスの文字列 `"Clearing default value is disallowed ["` と `"Invalid flag: "` を頼りにしています（[Fingerprints.kt](../patches/src/main/kotlin/app/re0/patches/gboard/flags/Fingerprints.kt)）。
適用に失敗したら jadx でこの文字列を探してください。
内部名 `com/google/android/libraries/inputmethod/flag/FlagFactory` と `FlagManager` もログ文字列として残っているので、目印になります。

## Morphe CLI でパッチを当てる

スマホの Morphe Manager を使う場合は要りません（[README](../README.md)）。パソコンで当てたいときの方法です。

[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) の jar に CLI が入っています（`morphe-desktop-<version>-all.jar`）。
GUI で使う場合も、ビルドした `.mpp` を他のパッチバンドルと同じように読み込めます。

**公式 Gboard と並べて入れたい場合:** 公式パッチの **Clone app**（パッケージ名の変更）と **Spoof signature** も一緒に当てます。
Morphe Manager では Expert mode で選べます。
CLI では `-e` と `-O` は直前の `--patches` に対して効く点に注意してください。

```sh
java -jar morphe-desktop-1.18.1-all.jar patch --exclusive \
  --patches=patches/build/libs/patches-<version>.mpp \
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

`adb install gboard-patched.apk` で入れて、端末の設定でキーボードを有効にします。

## 構成

```
patches/      パッチ本体（Kotlin）。fingerprint とバイトコード注入
extensions/   Gboard 内で動く Java コード（.mpe として同梱）
docs/         詳しい説明とフラグ一覧
```

## 変更の履歴

[CHANGELOG.md](../CHANGELOG.md) を見てください。
