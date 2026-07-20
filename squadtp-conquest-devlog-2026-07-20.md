# squadtp-conquest 開発ログ (2026-07-20時点)

squadtp(分隊TP/蘇生Mod)に依存する、Battlefieldのコンクエストモード風の対人ゲームモード。
squadtp本体のコード・configは一切変更していない(公開APIの読み取りのみ)。

> 以下は初版の記録。その後の追記は末尾の「追記」セクション参照
> (複数拠点対応・config即時変更コマンド・リスポーンチケット消費など)。

## プロジェクト基盤

- `C:\Users\tomip\program\java\squadtp-conquest`。squadtpと同じMDG legacyforge 2.0.141構成
- squadtp本体のjarへのローカル依存は**ivyリポジトリ(artifact-onlyメタデータ)**経由。
  `flatDir`や`files()`ではModDevGradleのSRG→namedリマップが効かず、これが唯一動く方法だった
  (詳細はメモリ`mdg-legacy-mod-dependency`、経緯は`HANDOFF.md`)
- squadtp本体を独立にリビルドする(バージョンが上がる)たびに`gradle.properties`の
  `squadtp_version`を実際のjarファイル名に合わせる必要がある。「Could not find」エラーの定番原因

## コア機能: 拠点占領とチケット

- `conquest/CapturePoint.java`: 座標+半径、所属勢力(A/B/中立)、占領進行度0-100%
- `conquest/ConquestManager.java`(SavedData): サーバー権威の唯一の状態源。占領点1つ・チーム所属マップ・
  両チームチケットを保持。毎秒(`tickSecond`)、範囲内チーム構成を判定して進行度を増減
  - 片方のみ在圏で進行、両チーム混在で係争中(停止)、無人で維持
  - 拠点保有側が`ticketBleedInterval`ごとに`ticketBleedAmount`だけ相手チケットを削る
- `/conquest point set [半径]`(OP): 実行者の足元に拠点を設置(半径省略時はconfig既定値)
- `/conquest team join <a|b>`: チーム参加。同時に**バニラのスコアボードチーム**
  `conquest_a`/`conquest_b`を自動作成・同期(フレンドリーファイア無効化、チーム色設定)。
  これによりsquadtpの`requireSameTeam`がconquestチーム分けと自動的に整合する

## 旗ブロック(1×2マルチブロック)

- `block/ConquestFlagBlock.java`: 単色立方体ではなく、ポール(丸太テクスチャ)+はためく布
  (チーム色の羊毛テクスチャを再利用したカスタムモデル)の1×2構造。`HALF`(lower/upper)と
  `TEAM`のブロックステートプロパティで色を管理
  - コマンドブロック相当の破壊不可(`.strength(-1, 3600000).noLootTable()`)。
    `/conquest point set`でのみ設置・再設置される、プレイヤーが手で置けるアイテムは持たない
  - 当たり判定もポール+布の細い形状に合わせて縮小(`getShape`オーバーライド)
  - 右クリックでGUI(`ConquestScreen`)を開く
- `conquest/FlagPole.java`: 旗の色更新ロジック。所有/占領中チームの解決は
  `Team.resolveActive(owner, capturingTeam, flagLevel)`に一本化(旗ブロック・GUI・HUDで共通利用)

## ラウンドライフサイクル

- `conquest/RoundState.java`: WAITING → IN_PROGRESS → ENDED の3状態、SavedDataで永続化
  (経過秒数はtickカウンタ方式でサーバー停止時間の影響を受けない)
- `/conquest start`(OP): 拠点未設置・チーム未参加(オンライン0人)・既に進行中/結果表示中を
  個別エラーで判定。開始時に拠点リセット・チケット初期化・チーム別スポーン地点へテレポート・
  チャット+バニラタイトル通知
- `/conquest spawn set <a|b>`(OP): チーム別スポーン地点を設定(未設定ならワールドスポーン)
- 勝利条件(毎秒チェック、優先順): チケット0 → チーム人数0(config) → 制限時間到達(ドロー対応)
- `/conquest stop`(OP): IN_PROGRESSからのみ、勝敗なしでWAITINGへ強制終了
- `/conquest reset`(OP): ENDEDから手動でWAITINGへ(`resultDisplaySeconds`待たずに)
- ラウンド終了時: バニラタイトルで結果表示、`ReviveSystem.clear()`でダウン/蘇生状態をクリア
  (squadtpにプレイヤー単位でのクリアAPIが無いため、**サーバー全体に影響する**既知の制約)
