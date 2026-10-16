# フラグを触る（上級者向け）

Gboard の「フラグ」（機能の ON/OFF や数値設定。Google がサーバーから配信して、機能を段階的に出し分けるのに使っている）を書き換えて遊ぶための説明です。
`Override flags` パッチ（初期状態 OFF）の使い方は [PATCHES.md](PATCHES.md#override-flags) にあります。

## 遊んでみたいフラグ

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

## 実機で試す

公式 Gboard と並べて入れた改造版（パッケージ名 `com.google.android.inputmethod.latin.morphe`）での例です。

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
