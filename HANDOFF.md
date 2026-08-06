# squadtp-conquest 引き継ぎメモ (2026-08-06 更新)

前回(2026-07-25)からの主な変化: BF風の各種機能(地形破壊・ゾーンワンド・戦場境界・
セクター別戦闘エリア・セクター突破チケットボーナス・コールイン・チームリスポーンビーコン)を
連続実装した後、**squadtp本体に2件連続で手を入れた**(いずれもユーザー明示許可、詳細は下記
「squadtp本体は改造しない」節参照): (1) チームビーコン/拠点スポーンをsquadtpの死亡後リスポーン
選択画面(`RespawnChoiceScreen`)から選べる公開API(`RespawnChoiceProvider`)を追加、
(2) **AED**アイテム(squadtp側、squadtp-conquestからは未使用): 分隊外・分隊未所属のプレイヤーでも
ダウン状態からの蘇生を可能にする再利用可能な道具(クールダウンは既定45秒→ユーザーフィードバックで
5秒に短縮済み)。squadtp-conquest側はこの2件目に対応するコード変更は無く(squadtp本体だけで完結する
機能のため)、依存バージョンの追従のみ。さらにsquadtp-conquest単体の変更として、拠点スポーン選択肢
から係争中/被占領中の拠点を除外し、拠点をJourneyMapのウェイポイントとして表示する機能、そして
それを土台にした**索敵マーキング(スポット)**(BFのスポット機能: クロスヘアの先の敵をキーで
マークし、一定時間自チームのJourneyMapに表示)を追加した。詳細は下記および**README.md**参照。

## 現在の状態

- **バージョン**: `mod_version=0.2.3`(索敵マーキングでの`PROTOCOL_VERSION` 12→13に伴うバンプ。
  **コードを含む変更のため、稼働中サーバー/クライアントは新jarでの再起動が必要**
  ——AEDクールダウンのような設定ファイルのみのホットパッチでは反映されない)
- **ライセンス**: GPL-3.0(`LICENSE`ファイルあり)
- **squadtp依存**: `gradle.properties`の`squadtp_version=0.2.3`(squadtp本体も同日0.2.3にバージョンアップ済み、
  `../squadtp/build/libs/squadtp-0.2.3.jar`と一致させてある。AEDクールダウン既定値短縮のための
  バージョンアップで、squadtp-conquest側のコード変更は無い)
- **ビルド**: `gradlew build`成功、警告のみ(エラーなし)
- **Git**: `master`ブランチ、リモート`origin`(`git@github-okonomiyak:okonomiyak/squadtp-conquest.git`)にpush済み、作業ツリーはクリーン

機能の全体像・コマンド一覧・設定項目は**README.md**、実装の経緯・設計判断・遭遇した罠は
**squadtp-conquest-devlog-2026-07-20.md**、今後の改善案は**TODO.md**を参照。
このファイルはそれらを読む前の「現在地」把握用。

## 実装済み機能(要約、詳細はREADME参照)

- **(squadtp側の変更によるコンクエストモードへの副作用、2026-08-06)** squadtp本体のAED追加に伴い、
  `SquadFeature.REVIVE`が有効な間(コンクエストモードは有効のまま。TDM/ブレイクスルーはTDMキル
  計上バグ対策で従来通り無効化されているため影響なし)、**分隊未所属のプレイヤーも致死ダメージで
  即死せずダウン状態に入るようになった**(以前は分隊未所属なら通常通り即死していた)。
  コンクエスト側のコードは無変更だが、`ScoreEvents.onDeath`(実際の死亡イベントのみで発火)の
  タイミングがダウン→タイムアウト/蘇生失敗までずれ込む点はTDMで一度対処した既知のパターンと同種。
  コンクエストの大半のプレイヤーは分隊未所属のままプレイするため、実質的に**ほぼ全滅時にダウン
  →蘇生待ち、というBFの「戦闘不能」状態がコンクエストに新たに現れる**ことになる(意図した挙動)
- コンクエストモード: 複数拠点占領・チケット・リスポーンコスト・開始前カウントダウン・
  拠点範囲パーティクル・1×3旗ブロック
- 管理人チーム(`Team.ADMIN`, OP限定の観戦チーム)
- `/conquest team shuffle`: ランダム振り分け+分隊解散&同チームでの自動再編成
- Team Deathmatch モード(`/conquest mode set tdm`, `tdmKillLimit`)
- マッププリセット(`/conquest preset save|load|remove|list`): 拠点配置・スポーン・モードを
  名前付きで保存/呼び出し