- `/conquest status`: 状態・チケット・残り時間・前回結果を表示

## GUI

- `client/gui/ConquestScreen.java`(**Lキー**、または旗ブロック右クリックで開く): squadtpの
  SquadScreenと同じ「ボタンは対応する`/conquest`コマンドを送るだけ」方式
  - 誰でも: チケット・拠点状況・自分のチーム表示、チームA/B参加ボタン、所属分隊(squadtpの
    `SquadClientData`経由)一覧
  - OPのみ: 半径編集+「拠点をここに設置」、「Aスポーンをここに設定」「Bスポーンをここに設定」、
    ラウンド状態に応じて自動切替する開始/停止/リセットボタン
- `client/gui/ConquestScoreScreen.java`(**右Altキー**、Tabキーはバニラのまま): BF風スコアボード。
  チケットバー・拠点アイコン列・セクター差分・デス数・自分のK/D/A、チームA/B別の順位付き
  プレイヤー一覧(スコア降順、20人超は別枠で自分の順位を表示)
  - 自分の行を強調、**自分の所属する分隊のメンバー行を緑ハイライト**
    (squadtpの`SquadClientData`を読むのみ、squadtp本体は無改造)

## HUD(常時表示・複数レイヤー)

- `client/gui/ConquestHudOverlay.java`: 画面上部中央のチケットバー(自チーム常に左/水色、
  敵チーム常に右/赤、視点で左右反転しない)+拠点アイコン列(中立=グレー/自チーム=青/
  敵チーム=赤/係争中=黄、占領進行度%も表示)。ラウンド進行中かつチーム参加済みのみ表示
- `client/gui/ConquestCaptureOverlay.java`: 拠点の**範囲内にいる間だけ**、画面下部中央に
  大きな文字(1.5倍)+進行度バーを表示(「占領中」「係争中」「占領完了」「占領されている」)。
  在圏判定はサーバー側で計算し`ConquestSyncPacket.inZone`で個人別に配信

いずれも拠点は現状1つのみだが、アイコン/在圏判定などの描画・配信ロジックは拠点リストを
汎用的に扱う設計にしてあるので、将来複数拠点に拡張しても大きな変更なく動く想定
(CLAUDE.mdの方針通り、複数拠点対応自体は次段階・未着手)

## スコアリング(キル/デス/アシスト/蘇生)

squadtp本体を変更せずに実装するため、いくつか独自の仕組みが必要だった:

- `ScoreEvents.java`: バニラForgeイベント(`LivingDamageEvent`/`LivingDeathEvent`)のみを使い、
  敵チームプレイヤーへのキル・被弾からのアシスト(`assistWindowSeconds`以内のダメージ)を集計
- `conquest/DamageLog.java`: 被害者ごとの直近攻撃者ログ(アシスト判定用、独自実装)
- **蘇生スコアの制約**: squadtpは「誰が蘇生したか」を公開API・イベントとして一切公開していない
  (`ReviveSystem.complete()`はprivate)。そこで`conquest/ReviveAttribution.java`で
  `PlayerInteractEvent.EntityInteract` + `ReviveSystem.isDowned()`のポーリングを独自に組み合わせ、
  squadtp自身が使っているのと同じ公開シグナルを外側から観測することで再現している
  (squadtp本体は無改造)
- `conquest/PlayerScore.java` / `ConquestManager`内のスコアマップ: ラウンド開始でリセット、
  SavedDataで永続化

## ネットワーク

- `network/ConquestSyncPacket.java`(S2C, id=0): 拠点状況・チケット・ラウンド状態・自分の
  チーム/在圏/OP権限などを毎秒全員に配信。旗ブロック右クリック時は`openScreen=true`で
  即座に1回送ってGUIを開かせる
- `network/ConquestScoreboardPacket.java`(S2C, id=1): オンライン参加者全員のK/D/A/スコアを
  毎秒配信
- `PROTOCOL_VERSION`は現在`"3"`。パケットのフィールドを追加・変更するたびに必ず上げること
  (上げ忘れると、新旧バージョン混在時にハンドシェイクは通るのに実際のバイト列がズレて
  `IndexOutOfBoundsException`でクライアントが切断される、という原因究明しづらい不具合になる
  ことを実際に踏んだ)

## config項目のゲーム内即時変更

