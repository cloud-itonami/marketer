# operator quickstart — marketer

`marketer` を初めて触る人が、**手を動かして現在地を確かめる**ための最短手順。
設計の説明は `README.md`、データセットの正本は `catalog.edn` にある。ここは
「何を打つと何が返るか」だけを書く。

この文書に載っている手順は、**すべて実際に走らせて出力を転記している**
（実行日 2026-09-01、macOS / darwin 25.3.0）。走らせていない手順は
最後の「踏んでいないもの」節に、踏んでいないと明記して分けてある。

---

## 0. この repo は何を持っているか

| 場所 | 何の正本か |
|---|---|
| `catalog.edn` | データセットと分類体系の一覧（README の "Data Contract (DIV-2)" の Dataset 側） |
| `scripts/catalog-report.cljk` | `catalog.edn` の現在地を出す operator 用レポータ |
| `appview/marketer-ui-iewsbshk/cljs` | 可視化 UI（ClojureScript + reagent + re-frame + jp-go-dds） |
| `README.md` | 設計（capability・データ契約・アーキテクチャ） |
| `PROJECT.jsonld` | 対外メタデータ（`marketer.etzhayyim.com`） |

**ingest worker と MCP backend はまだ無い**。README の Architecture 節が
「将来」と書いているとおりで、今日この repo で動くのは上の 3 つだけ。

## 1. 前提

```bash
node --version     # v26.7.0
nbb --version      # nbb v1.4.208   （無ければ npx --yes nbb でもよい）
curl --version     # curl 8.7.1
```

上は実行時に手元で出た値であって、下限の宣言ではない。`catalog-report.cljs` は
Node の global `fetch` を使うので Node 18 以上が要る。

## 2. catalog を読む（ネットワークを触らない）

```bash
kbb --backend sci scripts/catalog-report.cljk
```

実際の出力（2026-09-01）:

```
catalog	catalog.edn
SCANNED	16 entity
  dataset	8
  classification	6
  unverified-source	4
checked-at	2026-08-21T16:32:16Z	= 9 日前
url	21 本が catalog に載っている

datasets:
  estat-jp	sample:なし	e-Stat 政府統計の総合窓口
  eurostat	sample:実測値あり	Eurostat dissemination API
  ilostat-sdmx	sample:なし	ILOSTAT (SDMX REST)
  oecd-sdmx	sample:なし	OECD Data Explorer (SDMX REST)
  un-data	sample:なし	UNdata portal
  un-wpp	sample:なし	UN World Population Prospects
  unsd-ama	sample:なし	UN National Accounts — Analysis of Main Aggregates (AMA)
  worldbank-wdi	sample:実測値あり	World Bank World Development Indicators

OK	finding 0 件 —— ただし --urls を付けていないので URL については何も測っていない
```

この形が既定で見るもの。静的検査は 4 つある:

- `:catalog/entry-count` の申告と実体の一致
- 載っている URL がすべて `https://`
- `:catalog/unverified-sources` に URL が混ざっていないこと
  （catalog の規則は「取得できなかった出典は名前だけ」）
- README が名指しする分類体系（isic / cofog / cpc / nace / hs）の code list が在ること

**終了コードは 3 値**で、`0` = 走って finding 無し、`1` = 走って finding 有り、
`2` = **REFUSED**（catalog が読めない・0 件・EDN が壊れている等で、検査そのものが
成立しなかった）。`2` を `0` と混ぜないのは、「測れなかった検査が、測って問題が
無かった検査と同じ値を返す」形を避けるため。

```bash
kbb --backend sci scripts/catalog-report.cljk; echo "exit=$?"
```

⚠ `nbb ... | tail` のようにパイプで受けると `$?` は `tail` の値になり、
**検査の答えを一度も見ないまま `exit=0` が出る**。読みたいときは先にファイルへ
落として exit を採る（`kbb --backend sci scripts/catalog-report.cljk > /tmp/r.log; echo $?`）。

## 3. catalog の URL が今日も生きているか（ネットワークを触る）

`catalog.edn` の冒頭は「ここに載る URL は載せた時点で実際に取得し HTTP 2xx を
確認したものだけ」と書いている。それは**過去の主張**なので、今日の状態は測らないと
分からない。

```bash
kbb --backend sci scripts/catalog-report.cljk --urls --timeout 60
```

実際の出力の末尾（2026-09-01、21 本すべて 200）:

```
URL 実測	21 本  (timeout 60s, browser UA)
  200	934ms	worldbank-wdi :dataset/source-url	https://data.worldbank.org/
  200	7060ms	oecd-sdmx :dataset/api-url	https://sdmx.oecd.org/public/rest/dataflow
  ...
  200	3712ms	hs :classification/url	https://www.wcoomd.org/en/topics/nomenclature/...

OK	finding 0 件
```

**`--timeout` は 60 を既定として使うこと。** 既定の 25 秒で回すと
`https://sdmx.oecd.org/public/rest/dataflow` が abort する ——
この endpoint は 8.9 MB を返すので単体でも 15 秒前後かかり、21 本並列 +
高 load の下では 25 秒を越える。同じ URL を `curl --max-time 100` で単体で
叩けば `status=200 time=14.97s size=8894080` が返る（2026-09-01 実測）。