- **ブレイクスルーモード**(`/conquest mode set breakthrough`, 2026-07-24新規実装):
  攻撃側/防衛側の非対称チーム(`/conquest team assign`)、複数セクター
  (`/conquest sector add|spawn set|timelimit set|remove|list`、`conquest/Sector.java`)、
  攻撃側の共有チケット+ウェーブリスポーン制、セクター制限時間+占領ごとの延長。
  HUD/GUI/占領インジケーターもセクター進行・ロック状態に対応
- **GUIのセクター管理セクション**(2026-07-24追加): ブレイクスルーモード中、管理用GUI(Lキー)に
  セクター一覧(番号+所属拠点、行ごと削除ボタン)・セクター番号/拠点名入力+追加ボタン・
  攻撃側/防衛側スポーン設定ボタン・セクター別制限時間上書きが追加された。これに伴い
  `ConquestSyncPacket.PointStatus`に`sectorNumber`を追加(`PROTOCOL_VERSION` 9→10)
- **GUIのスクロール対応**(2026-07-24追加、ユーザーからの「スクロールができない」報告への対応):
  拠点一覧・セクター一覧・所属分隊一覧の3リストがそれぞれ独立してマウスホイールでスクロール可能に
  (`ConquestScreen.mouseScrolled`)。カーソルがそのリスト領域にある時だけそのリストが動く。
  一覧が表示行数を超える場合のみ右上に"3-7/12"形式のヒントを表示。ネットワーク変更なし
  (クライアント側のみ、サーバー側の変更は無し)。
  副次的な修正として、`radiusBox`等の入力欄が**毎秒(サーバーからの同期パケットのたび)自動的に
  リセットされていた既存の不具合**にも対処(入力欄を毎回作り直す際、直前の入力値を引き継ぐように
  変更)。これが無いとセクター拠点名などを1秒以内に入力し終える必要があり、実質使えなかった
- **自陣ゾーン**(`/conquest zone set|corner1 set|corner2 set|remove|list`, 2026-07-24新規実装
  →同日中に円柱(中心+半径)から**2点XYZ座標の直方体**へ設計変更): チームA/Bそれぞれ1つの
  ボックス(`zoneAPos1`/`zoneAPos2`等を`ConquestManager`に直接フィールド保持、専用クラス無し。
  min/maxは`zoneBounds(Team)`で都度計算)。`/conquest zone set <a|b> <座標1> <座標2>`
  (`BlockPosArgument`、`~`相対座標対応)で直接指定するか、`corner1 set`/`corner2 set`で
  実行者の足元を角として1つずつ設定(両方揃って初めて有効)。判定は軸並行境界ボックス
  (AABB)包含チェック(Y軸も含む3軸すべて)。敵チームのプレイヤーが`homeZoneKillSeconds`秒
  (既定10)連続滞在すると`player.hurt(genericKill, MAX_VALUE)`で処刑(ゾーン脱出で即リセット、
  アクションバーで警告表示)。ゲームモード非依存、`IN_PROGRESS`中のみ判定。可視化は
  `CaptureZoneVisualizer.renderBox`(直方体12辺のワイヤーフレーム、新規追加)を使用
  ―― 拠点用の円形リング(`render`)とは別メソッド。`homeZoneRadius`configは削除済み
  (直方体化に伴い意味を失ったため)。管理用GUI(Lキー)にはA/Bそれぞれ角1/角2設定ボタン+
  削除ボタンを配置(現在の設定状況の常時表示は無し、成否はチャットメッセージで確認)。
  NBT永続化キーも`ZoneAPos`+`ZoneARadius`→`ZoneAPos1`+`ZoneAPos2`に変更(未リリースのため
  後方互換は考慮していない)
- **GUIクラッシュ修正**(2026-07-24): セクター削除等で拠点数/分隊人数が急に減った直後に
  `ConquestScreen.render()`がリビルド前の古い行数・スクロール位置のまま最新のリストを
  インデックスして`IndexOutOfBoundsException`でクライアントがクラッシュする不具合を修正
  (ユーザー報告・crash-reportで確認済み)。拠点一覧・所属分隊一覧の描画を、キャッシュ値ではなく
  その場でリストサイズに合わせて再クランプするよう変更(セクター一覧は元々内部的に一貫していたが
  念のため同様に対処)
- **GUIのタブ化**(2026-07-25): `ConquestScreen`を単一の縦長パネルから**状況/設定/セクター**
  3タブ構成に再編。一度に1つのタブの内容だけを構築・表示するため(`Tab` enum、
  `buildStatusTab`/`buildSetupTab`/`buildSectorSection`)、パネルの縦の長さがタブごとの内容量に
  収まるようになった。OP以外はタブバー自体を表示せず「状況」タブのみ(従来通り)。
  「セクター」タブは、設定タブの共通半径欄(`radiusBox`)を流用していた「拠点をセクターに追加」の
  半径入力を**タブ専用の`sectorRadiusBox`に分離**した(設定タブを一度も開かずセクタータブを開くと
  `radiusBox`が未生成でクラッシュしうる問題を避けるため)。`mouseScrolled`のヒット判定も
  `activeTab`でゲートし、非表示タブの古い行位置を誤ってスクロール対象にしないようにした。
  ネットワーク変更なし(クライアント側のみ)