- `/conquest config list`(OP): 全調整可能キーと現在値を一覧表示
- `/conquest config set <key> <value>`(OP): サーバー再起動なしで即座に値を変更。
  `ForgeConfigSpec.ConfigValue.set()`はTOMLへの永続化も兼ねるため、追加の保存機構は不要
  (`captureRatePerSecond`等は`tickSecond`が毎回`Config.get()`で読むので次のtickから即反映)

## config項目 (`world/serverconfig/squadtpconquest-server.toml`)

`conquest`セクション: `captureRadius`(既定10) / `captureRatePerSecond`(既定5.0) /
`ticketBleedInterval`(既定5) / `ticketBleedAmount`(既定1) / `startingTickets`(既定100) /
`roundTimeLimitSeconds`(既定0=無制限) / `resultDisplaySeconds`(既定10) /
`endOnTeamEmpty`(既定false) / `autoResetAfterResult`(既定true)

`scoreboard`セクション: `assistWindowSeconds`(既定10) / `scorePerKill`(既定100) /
`scorePerAssist`(既定50) / `scorePerRevive`(既定50)

キーバインドのデフォルト(Lキー・右Altキー)はクライアント側の`KeyMapping`定義であり、
サーバーconfigの対象ではない(Forgeの仕様上そもそもconfig化できない)

## 既知の制約・次にやるなら

- `ReviveSystem.clear()`はサーバー全体に効く(squadtp側にプレイヤー単位のクリアAPIが無いため)
- 戦闘タグ(squadtpの`SquadManager`内部状態)はクリアする公開APIが無く、実装不可能。
  `combatBlockSeconds`の自然失効に任せている
- 蘇生アシストの帰属は独自ポーリングによる推定であり、squadtp内部の正確なセッション情報とは
  完全には一致しない可能性がある(通常のプレイでは十分機能する想定)
- 動作確認はビルド成功+`runServer`でのMod読み込み確認まで。実プレイでの2人テスト
  (占領/チケット増減/勝敗/ハイライト表示など)はユーザー側で実施

## 追記(初版以降の変更点)

- **管理GUIの拡張**: ConquestScreenの管理セクションに「拠点をここに設置」「Aスポーンをここに設定」
  「Bスポーンをここに設定」ボタンを追加。開始/停止ボタンをラウンド状態(WAITING/IN_PROGRESS/ENDED)
  に応じて自動切替する単一ボタン(開始/停止/リセット)に統合。これに伴い`ConquestSyncPacket`に
  `state`(RoundState)フィールドを追加(以前はIN_PROGRESSかどうかのbooleanしか送っておらず、
  ENDEDを区別できていなかった)
- **旗の高さ調整**: 拠点の地面から+2ブロック浮かせて設置するように変更
- **config即時変更コマンド**: `/conquest config list` / `/conquest config set <key> <value>`(OP)。
  `ForgeConfigSpec.ConfigValue.set()`がTOML永続化も兼ねるため追加の保存機構は不要。
  占領速度・チケット関連の値をサーバー再起動なしに調整できる
- **複数拠点対応**: `CapturePoint`を単一フィールドから名前キーの`LinkedHashMap`に変更。
  `/conquest point add <名前> [半径]` / `remove <名前>` / `list`を新設(`point set [半径]`は
  従来通りデフォルト拠点"Alpha"向けとして後方互換維持)。チケット減少はBF方式
  「`ticketBleedAmount × 保有数の差`を劣勢側へ、同数保有(0-0含む)は膠着」に変更。
  HUD/GUI/スコア画面はもともと「拠点のリスト」を汎用的に描画する作りにしていたため、
  実データに差し替えるだけで対応できた。`ConquestSyncPacket`の拠点情報を単一フィールド群から
  `List<PointStatus>`に変更(破壊的変更、PROTOCOL_VERSION 3→4)
- **リスポーンチケット消費**: `ticketCostPerRespawn`(既定1)。`PlayerRespawnEvent`をフックし、
  リスポーンするたびに本人の自チームチケットを消費するBFのリインフォースメント方式。
  squadtpのダウン→タイムアウト死亡/giveup経由の死亡も、最終的に通常のリスポーンを経るため
  自然にカバーされる
- **拠点範囲のパーティクル境界表示**: `CaptureZoneVisualizer`。0.5秒ごとに拠点の半径をチーム色の
  ダスト粒子でなぞる(ブロック設置ではないので地形改変なし、片付けも不要)。地面のYを固定して
  円を描くため、傾斜地では一部浮く/埋まることがあるが実用上は十分
