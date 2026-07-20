# squadtp-conquest 引き継ぎメモ (2026-07-19 更新)

## 完了済み
- squadtp API調査: `SquadManager.get(server)` / `getSquadOf(uuid)` / `Squad.getMembers()` 等が既にpublic。**squadtp側への変更は不要**(devlogファイルは存在せず、README.mdのみだった)
- プロジェクト一式作成済み・`gradlew build` 成功(jar生成OK):
  - Gradle設定 (squadtpと同じMDG legacyforge 2.0.141 / runClient・runClient2・runServer)
  - mods.toml (squadtp mandatory依存)
  - 全ソース: Config(5項目) / Team / CapturePoint(占領進行ロジック) / FlagPole(フェンス+羊毛の簡易旗) / ConquestManager(SavedData・チケット・1秒tick・アクションバーHUD) / ConquestCommand(/conquest team join・point set・start・stop・status) / ServerEvents
  - statusコマンドでsquadtpの分隊APIを実際に参照している
- **remapブロッカー解消済み**。真因は「MDG/ivyの不具合」ではなく**squadtp本体が別途0.1.0→0.1.1に更新されていたバージョン不一致**だった。
  - `squadtp-conquest/build.gradle` の依存定義: ローカルivyレポ(`../squadtp/build/libs`、`patternLayout '[module]-[revision].[ext]'`、`metadataSources { artifact() }`)+ `modImplementation "uk.iwaservice:squadtp:${squadtp_version}"` で正常にSRG→namedのremapが効く(`transforms/`配下にremap済みjarが生成される)
  - `gradle.properties` の `squadtp_version` を **0.1.1** に修正済み(squadtpをリビルドしたら要追随)
  - `runServer` で `Done (12.639s)!` まで到達、squadtp・squadtpconquest両方のserverconfig(.toml)が生成され、ロードエラーなしを確認済み

## 残タスク(次回セッションへ)
実プレイヤー操作を伴う検証は未実施(トークン消費を抑えるため中断)。`runClient` / `runClient2` を2窓起動して手動で:
1. `/conquest point set [radius]` で拠点設置 → `/conquest start`
2. Dev1のみ拠点範囲内 → 進行度が上昇し100%で占領成立(チャットに captured メッセージ)
3. Dev1・Dev2両方在圏 → 進行度停止(contested表示)
4. 無人 → 進行度維持
5. 拠点保有側が相手チケットを削り、0で victory メッセージが出ることを確認
6. `/conquest status` で分隊情報(squadtpのSquad API経由)が表示されることを確認
7. 確認後、devlog (squadtp-conquest-devlog-*.md) を作成してビルド・テスト結果を記録

## 環境の罠(再発注意)
- PS5.1の `Set-Content/Out-File -Encoding utf8` はBOM付き → build.gradle等のコード/設定ファイルをこの方法で書くとGradleがparse errorになる。eula.txt/server.propertiesは `-Encoding ascii` で書くこと
- runServerをバックグラウンド起動したまま次のrunServerを叩くと「Address already in use」でクラッシュする。起動前に `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` でsquadtp/squadtp-conquest関連の残留javaプロセスがないか確認し、あれば `Stop-Process` してから再起動すること
- ビルド時の警告 `ModLoadingContext.get()` 非推奨はsquadtpと同様で無害