レポータはこの 2 つを**別の finding として**区別する:

- `[url-dead]` — HTTP で答えが返り、それが 2xx ではなかった
- `[url-unreachable]` — 答えが返らなかった。**これは「死んでいる」ではなく「未測定」**

だから `url-unreachable` を見たら、まず `--timeout` を伸ばして測り直す。
それでも返らないなら次節へ。

## 4. URL が落ちていたとき何をするか

`catalog.edn` の規則は 2 行で、この repo で唯一守るべきもの:

1. **2xx を確認できた URL だけを載せる。**
2. 取得できなかった出典は `:catalog/unverified-sources` に**名前だけ**記録する
   （URL は載せない。載せるとレポータが `[unverified-url]` で落とす）。

つまり落ちた URL は、消して `:catalog/unverified-sources` へ理由付きで移す。
既にそこに 4 件在る（IMF / US Census NAICS / ISO 3166 / UN Comtrade —— どれも
bot に 403 か 404 を返す面）。`:dataset/verified-sample` を書き足すときも同じで、
**API が実際に返した値の転記だけ**を入れる（推計・補完をしない）。

移したら手順 2 を回して `exit=0` に戻ることを確かめる。`:catalog/entry-count` を
更新し忘れると `[count]` で落ちる。

## 5. UI を手元で走らせる

```bash
cd appview/marketer-ui-iewsbshk/cljs
npm ci                                   # 129 packages / 12s（初回のみ）
amu compile --target wasm32-browser test && node out/tests.js
```

実際の出力（2026-09-01）:

```
[:test] Build completed. (112 files, 111 compiled, 0 warnings, 24.82s)

Testing marketer.app-test
Ran 4 tests containing 11 assertions.
0 failures, 0 errors.
```

`re-frame: Subscribe was called outside of a reactive context.` が 2 行出るが、
これは既知で、テストが sub を直接呼んでいるためのもの。失敗ではない。

ビルドと配信:

```bash
amu compile --target wasm32-browser app              # → public/js/app.js（gitignore 済み）
cd public && python3 -m http.server 8099 --bind 127.0.0.1
```

実際の出力（2026-09-01）:

```
[:app] Build completed. (111 files, 110 compiled, 0 warnings, 66.72s)

$ curl -sS -o /dev/null -w 'status=%{http_code} bytes=%{size_download}\n' http://127.0.0.1:8099/
status=200 bytes=74442
$ curl -sS -o /dev/null -w 'status=%{http_code} bytes=%{size_download}\n' http://127.0.0.1:8099/js/app.js
status=200 bytes=3360432
```

`public/index.html` は jp-go-dds の CSS を vendoring 済みの 1 文書で、
`<script>` は 1 本きり（`js/app.js`）—— workspace の single-page app 規則どおり
document は 1 枚。静的サーバは何でもよく、上は手元に必ず在るものを選んだだけ。

⚠ **JVM を起こすビルドは resource guard を通す。** このワークスペースは 1 台に
多数のセッションが同居しているので、`amu compile --target wasm32-browser` を直接叩かず

```bash
node <superproject>/scripts/resource-guard.mjs run build -- amu compile --target wasm32-browser app
```

の形で 1 本に直列化する（superproject CLAUDE.md の repo-wide resource governor）。

## 6. この文書が踏んだもの・踏んでいないもの

**踏んだ**（2026-09-01 に実行し、出力を上に転記した）:

- 手順 2 の静的レポート — exit 0
- 手順 2 の 3 値終了コード — 壊した catalog のコピー 8 本で確認
  （entry-count 不一致 / `http://` / unverified に URL 混入 / 分類体系欠落 →
  それぞれ対応する finding + exit 1、ファイル不在 / 空 vector / 非 vector /
  壊れた EDN → exit 2）。壊した箇所と報告された finding の種類が一致することを
  1 件ずつ確認している
- 手順 3 の live 検査 — 21 本すべて 200。加えて 1 本を実在しないパスに差し替えた
  コピーで `[url-dead]` + exit 1 が出ることを確認（その URL が実際に返したのは
  HTTP 500）
- 手順 5 の `npm ci` / `compile test` / `node out/tests.js` / `compile app`、
  および `python3 -m http.server` で index.html と `js/app.js` が 200 で返ること

**踏んでいない**:

- **ブラウザで開いて描画を見るところ。** 上で確かめたのは「HTTP が 200 で
  bytes を返す」ところまでで、JS を実行していない。したがってこの文書は
  「ページが表示される」とは書いていない —— 200 は mount の証拠ではない
- ingest worker と MCP backend —— **存在しないので手順が書けない**。
  README の Architecture 節が「将来」と書いているとおり

## 7. 次に読むもの

- `README.md` — capability (CV-1) とデータ契約 (DIV-2)
- `catalog.edn` — 冒頭のコメントが収載規則の正本
- superproject の `CLAUDE.md` — UI スタック（jp-go-dds / reagent / re-frame）と
  single-page app 規則、resource guard、EDN 文書の規約