- **ラウンド開始前カウントダウン**: `RoundState`に`STARTING`を追加(WAITINGとIN_PROGRESSの間に
  挿入)。`/conquest start`は即座にIN_PROGRESSにはせず、チケット/拠点リセット・スポーン移動を
  先に済ませてから`startCountdownSeconds`秒(既定5、0で即開始)のタイトルカウントダウンを表示し、
  0になった時点でIN_PROGRESSへ遷移する。`/conquest stop`はSTARTING中も使え、カウントダウンを
  キャンセルしてWAITINGに戻す。カウントダウン秒数自体はSavedDataに永続化していないため、
  サーバー再起動でSTARTING状態のまま固まらないよう`load()`側でSTARTING→WAITINGへ強制フォールバック
  する安全策を入れている
- **管理人チーム(ADMIN)**: `Team`に`ADMIN`を追加(A/Bと同様に参加可能だがOP限定、
  `isCombatant()`で戦闘チーム判定を一本化)。占領集計・リスポーンチケット消費・K/D/A集計から除外、
  スコアボードでは「観戦中: ...」として別枠表示。`Team`の列挙順にADMINを挿入したことで
  ネットワーク上のordinal(NEUTRALなど)がズレるため、バイト長が変わらなくてもPROTOCOL_VERSIONを
  5→6に上げた
- **チームシャッフル**: `/conquest team shuffle`(OP)。ADMIN以外のオンラインプレイヤーを
  ランダムにシャッフルしA/Bへ交互に割り当て(既存の`joinTeam`経由でバニラチーム同期も自動)
- **TDM(チームデスマッチ)モード**: `GameMode`(CONQUEST/TDM)を追加し、`/conquest mode set`
  (WAITING中のみ変更可)で切替。拠点を使わないモードで、既存の「拠点リストが空なら関連UIは
  自動的に隠れる」設計がそのまま活きたため、HUD/GUI側の変更はほぼ不要だった。
  実装のキモは既存のチケットカウンタ(`ticketsA`/`ticketsB`)をモードによって意味を変えて
  再利用したこと: CONQUESTでは`startingTickets`から減っていく残機、TDMではキルのたびに
  加算される合計キル数として扱い、`tdmKillLimit`到達で即勝利。リスポーンチケット消費
  (`ticketCostPerRespawn`)はTDMでは無効化(キル加算と二重に動くと矛盾するため)。
  `tickSecond()`の集計ループは元々`!points.isEmpty()`でガードしていたため、TDM(拠点0件)では
  ラウンドタイマー・制限時間勝敗判定まで丸ごとスキップされてしまう不具合があり、
  「拠点処理」と「ラウンド進行(タイマー・人数0判定・制限時間判定)」を分離してガード範囲を
  修正した。`ConquestSyncPacket`に`mode`(GameMode)フィールドを追加したためPROTOCOL_VERSIONを
  6→7に上げた

## 遭遇した罠(再発防止)

- **PROTOCOL_VERSIONの上げ忘れ**: パケットのフィールドを追加・変更したのに`NetworkHandler`の
  `PROTOCOL_VERSION`を上げ忘れると、新旧混在時にハンドシェイクは通ってしまい、実際のバイト列が
  ズレて`IndexOutOfBoundsException`でクライアントが原因不明のまま切断される。パケット形式を
  変更したら必ずセットで上げること
- **サーバー多重起動によるポート競合**: 新しく`runServer`を起動しても、ポートを握っているのが
  実は起動しっぱなしの古いプロセスだった、という事象が複数回発生。`runServer`が失敗/様子がおかしい
  ときは`Get-NetTCPConnection -LocalPort 25565`で実際にポートを握っているPIDを確認すること
- **列挙型への挿入もPROTOCOL_VERSION対象**: `RoundState`の途中に`STARTING`を挿入したところ、
  以降の定数(`IN_PROGRESS`/`ENDED`)のordinalがズレた。ワイヤ上のバイト長は変わらない
  (enumはVarIntのordinal1つのまま)ため見落としがちだが、新旧混在時は値の意味が変わって
  しまう(例: 新サーバーの`IN_PROGRESS`を旧クライアントが`ENDED`と誤読する)。
  「バイト長が変わるかどうか」ではなく「意味が変わるかどうか」でバージョンを上げる判断をすること