- **BF風の地形破壊**(`ExplosionEvent.Detonate`をフック、新規実装): ラウンド`IN_PROGRESS`中の
  爆発(バニラTNT/クリーパー/他Mod由来も含む)を、単純な球形クレーターに差し替える。爆心付近は
  air・外周(`craterRubbleRingRatio`)は`craterRubbleBlock`。アイテムドロップなし。
  `maxBlocksPerExplosion`で処理上限を設け、超過分はバニラの通常処理に委ねる(負荷対策)。
  破壊禁止指定はブロック種類(`indestructibleBlocks`config)とエリア(`/conquest protectzone
  add|remove|list`、`conquest/ProtectZone.java`、自陣ゾーンと同じ2点AABBだが複数登録可)の
  両対応。破壊は`/conquest start`のたびに元へ復元(`ConquestManager.destroyedBlocks`、
  NBT非永続化の一時状態)。構造崩壊・素材別ガレキ出し分けは未実装(第1段階の意図的なスコープ外)
- **ゾーンワンド**(`squadtpconquest:zone_wand`、新規実装): 左クリックで角1・右クリックで角2を
  記録する選択アイテム(WorldEdit/Createのスキーマティック&クイル方式)。選択はプレイヤーごとの
  サーバー側一時状態(`conquest/ZoneSelection.java`、NBT非永続化)。`/conquest zone set <a|b>`・
  `/conquest protectzone add <名前>`を座標なしで実行すると自動的にこの選択を使う(既存の座標指定版
  はそのまま残っている)。左クリックの横取りは新規`ZoneWandEvents.java`
  (`PlayerInteractEvent.LeftClickBlock`をキャンセル)、右クリックは`item/ZoneWandItem#useOn`
- **戦場境界(アウトオブバウンズ)**(`/conquest boundary set|corner1 set|corner2 set|remove|list`、
  新規実装): 自陣ゾーンと同じ2点AABB判定を反転させただけ(境界の**外**に`boundaryKillSeconds`秒
  連続でいると処刑)。チームA/B別ではなくマップ全体で1つ。参加チーム全員が対象、ラウンド
  `IN_PROGRESS`中のみ判定、ゾーンワンドの座標省略設定にも対応。可視化・NBT永続化・実装パターンは
  自陣ゾーンをそのまま踏襲(専用クラス無し、`ConquestManager`に直接フィールド保持)
- **セクター別戦闘エリア**(`/conquest sector area set|remove`、新規実装): ブレイクスルーの
  各セクターに個別の戦闘エリア(2点AABB、`Sector.java`に追加)を設定でき、そのセクターが
  アクティブな間だけ戦場境界と同じ「外に出て`boundaryKillSeconds`秒で処刑」判定がそのエリアに
  対して働く(グローバルな`/conquest boundary`より優先、エリア未設定セクターはグローバル境界に
  フォールバック)。セクター突破直後は`sectorAreaTransitionGraceSeconds`秒(既定20)の猶予期間があり
  境界判定自体が止まる(`ConquestManager.sectorAreaGraceSecondsRemaining`、ラウンドスコープの
  一時状態でNBT非永続化、`advanceSector()`でセット)。可視化はアクティブセクターのエリアのみ
  (グローバル境界と排他、両方は出さない)
- **セクター突破時の攻撃側チケットボーナス**(`ticketsPerSectorCapture`既定10、新規実装):
  `advanceSector()`内で`attackerTickets`へ直接加算するだけの単純な変更。個々の拠点占領ごとの
  `sectorTimeExtensionOnCapture`(制限時間延長)とは別軸のボーナスで、最終セクター突破時
  (`next == null`で即`endRound`する分岐)は加算されない。`conquest.msg.sector_cleared`の
  チャットメッセージにボーナス数を追記(パラメータ追加のため翻訳キー変更、後方互換は考慮していない)
