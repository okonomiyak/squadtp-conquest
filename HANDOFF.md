# squadtp-conquest 引き継ぎメモ (2026-07-21 更新)

前回(2026-07-19)の引き継ぎ内容は解消済みのため全面更新。当時の課題(remapブロッカー・
squadtpバージョン不一致)は恒久的な運用ルールとして「環境の罠」節に統合した。

## 現在の状態

- **バージョン**: `mod_version=0.2.0`。GitHubに`v0.2.0`タグ+リリース公開済み
  (https://github.com/okonomiyak/squadtp-conquest/releases/tag/v0.2.0 、jar添付)
- **ライセンス**: GPL-3.0(`LICENSE`ファイルあり)
- **squadtp依存**: `gradle.properties`の`squadtp_version=0.2.0`(2026-07-21時点で`../squadtp/build/libs`にある実際のjarと一致させてある。squadtp本体も同日0.2.0にバージョンアップ済み)
- **ビルド**: `gradlew build`成功、警告のみ(エラーなし)
- **Git**: `master`ブランチ、リモート`origin`(`git@github-okonomiyak:okonomiyak/squadtp-conquest.git`)にpush済み、作業ツリーはクリーン

機能の全体像・コマンド一覧・設定項目は**README.md**、実装の経緯・設計判断・遭遇した罠は
**squadtp-conquest-devlog-2026-07-20.md**、今後の改善案は**TODO.md**を参照。
このファイルはそれらを読む前の「現在地」把握用。

## 実装済み機能(要約、詳細はREADME参照)

- コンクエストモード: 複数拠点占領・チケット・リスポーンコスト・開始前カウントダウン・
  拠点範囲パーティクル・1×3旗ブロック
- 管理人チーム(`Team.ADMIN`, OP限定の観戦チーム)
- `/conquest team shuffle`: ランダム振り分け+分隊解散&同チームでの自動再編成
- Team Deathmatch モード(`/conquest mode set tdm`, `tdmKillLimit`)
- スコアボード(右Alt)2ページ目: 累計スコア+K/D比率
- HUD/GUIのチーム色を自分/敵視点から**チーム固定色**(A=青・B=赤)に変更
- 管理用GUI(Lキー)・BF風HUD(常時表示)・adjustable config(`/conquest config set`)

## ⚠️ 未解決の既知の問題(次に着手すべきこと)

**TDMモードでキル数が増えない不具合が未解決のまま棚上げになっている。**

- ユーザーが実プレイでTDMを試した際、チーム合計キル数(チケットバー)どころか
  **個人のK/D(スコアボードのK)すら増えなかった**と報告があった
- `/conquest mode set tdm`は成功メッセージを確認済み、拠点が残っていた状態でテストしていたが、
  拠点処理自体は`mode == CONQUEST`のときだけ動く設計なので拠点残存は理論上は無関係のはず
- 個人のK/D/Aすら増えていない = `ScoreEvents.onDeath`より手前(ラウンド状態がIN_PROGRESSか、
  両プレイヤーが実際にチームA/Bへ所属していたか、キルが本当にPvPだったか)を疑う必要がある
- ユーザーは「まあいいか」と流して深掘りせずに次の作業(チームシャッフル等)に進んだため、
  **根本原因は特定できていない**
- 次回、TDMのキル計上を再現テストする際は `/conquest status` の `Mode:`行と`running`状態、
  および両プレイヤーの`/conquest team join a|b`実行状況を最初に確認すること

## 環境の罠(再発防止・恒久ルール)

- **squadtpバージョン同期**: squadtpは独立にリビルドされ続けるため、squadtp-conquestのビルド前に
  必ず`../squadtp/build/libs/`の実際のjarファイル名を確認し、`gradle.properties`の
  `squadtp_version`と一致させること。不一致だと`Could not find uk.iwaservice:squadtp:x.x.x`で
  ビルド失敗する(このセッション中も0.1.2→0.1.3→0.1.4と複数回発生)
- **JDK21固定**: 既定のJava(25等)ではGradle 8.8が動かない。`gradle.properties`の
  `org.gradle.java.home`で`C:/Program Files/Java/jdk-21.0.10`を明示指定済み。ビルドコマンドを
  手動実行する場合も`JAVA_HOME`をJDK21に向けること
- **PowerShellのBOM問題**: `Set-Content`/`Out-File -Encoding utf8`はBOM付きで書き出すため、
  build.gradle等のコード/設定ファイルをこの方法で編集するとGradleがparse errorになる。
  Writeツール(BOM無し)を使うこと
- **PROTOCOL_VERSIONの上げ忘れ**: パケットのフィールド追加・変更、または列挙型への新定数挿入
  (既存定数のordinalがズレる場合)は、バイト長が同じでも`NetworkHandler.PROTOCOL_VERSION`を
  上げること。現在値は`8`。上げ忘れると新旧クライアント/サーバー混在時に
  `IndexOutOfBoundsException`で原因不明の切断が起きる
- **サーバー多重起動**: `runServer`を起動したまま次の`runServer`を叩くとポート競合で失敗する。
  `Get-NetTCPConnection -LocalPort 25565`(または`Get-CimInstance Win32_Process -Filter "Name='java.exe'"`)
  で残留プロセスを確認してから起動すること
- **squadtp本体は改造しない**: 公開API(`SquadManager`/`Squad`/`ReviveSystem`/`TeleportHelper`)
  経由の読み取り専用利用のみ。squadtp側のコード・configに手を入れたことは一度もない

## 次にやること候補

1. 上記のTDMキル計上バグの再現・原因特定(最優先)
2. TODO.mdの「優先度高」2件(拠点からのリスポーン選択・スポーン安全確認)
3. TODO.mdの「未検証」項目全般(実プレイでの動作確認がまだ大半未実施)
