# etzhayyim-project-marketer

`marketer.etzhayyim.com` 向けに、全世界のマーケット統計データを収集し、分類体系（COFOG / ISIC など）で正規化して可視化する。

## Capability (CV-1)

1. 統計データ収集
- 公式統計・国際機関データ（例: World Bank, OECD, IMF, UN 等）から取得
- 更新頻度の管理（年次/四半期/月次）と差分更新

2. 正規化（分類体系の統合）
- 産業別: ISIC / NAICS / NACE 等
- 政府支出別: COFOG
- プロダクト別: CPC / HS 等
- 人口属性: 年齢階級（国勢調査の区切り差を吸収する age_band 規格化）
- `IPQC` はここでは `ISIC` を指すものとして扱う（名称の揺れを吸収する）

3. 分析・集計（OLAP）
- 国別・産業別・年齢別・プロダクト別のクロス集計
- トレンド（年次推移）、構成比（シェア）、比較（国際比較）

4. 可視化（Svelte）
- フィルタと group-by を UI で選び、チャートに描画
- 大規模データは Parquet で配布し、ブラウザ側で DuckDB-Wasm により集計して描画する

## Data Contract (DIV-2)

実装の中心は「観測値 Observation（ファクト）」と「分類/ディメンション（ディメンション）」の分離。

### Observation (fact)

- `dataset_id`: データセット識別子
- `measure`: 指標名（例: `expenditure`, `sales`, `population`, `gdp`）
- `value`: 数値
- `unit`: 単位（例: `USD`, `JPY`, `people`, `index`）
- `currency`: 通貨（必要な場合）
- `year` / `period`: 時間軸
- `geo`: 国/地域（ISO 3166-1 alpha-2/3 を推奨）
- `classification_system`: 例 `cofog`, `isic`, `nace`, `naics`, `cpc`, `hs`
- `classification_code`: 例 `GF01`, `A`, `62`, `0111`
- `product`: プロダクト識別（CPC/HS の code を推奨）
- `age_band`: 年齢階級（例 `0-14`, `15-64`, `65+`）
- `source_url` / `license` / `revision`: 出典・再現性

### Dataset (metadata)

- `dataset_id`, `name`, `source`, `license`, `update_schedule`
- `coverage`: 期間・国・分類体系・指標の範囲
- `distribution`: Parquet/CSV の URL（署名付き URL / public URL）

## Architecture

推奨アーキテクチャ（段階導入）:

1. CDN UI (`projects/.../wasm/marketer-ui-*/svelte`)
- カタログ取得（後述の MCP） + Parquet URL を受け取る
- 受け取った Parquet/CSV を DuckDB-Wasm で集計し、チャート描画

2. MCP Backend (将来)
- `ListDatasets` / `GetDataset` / `GetDistributionURL` / `QuerySlice` (optional) を MCP tool として提供
- 大規模クエリはサーバ側で slice Parquet を生成し配布、UI はローカル集計

3. Ingestion Workers (将来)
- ソース別コネクタ（World Bank, OECD, IMF, UN 等）
- 分類体系のマッピング（コード表の取り込み、同義語、改訂対応）