- **コールイン(スコアストリーク報酬)**(`/conquest callin add|remove|list|use`、新規実装、
  `conquest/CallIn.java`): OPがスコア閾値↔アイテムの組み合わせを複数登録でき、プレイヤーは
  `/conquest callin use`で利用可能スコア(`totalScore` − 使用済み)を消費してアイテムを受け取る。
  `PlayerScore`に`spent`フィールドを追加(ラウンドスコープのみ、NBT永続化は`Scores`リストのみで
  `LifetimeScores`には含めない)。キル/デス/アシスト/蘇生の実カウントは書き換えず、
  `availableScore()`で差し引くだけなのでスコアボード表示は影響を受けない。アイテムIDは
  `ForgeRegistries.ITEMS`で存在確認(登録時・使用時とも)、入りきらない分は足元にドロップ。
  コールインが1つ以上登録されていれば`/conquest status`に自分の利用可能スコアを表示
- **チームリスポーンビーコン**(`squadtpconquest:team_beacon`、新規実装): 右クリックで設置、
  `teamBeaconLifetimeSeconds`秒(既定30)で自動消滅、設置者のチーム全員が死亡リスポーンのたびに
  (回数無制限で)そこへテレポートするようになる。squadtpの`RespawnBeaconItem`(分隊単位・使用回数制限
  ・時間無制限)とは似て非なる**完全に別実装**(squadtp本体は無改造)。`ConquestManager`に
  `Map<Team, TeamBeacon>`(ネストしたprivateクラス、NBT非永続化のラウンドスコープ一時状態)を追加、
  `teleportToRoleSpawn()`の先頭でビーコンの有無をチェックする形にしたため、既存のブレイクスルー
  ウェーブリスポーン・セクタースポーンのコード自体は無改造。コンクエスト/TDMは元々リスポーン時の
  テレポートが一切無かった(ラウンド開始時の初回移動のみ)ため、`onRespawn`にビーコン用の
  テレポート呼び出しを追加で足した(ビーコン無しの場合の挙動は変更していない)。可視化は拠点と
  同じ円形パーティクル(`CaptureZoneVisualizer.render`)を流用、敵からの破壊要素は無し(第1段階)
- **コールインのプレイヤー向けGUI**(新規実装): 管理用GUI(Lキー)状況タブに、登録済みコールイン
  一覧+[使用]ボタン(利用可能スコア不足時は無効化)を追加。表示にはコールイン一覧・利用可能スコアの
  同期が必要だったため`ConquestSyncPacket`に`callIns`(`CallInStatus`レコード新規)・
  `availableScore`フィールドを追加し`PROTOCOL_VERSION` 10→11。`ConquestClientData`/
  `ClientPacketHandler`もあわせて更新。登録(`add`)・削除(`remove`)は引き続きコマンドのみ
  (GUIは`use`のみ対応)、拠点/分隊一覧と同じ5件超過時スクロール対応
- **チームビーコン/拠点スポーンの選択UI化**(2026-08-06、squadtp本体に新規公開API追加): チーム
  リスポーンビーコンと拠点スポーン(`spawnAtOwnedPointsEnabled`、コンクエスト限定)は、従来の
  「自動・無選択でテレポート」から、squadtpの`RespawnChoiceScreen`(分隊のラリー/ビーコン/
  メンバースポーンを選ぶ既存画面)で**プレイヤー自身が選ぶ**方式に置き換えた。
  squadtp側に新規`uk.iwaservice.squadtp.api`パッケージ
  (`RespawnChoiceProvider`/`RespawnChoiceEntry`/`RespawnChoiceRegistry`)を追加し、
  `RespawnChoicePacket`に`external`エントリー一覧を追加(`PROTOCOL_VERSION` 1→2)、
  `ServerEvents.onPlayerRespawn`の「分隊未所属なら即return」制約を緩和(外部プロバイダーの
  選択肢は分隊未所属でも・squadtpの`RESPAWN_CHOICE`機能トグルに関係なく表示される)、
  `/squad respawn external <providerId> <choiceId>`コマンドで選択を受け取る。
  squadtp-conquest側は`ConquestRespawnChoiceProvider`(新規、`conquest`パッケージ)を実装して
  `SquadTpConquest`コンストラクタで登録。`ConquestManager.teleportToRoleSpawn()`/`onRespawn()`
  からビーコン/拠点への自動テレポート分岐を削除し、`teleportToTeamBeacon`/`teleportToPoint`を
  プロバイダーの`onChosen`からのみ呼ぶ形にした。拠点スポーンは「死亡位置に一番近い1つを自動選択」
  から「保有拠点をすべて個別の選択肢として列挙」に変更(`lastDeathPositions`関連コードは削除)。
  `teleportToTeamBeacon`/`teleportToPoint`はプロバイダーからのみ呼ばれるためパッケージ内可視
  (`private`から変更)にとどめた
