# Re0 Patch — Gboard を便利にするパッチ

Android の Gboard（Google のキーボード）を、[Morphe](https://morphe.software) でパッチして使いやすくするパッチ集です。
スマホだけで入れられます（パソコンは要りません）。

> Morphe 公式プロジェクトとは無関係の個人プロジェクトです（[NOTICE](NOTICE)）。

## できること

- **キーボードの背景に動画・GIF・画像を流せる。** 閉じた画面と開いた画面（折りたたみの広い画面など）で、別のファイル・別の見せ場所にできる
- **クリップボードのキー**を、文字入力のキーボードにも数字キーボードにも足す
- **クリップボードの強化。** 履歴が消えない、全件見える、1件20万字まで
- **数字・電話のキーボードに左右の矢印キー**を足す
- **日本語入力中の数字を、最初から半角**にする（設定でON/OFF）
- **テーマの画像が荒くならない**（読み込み上限を512pxから2048pxに）
- **テーマの新規作成で動画・GIFを選べる**

何がどこに出るかの説明は、下の「[使い方](#使い方)」にあります。パッチごとの細かい仕組みは [docs/PATCHES.md](docs/PATCHES.md) です。

## 入れ方

対象は **Gboard 18.4.1.985164140-release-arm64-v8a** だけです。他のバージョンでは動きません（当たらないか、壊れます）。

1. Morphe Manager を入れる（[公式サイト](https://morphe.software)）
2. [Releases](https://github.com/0Re0-8192/Re0-Patch/releases/latest) から、最新の `.mpp` をスマホにダウンロードする
3. Morphe Manager で **Sources → ＋ → Local** を開いて、その `.mpp` を選ぶ
4. 上の Gboard 18.4.1 の APK を用意して、Manager のホームから Gboard を選んでパッチする
   - **Simple mode** なら、初期状態がONのパッチがそのまま全部当たる
5. 公式の Gboard が入っていると、Manager に「アンインストールして」と言われるので、消してから入れる
6. 端末の **設定 → システム → キーボード → 画面キーボード** で、入れた Gboard を有効にする

公式 Gboard と並べて入れたいときは、Expert mode で公式パッチの **Clone app** と **Spoof signature** も一緒に当てます。
アプリ名が `Gboard Re0` になるので見分けられます。

## 使い方

### 背景の動画・画像

次のどちらかから開きます。

- Gboard のテーマの画面の一番上にある **「動画・画像の背景を設定する（Re0）」**
- Gboard の設定の一番下の **「背景の動画・画像」**

画面ごとに「閉じた画面」「開いた画面」のカードがあるので、ファイルを選んで、指でずらして見せる場所を決めて、バーで拡大します。
動画は音なしでくり返し再生され、キーボードが出ている間だけ動きます。

**キーボードのテーマは「写真」の背景にしてください。** 単色のテーマだと、背景が不透明で動画がキーの下に隠れます。
明るさは、選んでいるテーマの明るさの設定に従います。

テーマの **＋**（新しいテーマ）で動画や GIF を選んでも、同じ背景として使えます。

### クリップボードのキー

- 文字入力のキーボード: スペースキーの左の地球儀のキーが、クリップボードのキーに変わります。言語の切り替えは**スペースキーの長押し**です。
- 数字・電話のキーボード: 下に足した矢印キーの行の、左右の矢印の間に入ります。
- 日本語の数字・記号のキーボード（あa1 キー）にも入ります。

クリップボードは、保存される文字数が20万字まで、履歴は自分で消すまで残り、画面には保存されている全件（ピン留めなしで約100件）が出ます。

### 日本語入力の半角の数字

Gboard の **設定 → キー → 「数字を常に半角で入力」** で切り替えられます。切り替えたあとは、次に入力欄を開いたときに反映されます。

## パッチの一覧

| パッチ | 初期状態 | 内容 |
|---|---|---|
| Video and image background | ON | キーボードの背景に動画や画像を流す。画面ごとに見せる場所を決められる |
| Video and GIF in the theme builder | ON | テーマの新規作成で動画・GIFも選べる |
| Better theme image quality | ON | テーマの画像の読み込み上限を2048pxに上げる |
| Clipboard key | ON | どのキーボードからでもクリップボードを開くキーを足す |
| Arrow keys in number keyboards | ON | 数字・電話のキーボードに左右の矢印キーの行を足す |
| Half-width digits in Japanese input | ON | 日本語入力中の数字を半角にする |
| Keep clipboard history forever | ON | クリップボードの履歴を時間で消さない |
| Show all clipboard history | ON | クリップボードの履歴を保存されている全件出す |
| Raise clipboard text limit | ON | クリップボード1件の文字数上限を20万字に上げる |
| Change app name | ON | アプリ名を `Gboard Re0` にする |
| Extend clipboard history | OFF | 履歴の保持期間を最大24時間から選べる設定を出す（上の「消さない」と同時には効かない） |
| Override flags | OFF | Gboard のフラグを上書きする（上級者向け。[docs/FLAGS.md](docs/FLAGS.md)） |

## ⚠️ 注意

- **動作を確かめた端末は Galaxy Z Fold 7（作者）と vivo X100 Pro（友達の報告）です。** ほかの端末では、キーの並びが違ったり、動かなかったりするかもしれません。
- 動画（mp4）の再生は、まだ作者が十分に確かめていません。GIF とアニメーションの画像は確認済みです。
- 動画や画像は、解像度が低いとぼけます。横1080px以上（フルHD）がおすすめです。
- 署名が変わるので、Google アカウントの同期や音声入力など、Google Play 開発者サービスを使う機能は動かないかもしれません。
- 壊れたら、公式の Gboard を入れ直せば元に戻ります。

## もっと詳しく

- [docs/PATCHES.md](docs/PATCHES.md): 各パッチの仕組み
- [docs/BUILD.md](docs/BUILD.md): ビルド、パソコンのコマンドで当てる方法、新しい Gboard への対応
- [docs/FLAGS.md](docs/FLAGS.md): フラグを触って遊ぶ方法とフラグ一覧
- [CHANGELOG.md](CHANGELOG.md): 変更の履歴

## 📜 ライセンス

[GPLv3](LICENSE)（[Morphe Patches template](https://github.com/MorpheApp/morphe-patches-template) をもとにしています）
