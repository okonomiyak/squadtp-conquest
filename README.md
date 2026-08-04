# SquadTP Conquest (squadtp-conquest)

Minecraft 1.20.1 / Forge 47.x 向けの、Battlefieldの「コンクエスト」モード風の対人ゲームモード。
[squadtp](../squadtp)(分隊テレポート/蘇生Mod)に依存し、その分隊APIを読み取り専用で利用する。
**squadtp本体のコード・configは一切変更していない。**

拠点は複数設置できる(名前で管理)。

ライセンス: [GPL-3.0](LICENSE)

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/conquest team join <a\|b\|admin>` | チームA/Bに参加。同時にバニラチーム`conquest_a`/`conquest_b`にも自動参加(フレンドリーファイア無効・チーム色設定)。`admin`は観戦用の第3チームでOP限定 | - (`admin`のみOP) |
| `/conquest team shuffle` | 管理人チーム以外のオンラインプレイヤーをランダムにA/Bへ均等に振り分け直す | OP |
| `/conquest team assign <attacker\|defender> <a\|b>` | ブレイクスルーモードで、チームA/Bのどちらが攻撃側/防衛側かを設定。ラウンドが`WAITING`の時のみ変更可(既定は攻撃側=チームA) | OP |
| `/conquest mode set <conquest\|tdm\|breakthrough>` | ゲームモードを切り替え。ラウンドが`WAITING`の時のみ変更可 | OP |
| `/conquest sector add <番号> <拠点名> [半径]` | 実行者の足元に拠点を追加し、指定番号のセクターに割り当てる(セクターが無ければ新規作成) | OP |
| `/conquest sector spawn set <attacker\|defender> <番号>` | 実行者の足元を、指定セクターのその役割のスポーン地点に設定 | OP |
| `/conquest sector timelimit set <番号> <秒>` | セクター個別の制限時間を上書き(0で`sectorTimeLimitSeconds`既定値に戻す) | OP |
| `/conquest sector remove <番号>` | セクターと、それに属する全拠点を削除(旗ブロックも撤去) | OP |
| `/conquest sector list` | 全セクターの番号・所属拠点一覧を表示 | - |
| `/conquest preset save <名前>` | 現在の拠点配置・スポーン地点・ゲームモードを名前付きで保存(同名は上書き) | OP |
| `/conquest preset load <名前>` | 保存済みプリセットを読み込み、現在の拠点・スポーン・モードを置き換える。ラウンドが`WAITING`の時のみ | OP |
| `/conquest preset remove <名前>` | プリセットを削除 | OP |
| `/conquest preset list` | 保存済みプリセット一覧(拠点数・モード)を表示 | - |
| `/conquest point set [半径]` | デフォルト拠点"Alpha"を実行者の足元に設置(半径省略時はconfig既定値)。旗ブロック(1×3)が自動生成される。TDMモードでは不要 | OP |
| `/conquest point add <名前> [半径]` | 名前付きの拠点を追加(複数拠点はこちら) | OP |
| `/conquest point remove <名前>` | 拠点を削除(旗ブロックも撤去) | OP |
| `/conquest point list` | 全拠点の状況一覧 | - |
| `/conquest spawn set <a\|b>` | 実行者の足元をそのチームのラウンド開始時スポーン地点に設定(未設定ならワールドスポーン) | OP |
| `/conquest zone set <a\|b> <x1 y1 z1> <x2 y2 z2>` | 2点の座標(`~`による相対座標も可)を対角線とする直方体を、そのチームの**自陣ゾーン**として設定。敵チームのプレイヤーが`homeZoneKillSeconds`秒連続で滞在すると処刑される(ゾーンを出た瞬間カウントはリセット)。ゲームモード共通、ラウンド進行中のみ判定 | OP |
| `/conquest zone corner1 set <a\|b>` / `/conquest zone corner2 set <a\|b>` | 実行者の足元をそのチームのゾーンの角1/角2に設定(両方設定されて初めてゾーンが有効になる) | OP |
| `/conquest zone remove <a\|b>` | 自陣ゾーンを削除 | OP |
| `/conquest zone list` | 設定済みの自陣ゾーン一覧(角1〜角2の座標)を表示 | - |
| `/conquest protectzone add <名前> <x1 y1 z1> <x2 y2 z2>` | 2点の座標を対角線とする直方体を**破壊禁止ゾーン**として登録(複数登録可)。ラウンド中の地形破壊(下記)がこの範囲内のブロックを一切変更しない | OP |
| `/conquest protectzone remove <名前>` | 破壊禁止ゾーンを削除 | OP |
| `/conquest protectzone list` | 登録済みの破壊禁止ゾーン一覧を表示 | - |
| `/conquest start` | ラウンド開始。両チームに最低1人ずつオンラインでいることが条件(コンクエストモードはさらに拠点が1つ以上必要)。`startCountdownSeconds`秒のカウントダウン後に実際に開始 | OP |
| `/conquest stop` | 強制終了、またはカウントダウン中の開始をキャンセル。勝敗をつけずに待機状態(WAITING)へ | OP |
| `/conquest reset` | 結果表示中(ENDED)を手動で待機状態(WAITING)へ戻す。`autoResetAfterResult`が有効なら自動でも戻る | OP |
| `/conquest config list` | 調整可能な設定項目と現在値を一覧表示 | OP |
| `/conquest config set <key> <value>` | 設定値をサーバー再起動なしで即座に変更(TOMLにも永続化) | OP |
| `/conquest status` | ゲームモード・ラウンド状態・チケット(またはキル数)・残り時間・全拠点の状況・自分のチーム・所属分隊を表示 | - |

## GUI

- **Lキー**(旗ブロックを右クリックしても同じ画面が開く): OPには画面上部にタブが表示され、
  一度に1つのタブの内容だけを表示する(パネルが縦に伸びすぎないようにするための構成)。
  OP以外はタブなしで「状況」タブの内容のみが常に表示される
  - **状況タブ**(全員が閲覧可): チケット(またはブレイクスルーのセクター進行状況)・
    全拠点の状況一覧、自分のチーム表示、チームA/B参加ボタン、所属分隊(squadtp経由)の一覧
  - **設定タブ**(OP限定): デフォルト拠点"Alpha"向けの半径編集+「ここに設置」、
    スポーン地点設定ボタン、自陣ゾーンの角1/角2設定ボタン(A/Bそれぞれ、足元の座標を角として登録)
    +削除ボタン(全ゲームモード共通のためブレイクスルー限定ではない)、ゲームモード切替ボタン
    (conquest⇔breakthrough)、ラウンド状態に応じて自動切替する開始/キャンセル/停止/リセットボタン。
    複数拠点の追加・削除はコマンド(`/conquest point add|remove|list`)から行う
    (デフォルト拠点"Alpha"のみGUIで完結)
  - **セクタータブ**(OP限定・ブレイクスルーモード中のみタブ自体が表示される): 既存セクター一覧
    (セクター番号+所属拠点名、行ごとに削除ボタン)、セクター番号/拠点名/半径の入力欄+
    「拠点をセクターに追加」ボタン(既存番号を指定すれば追加、新規番号ならセクターごと新規作成)、
    指定セクターへの攻撃側/防衛側スポーン設定ボタン、セクター個別の制限時間上書き欄+設定ボタン
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
- ブレイクスルーモードでは上部のチケットバーの代わりに「セクターX/Y」、自分の役割に応じて
  攻撃側チケット数(攻撃側)または「防衛中」(防衛側)、攻撃側視点では次のリスポーンウェーブまでの
  秒数を表示。拠点アイコン列は**現在アクティブなセクターの拠点のみ**表示される。
  ロック中/既に突破済みの拠点の範囲内に入った場合、通常の占領インジケーターの代わりに
  「占領不可」と表示される

## ゲームモード

`/conquest mode set <conquest|tdm|breakthrough>` で切り替える(ラウンド待機中のみ)。

- **conquest**(既定): 上記の拠点占領モード
- **tdm**(チームデスマッチ): 拠点なしで開始可能。チケット表示欄がそのまま**チーム合計キル数**として
  カウントアップし、`tdmKillLimit`に到達したチームが即座に勝利。制限時間に達した場合はキル数が
  多いチームの勝ち(同数はドロー)。リスポーンによるチケット消費は発生しない
  (キル自体がスコアを増やす方式のため)。拠点関連のHUD/GUI表示(拠点アイコン・占領インジケーター・
  セクター表示)は拠点が存在しないため自動的に非表示になる。蘇生・アシストなどのスコアリングは
  コンクエストと共通
- **breakthrough**(ブレイクスルー、Battlefield風の非対称モード): 攻撃側/防衛側に分かれ、
  複数の**セクター**(それぞれ1つ以上の拠点で構成)を順番に攻略する
  - `/conquest team assign`で、チームA/Bのどちらが攻撃側かを設定(既定はチームA)
  - 現在アクティブなセクターの拠点を**すべて攻撃側が占領**すると次のセクターへ前線が進み、
    前のセクターの拠点はそれ以降占領判定の対象外になる(HUD/GUI/占領インジケーターには
    「占領不可」として表示される)。最終セクターを陥落させると攻撃側の勝利
  - 攻撃側は`attackerTickets`枚のリスポーンチケットを共有する**ウェーブリスポーン制**。
    死亡のたびに1枚消費し、`respawnWaveIntervalSeconds`秒ごとにまとめてアクティブセクターの
    攻撃側スポーン地点へ再出撃する(それまでは観戦者として待機)。チケットが尽きた状態での
    死亡はそのラウンド中はリスポーン不可。生存者・待機者が両方いなくなった時点で防衛側の勝利
  - 防衛側はチケット無制限。リスポーンのたびに即座にアクティブセクターの防衛側スポーン地点へ移動
    (前線の後退に合わせて自動的に切り替わる)
  - 各セクターには`sectorTimeLimitSeconds`(`/conquest sector timelimit set`で個別上書き可)の
    制限時間があり、攻撃側が拠点を1つ占領するたびに`sectorTimeExtensionOnCapture`秒延長される。
    時間切れは防衛側の勝利
  - **squadtpの蘇生システムはブレイクスルー中(TDMも同様)は自動的に無効化**され、
    ラウンド終了時に元へ戻される。死亡が即座にスコア/チケットへ反映される必要があるため
    (詳細は「設計メモ」参照)

## 旗ブロック

拠点の目印となる1×3マルチブロック(石の基台+ポール+チーム色の旗布、`flag.json`のBlockbench
デザインを反映)。下から順に「基台+ポール」「純粋なポールの通過部分」「ポール+旗布」の3段構成。
石テクスチャの部分(基台・ポール)はチーム色に関わらず固定、最上段の旗布部分だけチーム色の
羊毛テクスチャに変わる。コマンドブロック相当の破壊不可で、
`/conquest point set|add`でのみ設置・移動される(手持ちアイテムとしては入手できない)。
占領進行度・所有チームに応じて自動で色が変わる。右クリックでGUIが開く。

## 自陣ゾーン

チームA/Bそれぞれに1つずつ設定できる、**2点の座標を対角線とする直方体**の縄張り(円/半径では
なく、`/fill`と同じ感覚でXYZ座標2点を指定する)。`/conquest zone set <a|b> <座標1> <座標2>`
(相対座標`~`も使用可)、または`/conquest zone corner1 set`・`corner2 set`で実行者の足元を
角として1つずつ設定する方式でも登録できる(両方の角が揃って初めてゾーンが有効になる)。
敵チーム(チームAゾーンならチームB、逆も同様)のプレイヤーが
**`homeZoneKillSeconds`秒間連続でゾーン内に滞在すると処刑される**。ゾーンから一歩でも出た瞬間に
カウントは0にリセットされる(出入りを繰り返しても蓄積しない)。滞在中は本人にアクションバーで
「あと%s秒」の警告が表示される。ゲームモードを問わず、ラウンドが`IN_PROGRESS`の間のみ判定する。
拠点の円形パーティクルとは別に、直方体の12辺をなぞるワイヤーフレーム状のチーム色パーティクルで
常時可視化される(ラウンド状態に関わらず表示)。自チームプレイヤー・管理人チーム・未参加者は
対象外。管理用GUI(Lキー)からも角1/角2の設定・削除ができる(A/Bそれぞれ)。

## 地形破壊(BF風クレーター)

ラウンドが`IN_PROGRESS`の間に起きた爆発(バニラTNT・クリーパー・他Mod由来のものも含む、
`ExplosionEvent.Detonate`をフックして拾う)を、バニラの単純なブロック除去ではなく
BF風のクレーターに差し替える。爆心に近いブロックはair、外周(`craterRubbleRingRatio`、
既定で影響ブロックのうち爆心から遠い側25%)は`craterRubbleBlock`(既定`minecraft:coarse_dirt`)
に変わる。アイテムドロップは発生しない。1回の爆発で処理するブロック数は`maxBlocksPerExplosion`
(既定200、爆心に近い順)で頭打ちにし、それを超えた分はバニラの通常処理に委ねる(サーバー負荷対策)。

**破壊されないブロックの指定は2通り**:
- `indestructibleBlocks`(config): ブロックの種類(レジストリ名)で常に除外。既定で
  `minecraft:bedrock`・チェスト類・`squadtpconquest:conquest_flag`などを含む
- `/conquest protectzone add|remove|list`: 2点座標の直方体エリアを破壊禁止として登録(自陣ゾーンと
  同じ2点指定方式、複数登録可)。管理用GUIには未対応(コマンドのみ)。自陣ゾーンと同じワイヤーフレーム
  パーティクル(白系)で常時可視化される

**破壊された地形は`/conquest start`のたびに元へ復元される**(BFのラウンドリセットと同じ)。
`/conquest stop`では復元しない(停止直後に被害状況を確認したい場合を想定)。破壊記録自体は
ラウンドスコープの一時状態でありNBTに永続化されない(サーバー再起動を挟むと復元されない点に注意)。

第1段階の実装のため、構造崩壊(支持を失ったブロックの落下)や壊す素材ごとのガレキの出し分けは
行わない(単純な球形クレーターのみ)。`terrainDestructionEnabled`をfalseにすると機能全体を無効化し
バニラの爆発挙動に戻せる。

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
- **TDM/ブレイクスルーでのsquadtp蘇生システム自動無効化**: squadtpは同一分隊メンバーへの
  致死ダメージを、`SquadFeature.REVIVE`が有効な限り即死ではなく「ダウン状態」に変換する
  (`ReviveSystem`)。TDMのキル計上・ブレイクスルーのチケット消費はどちらも
  `LivingDeathEvent`(本当の死亡)にのみフックしているため、分隊内で戦っている場合に
  ダウン変換が挟まると本来の死亡タイミングより大幅に遅延する(蘇生され続ける限り実質発生しない)
  問題があった。これを避けるため、`ConquestManager`はconquest以外のモードでラウンドを
  開始する際に`SquadManager.setFeatureEnabled(SquadFeature.REVIVE, false)`
  (squadtpの公開API、`/squad feature revive disable`と同じもの)を呼び、ラウンド終了時に
  `true`へ戻す。squadtp本体のコード・configには一切手を入れていない
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
- `homeZoneKillSeconds`(既定10) — 自陣ゾーンに敵が連続滞在できる秒数。超えると処刑される

`scoreboard`セクション:
- `assistWindowSeconds`(既定10)
- `scorePerKill`(既定100) / `scorePerAssist`(既定50) / `scorePerRevive`(既定50)

`breakthrough`セクション:
- `attackerTickets`(既定30) — 攻撃側が共有するリスポーンチケットの総数
- `respawnWaveIntervalSeconds`(既定15) — 攻撃側のリスポーンウェーブ間隔(秒)
- `sectorTimeLimitSeconds`(既定300) — セクター1つあたりの制限時間の既定値(`/conquest sector timelimit set`で個別上書き可)
- `sectorTimeExtensionOnCapture`(既定120) — 拠点を1つ占領するごとに残り時間へ加算される秒数

`terrainDestruction`セクション:
- `terrainDestructionEnabled`(既定true) — falseで機能全体を無効化しバニラの爆発挙動に戻す
- `indestructibleBlocks`(既定`bedrock`・チェスト類・`squadtpconquest:conquest_flag`等) — ブロック種類による破壊禁止リスト(レジストリ名、`modid:block_id`形式)
- `craterRubbleBlock`(既定`minecraft:coarse_dirt`) — クレーター外周に置き換わるブロック
- `craterRubbleRingRatio`(既定0.25) — 影響ブロックのうち外周(ガレキ)になる割合。残りはair
- `maxBlocksPerExplosion`(既定200) — 1回の爆発で処理する上限(爆心に近い順)。超過分はバニラ処理に委ねる

これらは`/conquest config set <key> <value>`でゲーム内から再起動なしに変更できる
(TOMLにも自動で永続化される)。ただし対応しているのは元々の`conquest`/`scoreboard`セクションの
数値・真偽値項目のみで、`breakthrough`・`homeZoneKillSeconds`・`terrainDestruction`セクションの
項目(リスト・文字列型を含む)は`world/serverconfig/squadtpconquest-server.toml`の直接編集が必要
(既存の制約で、今回追加した項目も同様の扱いにしている)。

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