- **拠点スポーン選択肢から係争中/被占領中の拠点を除外**(2026-08-06): 上記の拠点スポーン選択肢に、
  敵チームがその拠点の占領範囲内に(生存・非ダウン状態で)1人でもいる場合は出さないようにした
  (係争中=両チーム在圏、被占領中=敵単独在圏、のどちらも同じ条件でまとめて除外できる ——
  拠点占領の進行ロジック自体には手を入れていない)。新規`ConquestManager.isPointSpawnSafe(server, point)`
  (既存の`computeOccupancy`を再利用、その場で毎回再計算するのでtickのキャッシュ状態に依存しない)を
  `ConquestRespawnChoiceProvider`の`getChoices`(一覧に出す時点)と`onChosen`(選んだ瞬間の再検証、
  画面表示後に状況が変わっていた場合に備えて)の両方でチェック
- **拠点のJourneyMapウェイポイント表示**(2026-08-06新規実装、squadtp-conquest単体、squadtp本体は
  無改造): squadtpの`compat/journeymap`と同じソフト依存パターン(`compileOnly`+`ModList.isLoaded`
  実行時検出、`compat.journeymap`配下のJourneyMap型は`JourneyMapCompat`のガード内でしか参照しない
  のでJourneyMap未導入でも安全)を独自実装として複製。`ConquestSyncPacket.PointStatus`に`dimension`/
  `pos`フィールドを追加(今までは座標を同期していなかった、`PROTOCOL_VERSION` 11→12)。
  `ClientPacketHandler.handleSync`の同期のたびに`JourneyMapCompat.refresh()`で全ウェイポイントを
  作り直す(squadtpと同じ「差分更新ではなく毎回全消し→全再生成」方式)。ラウンドが`IN_PROGRESS`の
  間だけ表示、チーム色(`Team.hudColor()`のアルファを落として使用)で保有チームを表現。
  ログアウト時は`ClientEvents.onLoggingOut`から`JourneyMapCompat.clear()`(古いデータに基づいて
  再表示してしまわないよう、`refresh()`とは別に「無条件で全消し」だけを行う専用メソッドを用意)
- **索敵マーキング(スポット)**(2026-08-06新規実装、squadtp-conquest単体、squadtp本体は無改造):
  新規キー`key.squadtpconquest.spot`(既定マウス中央ボタン)を押すと、クライアント側で視点から
  `spotRangeBlocks`(既定100)先までブロックレイキャスト(`Level.clip`)+バニラの
  `ProjectileUtil.getEntityHitResult`でエンティティレイキャストを行い、遮蔽物より手前で命中した
  敵プレイヤーだけを対象にする(壁越し索敵は不可)。命中したら`/conquest spot <対象>`を送信
  (初めて`EntityArgument.player()`を使用、既存コマンド群の`StringArgumentType.word()`名前指定とは
  異なる)。サーバー側`ConquestManager.spotPlayer`は`spotCooldownSeconds`(既定2)の
  クールダウン管理のみ行い、対象位置のスナップショットを新規`SpotPacket`(S2C)でスポッター
  チーム全員に送る(`PROTOCOL_VERSION` 12→13)。継続追従は無く、`spotDurationSeconds`(既定8)後に
  クライアント側のゲーム内時刻比較で自動的に消える(サーバーからの明示的な解除パケットは無い、
  `ConquestClientData.pruneExpiredSpots`を`ClientEvents.onClientTick`で毎tick呼ぶだけ)。
  表示は上記の拠点ウェイポイント基盤をそのまま流用(`ConquestJmWaypointHandler.refresh()`に
  スポット一覧を追加しただけ)、スポット対象は常に敵チームなので`ConquestClientData.getYourTeam()
  .opponent().hudColor()`で色付け。画面上の頭上マーカー(BF本編のような3D→2D投影)は今回のスコープ外
  (squadtp本体の分隊メンバー位置共有もJourneyMapのみで完結しており、同じ方針を踏襲)
- スコアボード(右Alt)2ページ目: 累計スコア+K/D比率
- HUD/GUIのチーム色を自分/敵視点から**チーム固定色**(A=青・B=赤)に変更
- 管理用GUI(Lキー)・BF風HUD(常時表示)・adjustable config(`/conquest config set`)

## ⚠️ 既知の問題・積み残し

**索敵マーキング(スポット)も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- 敵を見て中央マウスボタン(既定)を押すと実際に`/conquest spot`が飛ぶか、味方のJourneyMapに
  スポット位置が表示されるか
- 壁越しでは反応しないこと(ブロック遮蔽の判定が効いているか)
- `spotDurationSeconds`後に自動的にウェイポイントが消えるか
- `spotCooldownSeconds`中に連打しても再送されないか(サーバー側で無視されるだけで、クライアントは
  投げっぱなしなのでスパム自体は防げていない点に注意 — 実害は無いはずだが通信量は見ておきたい)
