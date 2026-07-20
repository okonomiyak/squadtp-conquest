# SquadTP Conquest (squadtp-conquest)

Minecraft 1.20.1 / Forge 47.x 向けの、Battlefieldの「コンクエスト」モード風の対人ゲームモード。
[squadtp](../squadtp)(分隊テレポート/蘇生Mod)に依存し、その分隊APIを読み取り専用で利用する。
**squadtp本体のコード・configは一切変更していない。**

拠点は複数設置できる(名前で管理)。

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/conquest team join <a\|b\|admin>` | チームA/Bに参加。同時にバニラチーム`conquest_a`/`conquest_b`にも自動参加(フレンドリーファイア無効・チーム色設定)。`admin`は観戦用の第3チームでOP限定 | - (`admin`のみOP) |
| `/conquest team shuffle` | 管理人チーム以外のオンラインプレイヤーをランダムにA/Bへ均等に振り分け直す | OP |
| `/conquest mode set <conquest\|tdm>` | ゲームモードを切り替え。ラウンドが`WAITING`の時のみ変更可 | OP |
| `/conquest point set [半径]` | デフォルト拠点"Alpha"を実行者の足元に設置(半径省略時はconfig既定値)。旗ブロック(1×3)が自動生成される。TDMモードでは不要 | OP |
| `/conquest point add <名前> [半径]` | 名前付きの拠点を追加(複数拠点はこちら) | OP |
| `/conquest point remove <名前>` | 拠点を削除(旗ブロックも撤去) | OP |
| `/conquest point list` | 全拠点の状況一覧 | - |
| `/conquest spawn set <a\|b>` | 実行者の足元をそのチームのラウンド開始時スポーン地点に設定(未設定ならワールドスポーン) | OP |
| `/conquest start` | ラウンド開始。両チームに最低1人ずつオンラインでいることが条件(コンクエストモードはさらに拠点が1つ以上必要)。`startCountdownSeconds`秒のカウントダウン後に実際に開始 | OP |
| `/conquest stop` | 強制終了、またはカウントダウン中の開始をキャンセル。勝敗をつけずに待機状態(WAITING)へ | OP |
| `/conquest reset` | 結果表示中(ENDED)を手動で待機状態(WAITING)へ戻す。`autoResetAfterResult`が有効なら自動でも戻る | OP |
| `/conquest config list` | 調整可能な設定項目と現在値を一覧表示 | OP |
| `/conquest config set <key> <value>` | 設定値をサーバー再起動なしで即座に変更(TOMLにも永続化) | OP |
| `/conquest status` | ゲームモード・ラウンド状態・チケット(またはキル数)・残り時間・全拠点の状況・自分のチーム・所属分隊を表示 | - |

## GUI

- **Lキー**(旗ブロックを右クリックしても同じ画面が開く): チケット・全拠点の状況一覧、自分のチーム表示、
  チームA/B参加ボタン、所属分隊(squadtp経由)の一覧。
  OPには追加で、デフォルト拠点"Alpha"向けの半径編集+「ここに設置」、スポーン地点設定ボタン、
  ラウンド状態に応じて自動切替する開始/キャンセル/停止/リセットボタン。
  複数拠点の追加・削除はコマンド(`/conquest point add|remove|list`)から行う
- **右Altキー**(Tabキーはバニラのまま変更していない): BF風スコアボード。パネルはグレー半透明で
  画面全体は暗転しない(バニラのTabプレイヤー一覧と同じ見え方)。
  チケットバー・拠点アイコン列(拠点ごと)・セクター差分・デス数・自分のK/D/A、
  チームごとの順位付きプレイヤー一覧(スコア降順、21位以降は自分の順位のみ別枠表示)。
  自分の行を強調表示、**自分の分隊のメンバーの行を緑でハイライト**
  - 既定は「押して開く/Escで閉じる」だが、クライアントconfigの`holdToOpenScoreboard`をtrueにすると
    Tabと同じ「押している間だけ開く」方式に切り替えられる
    (`config/squadtpconquest-client.toml`、サーバー側configとは別ファイル)

## HUD(常時表示)

- 画面上部中央: 自チーム/敵チームのチケットバー(自チーム常に左・水色、敵チーム常に右・赤、
  視点で左右反転しない)+拠点アイコン列(拠点ごとに1つ、中立=グレー・自チーム=青・敵チーム=赤・
  係争中=黄、占領進行度%表示)。ラウンド進行中かつチーム参加済みの場合のみ表示
- 拠点の**範囲内にいる間だけ**、画面下部中央に大きな文字(「占領中」「係争中」「占領完了」
  「占領されている」)+進行度バーを表示。複数拠点が重なる場所では最初に該当したものを表示
- 各拠点の周囲に、地面レベルでチーム色のパーティクルの円(境界線)を0.5秒間隔で表示。
  ラウンドの状態に関わらず拠点が存在する限り常時表示(設置確認にも使える)

## ゲームモード

`/conquest mode set <conquest|tdm>` で切り替える(ラウンド待機中のみ)。

- **conquest**(既定): 上記の拠点占領モード
- **tdm**(チームデスマッチ): 拠点なしで開始可能。チケット表示欄がそのまま**チーム合計キル数**として
  カウントアップし、`tdmKillLimit`に到達したチームが即座に勝利。制限時間に達した場合はキル数が
  多いチームの勝ち(同数はドロー)。リスポーンによるチケット消費は発生しない
  (キル自体がスコアを増やす方式のため)。拠点関連のHUD/GUI表示(拠点アイコン・占領インジケーター・
  セクター表示)は拠点が存在しないため自動的に非表示になる。蘇生・アシストなどのスコアリングは
  コンクエストと共通

## 旗ブロック

拠点の目印となる1×3マルチブロック(石の基台+ポール+チーム色の旗布、`flag.json`のBlockbench
デザインを反映)。下から順に「基台+ポール」「純粋なポールの通過部分」「ポール+旗布」の3段構成。
石テクスチャの部分(基台・ポール)はチーム色に関わらず固定、最上段の旗布部分だけチーム色の
羊毛テクスチャに変わる。コマンドブロック相当の破壊不可で、
`/conquest point set|add`でのみ設置・移動される(手持ちアイテムとしては入手できない)。
占領進行度・所有チームに応じて自動で色が変わる。右クリックでGUIが開く。

## ラウンドの流れ

状態は `WAITING`(待機中) → `STARTING`(開始カウントダウン中) → `IN_PROGRESS`(進行中) →
`ENDED`(結果表示中) の4段階。

1. OPが拠点(`/conquest point set|add`)・必要ならスポーン地点(`/conquest spawn set`)を設置
2. プレイヤーが `/conquest team join a|b` でチーム参加
3. OPが `/conquest start` を実行。この時点で全拠点のチケット/占領状態がリセットされ、
   各プレイヤーはチームのスポーン地点へ移動、`STARTING`へ。以後`startCountdownSeconds`秒間、
   タイトルで残り秒数の「Get Ready!」カウントダウンが表示される(0で無効化しすぐ開始)。
   この間に`/conquest stop`で開始をキャンセルできる
4. カウントダウン終了で`IN_PROGRESS`へ。拠点の範囲内に片方のチームだけがいると占領進行度が変化。
   両チーム混在で停止(係争中)、無人で維持
5. 拠点を保有しているチームが多いほど、一定間隔ごとに劣勢側のチケットが多く削られる
   (`減少量 = ticketBleedAmount × 保有数の差`、同数保有は膠着)
6. リスポーンするたびに、そのプレイヤーの**自チームの**チケットを`ticketCostPerRespawn`だけ消費
   (BFのリインフォースメント方式。squadtpのダウン→蘇生失敗による死亡も含め、実際にリスポーンした
   タイミングでのみ発生)
7. 以下のいずれかでラウンド終了(`ENDED`へ): チケット0、(config有効時)片方のチームのオンライン人数が0、
   制限時間到達(チケット差で判定、同数はドロー)
8. `resultDisplaySeconds`後に自動で(または`/conquest reset`で手動で)`WAITING`へ戻る

## スコアリング

キル・デス・アシスト・蘇生をラウンド単位で集計し、`/conquest`のスコアボード画面に表示する。

- キル/デス: 敵チームプレイヤーを倒す/倒されるで加算
- アシスト: デス前`assistWindowSeconds`秒以内にダメージを与えていた敵チームプレイヤー全員に加算
- 蘇生: squadtpの蘇生成功時、蘇生した本人に加算
  - **既知の制約**: squadtpは「誰が蘇生したか」を公開APIで一切教えないため、
    独自にダウン中プレイヤーへの右クリック監視+ダウン状態解除の検知を組み合わせて推定している
    (squadtp本体は無改造)。通常のプレイでは問題なく機能するが、squadtp内部の判定と
    完全に一致する保証はない

## 設計メモ

- **サーバー権威**: 状態は`ConquestManager`(SavedData)がオーバーワールドに永続化。
  クライアントからのC2Sパケットは存在せず、全操作は`/conquest`コマンド経由
- **同期**: S2Cパケット2種、いずれも毎秒全員に配信 — `ConquestSyncPacket`(全拠点の状況リスト・
  チケット・ラウンド状態など)と`ConquestScoreboardPacket`(全参加者のK/D/A)
- squadtpとの連携は読み取り専用API(`SquadManager`/`SquadClientData`/`ReviveSystem`/
  `TeleportHelper`)経由のみ。squadtpのバニラチーム同名判定(`requireSameTeam`)は、
  `/conquest team join`が自動作成する`conquest_a`/`conquest_b`バニラチームと自然に整合する
- squadtpの公開APIには「誰が蘇生したか」も「戦闘タグのクリア」も手段がなく、前者は独自推定、
  後者は実装を諦めている(詳細はdevlog参照)
- パケットのフィールドを追加・変更・並び替えするたびに`NetworkHandler.PROTOCOL_VERSION`を
  上げること。列挙型に新しい定数を挿入して既存定数のordinalがズレる場合も同様(バイト長は
  変わらなくても意味が変わるため)

## 設定 (`world/serverconfig/squadtpconquest-server.toml`)

`conquest`セクション:
- `captureRadius`(既定10) — 拠点の占領判定半径
- `captureRatePerSecond`(既定5.0) — 占領進行度の変化速度(%/秒)
- `ticketBleedInterval`(既定5) / `ticketBleedAmount`(既定1) — 拠点保有チームによるチケット減少
- `startingTickets`(既定100)
- `roundTimeLimitSeconds`(既定0=無制限)
- `resultDisplaySeconds`(既定10) — 結果表示から自動リセットまでの秒数
- `endOnTeamEmpty`(既定false) — 片方のチームのオンライン人数が0になったら即終了するか
- `autoResetAfterResult`(既定true) — falseなら`/conquest reset`必須
- `ticketCostPerRespawn`(既定1) — リスポーンごとに自チームのチケットから消費する数。0で無効(TDMでは無効)
- `startCountdownSeconds`(既定5) — `/conquest start`後のカウントダウン秒数。0で即開始
- `tdmKillLimit`(既定50) — TDMモードでチームが勝利するのに必要なキル数。0で無効(制限時間頼み)

`scoreboard`セクション:
- `assistWindowSeconds`(既定10)
- `scorePerKill`(既定100) / `scorePerAssist`(既定50) / `scorePerRevive`(既定50)

これらは`/conquest config set <key> <value>`でゲーム内から再起動なしに変更できる
(TOMLにも自動で永続化される)。

キーバインドのデフォルト(Lキー・右Altキー)はクライアント側の設定でありサーバーconfigの対象外。

## ビルド・実行

要件: JDK 21(Gradle実行用。`gradle.properties`の`org.gradle.java.home`で指定)

```
gradlew build        # → build/libs/squadtpconquest-0.1.0.jar
gradlew runClient     # 開発用クライアント1 (ユーザー名 Dev1, run/)
gradlew runClient2    # 開発用クライアント2 (ユーザー名 Dev2, run2/)
gradlew runServer     # 開発用サーバー (run-server/)
```

squadtp本体は`../squadtp/build/libs/`のjarをローカルivyリポジトリ経由で参照する
(`gradle.properties`の`squadtp_version`)。**squadtp本体をリビルドして`build/libs/`のjar名が
変わったら、`squadtp_version`をそれに合わせて更新すること**(合っていないと
`Could not find uk.iwaservice:squadtp:x.x.x`でビルドが失敗する)。

### 2プレイヤーテスト手順

1. ターミナル3つで `runServer` → `runClient` → `runClient2` を起動
2. 両クライアントでサーバー`localhost`に接続
3. 双方で `/conquest team join a` / `/conquest team join b`(別チームに)
4. 片方(OP)が `/conquest point set` → `/conquest start`。カウントダウン中に`/conquest stop`で
   キャンセルできることも確認
5. 拠点範囲に片方だけ入って占領進行度が上がることを確認、両方入って係争中(停止)になることを確認
6. `/conquest config set ticketBleedAmount 50` などで即時反映されることを確認
7. チケット0またはタイムリミットで結果表示・自動リセットまで確認
8. `/conquest point add Bravo` で2つ目の拠点を追加し、両拠点の保有状況がチケット減少速度に
   反映されることを確認

詳しい実装経緯・既知の制約は `squadtp-conquest-devlog-2026-07-20.md` を参照。