- 味方・スペクテイター・ダウン中のプレイヤーを誤って対象にしないか
- キーバインドがコンフィグ画面で正しく表示・再割り当てできるか(マウス中央ボタンが他の操作と
  衝突しないか)

**拠点のJourneyMapウェイポイント表示も実プレイ未検証(ビルド成功のみ、JourneyMap実機確認は未実施)。**
次回確認が必要な点:
- JourneyMap導入環境で実際に拠点がウェイポイントとして表示されるか、チーム色(保有チーム変更に
  追従して色が変わるか)
- ラウンドが`IN_PROGRESS`でなくなった時(結果表示・待機中)にウェイポイントが消えるか
- サーバーからログアウトした時にウェイポイントが残らず消えるか
- JourneyMap未導入環境でクラッシュ・エラーログが出ないこと(`compileOnly`+実行時検出が
  squadtp側と同じパターンで機能しているかの確認)
- `PROTOCOL_VERSION`を11→12に上げたので、旧クライアント/サーバー混在時に想定通り接続拒否されるか

**squadtpのAED追加(分隊未所属もダウンするようになった)の副作用も実プレイ未検証。** 次回確認が必要な点:
- コンクエストで分隊未所属のプレイヤーが致死ダメージを受けた際、即死せずダウン状態に入るか
- ダウン中は`/conquest`のリスポーン/チケット消費が(実際の死亡まで)発生しないため、
  タイムアウト死亡までチケットが減らない体感になっていないか(想定通りではあるが要確認)
- AEDを持ったプレイヤーが分隊外・分隊未所属のダウンプレイヤーを実際に蘇生できるか、
  クールダウン中は失敗する(かつメッセージが出る)か
- TDM/ブレイクスルーでは`SquadFeature.REVIVE`が無効化されたままなので、この副作用が
  一切出ない(従来通り即死する)ことの確認

**TDMモードでキル数が増えない不具合 → 対処済み(2026-07-24、ブレイクスルー実装の一部として)。**

- 根本原因(再掲): squadtpの`ReviveSystem`は、victimがsquadtpのsquadに所属していれば
  致死ダメージを即死ではなく「ダウン状態」に変換する。`/conquest team shuffle`は同チーム同士を
  自動でsquadtpのsquadに再編成するため、TDM(・今回追加したブレイクスルーも同様に該当)では
  ほぼ常にこの変換が起き、`ScoreEvents.onDeath`(本物の`LivingDeathEvent`にのみフック)が
  遅延どころか実質発生しなくなっていた
- 対処: `ConquestManager`がconquest以外のモードでラウンド開始時に
  `SquadManager.setFeatureEnabled(SquadFeature.REVIVE, false)`(squadtpの公開API)を呼び、
  ラウンド終了時に`true`に戻すようにした。squadtp本体は無改造のまま
- **未検証**: この修正を実プレイで確認したセッションはまだない。次回TDM/ブレイクスルーを
  プレイする際、`/conquest status`のK/D/Aが分隊メンバー同士の戦闘でも即座に増えることを
  確認すること

**ブレイクスルーモード自体も実プレイ未検証(ビルド成功+`runServer`読み込み確認のみ)。**
特に以下は次回の実プレイで確認が必要:
- 攻撃側のウェーブリスポーン(死亡→observer→ウェーブでの一斉リスポーン)の実際の見え方
- セクタークリア→次セクターへの前線移動、ロック済み拠点の「占領不可」表示
- 攻撃側チケット0かつ生存者0での防衛側勝利判定、セクター制限時間切れでの防衛側勝利判定
- GUI(Lキー)のモード切替ボタン、セクター管理セクション(追加/削除/スポーン設定/制限時間)の
  一連の操作

攻撃側/防衛側の自動スワップ(ラウンドごと)は未実装で、`/conquest team assign`による
手動設定のみ(設計判断として据え置き)。

**自陣ゾーンも実プレイ未検証(ビルド成功+`runServer`読み込み確認のみ)。**
次回確認が必要な点:
- 敵チームプレイヤーがゾーンに10秒(既定)滞在した際に実際に処刑されるか、境界を跨いだ時に
  カウントが正しくリセットされるか(Y軸も含めた3軸判定になったので、ゾーンの上下にすり抜けて
  出入りできてしまわないかも含めて)
- `/conquest zone set`(座標直接指定・`~`相対座標)と`corner1/corner2 set`(足元指定)の
  両方の登録方法が意図通り動くか
- アクションバーの警告表示のタイミング・見え方
- 直方体ワイヤーフレームパーティクル境界の視認性(特に高さのあるゾーンで縦辺が見えるか)

**GUIのタブ化も実プレイ未検証(ビルド成功のみ、クライアント専用コードのためrunServerでは
検証不可)。** 次回`runClient`等で確認が必要な点:
- タブ切り替えボタンのクリックが正しく反応するか、選択中タブが無効化(グレーアウト)表示になるか
- 設定タブを一度も開かずにセクタータブを直接開いた場合の「拠点をセクターに追加」が
  正しい半径(`sectorRadiusBox`、設定タブの半径欄とは独立)で動くか
- モード切替でブレイクスルー→コンクエストに変わった際、セクタータブを開いていたら
  「状況」タブに戻るか(タブ自体が消えるため)
- 各タブでのスクロール(状況タブの拠点/分隊一覧、セクタータブのセクター一覧)が
  他タブの位置情報と混線しないか

**地形破壊も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- TNT起爆で実際にクレーター(air+外周ガレキ)ができるか、見た目のバランス(`craterRubbleRingRatio`)
- `indestructibleBlocks`指定ブロック・`/conquest protectzone`登録エリア内が実際に無傷か
- `/conquest start`で前ラウンドの破壊が確実に復元されるか(サーバーが1つだけの単純ケースでまず確認)
- `maxBlocksPerExplosion`超過時(大量TNT同時起爆等)にラグ・クラッシュしないか
- 自陣ゾーンと同じワイヤーフレームパーティクルで破壊禁止ゾーンが視認できるか

**ゾーンワンドも実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- 左クリックが本当にブロック破壊をキャンセルしつつ角1を記録するか(サバイバルモードで確認)
- 右クリックでの角2記録が、対象ブロックの設置/開閉等バニラの右クリック動作を邪魔しないか
- `/conquest zone set <a|b>`・`/conquest protectzone add <名前>`の座標省略版が選択範囲を正しく使うか
- ディメンションをまたいで角を設定した場合に選択がリセットされる(意図通り)か
- アイテムのモデル表示(`models/item/zone_wand.json`、`minecraft:item/blaze_rod`を仮テクスチャ流用)

**戦場境界も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- 境界の外に出て`boundaryKillSeconds`秒後に実際に処刑されるか、境界内に戻った瞬間カウントが
  リセットされるか
- 自陣ゾーンとの重複時(境界の外かつ敵陣の中、等)に処刑理由メッセージが混線しないか
  (両方とも`player.hurt(genericKill, MAX_VALUE)`を独立に呼ぶだけなので実害は無いはずだが未確認)
- 管理人チーム・未参加者が対象外になっているか(`teamOf(uuid).isCombatant()`判定)

**セクター別戦闘エリアも実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- セクターの戦闘エリア外に出て`boundaryKillSeconds`秒後に処刑されるか、エリア内に戻ればリセットされるか
- セクター突破直後の`sectorAreaTransitionGraceSeconds`秒間、実際に境界判定が止まっているか
  (突破した瞬間に新エリア外にいても処刑されないこと)
- エリア未設定のセクターでグローバル`/conquest boundary`へ正しくフォールバックするか
- セクター切替でエリアの可視化(ワイヤーフレーム)が正しく新エリアに切り替わるか

**セクター突破チケットボーナス・コールインも実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- セクター突破のたびに`attackerTickets`が正しく増えるか(最終セクターでは増えないこと)
- `/conquest callin add`で登録したコールインが`/conquest callin use`で正しく消費・付与されるか、
  利用可能スコア不足時に拒否されるか
- インベントリが満杯の時に足元へドロップされるか
- `/conquest status`の利用可能スコア表示がキル/アシスト/蘇生に応じて増減し、コールイン使用後に
  正しく減るか

**チームリスポーンビーコンも実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- 設置したチームの他プレイヤーが死亡→リスポーンで実際にビーコン位置へ飛ぶか(何度でも)
- `teamBeaconLifetimeSeconds`後に自動消滅し、以降は通常のスポーン地点に戻るか
- 同じチームが2個目を設置すると1個目が置き換わるか
- ブレイクスルーで、攻撃側のウェーブリスポーン(観戦者→SURVIVAL復帰)の際もビーコンが優先されるか、
  防衛側の即時リスポーンでも同様か
- 敵チームのビーコンには一切影響を受けない(自チームのみ対象)ことの確認

**コールインのプレイヤー向けGUIも実プレイ未検証(ビルド成功のみ、`runClient`でのみ確認可能)。**
次回確認が必要な点:
- コールイン未登録時は状況タブに何も表示されない(既存レイアウトを崩さない)ことの確認
- 登録済みコールインの一覧・[使用]ボタンが正しく表示され、利用可能スコア不足時にボタンが
  グレーアウトすること
- ボタン押下で実際に`/conquest callin use`相当の効果(アイテム付与・スコア消費)が起きること
- 5件を超えるコールイン登録時、マウスホイールでスクロールできること
- PROTOCOL_VERSION不一致(旧クライアントでの接続)が想定通り拒否されること

**チームビーコン/拠点スポーンの選択UI化も実プレイ未検証(ビルド成功+`runServer`読み込み確認のみ)。**
次回確認が必要な点:
- 死亡後、squadtpのリスポーン選択画面に「チームビーコン」「保有拠点名」が実際に選択肢として
  表示されるか(分隊未所属のプレイヤーでも表示されること、squadtpの`RESPAWN_CHOICE`機能を
  OFFにしても表示され続けることを含む)
- 選択肢をクリックすると実際にその場所へテレポートするか
- ビーコンが消滅していたり拠点の所有が変わっていたりした場合、選択時に
  `squadtp.msg.respawn_expired`で失敗すること(クラッシュしないこと)
- `spawnAtOwnedPointsEnabled`をfalseにすると拠点の選択肢自体が出なくなること
- TDM・ブレイクスルーの防衛側respawnではこの機構が一切働かない(拠点スポーンはコンクエスト限定、
  ビーコンは両モードで選択肢に出る想定)ことの確認
- 分隊の既存選択肢(ラリー/ビーコン/メンバー)と同じ画面に共存して問題なく表示・操作できるか
- 敵が保有拠点の占領範囲に入ったら、その拠点がリスポーン選択肢から消えるか(両チーム在圏=係争中・
  敵単独在圏=被占領中のどちらのケースも)。敵が離脱したら再び選択肢に戻ること

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
  上げること。現在値は`13`(ブレイクスルー実装で`8`→`9`、GUIセクター管理追加で`9`→`10`、
  コールインGUI追加で`callIns`/`availableScore`フィールドを`ConquestSyncPacket`に追加し`10`→`11`、
  JourneyMap連携で`PointStatus`に`dimension`/`pos`を追加し`11`→`12`、
  索敵マーキングで新規`SpotPacket`を追加し`12`→`13`)。
  上げ忘れると新旧クライアント/サーバー混在時に`IndexOutOfBoundsException`で原因不明の切断が起きる。
  なお**squadtp本体**の`NetworkHandler.PROTOCOL_VERSION`は別物(こちらは`1`→`2`、
  `RespawnChoicePacket`に`external`フィールドを追加したため)。2つのmodは別チャンネル
  (それぞれ`squadtp:main`/`squadtpconquest:main`)なので互いのバージョン番号は独立
- **サーバー多重起動**: `runServer`を起動したまま次の`runServer`を叩くとポート競合で失敗する。
  `Get-NetTCPConnection -LocalPort 25565`(または`Get-CimInstance Win32_Process -Filter "Name='java.exe'"`)
  で残留プロセスを確認してから起動すること
- **squadtp本体の改造は原則禁止、例外は明示許可された範囲のみ**: 基本方針は変わらず、
  公開API(`SquadManager`/`Squad`/`ReviveSystem`/`TeleportHelper`)経由の読み取り専用利用のみで
  進めること。**2026-08-06にユーザーから明示的な許可**を得て、squadtp本体へ2件の変更を加えた:
  (1) `uk.iwaservice.squadtp.api`パッケージの新設(`RespawnChoiceScreen`への選択肢追加を
  第三者Modに許可する公開API)、(2) AEDアイテム(`squad/ReviveSystem.java`の蘇生ロジック変更・
  `ServerEvents.onLivingDeath`から「分隊未所属なら通常死亡」分岐を削除・新規`aed`アイテム)。
  いずれも**ユーザーがsquadtp-1.20.1への直接の作業指示を出した都度**、その回に限定して許可された
  ものであり、squadtp本体への変更を一般的に許可するものではない。今後squadtpへさらに手を
  入れる必要が生じた場合は、改めてユーザーに確認すること

## 次にやること候補

1. チームビーコン/拠点スポーンの選択UI化、AED(分隊外蘇生)、拠点のJourneyMapウェイポイント表示、
   索敵マーキング(スポット)の実プレイテスト(上記「未検証」参照、最優先。稼働中サーバー/
   クライアントは新jarでの**再起動が必要**な変更を含むので注意)
2. ブレイクスルーモードの実プレイテスト(2人以上、上記の未検証項目を中心に)
3. TDMキル計上修正が実プレイで効いているかの確認(上記参照)
4. TODO.mdの「優先度高」(スポーン安全確認)
5. TODO.mdの「未検証」項目全般(実プレイでの動作確認がまだ大半未実施)
