# squadtp-conquest 引き継ぎメモ (2026-08-07 更新)

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
マークし、一定時間自チームのJourneyMapに表示)を追加した。本日(2026-08-07)は続けて
**試合終了時の集合**(`/conquest gather set`で設定した1箇所へ、ラウンド終了時に全参加者が
テレポート)、および**破壊禁止ブロックのゲーム内管理**(`/conquest protectblock add|remove|list`、
TOML編集なしで地形破壊の対象外ブロックを追加/削除)を追加。ユーザー報告で見つかった地形復元の
バグを機に、地形復元を差分追跡から**`StructureTemplate`による戦場境界の丸ごとスナップショット
/復元方式**へ全面置き換え。さらに**最大HPのconfig化**(`maxHealth`、銃Modとのバランス調整用)、
および**破壊禁止ブロック/エリアを通常のブロック破壊からも保護**(従来は爆発からしか
保護していなかったギャップをユーザー指摘で発見・解消)も追加。さらに、SuperbWarfareのRPGに関する
ユーザー報告を機に、`maxBlocksPerExplosion`の上限を超えた影響ブロックが破壊禁止判定自体を
すり抜けてしまうバグも発見・修正した。詳細は下記および**README.md**参照。

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

- **待機チーム参加時にテレポート**(2026-08-16): ユーザーから「待機に参加したらtpされるように
  したい」と依頼(`waiting`チームには元々テレポート先が無く、参加してもその場に留まるだけ
  だった)。演習場の[明示的なスポーン地点](#演習場)(`rangeSpawnDim`/`rangeSpawnPos`、
  `/conquest range spawn set`)と全く同じパターンで`waitingSpawnDim`/`waitingSpawnPos`を
  `ConquestManager`に追加し、`/conquest waiting spawn set|remove|list`で設定・NBTに永続化。
  `joinTeam`の`WAITING`分岐(インベントリクリアの直後)で`TeleportHelper.findSafeSpot`経由の
  安全な位置へテレポート、未設定なら従来通り無変化(no-op)。演習場と違いエリアという概念が
  無いチームなので、フォールバック先(box中央)は存在しない——スポーン地点が一切未設定なら
  常にテレポートされない。
- **v0.2.9リリース**(2026-08-16、squadtp 0.2.6同時リリース): v0.2.8以降の17コミット分
  (戦闘区域リセットの複数回化・試合終了時のPvP無効化とMVP/チーム別トップ表示・強制蘇生・
  拠点占領のスコア化・TDM蘇生アリ化・演習場スポーン地点設定・待機チーム新設・演習場での
  事前カウントダウン中の足止め・TDMのGUI/HUD対応・地形手動復元への5秒警告・
  `/conquest team join`の複数対象指定対応・classloadout連携・コンクエスト/TDMの
  リスポーン自動tp・招待制分隊を維持する`/conquest team shuffle`の改修とその演習場/待機
  チームへの拡大・`waiting`への個別移動での招待制分隊維持)をまとめてリリース。squadtp側は
  `ReviveSystem.forceReviveAll`(試合終了時の強制蘇生に使用)と募集タブのスクロール対応を含む
  0.2.6。
- **`waiting`チームへの個別移動でも招待制の分隊を維持**(2026-08-16): ユーザーから
  「waitingになっても招待制の分隊はリセットしないで」と依頼。`/conquest team join waiting`
  (個別コマンド、シャッフルとは別経路)は公開の`joinTeam(player, team)`を通るため、これまでは
  移動のたびに`leaveSquadIfAny`が無条件で呼ばれ、招待制の分隊メンバーでも`waiting`に移動した
  瞬間に分隊から外れてしまっていた。`joinTeam(player, team)`に、移動先が`WAITING`かつ本人の
  分隊が招待制(`isOpenJoin() == false`)の場合だけ`leaveSquad=false`で3引数版を呼ぶ分岐を追加。
  `waiting`は非戦闘チーム(`isCombatant() == false`、PvP不可)なので、`leaveSquadIfAny`が本来
  防いでいた「A/Bをまたいだ分隊機能の悪用」は起こり得ず、外す理由が無いと判断。A/B間の移動や
  オープン参加の分隊は従来通り(移動のたびに分隊を外れる)。
- **`/conquest team shuffle`に演習場/待機チームも対象に含める**(2026-08-16): ユーザーから
  「waitingとrangeもteamshuffleでa_bにわかれるようにして」と依頼(それまでは`ADMIN`/`RANGE`/
  `SPECTATOR`/`WAITING`の4チームを一律で対象外にしていた)。`shuffleTeams`の除外条件を
  `Team.ADMIN`と`Team.SPECTATOR`のみに縮小し、`range`/`waiting`のプレイヤーもA/Bへ振り分け
  対象にした(招待制分隊の維持ロジックはそのまま両チームにも適用される)。`joinTeam`は元々
  `range`→A/B、`waiting`→A/Bのチーム変更(`/conquest team join`経由)を問題なく処理できていた
  ため、シャッフル側のフィルタ変更のみで対応。
- **`/conquest team shuffle`で招待制の分隊を維持**(2026-08-16): ユーザーから「team shuffleする
  とき分隊リセットされるやん でも分隊を招待制にしたらリセットしないで同じチームにして」と依頼
  (それまでは対象プレイヤー全員の分隊を一律解散→新チームで再結成していたため、リーダー承認制の
  招待制分隊もシャッフルのたびに壊れていた)。squadtpの`Squad.isOpenJoin()`で判定し、招待制
  (`false`)の分隊は解散せずメンバー全員をまとめて同じ新チームへ移動、オープン参加の分隊・
  分隊未所属は従来通り個別シャッフル(解散→新チームで再結成)。実装は2段階: (1)まず全プレイヤーを
  「招待制分隊のユニット」と「バラの個人」に分類、(2)人数が少ない方のチームへ、ユニット→個人の
  順に貪欲法で割り当て。既存の`ConquestManager.joinTeam(player, team)`はチーム変更のたびに
  無条件で`leaveSquadIfAny`を呼んでいたため、そのまま招待制分隊のメンバーに使うと移動の瞬間に
  分隊から抜けてしまい維持できない矛盾を発見。`joinTeam`を`boolean leaveSquad`引数付きの
  private 3引数オーバーロードに分割し、招待制分隊メンバーの移動時のみ`leaveSquad=false`で
  呼ぶことで解決(通常の`/conquest team join`など他の全呼び出し元は今まで通り2引数版で
  `leaveSquad=true`のまま)。詳細は[README「チームシャッフルと分隊」](README.md)参照。
- **演習場もclassloadoutのロードアウト装備対象に**(2026-08-16): ユーザーから
  「rangeくんもリスポーンからtp」と依頼(直前のclassloadout連携がラウンド開始のみ対応だった
  ことへの追加要望)。`teleportIntoRange`(`range`チーム参加時・演習場内での死亡復帰時・自動/
  手動リセット時のテレポート、いずれもこの1メソッド経由)の末尾に`ClassLoadoutCompat.equip
  (player)`を1箇所追加するだけで3つの呼び出し元すべてに反映される形にした
  (`teleportToSpawns`と同じ理屈: いずれも死亡→リスポーンイベントを経由しない直接テレポートなので、
  classloadout自身の「リスポーンごとに自動装備」フックでは拾えない)
- **classloadout連携: ラウンド開始時にロードアウトを装備**(2026-08-16): ユーザーから
  「classloadoutと連携してゲーム開始時にロードアウトを付与させて」と依頼(`C:\Users\tomip\program\
  java\classloadout`を指定)。Exploreエージェントで調査したところ、classloadoutは
  `api`パッケージを持たない(squadtpの`RespawnChoiceProvider`のような正式な連携APIが無い)ので、
  内部クラス`uk.iwaservice.classloadout.ServerEvents.equipLoadout(ServerPlayer)`
  (`/class select`やロードアウト・ステーションが使っているのと同じ「即時装備」経路、
  設定次第でインベントリクリア+5スロット装備+弾薬支給まで行う)を直接呼ぶ形で実装。
  classloadout側には一切手を入れていない。
  - 既存の[JourneyMap連携](#拠点のウェイポイント表示journeymap連携)と全く同じ「隔離パターン」を
    踏襲: `compat.ClassLoadoutCompat`(`ModList.isLoaded("classloadout")`チェックのみ、
    classloadoutのクラスを一切参照しない)→ `compat.classloadout.ConquestLoadoutHandler`
    (実際にclassloadoutのクラスを参照する側、`isLoaded`がfalseなら絶対にクラスロードされない)。
    呼び出し失敗時は例外キャッチして以後そのセッション中は自動無効化(`broken`フラグ)
  - `teleportToSpawns`(ラウンド開始時に全参加者をスポーンへ運ぶ既存メソッド)の末尾で
    各プレイヤーに`ClassLoadoutCompat.equip(player)`を呼ぶだけ。ラウンド中の通常リスポーンは
    classloadout自身の「リスポーンごとに自動装備」機能がそのまま働くので変更不要——ラウンド開始は
    「既に生存しているプレイヤーをテレポートするだけ」で死亡イベントを経由しないため、
    ここだけ明示的に呼ぶ必要があった
  - `build.gradle`にsquadtpと同じivyローカルリポジトリパターンを追加(`../classloadout/build/libs`、
    ただしclassloadoutのjar名にはMCバージョン/ローダー接尾辞が無いので`patternLayout`は
    `[module]-[revision].[ext]`のまま)。依存スコープは`modCompileOnly`+`modRuntimeOnly`
    (JourneyMap APIと同じ考え方だが、classloadoutに専用の薄いAPI jarが無いためフルの本体jarを
    参照——実行時は`mods.toml`で`mandatory=false`なので問題ないが、**ビルド時はGradleの依存解決が
    必ず走るため`../classloadout`が存在しビルド済みである必要がある**、squadtpと同じ制約)。
    `gradle.properties`に`classloadout_version=0.5.2`、`mods.toml`に
    `[[dependencies.squadtpconquest]]`ブロック(`modId="classloadout"`, `mandatory=false`)を追加
- **コンクエスト/TDMもリスポーン時に自動テレポートするように**(2026-08-16): ユーザーから
  「コンクエストやTDMでも復活後ブレイクスルーのようにtpされるようなしくみに」と依頼。
  AskUserQuestionで「squadtpのリスポーン選択画面(チームスポーン/スポーン2/ビーコン/拠点)は
  残しつつ、リスポーン直後にまず自動でチームスポーンへテレポートする」を選択(選択画面自体を
  廃止する案もあったが不採用)。従来`onRespawn`はコンクエスト/TDMの場合チケット消費のみ行い
  テレポートは一切せず、squadtpの選択画面で何も選ばなければバニラのワールドスポーンのまま放置
  されていた(ブレイクスルーだけ`handleBreakthroughRespawn`で`teleportToRoleSpawn`を呼び自動配置
  していた)。`onRespawn`のCONQUEST/TDM分岐(elseブロックとしてTDMもカバー)に
  `teleportToRoleSpawn(player, team)`を追加しただけで、既存の選択画面(squadtp本体の
  `RespawnChoiceScreen`)との共存は無改造。選択画面はこの自動テレポートの**後**に開くため、
  ビーコン等の別の場所を選べばそこへ再テレポートされる(何も選ばなければ最初の自動配置のまま)
- **TDMでも蘇生を有効なままに**(2026-08-16): ユーザーから「TDMで蘇生アリがいい」と依頼。
  `ConquestManager`の`start`/`stop`/`endRound`にあった`mode == GameMode.TDM`時の
  `SquadManager.setFeatureEnabled(SquadFeature.REVIVE, false/true)`(TDM開始時に無効化・
  終了/停止時に再有効化)を3箇所とも削除。これで`SquadFeature.REVIVE`をトグルする箇所が
  コード全体から無くなった(ブレイクスルーは2026-08-14に、CONQUESTは元々無効化していなかった)ため、
  未使用になった`SquadFeature`のimportも削除。副作用として、squadmateを撃破しても即座には
  キルカウントされず、相手が蘇生されずに`downedTimeoutSeconds`後に本当に死亡した時点で計上される
  ようになる(ブレイクスルーのチケット消費と全く同じ遅延特性——ユーザーが把握した上での選択と
  判断し、そのまま反映)
- **待機チームを新規追加**(2026-08-16): ユーザーから「待機チームを追加してインベントリクリア
  PVP禁止」と依頼(同じメッセージで「演習場チームに入ったとき演習場スポーンに強制移動されるように」
  とも言われたが、コードを再確認したところ`joinTeam`が`team == Team.RANGE`で
  `teleportIntoRange`(明示的なスポーン地点優先)を既に呼んでおり、こちらは追加対応不要と判断)。
  `Team`enumに`WAITING`を追加(`ADMIN`/`RANGE`/`SPECTATOR`と同じく`isCombatant()`は`false`、
  既存の非戦闘チーム共通ロジック——占領カウント除外・HUD非表示・`shuffleTeams`除外・
  `isConquestTeam`のバニラチーム同期対象——にそのまま乗る形で追加箇所は最小限)。
  `joinTeam`に`team == Team.WAITING`時の`player.getInventory().clearContent()`を追加(片道のみ、
  復元機能は無し)。PvP禁止は新規ロジックが必要だったため、既存の`SpawnZoneEvents`
  (スポーン区域+ラウンド終了後PvP無効化と同じ`LivingAttackEvent`フック)に
  「攻撃者または被害者が`waiting`チームならキャンセル」の条件を追加(同じチーム同士の攻撃は
  バニラのフレンドリーファイア無効設定で元々防がれるが、他チームからの一方的な攻撃はこの新条件が
  無いと防げなかった)。`spectator`と違いバニラのゲームモードは変えない(通常のサバイバルのまま
  移動・視点操作可能)
- **手動地形復元コマンドに遅延+チャット警告を追加**(2026-08-15): ユーザーから「地形読み込み時には
  コマンドの5秒後に実行されるようにして、打ったときに5秒後にリセットされますの注意をChatに流して」
  と依頼。従来`/conquest boundary restore`・`/conquest range reset`はどちらも実行した瞬間に
  即座にスナップショットを貼り戻していた(範囲内のプレイヤーが巻き込まれる警告なし)。新規
  `Config.TERRAIN_RESTORE_DELAY_SECONDS`(既定5、`terrainDestruction`セクション、他の
  同セクション項目と同じく`/conquest config set`には未登録)を追加し、両コマンドとも
  「即座に警告をチャットへブロードキャスト→`pendingTerrainRestoreSeconds`/
  `pendingRangeResetSeconds`をセット→毎秒`tickSecond`/`tickRange`でカウントダウンし0になった瞬間に
  実際の貼り戻しを実行」という2段階方式に変更。**ラウンド終了時の自動復元(`endRound`)と演習場の
  定期自動リセット(`rangeResetIntervalSeconds`ごと)はこの遅延の対象外**(ユーザーが明示的に
  「打ったとき」=コマンド実行時と言っていたため、自動トリガーには手を入れていない)。設定値を
  0にすると警告なしの即時実行に戻る(移行前の挙動と同じ)
- **TDMモードの作り込み不足を解消**(2026-08-15): ユーザーから「TDMをちゃんと作ろう」と依頼され、
  Exploreエージェントでコード全体を監査。TDM自体のコアロジック(キル計上・キルリミット勝利・
  蘇生自動無効化)は既に動いていたが、GUI/HUD/コマンド周りがコンクエスト用の配管に相乗りしたまま
  TDM向けの調整を受けていなかった。見つかった6件のうちユーザーが選んだ3件を対応:
  - **GUIのモード切替ボタンがTDMに届かなかったバグを修正**(`ConquestScreen.java`):
    `currentMode == CONQUEST ? BREAKTHROUGH : CONQUEST`という決め打ちの2値切替になっており、
    TDMへは`/conquest mode set tdm`コマンドでしか到達できなかった。`GameMode.values()`の
    `ordinal()`を使った汎用の巡回に変更(conquest→tdm→breakthrough→conquest…、将来モードが
    増えても自動対応)
  - **`tdmKillLimit`をクライアントに同期しHUDへ常時表示**: 従来はラウンド開始時のタイトル表示
    1回きりで、キル目標を確認する手段が無かった。`ConquestSyncPacket`に`tdmKillLimit`フィールドを
    追加(`PROTOCOL_VERSION` 18→19)、`ConquestHudOverlay`のTDM表示(通常のticketバーを流用)で
    左右の数値を`現在/目標`形式に変更(`tdmKillLimit`が0=無制限の場合は従来通り数値のみ)。
    バー自体の見た目(K/D比率の色分割)はそのまま維持——2チームが同じ目標に向かって競り合う
    構図では「どちらが優勢か」を示す現行のバーの方が「単独チームの目標達成度バー」
    (ブレイクスルーの攻撃側チケットバーと同じ形)より意味が通ると判断し、バーの再設計は見送った
  - **`/conquest status`がTDM中もキル数を「Tickets」とラベル表示していたのを修正**: `ticketsA/B`
    フィールドを内部でキル数として流用している都合で発生していた表記ミス。TDMの時だけ新規
    `conquest.status.kills`キーを使うように分岐(`ConquestCommand.status`)
  - スコープ外にした3件(GUIから見送り): TDMのHUDバーをブレイクスルー式の単独目標達成度バーに
    再設計すること(上記の理由で見送り)、ラウンド開始の前提条件としてスポーン地点設定を促す仕組み
    (拠点/セクターが無くても始められるTDMの気軽さを損なうため見送り)、実プレイでの動作確認
    (TODO.md参照、まだ未実施)
- **開始前カウントダウン中、自陣ゾーンの外に出られないように**(2026-08-15): ユーザーから
  「最初の準備の5秒間は自陣から出れないようにしたい」と依頼(`startCountdownSeconds`の既定値が
  ちょうど5)。新規`confineToHomeZones`/`confineToHomeZone`を`tickSecond`の`STARTING`分岐に追加し、
  毎秒、各チームの自陣ゾーン(`/conquest zone`)の外にいる自チームメンバーを検出したら
  `teleportToRoleSpawn`でスポーン地点へ押し戻す(処刑ではなく、単なる押し戻し+
  `conquest.msg.starting_zone_warning`のアクションバー警告)。既存の`checkZoneIntrusion`
  (敵陣処刑用)と対になる形だが別実装——あちらは「敵チームがゾーン内にいたら処刑」、こちらは
  「自チームがゾーン外にいたら押し戻す」で対象・アクションが逆なので使い回さず並列実装。
  自陣ゾーン未設定のチーム/モードでは何も起きない(既存の他ゾーン系機能と同じ任意機能の扱い)。
  判定は毎秒(他のゾーン処刑と同じ粒度)なので、外に出てから最大1秒ほどは戻されないソフトな
  バリア
- **`/conquest team join <team> <プレイヤー>`で`@a`等の複数対象セレクターに対応**(2026-08-15):
  ユーザーから「teamで@aを使えるようにして」と依頼。`player`引数が`EntityArgument.player()`
  (単一ターゲットのみ、`@a`のような複数マッチを構文エラーで拒否)だったのを`EntityArgument.
  players()`に変更し、`joinTeamOther`は`getPlayer`ではなく`getPlayers`(`Collection<ServerPlayer>`)
  を受け取って全員をループでチーム参加させるように変更。確認メッセージ
  (`conquest.msg.team_joined_other`)も対象1名の名前表示から人数表示に変更(`@a`で全員を一括参加
  させた場合に破綻しないため)
- **演習場の明示的なスポーン地点を追加**(2026-08-15): ユーザーから「演習場のスポーンを決められる
  ように」と依頼。従来`teleportIntoRange`は常にエリアのボックス中央(X/Z中央、Y最大)に固定
  テレポートしていた。`rangeSpawnDim`/`rangeSpawnPos`を新設(`/conquest range spawn set|remove`、
  `/conquest spawn set`と同じく実行者の足元を使う単一点方式、ワンド不要)、`teleportIntoRange`は
  設定されていればそちらを優先、未設定なら従来通りエリア中央にフォールバック。エリア本体
  (`rangePos1/2`)とは独立して管理しているため、`/conquest range set`でエリアを再設定しても
  スポーン地点は消えない(逆に`removeRange`でエリアを削除してもスポーン地点は残る)。NBT永続化対応
- **拠点制圧のスコア化+チーム別トップ表示**(2026-08-15): ユーザーから「チームごとの上位プレイヤー
  表示(A/Bそれぞれのトップ)」「拠点制圧もポイント付けて」と依頼。`PlayerScore`に`captures`
  フィールドを追加(kills/deaths/assists/revivesと同じ扱い、NBT永続化・lifetime版も対応)、
  新規`Config.SCORE_PER_CAPTURE`(既定100、`/conquest config set`にも登録)、`weightedScore`に
  加算。付与タイミングは`tickOnePoint`の`CaptureEvent.CAPTURED`発火時のみ(1tickごとの継続加点
  ではない)、対象は制圧達成の瞬間にその拠点の半径内にいた制圧チーム側の全プレイヤー
  (`occ.inZone()`をteam判定でフィルタ、均等付与・傾斜配分は無し)。`announceMvp`を拡張し、
  全体MVPに加えてチームA/B各自のトップスコアも`conquest.msg.team_top`で別途チャット発表
  (MVPと重複しても両方表示、該当者がいないチームはスキップ)
- **試合終了時にダウン中のプレイヤーが蘇生不能のまま固まるバグを修正**(2026-08-15、squadtp本体の
  変更): ユーザーから「試合終了時にダウン状態のときに蘇生できなくなるので強制蘇生で」と報告。
  `endRound`が呼んでいた`ReviveSystem.clear()`はDOWNED/SESSIONSマップを空にするだけで、
  プレイヤー本体の状態(伏せポーズ・鈍化/発光エフェクト・HP)は一切リセットしない
  実装だった——squadtp側で確認したところ、これは**バグではなく元々`onServerStopped`
  (サーバー停止時)専用の設計**(停止時はクライアント側を直す意味が無いため、あえて副作用無しの
  マップ全消去のみ)で、squadtp-conquestがラウンド終了(プレイヤーはまだオンライン)という別の
  文脈でこの関数を流用してしまっていたのが根本原因。squadtp側に新規`ReviveSystem.forceReviveAll
  (MinecraftServer)`を追加(まだダウン中の全プレイヤーを通常の蘇生成功時と同じ内容——エフェクト
  除去・ポーズ解除・`reviveHealPercent`分のHP回復・クライアントへの状態通知——で正常化してから
  `clear()`を呼ぶ。蘇生者不在のため無敵付与は無し)、squadtp-conquestの`endRound`はこちらを
  呼ぶように変更。squadtp側のバージョンは今回のセッションでは未リリース(次回リリース時に含める)
- **試合終了後の体験を改善**(2026-08-15): ユーザーから「ゲーム終了後をもっとマシにしたい」と
  依頼され、現状(勝敗タイトル+集合地点テレポートのみ、MVP等の成績表示は無し、集合地点でも
  PvPが有効なまま)を提示してAskUserQuestionで確認、両方まとめて対応:
  - **集合地点でのPvP無効化**: `SpawnZoneEvents.onAttack`(既存のスポーン区域PvP無効化と同じ
    `LivingAttackEvent`フック)の条件に`manager.getState() == RoundState.ENDED`を追加。
    ラウンドが終了している間はプレイヤー起因ダメージを一律キャンセルする(環境ダメージは対象外)。
    クラス名・ファイルは維持しつつjavadocだけスコープ拡大を明記(新規ファイルは作らず既存フックに
    条件追加するだけの最小差分)
  - **MVP発表**: `endRound`から新規`announceMvp`を呼び、オンラインの参加者(A/Bチーム)のうち
    既存の`totalScore`(キル/アシスト/蘇生の重み付け合計、スコアボードと同じ計算)が最も高い
    プレイヤーをチャットに発表(`conquest.msg.mvp`)。タイトル/サブタイトルには載せず
    (2行の枠に収まらない)、既存の`conquest.msg.victory`と同じ「補足はchatブロードキャスト」の
    パターンを踏襲。チームごとの上位プレイヤー表示は今回スコープ外(全体MVP1名のみで
    「締まりが良くなる」効果は十分と判断、TODO.mdの元案は両論併記だった)
- **`/conquest boundary restore`を何度でも呼び直せるように**(2026-08-15): ユーザーから
  「何回でも戦闘区域をリセットできるように」と依頼。`restoreTerrainSnapshot`が復元後に
  `terrainSnapshot`(保持していた`StructureTemplate`)を`null`にリセットしていたため、
  `/conquest boundary restore`は1ラウンドにつき1回しか成功せず、2回目以降は「スナップショットが
  無い」エラーになっていた(マップテスト中に壊す→復元→また壊す、を繰り返すには毎回
  `/conquest start`し直す必要があった)。復元後もスナップショットを保持したままにするよう変更
  (次の`/conquest start`で新しいスナップショットに上書きされるまでそのまま)。`endRound`の
  自動復元も同じ`restoreTerrainSnapshot`を使っているが、そちらは元々1回しか呼ばれないので
  挙動に変化は無い
- **Minecraftのmodリスト画面でバージョン表示が更新されないバグを修正**(2026-08-14、
  squadtp本体も同じ修正): v0.2.7リリース直後、ユーザーから「mod verがminecraft上でうまく
  反映されてない」と報告。`mods.toml`の`version`欄が初期スキャフォールドのまま`"0.2.1"`
  (squadtp側は`"0.2.0"`)に固定されており、`gradle.properties`の`mod_version`をいくら上げても
  ゲーム内のmodリストには常に同じ値が表示され続けていた——これまでの全リリース(v0.2.1〜v0.2.7)
  がこの状態だったと判明。標準の`version="${file.jarVersion}"`プレースホルダーに変更し、
  これが参照するjarマニフェストの`Implementation-Version`属性を`build.gradle`の`jar`タスクに
  追加(従来はこの属性自体が存在せず、プレースホルダーが解決できない状態だった)。
  **squadtp本体にも全く同じ不具合があり**、ユーザーの指示で両方修正・同時リリースした
  (squadtp v0.2.5、squadtp-conquest v0.2.8)
- **スポーン区域を新規実装**(2026-08-14): ユーザーから「スポーン区域という何もできないけど戦闘区域の
  中に区域を作成して」と依頼。AskUserQuestionで「何もできない」の内容を確認し、PvPダメージ無効+
  ブロック破壊/設置不可の両方が必要と判明。既存の[破壊禁止ゾーン](#地形破壊bf風クレーター)
  (`ProtectZone`、名前付き複数box、ワンド対応)と構造的にほぼ同じだが、破壊禁止ゾーンは
  「terrain destructionからの保護」専用でPvP/設置には一切関与しない設計だったため、意味を
  混ぜずに新規`SpawnZone`クラス(`ProtectZone`とフィールド構成は同一)として別管理にした:
  - `ConquestManager`に`spawnZones`マップ+`addSpawnZone`/`removeSpawnZone`/`isInSpawnZone`
    (`isProtected`と同じ`containsPos`ヘルパーを再利用)
  - `BlockProtectionEvents.onBreak`に`isInSpawnZone`判定を追加、新規`onPlace`
    (`BlockEvent.EntityPlaceEvent`)でスポーン区域内の設置だけを禁止(破壊禁止ゾーン/
    indestructibleBlocksは従来通り設置には無関与のまま——設置禁止はスポーン区域だけの新機能)
  - 新規`SpawnZoneEvents`(`LivingAttackEvent`をフックし、被害者がスポーン区域内かつ攻撃者が
    プレイヤーの場合にキャンセル。転落・溺死等の環境ダメージは対象外)、`SquadTpConquest`に登録
  - `/conquest spawnzone add|remove|list`(`protectzone`と同じ形、ワンド対応)
  - `CaptureZoneVisualizer.renderBox`をTeamベースの色だけでなく生の`Vector3f`色も受け取れるように
    オーバーロード追加(スポーン区域専用の黄色`COLOR_SPAWN_ZONE`を、既存のTeam列挙型を汚さずに
    導入するため)。`ServerEvents`の10tick可視化ループに追加
  - `MapPreset`にも`protectZones`と並べて`spawnZones`を追加(コンストラクタ引数が増える
    cascadingな変更——`ConquestManager`の3箇所の呼び出しと`savePreset`/`loadPreset`を更新)
- **ピン機能を新規実装**(2026-08-14): ユーザーから「ピン機能がほしい」と依頼され、
  AskUserQuestionで「任意の地点にマーカーを立てる(既存のスポットと同じ仕組みを、地点指定・
  手動消滅ありで流用)」を選択。既存の[索敵マーキング(スポット)](#索敵マーキングスポット)
  の配管をほぼそのまま複製した:
  - 新規キー`key.squadtpconquest.pin`(既定`N`キー)。`ClientEvents.tryPin`がクロスヘアの
    ブロックレイキャスト(`pinRangeBlocks`)を行い`/conquest pin <x> <y> <z>`を送信。
    しゃがみながら押すと代わりに`/conquest pin clear`を送信(自分のピンを早期削除)
  - 新規`PinPacket`(`placer`のUUIDをキーにする点がSpotPacketと違う——1人1ピンなので新しいピンで
    古いピンを自動置き換えでき、`cleared`フラグで削除も同じパケット型で表現できる)、
    `NetworkHandler`にID 3で登録、`PROTOCOL_VERSION` 17→18
  - `ConquestManager.placePin`/`clearPin`(スポットと同じ`Map<UUID, Integer>`クールダウン方式、
    ただし`clearPin`はクールダウン対象外)。ブロードキャスト範囲は最初チーム全体にしていたが、
    直後にユーザーから「ピンは分隊ごとでいい」と修正依頼があり、`SquadManager.getSquadOf`で
    判定する分隊単位に変更(`broadcastPin`)。分隊未所属なら自分にしか送らない(スポットは
    そのままチーム全体スコープで維持、ピンだけ分隊スコープにした)
  - `ConquestClientData`に`Map<UUID, PinEntry>`追加(キーは設置者UUID)、
    `ConquestJmWaypointHandler`でJourneyMapウェイポイントとして表示(自チーム色、
    `pin_<placerUUID>`のID)。`pinDurationSeconds`(既定60)経過で自動消滅、または
    しゃがみ+ピンキーで手動消滅
  - `Config`に`pinRangeBlocks`(既定100)/`pinDurationSeconds`(既定60)/`pinCooldownSeconds`
    (既定2)を追加。`/conquest config set`には未登録(スポット関連configと同じ扱い)
  - ラウンド開始時に`pinCooldownUntilTick`をクリア(スポットと同様)。ラウンド終了時に
    アクティブなピンを強制消去する処理は無い(スポットも同様に無い——`pinDurationSeconds`が
    短めなら次ラウンドまでに自然消滅する想定、既存踏襲でスコープ外とした)
- **ブレイクスルーで地形の自動復元が機能しない穴を修正**(2026-08-14): ユーザーから「地形破壊を
  行けるようにして」と依頼。地形破壊(クレーター化)自体は`TerrainDestructionEvents`が
  `terrainDestructionEnabled`とラウンド状態だけを見ており全モードで機能していたが、
  **自動復元**(`ConquestManager.captureTerrainSnapshot`/`restoreTerrainSnapshot`)は
  グローバルな`/conquest boundary`が設定されている場合にしか動かなかった。ブレイクスルーは
  グローバル境界を使わずセクター単位の`sector area`だけで運用することが多いため、そういう
  マップでは地形が壊れっぱなしで戻らない抜け穴になっていた。`resolveSnapshotRegion()`を新設し、
  グローバル境界があればそれを優先、無ければ(ブレイクスルーのみ)全セクターの戦闘エリアの
  バウンディングボックス(合算範囲)にフォールバックするようにした。異なるディメンションの
  戦闘エリアが混在する場合は最初に見つかったディメンションのセクターだけを合算(通常は
  発生しない想定)。`captureTerrainSnapshot`/`restoreTerrainSnapshot`自体のロジック(スナップショット
  ・貼り戻し・`/conquest boundary restore`との連携)は変更していない——対象範囲の決め方だけを
  差し替えた
- **攻め陣/守り陣判定が古いセクター情報を使っていたバグを修正**(2026-08-14): ユーザーから
  「ブレイクスルーで自陣敵陣システムどうなってる」と聞かれ`tickBreakthrough`を読み直したところ、
  `checkSectorFrontZones(server, sector)`が`advanceSector(server)`(セクター突破時に
  `activeSectorNumber`を次へ進める)の**後**に、突破前に取得した古い`sector`変数のまま
  呼ばれていた自己発見バグを確認。セクターが切り替わったその1tickだけ、1つ前のウィンドウ
  (突破されたセクターを基準にした攻め陣/守り陣)で判定してしまう。`zoneIntrusionSeconds`は
  次のtickで正しいウィンドウに戻れば該当プレイヤーが「対象ゾーンの外」と判定されカウンターが
  即リセットされるため実害は無かった(`homeZoneKillSeconds`既定10秒に対して誤カウントは
  最大1秒)が、`advanceSector`の直後に`currentSector()`で最新のアクティブセクターを取り直す
  ように修正
- **ブレイクスルーの攻撃側チケットもバー表示に**(2026-08-14): ユーザーから「チケットをバーにして」
  と依頼。従来は「攻撃側チケット: %s」という数値テキストのみだったが、コンクエストのチケットバーと
  同じ見た目のバー(`BAR_WIDTH`×`BAR_HEIGHT`、`attackerTeam.hudColor()`で塗りつぶし、数値をバーに
  重ねて表示)に変更。分母(何%かの基準)が必要なため`ConquestManager`に新規`attackerTicketsMax`
  フィールドを追加(ラウンド開始時に`attackerTickets`と同じ値で初期化、`advanceSector`の
  セクター突破ボーナスと同じ量だけ一緒に増える——ボーナスでチケットが増えるたびバーが
  一旦満タンに戻り、そこから減っていく見た目になる)。NBT永続化、`ConquestSyncPacket`への
  新規フィールド追加のため`PROTOCOL_VERSION` 16→17。防衛側視点の「防衛中」表示はバー化せず
  従来通りテキストのまま(表示するチケット数値が元々無いため)
- **HUDのチケットバー上部表示が画面外にはみ出るバグを修正**(2026-08-14): ユーザーから
  「上の表示が上すぎて出てる」と報告。`ConquestHudOverlay.BAR_Y`が`6`固定で、チケットバーの
  上に出す優勢インジケーター(▲/▼/=)やブレイクスルーの「セクターX/Y」表示は`barY - 10`
  (= -4)に描画していたため、画面上端からはみ出て(y座標が負)クリップされていた。
  `BAR_Y`を`18`に上げ、上のラインが`y=8`から始まるようにして画面内に収めた
- **セクター戦闘エリアにcorner1/corner2の歩いて設定する方式を追加**(2026-08-14): 「wandで行けるように
  して sector」と依頼され、`/conquest sector area set <番号>`は既にワンド(右クリック/左クリックで
  2点選択)に対応済みだったため一度AskUserQuestionで確認したところ、実際に欲しかったのは
  zone/boundary/rangeに既にある「歩いて行った場所で1点ずつ`corner1 set`/`corner2 set`」という
  ワンド不要の代替手段だった。`Sector`に`setCombatAreaCorner(dim, corner1, pos)`
  (`ConquestManager.setZoneCorner`と全く同じ「別ディメンションに切り替わったら両方リセット」
  ルール)を追加し、`ConquestManager.setSectorAreaCorner`経由で
  `/conquest sector area corner1 set <番号>` / `corner2 set <番号>`として公開。新規フィールド無し、
  既存の`combatAreaPos1/2`を書き換えるだけ
- **セクター制圧時のチケット回復量を`/conquest config set`で変更可能に**(2026-08-14):
  `ticketsPerSectorCapture`(`BT_TICKETS_PER_SECTOR_CAPTURE`)は既に存在していたconfig値だが
  `ConquestCommand.CONFIG_KEYS`に未登録で、TOML直接編集でしか変更できなかった。ユーザーから
  「セクター制圧時のチケット回復量を決めたい」と依頼され、他の`breakthrough`項目は据え置いたまま
  これだけ`CONFIG_KEYS`に追加(1行、他の数値項目と同じ`intEntry`パターン)
- **ブレイクスルーモードの調整3件**(2026-08-14): ユーザーから「蘇生はあり、間隔スポーンなしで、
  戦闘区域は0が攻め陣・1が戦闘区域・2が守り陣でセクター攻略ごとに1つずつずれるように」と依頼。
  (1) **蘇生を有効なままに**: `SquadFeature.REVIVE`の自動無効化条件を`mode != CONQUEST`
  (TDM・ブレイクスルー両方)から`mode == GameMode.TDM`のみに変更(`start`/`stop`/`endRound`の
  3箇所)。ブレイクスルーのチケット消費(`handleBreakthroughDeath`)はTDMのキル計上と同じく
  `LivingDeathEvent`(本当の死亡)にのみフックしているため、蘇生を有効にしても「ダウン中は
  チケット未消費」になるだけでBFのRush的挙動として自然(TDMのキル計上遅延問題とは事情が違う)。
  (2) **間隔(ウェーブ)リスポーンを廃止し即時リスポーンに**: 従来は攻撃側が死ぬと
  `pendingAttackerRespawns`に貯めて`respawnWaveIntervalSeconds`秒ごとに`releaseAttackerWave`で
  まとめて解放していたが、ユーザー要望で撤廃。`handleBreakthroughRespawn`を「pending なら即座に
  `teleportToRoleSpawn`」に変更し、`releaseAttackerWave`メソッド・`respawnWaveSecondsRemaining`
  フィールド・`BT_RESPAWN_WAVE_INTERVAL_SECONDS`configを削除。`ConquestSyncPacket`から
  `respawnWaveSecondsRemaining`フィールドを削除したため`PROTOCOL_VERSION` 15→16
  (HUDの「次の出撃まで」表示も削除)。`pendingAttackerRespawns`自体は「チケット消費済みで
  即時リスポーンが確定している」フラグとして存続(NBT永続化も維持)。
  (3) **攻め陣/守り陣がセクター前進とともに自動でスライド**: 既存の`Sector.combatArea`
  (`/conquest sector area set`、javadocに「未設定なら次善としてグローバル境界にフォールバック」
  とある通り既に戦闘区域=index1相当は実装済みだった)を**そのまま流用**し、新規フィールドを
  一切増やさずに実現。新規`checkSectorFrontZones`が毎秒、アクティブセクターの
  `sectors.lowerKey`(1つ前、index0相当=攻め陣)と`sectors.higherKey`(1つ先、index2相当=守り陣)
  の戦闘エリアに対し、既存の`checkZoneIntrusion`(自陣ゾーン処刑で使っている private ヘルパー、
  ownerとdim/min/maxを渡すだけの汎用形)をそのまま呼ぶだけ。攻め陣は`owner=attackerTeam`
  (侵入した防衛側を処刑)、守り陣は`owner=defenderTeam()`(侵入した攻撃側を処刑)。セクターが
  進むと`sectors.lowerKey`/`higherKey`の結果が自動でずれるため、シフトのための特別なロジックは
  不要。さらに1つ前(index-1相当、もう攻め陣ですらない)は`tickBoundary`が引き続き
  「アクティブセクターの戦闘エリアの外」として扱うため、ユーザーが要求した「0は戦闘区域外」も
  既存の境界処刑ロジックがそのままカバーする。ビルド成功のみ、実プレイでの動作未確認
- **チームごとの第2スポーン地点**(2026-08-14新規実装): ユーザーから「二個リスポーンポイントを
  設定できるようにして、二個目はテレポートできるだけでいい」と依頼。既存の`spawnA/B`
  (`/conquest spawn set`、ラウンド開始時テレポート先+リスポーン選択肢)とは別に、`spawnA2/B2`を
  新設(`/conquest spawn set2 <a|b>`)。`resolveRoleSpawn`/`teleportToRoleSpawn`側には一切触れず
  (ラウンド開始時テレポートは引き続き1個目のみ)、`ConquestRespawnChoiceProvider`に
  `team_spawn2`という選択肢を追加しただけ(`teleportToTeamBeacon`と同じ「あれば安全地点に
  テレポートするだけ」の最小実装、拠点スポーンのような危険判定は無し)。ワールドNBTへの
  保存/読込は1個目と同様に対応(サーバー再起動で消えないように)。**プリセット
  (`/conquest preset save|load`)には未対応**——`MapPreset`のコンストラクタ引数が増える
  cascadingな変更になるためスコープ外とした。プリセットで1個目のスポーンは復元されるが、
  2個目は復元されない(古い値が残ったまま)点に注意
- **squadtp本体の「募集」タブを分隊単位でグループ表示に**(2026-08-12、squadtp側の変更、
  `PROTOCOL_VERSION`(squadtp側) 2→3): 直前の同一チーム絞り込みの後、ユーザーから改めて
  「squadtp本体の募集タブを分隊ごとにしてほしい」と依頼。それまでの「分隊に参加申請する」欄は
  オンラインの全プレイヤーを1人1行で列挙していたため、同じ分隊のメンバーが複数人いると
  行が重複し、しかも分隊未所属のプレイヤーへの参加申請はサーバー側でどうせ失敗する
  (`squadtp.msg.target_no_squad`)、という無駄のある一覧だった。新規`SquadListPacket`
  (分隊ごとに代表名+全メンバー名のリスト)をサーバーの毎tick位置情報配信と同じ間隔で配信するよう
  `ServerEvents.broadcastSquadList`を追加(オンライン全員へ、ただし自分の所属分隊とチームが
  合わない分隊は`SquadCommand.sameTeam`——直前の修正でpublic化——で除外)。クライアントは
  `SquadClientData`に`joinableSquads`を追加、`SquadScreen.buildJoinRequestList`を
  1プレイヤー1行から1分隊1行(メンバー名カンマ区切り、リーダー★、squadtp-conquest側の一覧と
  同じ見た目)に全面書き換え。分隊に既に所属していても他の分隊への切り替え申請は引き続き可能
  (元の`excludeOwnSquad`引数の役割はサーバー側の除外に統合)。`../squadtp`
  (squadtp-conquestの実依存先)・`../squadtp-1.20.1`(検証用クローン)の両方に同じ差分を
  `git diff`→`git apply`で適用・それぞれコミット。squadtp-conquest側でも
  `--refresh-dependencies`付きビルド+`gradlew printSquadtpCp`で、新しいjar
  (バージョン番号自体は0.2.3のまま、中身だけ更新)が正しく解決される(=Gradleのキャッシュに
  古いjarが居座っていない)ことを確認済み
- **squadtp本体のGUIも同一チームに絞り込み+jarファイル名パターンのビルド設定修正**
  (2026-08-12、squadtp側の変更)。ユーザーがスクリーンショットで、squadtp本体の分隊GUI
  (「募集」タブ)の「招待できるプレイヤー」「分隊に参加申請する」の両リストが、チームに
  関係なく全オンラインプレイヤーを表示していることを提示。`SquadScreen.onlinePlayersExcept`が
  `minecraft.getConnection().getOnlinePlayers()`をチーム条件無しでそのまま使っていたのが原因
  (実際の`/squad invite`・`/squad join`コマンド自体はサーバー側で`sameTeam`チェック済みだが、
  GUIの候補一覧はその制限を反映していなかった)。バニラの`Scoreboard.getPlayersTeam`を
  クライアント側でも比較する`sameTeam(UUID)`を追加し、両リストをこれでフィルタ
  (`requireSameTeam`がfalseなら従来通り絞り込み無し)。**squadtp本体は無改造の方針の例外**として、
  ユーザーの明示的な提示・依頼を受けて実施。`../squadtp`(ローカルの依存先)と、別途用意された
  `../squadtp-1.20.1`(検証用クローン)の両方に同じ修正を適用・コミット(それぞれのリポジトリで
  別コミット)。
  修正作業中、`../squadtp`が(このセッション序盤で懸念していた通り)`main`(NeoForge 1.21.1)
  ブランチのままだったため`1.20.1`ブランチへ切り替え、`fetch`したところ`origin/1.20.1`が
  v0.2.3相当まで進んでいることを確認(以前の「作業が失われたかもしれない」という懸念は解消——
  単にfetchしていなかっただけ)。ビルドし直したところ、squadtp側の最近の変更
  (「ファイル名にMCバージョン/ローダーを含める」コミット)でjar名が`squadtp-0.2.3.jar`から
  `squadtp-1.20.1-forge-0.2.3.jar`に変わっており、squadtp-conquest側の`build.gradle`の
  ivy `patternLayout`(`[module]-[revision].[ext]`)が対応できていなかったことが判明。
  `[module]-${minecraft_version}-forge-[revision].[ext]`に変更して対応(`gradlew
  printSquadtpCp`で実際に解決されるjarを確認できる)。
- **参加可能な分隊一覧をメンバー名で表示**(2026-08-12、`PROTOCOL_VERSION` 14→15): 前項の
  一覧実装直後、ユーザーから「GUIでもやった?」(squadtp本体のGUI側も同じチーム制限が効くか)と
  聞かれ、squadtpの実ソース(`../squadtp-1.20.1`、v0.2.3相当のブランチ)を確認したところ
  `invite`/`accept`/`requestJoin`/`approve`の4経路すべてに`sameTeam`チェックが入っており、
  クライアントはコマンドしか送らない設計(`NetworkHandler`のコメントに明記)のため、GUI固有の
  抜け道は見当たらなかった。続けて「混ざるのはいい」「GUIの調整をしてほしい」「参加可能な分隊を
  分隊ごとに表示してほしい」という流れで、直前に追加した`allSameTeam`除外フィルタ自体は
  そのまま残しつつ、本題は一覧の**表示内容**だったと判明: `SquadStatus`が`memberCount`(人数のみ)
  だったのを`memberNames`(全メンバー名のリスト)に変更し、GUIでは自分の所属分隊一覧と同じ形式
  (メンバー名をカンマ区切り、リーダーに★)で1分隊1行として表示するようにした。
  `ConquestManager.joinableSquadsFor`は`squad.getMembers().values()`をそのまま渡すだけ、
  GUI側は「Your Squad」セクションの描画ロジックと同じ★マーク付けパターンを再利用している。
  パケットのフィールド型が変わった(int→リスト)ため`PROTOCOL_VERSION`を14→15に再度bump
- **GUIから他の分隊を見て参加リクエストを送れるように**(2026-08-12新規実装、`PROTOCOL_VERSION`
  13→14): ユーザーから「squadtpで分隊ごとに見れて参加リクエストを送れるようにしてほしい」と依頼。
  途中「別チームと入れないようにしたい」という話も出たが、これは既存の
  squadtp本体`requireSameTeam`(参加/招待/参加リクエスト時にバニラチーム一致を要求)+
  squadtp-conquest側で以前追加した`leaveSquadIfAny`(チーム切替時に強制離脱)の組み合わせで
  既にカバー済みと判断し、対応不要として見送った(ユーザーからも「別チームはいいよ」と確認)。
  本題は分隊の**発見**手段が無かったこと: squadtpは分隊一覧を返す公開APIを持たず
  (`SquadManager`に`getSquadOf(UUID)`という「特定プレイヤーの所属分隊を引く」メソッドしか無い)、
  クライアント側の`SquadClientData`も自分の分隊の情報しか同期していない。squadtp本体を改造せず
  squadtp-conquest側だけで完結させるため、`ConquestManager.joinableSquadsFor(viewer)`を新設し、
  オンラインの同チームプレイヤー全員について`SquadManager.getSquadOf`を呼んで重複排除
  (コンバットチーム所属かつ自分がまだどの分隊にも入っていない場合のみ)、
  `ConquestSyncPacket`(既存の毎秒配信パケット)に`List<SquadStatus>`(リーダー名+人数)として
  追加。GUIの状況タブでは、既に分隊に入っている場合は従来通り自分の分隊メンバー一覧を表示するが、
  入っていない場合はこの新しいリストを表示し、行ごとに[参加]ボタンを追加。ボタンは
  squadtpの`/squad join <プレイヤー名>`をそのまま送信するだけ(対象は分隊の誰でもよく、
  squadtp側が`getSquadOf`で解決するので、代表としてリーダー名を使っている)——参加ポリシー
  (即時参加/リーダー承認)・同一チーム判定はすべてsquadtp本体の既存ロジックに委ねている。
  一覧の各行にはボタンが付くため(自分の分隊メンバー一覧は元々プレーンテキストでボタン無し)、
  スクロール時に`rebuild()`でボタン位置を再計算する必要がある点が既存の拠点/コールイン一覧と
  同じパターン、旧分隊メンバー一覧とは異なる点として`ConquestScreen`のコメントに明記した。
- **↑の一覧に別チーム混在の分隊が出ないように**(2026-08-12): ユーザーから「別チームは出さない
  ようにしたい」と報告。原因: `joinableSquadsFor`は「同チームのプレイヤーが所属する分隊」を
  拾っていたが、その分隊自体に**別チームのメンバーが混ざっていないか**は確認していなかった。
  squadtpの`requireSameTeam`は参加/招待/参加リクエスト**時点**でしかチームを比較しないため
  (継続監視ではない)、squadtp側のGUI(分隊タブ)経由でこのチェックを迂回して別チームのプレイヤーが
  同じ分隊に混ざるケースがある、とユーザーから以前指摘されていた事象がここでも表面化した形。
  対処として、候補分隊ごとに`squad.getMembers().keySet()`を全員チェックし、1人でも
  `teamOf(member) != viewerTeam`なら一覧から除外するよう`allSameTeam`判定を追加。
  **未検証**: 実プレイで一覧が正しい分隊(自チームの他分隊)を表示するか、[参加]ボタンで
  実際に`/squad join`が送信され、squadtp側の参加ポリシー通りに動作するか、スクロールでボタン位置が
  ずれないか
- **みかんに専用テクスチャ**(2026-08-10): ユーザーが自作の16x16テクスチャ(`mandarin .png`)を提供。
  それまでの「りんごのテクスチャを流用」(2026-08-08の初期実装時の暫定対応)を置き換え、
  `assets/squadtpconquest/textures/item/mikan.png`として追加。`models/item/mikan.json`の
  `layer0`を`minecraft:item/apple`から`squadtpconquest:item/mikan`に変更。このMod初の
  専用アイテムテクスチャ(既存のzone_wand/team_beaconはバニラテクスチャ流用のまま)
- **みかんを食べると即死するように**(2026-08-10新規実装): ユーザーから「mandarinを食べたら死ぬ
  アイテムにして」と依頼。当初みかん(`squadtpconquest:mikan`)は右クリックで投げてブロックを
  破壊するだけのプレーンな`SnowballItem`登録だったため、`Item.use`はバニラの`SnowballItem.use`
  (常に即座に投げる)で完全に上書きされており、そのままでは「食べる」動作(`startUsingItem`→
  一定時間右クリック保持→`finishUsingItem`)を割り込ませる余地が無かった。新規`MikanItem`
  (`SnowballItem`を継承)を作り、`use()`をオーバーライドして**しゃがみ判定で分岐**: しゃがみながら
  右クリックなら`startUsingItem`を呼んで食べる動作を開始、それ以外は`super.use(...)`で従来通り
  投げる(`MikanEvents`の着弾破壊ロジックは`Snowball.getItem()`でアイテムを判定しているだけなので
  無変更で動作継続)。`getUseAnimation`は`EAT`、`getUseDuration`は32tick(バニラ食料と同程度)。
  `finishUsingItem`で`player.hurt(damageSources().genericKill(), Float.MAX_VALUE)`を発生させて
  即死させる——`genericKill()`はバニラの`bypasses_invulnerability`タグに含まれるため、
  squadtpのダウン変換(`ReviveSystem`)を素通りして確実に本当の死亡になる(自陣ゾーン/戦場境界処刑
  ・演習場のTNT誘爆修正と同じ、このプロジェクトで繰り返し使っているパターン)。`ModRegistry.MIKAN`
  の登録を`new SnowballItem(...)`から`new MikanItem(...)`に変更。
  **未検証**: 実プレイでしゃがみ+右クリックで食べるモーションが出て即死するか、しゃがまない通常の
  右クリックは従来通り投げて着弾破壊するか
- **観戦者チーム(`Team.SPECTATOR`)**(2026-08-10新規実装): 直前の「管理人チームでも試合HUDが
  見えるように」対応を受けて、ユーザーから「adminと同等なので別枠で観戦者を」と依頼
  (=試合を見る権利をOP限定の`admin`に混ぜず、誰でも参加できる別のチームとして独立させたい、
  という趣旨と解釈)。`Team.RANGE`追加時に確立したパターンをそのまま再利用:
  `isCombatant()`が自動でfalseを返す(チケット・スコア集計・自陣ゾーン/戦場境界処刑・
  スポット等から追加コード無しで除外)、`shuffleTeams`の除外リストと`isConquestTeam`
  (バニラチーム同期)の2箇所だけ`Team.SPECTATOR`を明示追加。`Team.RANGE`との違いは:
  - 専用エリアへのテレポートは無し。代わりに`/conquest team join spectator`した瞬間
    バニラのスペクテイターモード(`GameType.SPECTATOR`)へ強制切り替え(ノークリップで自由に
    観戦移動でき、誰にも見えず干渉もできない)。他チームへ移ると`GameType.SURVIVAL`に戻す
    (`ConquestManager.joinTeam`、ブレイクスルーの攻撃側ウェーブ待機で既に使われていた
    `setGameMode(GameType.SPECTATOR/SURVIVAL)`と同じパターンを流用)
  - OP不要(`admin`だけが`ConquestCommand.joinTeam`で`hasPermission(2)`チェックの対象、
    `spectator`は素通り)
  - スコアボード画面(右Alt)の「観戦中」欄(`conquest.score.spectating`、元は`admin`のみ表示)に
    `spectator`も含めるよう`ConquestScoreScreen`のフィルタを拡張。`range`は対象外のまま
    (演習場のプレイヤーはこの試合を観戦しているわけではないため)
  - HUD(`ConquestHudOverlay`)の`spectating`フラグに`Team.SPECTATOR`も追加(直前のadmin対応と
    同じ経路でチケットバー・拠点アイコンが見えるようになる)
  - **未検証**: 実プレイで`/conquest team join spectator`した瞬間スペクテイターモードに
    切り替わるか、他チームへ移るとサバイバルに戻るか、HUD・スコアボードに正しく表示されるか
- **管理人チームでも試合HUD(チケットバー・拠点アイコン)が見えるように**(2026-08-10新規実装):
  ユーザーから「adminも試合を見れるようにして pointのアイコンの表示とか」と依頼。
  `ConquestHudOverlay`(常時表示のチケットバー+拠点アイコン列)は
  `!yourTeam.isCombatant()`で丸ごと非表示になっており、管理人チームは何も見えなかった。
  ゲートを`!yourTeam.isCombatant() && !spectating`(`spectating = yourTeam == Team.ADMIN`)に
  緩和し、管理人視点では「自チーム/敵チーム」ではなく**チームAを常に左**に固定表示するよう
  `selfTickets/enemyTickets` → `leftTickets/rightTickets`にリネーム。ブレイクスルーの
  攻撃側/防衛側切り替えロジックも同様に、管理人視点では常に攻撃側チケット+次ウェーブ秒数を表示
  (`showAttackerInfo = spectating || yourTeam == attackerTeam`)。拠点アイコン列自体は元々
  チーム固定色(`Team#hudColor()`)で描画されておりyourTeamに依存していなかったため、ゲートを
  緩めるだけで自動的に正しく表示されるようになった。**`ConquestCaptureOverlay`
  (「占領中/占領されている」の拠点内インジケーター)は意図的に対象外のまま**——
  `activeTeam == yourTeam`という自チーム基準の判定ロジックのため、`yourTeam`がA/Bでない
  管理人には「占領中」か「占領されている」かの区別自体が意味を持たない
  (サーバー側の占領進行度計算自体も`Team.A`/`Team.B`のプレイヤーだけをカウントしており、
  管理人が拠点範囲に入っても占領進行度には一切影響しない——これは元から正しい実装)。
  スコアボード画面(右Alt)は元々「Admins」専用セクションで既に管理人を表示していたため無変更。
  **未検証**: 実プレイで管理人チームでチケットバー・拠点アイコンが正しく表示されるか、
  ブレイクスルーで管理人視点の表示が崩れないか
- **演習場**(`Team.RANGE`、2026-08-10新規実装): ユーザーから「演習場モードを実装してください、
  30分に一回リセットされ演習場チームを追加して参加させてください」「エリアは戦闘区域でいい」と依頼
  (=専用エリアの座標指定方法は既存の戦場境界と同じ2点ボックスでよい、という指示と解釈)。
  ラウンドの状態(`WAITING`/`STARTING`/`IN_PROGRESS`/`ENDED`)やゲームモードから完全に独立した、
  武器・立ち回りを試すための専用エリア+チーム。設計のポイント:
  - `Team`enumに新規`RANGE`を追加(`ADMIN`と`NEUTRAL`の間)。`isCombatant()`が既に`false`を返す
    (A/B以外は全部false)ため、チケット・キル/デス集計・自陣ゾーン処刑・戦場境界処刑・
    スポット等、`isCombatant()`でガードされている既存の対戦要素から**追加コード無しで**自動的に
    除外される(このガードを流用できたのがこの実装が小さく済んだ最大の理由)。一方
    `/conquest team shuffle`の除外リスト・`isConquestTeam`(バニラチーム同期)の2箇所は
    `Team.ADMIN`を個別にハードコードしていたため、`Team.RANGE`も明示的に追加する必要があった
  - エリアは戦場境界(`/conquest boundary`)と全く同じ2点ボックス方式
    (`/conquest range set|corner1 set|corner2 set|remove|list`)。`ConquestManager`に
    `rangeDim`/`rangePos1`/`rangePos2`(NBT永続化)を追加
  - 地形リセットは戦場境界の`StructureTemplate`スナップショット方式をそのまま流用するが、
    対象・タイミングは完全に別管理: `/conquest range set`(または両角が揃った瞬間)に
    「きれいな状態」を撮影し、`rangeResetIntervalSeconds`(既定1800秒=30分、config)ごとに
    自動で復元+在室`RANGE`チームプレイヤーをエリア中央へテレポート・全回復。この周期処理は
    `ConquestManager.tickSecond`の一番先頭(`state`分岐より前)に`tickRange`として追加し、
    ラウンドの状態に一切関係なく毎秒必ず動く。スナップショット自体はNBTに永続化しない
    (戦場境界の既存スナップショットと同じ設計判断)ため、サーバー再起動で失われるが、
    `tickRange`が「範囲は設定済みだがスナップショットが無い」場合に現状を自動的に撮り直す
    自己修復ロジックを入れたので、再起動後に管理者が`/conquest range set`をやり直す必要はない
  - `/conquest team join range`は誰でも参加可(`admin`のようなOP限定にはしていない)。参加した
    瞬間・死亡してリスポーンした時にエリア中央へテレポート(`onRespawn`の一番先頭、
    `state != IN_PROGRESS`のガードより前に`Team.RANGE`分岐を追加)
  - スコープを絞った点(未実装、後で欲しければ追加): 管理用GUI(Lキー)への参加ボタン追加、
    スコアボード画面での`range`チーム専用セクション(現状`admin`セクションには出ない=非表示なだけで
    エラーにはならない)、PvP可否の個別設定(現状は他の全チームと同じくバニラチームの
    フレンドリーファイア無効設定に従うだけ)
  - **未検証**: 実プレイで`/conquest range set`→`team join range`→30分待つ(または
    `/conquest range reset`)で地形とプレイヤー位置が実際にリセットされるか。境界処刑・
    自陣ゾーン処刑・チケット等が`range`チームに一切効かないままか。サーバー再起動後の
    自己修復(スナップショット再撮影)が実際に働くか
- **敵プレイヤーがスポットしなくてもJourneyMapに常時見えてしまう問題 → 対処済み(2026-08-09)。**
  ユーザーから「waypointがない」「スポットが出来ない」と連続で報告。調査の結果、拠点の
  JourneyMapウェイポイント自体は正常表示、スポット機能のコード(キー→コマンド送信→クールダウン
  →`SpotPacket`配信→クライアント保存→JourneyMapウェイポイント表示)も実装上のバグは無く、
  最終的にユーザーから「そもそも敵の位置がスポット無しでも地図から見える」との情報で問題を特定。
  最初は`server.properties`の`entity-broadcast-range-percentage`(バニラの`EntityType.PLAYER`
  トラッキング範囲、既定でおよそ512ブロック相当を全クライアントに配信する設定)を絞る対処を試みたが
  (この設定は今も`run-server/server.properties`で`20`のまま、ただし`run-server/`はgit管理外)、
  **これでは至近距離の敵まで隠すことは原理的にできない**(近くにいる相手を隠すと戦闘自体が
  成立しなくなるため、バニラは近距離のエンティティは必ずトラッキングする)ため効果なしとの報告。
  さらにユーザーから「クライアント設定で敵表示のon/offができると困る(自分でJourneyMapの
  レーダー設定を切り替えて回避されてしまう)」という重要な制約が判明し、サーバー/ネットワーク層
  ではなく**JourneyMap本体のレーダー描画そのものをMod側で強制的に抑制する**方針に転換。
  JourneyMap APIの`journeymap.client.api.event.forge.EntityRadarUpdateEvent`
  (Cancelable、レーダー描画直前に1エンティティずつ発火)を新規`ConquestJmRadarEvents`でフックし、
  `WrappedEntity.getEntityLivingRef()`から実体を取り出してバニラのスコアボードチームが自分と異なる
  プレイヤーを`WrappedEntity.setDisable(true)`でレーダー描画対象から除外する。JourneyMap本体の
  「Radar」オンオフ設定とは別レイヤーでMod側コードが強制するため、プレイヤー自身の
  クライアント設定変更では回避できない。登録は`ConquestJmPlugin.initialize()`
  (JourneyMap自身がプラグインをロードする時にしか呼ばれない、確実にJourneyMap導入時のみの
  フック)から`MinecraftForge.EVENT_BUS.register(ConquestJmRadarEvents.class)`。既存の
  スポットWaypoint表示(`ConquestJmWaypointHandler`)とは別系統のAPI(Radar vs Waypoint)なので、
  スポットされた敵は従来通りWaypointで見える。詳細はREADMEの「索敵マーキング(スポット)」節参照。
  **未検証**: 実プレイでJourneyMapのレーダーから敵チームが実際に消えるか、味方は従来通り映るか、
  スポット時のWaypoint表示に影響が無いか
- **TNTの誘爆が起きないバグ → 対処済み(2026-08-08)。** ユーザーから「TNTの誘爆が発生してくれない」
  と報告。原因: `TerrainDestructionEvents`はBF風クレーター化のため、爆風で影響を受けたブロックを
  バニラの`Explosion.finalizeExplosion()`(ドロップ処理+`Block#wasExploded`呼び出しを経て
  ブロックを消す)を経由せず、`level.setBlock(pos, air/rubble, 3)`で直接上書きしていた。
  `Block#wasExploded`はほとんどのブロックでは何もしないが、`TntBlock`はこれをオーバーライドして
  起爆済みTNTエンティティ(`PrimedTnt`)を生成する処理になっており、これが一切呼ばれていなかった
  ため、爆風に巻き込まれたTNTブロックはただ消えるだけで誘爆しなくなっていた。対処として、
  ブロックをair/ガレキで上書きする直前に`current.getBlock().wasExploded(level, pos,
  event.getExplosion())`を追加(バニラが内部で呼んでいるのと同じフック)。ドロップ処理は
  意図的に元々スキップ(README参照、「アイテムドロップは発生しない」の既存仕様)なのでそのまま。
  **未検証**: 実プレイでTNTの近くでTNT/他の爆発を起こし、誘爆の連鎖が起きるか
- **プリセットに各種ゾーン+破壊禁止ブロックを含める+既定の"Normal"(まっさら)プリセット**
  (2026-08-08新規実装): ユーザーから「Presetに各種ゾーンを含めるように、Presetに何もない
  まっさらな状態をNormalとして登録しといて」、続けて「破壊禁止ブロックも記憶して」と依頼。
  従来`MapPreset`は拠点配置・スポーンA/B・モードのみを保存しており、自陣ゾーンA/B・戦場境界・
  破壊禁止ゾーン・ゲーム内追加の破壊禁止ブロックは対象外だった。`MapPreset`に新規`ZoneBox`
  レコード(dim+corner1+corner2、home zone/boundaryで共通の3フィールド構成)と
  `List<ProtectZone>`(`ProtectZone`は既存の`save()`/`load()`をそのまま流用)、
  `List<String>`(`/conquest protectblock add`でのゲーム内追加分のみ——config既定の
  `indestructibleBlocks`はTOML側の設定なのでプリセット対象外)を追加し、
  `ConquestManager.savePreset`/`loadPreset`もこれらを保存・復元するよう拡張。
  加えて、`ConquestManager`の無引数コンストラクタ(`SavedData.computeIfAbsent`が**新規ワールドの
  初回作成時のみ**呼ぶ、`ConquestManager::new`)で、拠点・スポーン・ゾーンが全て未設定の
  ブランクな`MapPreset`を`"Normal"`という名前で`presets`に登録するようにした。既存ワールド
  (このプロジェクトのテスト用ワールド含む)には遡って追加されない点に注意
  ——`/conquest preset save Normal`を全部クリアした状態で一度手動実行すれば同じものが作れる。
  `get(server)`側では再注入していないので、`/conquest preset remove Normal`で消せば消えたまま
  (毎tick呼ばれる`get()`内で復活させると削除不能になるため、あえて初回作成時のみに限定)
- **`/conquest team join <team> <プレイヤー>`**(2026-08-08新規実装): ユーザーから「teamを他人でも
  加入させられるように」と依頼。従来の`/conquest team join <team>`(実行者自身のみが対象、
  `ctx.getSource().getPlayerOrException()`)に、末尾に`player`引数(`EntityArgument.player()`)を
  追加した形の別ルートを新設し、OP限定(`.requires(hasPermission(2))`)で他プレイヤーを指定
  チームへ参加させられるようにした。内部の`ConquestManager.joinTeam`自体は無変更(元々
  `ServerPlayer`を受け取るだけで実行者かどうかを問わない実装だった)。対象プレイヤーにも
  アクションバーで参加を通知する(`conquest.msg.team_joined`を`target.displayClientMessage`で送信)。
  実行者向けの成功メッセージは新規`conquest.msg.team_joined_other`
- **`/conquest boundary restore`**(2026-08-08新規実装): ユーザーから「Conquestで途中中断しても
  地形が巻き戻されるコマンドを作成して」と依頼。`/conquest stop`は仕様として自動復元をスキップする
  (停止直後の被害状況確認用、`endRound`とは別経路——README参照)ため、そのままでは`stop`後は
  誰かが手動で戦場境界を直さない限り地形が壊れたままだった。既存の`restoreTerrainSnapshot`
  (`endRound`専用のprivateメソッド)を`ConquestManager.restoreTerrain(server)`として公開し、
  スナップショットが無ければ`false`を返す形に。新規コマンドはこれを呼ぶだけの薄いラッパー
  (`ConquestCommand.boundaryRestore`)。既存の地形スナップショット/復元の仕組み
  (`captureTerrainSnapshot`/`terrainSnapshot`フィールド、`/conquest start`時に撮影・
  `endRound`か本コマンドで消費)をそのまま流用しており、新規ロジックはゼロ
- **みかん**(`squadtpconquest:mikan`、2026-08-08新規実装): ユーザーから「みかんを投げてブロックを
  破壊できるようにして、破壊できない/できるブロックの仕組みを流用して」と依頼。投げると着弾した
  ブロックを1つ破壊するアイテム。実体はバニラの`SnowballItem`をそのまま`mikan`として登録しただけ
  (見た目はりんごのテクスチャを流用、新規テクスチャなし)、新規エンティティ・レンダラーは追加せず、
  `MikanEvents`が`ProjectileImpactEvent`をフックして着弾した雪玉が`Snowball.getItem()`で
  `mikan`かどうかを判定してから破壊する方式。破壊可否は`BlockProtectionEvents`と同じ
  `isIndestructible`/`isProtected`判定をそのまま流用しており、破壊禁止ブロック/エリアは
  みかんでも壊せない。`/give`でのみ入手、クリエイティブタブ非掲載(zone_wand/team_beaconと同じ扱い)。
  **未検証**: 実プレイでみかんが命中ブロックを破壊するか、破壊禁止ブロック/エリアには効かないか
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
- **試合終了時の集合**(2026-08-07新規実装): `/conquest gather set|remove|list`で1箇所(チーム別で
  はなく共通)設定でき、`endRound()`(`ENDED`遷移直後、結果タイトル表示と同じタイミング)で
  チームA/B全員をそこへテレポートする。既存の`spawnADim`/`spawnAPos`と全く同じ形の
  `gatherDim`/`gatherPos`フィールド+NBT永続化(`GatherDim`/`GatherPos`キー)、テレポート自体は
  `TeleportHelper.findSafeSpot`を1回だけ呼んで全員同じ着地点へ(チームリスポーンビーコン等と
  同じ安全地点探索ヘルパーを再利用)。未設定なら何もしない(後方互換、既存のラウンド終了挙動は
  無変更)。ネットワークパケットの変更は無い(サーバー側のみで完結する機能のため
  `PROTOCOL_VERSION`は据え置き)
- **破壊禁止ブロックのゲーム内管理**(2026-08-07新規実装): `/conquest protectblock add|remove|list`で、
  地形破壊が壊さないブロック種類を、TOML編集無しでゲーム内から追加/削除できるようにした。
  既存の`indestructibleBlocks`(config)はそのまま「常時有効な既定リスト」として残し、
  新規`ConquestManager.protectedBlocks`(`LinkedHashSet<String>`、NBT永続化、`ProtectZones`と
  同じ並びに追加)が追加分を保持。両者は`ConquestManager.isIndestructible(BlockState)`で
  ORされる(configにあるものはこのコマンドでは解除できない設計 — 意図的な非対称性)。
  `TerrainDestructionEvents`側は`Config.INDESTRUCTIBLE_BLOCKS.get().contains(...)`の直接参照を
  `manager.isIndestructible(current)`呼び出しに置き換えただけ。ブロックIDは
  `ResourceLocationArgument`+`ForgeRegistries.BLOCKS.containsKey`で検証(コールインのアイテムID
  検証と同じパターン)
- **地形の復元方式をスナップショット方式に全面置き換え**(2026-08-07): 従来の「爆発1つ1つを
  `recordDestroyedBlock`で個別記録→`/conquest start`で個別に戻す」差分追跡方式
  (`ConquestManager.destroyedBlocks`)を完全に削除し、代わりにバニラの構造物ブロックと同じ
  `StructureTemplate`(`fillFromWorld`/`placeInWorld`)で**戦場境界(`/conquest boundary`)全体を
  ラウンド開始時に丸ごとスナップショット→ラウンド終了時(`endRound`、集合地点テレポートの直後)に
  丸ごと貼り戻す**方式にした。ユーザーからの明示的な要望(「開始した時のデータを保存して終了したら
  それに戻す」)。差分追跡方式は直前に見つかった「STARTING中の爆発が記録されない」バグの根本原因
  そのもの(=個々のイベントを正しく拾えているかに依存する脆さ)だったため、そもそも個別記録が
  不要な設計に置き換えることでこのクラスのバグごと解消した。新規`terrainSnapshot`/
  `terrainSnapshotDim`/`terrainSnapshotOrigin`フィールド(NBT非永続化、ラウンドスコープ)。
  境界が未設定なら何もしない(オプトイン、集合地点機能と同じ扱い)。エンティティは対象外
  (`includeEntities=false`)。境界外の破壊は対象外、境界が非常に広い場合は開始/終了時に
  一括処理コストがかかる(上限キャップ無し)という新たなトレードオフをREADME.mdに明記した。
  クレーター生成ロジック自体(`TerrainDestructionEvents`のair/rubble振り分け、
  `isIndestructible`/`isProtected`によるラウンド中の破壊禁止判定)は無変更
- **最大HPをconfigで調整可能に**(2026-08-07新規実装): 新規`maxHealth`(既定20.0、バニラ標準)。
  TACZ/SuperbWarfare等の銃Modのダメージ量スケールに合わせたTTK調整用。バニラの
  `Attributes.MAX_HEALTH`属性を直接操作する新規`ConquestManager.applyMaxHealth(player, team)`を
  3箇所で呼ぶ: (1)`joinTeam`(ただし実際にチームが変わった時のみ — 同じチームへの
  `/conquest team join`連打でヒールを繰り返せる抜け穴を防ぐガード)、(2)`teleportToSpawns`
  (ラウンド開始時の一括ループ、こちらは無条件で全参加者に適用 — config変更後も次ラウンドで
  確実に反映される)、(3)`onRespawn`(2026-08-08追加、下記バグ対処)。管理人チームでは常に
  バニラの20に戻す。`/conquest config set maxHealth <値>`にも対応(`CONFIG_KEYS`に登録済み、
  再起動なしで変更可)
- **最大HP: 死亡後リスポーンするとバニラの20に戻ってしまうバグ → 対処済み(2026-08-08)。**
  ユーザーから「ヘルスを死んだあとでも指定した(値の)にして」と報告。原因は`applyMaxHealth`が
  `joinTeam`とラウンド開始時にしか呼ばれておらず、`ServerEvents.onPlayerRespawn`→
  `ConquestManager.onRespawn`のリスポーン処理では未呼び出しだったこと(リスポーンで生成される
  プレイヤーエンティティはバニラのデフォルト最大HPに戻る)。対処として`onRespawn`内、
  チームがcombatantと判定した直後に`applyMaxHealth(player, team)`を追加。**実プレイ未検証**
- **破壊禁止ブロック/エリアを通常の破壊からも保護**(2026-08-07新規実装): ユーザーから
  「指定した破壊できないブロックはクリエの破壊以外だと壊れないようにした?」と質問され、当時は
  爆発(`TerrainDestructionEvents`)からしか保護していなかったことが判明。新規
  `BlockProtectionEvents.java`が`BlockEvent.BreakEvent`をフックし、`isIndestructible`/
  `isProtected`に該当するブロックはクリエイティブモード以外では一切壊せないようにした
  (`player.isCreative()`のみ例外扱い)。地形破壊のクレーター生成(`terrainDestructionEnabled`・
  ラウンド進行状況に依存)とは別の関心事として扱い、**ラウンドの状態に関係なく常時有効**
  (旗ブロック等のマップ構成要素を守る目的のため)
- スコアボード(右Alt)2ページ目: 累計スコア+K/D比率
- HUD/GUIのチーム色を自分/敵視点から**チーム固定色**(A=青・B=赤)に変更
- 管理用GUI(Lキー)・BF風HUD(常時表示)・adjustable config(`/conquest config set`)

## ⚠️ 既知の問題・積み残し

**地形破壊: `maxBlocksPerExplosion`の上限を超えた分は破壊禁止判定自体をすり抜けるバグ → 対処済み
(2026-08-07)。**
- 報告: ユーザーから「SuperbWarfareのRPGなどの破壊でも破壊しないようにして」と依頼。squadtp本体でも
  squadtp-conquestでもなく、SuperbWarfare側のキャッシュ済みjar(`com.atsuishio.superbwarfare.tools.
  CustomExplosion`、`net.minecraft.world.level.Explosion`を継承)をバイトコード解析
  (`javap -p -c -constants`)して原因調査。`ForgeEventFactory.onExplosionDetonate`は正しく
  呼ばれており(`explode()`内)、`ExplosionEvent.Detonate.getAffectedBlocks()`は
  `Explosion.getToBlow()`への生参照(コピーではない)であることも確認できたため、
  `TerrainDestructionEvents`側の`affected.removeIf(...)`によるミューテーションは理論上
  `finalizeExplosion()`(`getToBlow()`を読み直す実装)にも反映されるはず——という所までは
  問題なかった。実際の原因は別にあった: 旧実装は`maxBlocksPerExplosion`(既定200、爆心に近い順)で
  影響ブロックを先に絞り込んでから破壊禁止チェックをしていたため、**上限を超えた分は
  破壊禁止チェック自体が一切実行されずバニラの通常破壊に素通りしていた**。TNT(バニラ既定半径4、
  影響ブロック数が200を超えにくい)では顕在化しにくく、SuperbWarfareのRPG(TNTよりずっと広い範囲に
  影響が及ぶ)で初めて実害が出たと考えられる
- 対処: 破壊禁止判定(`isIndestructible`/`isProtected`)を影響ブロック全体に対して**上限とは無関係に
  先に**適用し(`affected.removeIf(...)`で保護対象を`toBlow`から完全除去)、その後で
  クレーター化(air/ガレキ化、`setBlock`を伴う本当にコストのかかる部分)だけを
  `maxBlocksPerExplosion`で頭打ちにするよう順序を入れ替えた。爆発のサイズに関わらず
  保護は必ず効くようになった
- **未検証**: 実プレイでSuperbWarfareのRPGが実際に破壊禁止ブロック/エリアを壊さなくなったかの確認は
  まだ行われていない

**破壊禁止ブロック/エリアの通常破壊からの保護も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- サバイバルモードで、破壊禁止ブロック(`indestructibleBlocks`/`/conquest protectblock`追加分)・
  破壊禁止エリア(`/conquest protectzone`)内のブロックが実際に壊せなくなっているか
- クリエイティブモードなら従来通り壊せるか
- ラウンドが`WAITING`中(進行していない時)でも保護が効くか(意図通り常時有効であること)
- `terrainDestructionEnabled`をfalseにしても、この破壊阻止自体は効き続けるか(意図通り独立)

**最大HPのconfig化も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- `maxHealth`を変更後、`/conquest team join a`でハート表示が正しく伸びる/縮む(バニラ20→変更後の値)か、
  フルヒールされるか
- 既にチームに所属中のプレイヤーも`/conquest start`で最新のconfig値に更新されるか
- `admin`チームに移るとバニラの20に戻るか
- 同じチームへ`/conquest team join`を連打してもヒールを繰り返せない(ガードが効いている)か
- `/conquest config set maxHealth <値>`が再起動なしで反映されるか
- 死亡してリスポーンした後もハート表示が変更後の値のままか(2026-08-08修正、上記バグ対処)

**地形破壊: STARTING中(開始カウントダウン中)の爆発が復元されないバグ → 対処済み(2026-08-07)。**
- 報告: 実プレイでユーザーが「破壊した地形が戻らない」と報告。原因調査の結果、
  `TerrainDestructionEvents.onDetonate`が`RoundState.IN_PROGRESS`の時しか介入しておらず、
  `STARTING`(`/conquest start`直後の「Get Ready!」カウントダウン中)に起きた爆発は完全にバニラの
  ままだったことが判明(クレーター化されない=`recordDestroyedBlock`も呼ばれない=
  復元対象として記録されない=永久にそのまま)。ユーザーは「サーバー再起動していないのに
  `/conquest start`しても戻らない」と報告し、さらに聞き取りで「STARTING中に壊した」ことが
  判明して特定できた
- 対処: ガード条件を`STARTING`も含むように拡張(`state != STARTING && state != IN_PROGRESS`
  なら素通し)。`Config.java`の`terrainDestructionEnabled`コメント、README.mdの説明文も
  「`IN_PROGRESS`の間」→「`STARTING`または`IN_PROGRESS`の間」に修正
- **重要: この修正は今後の爆発にのみ効く。ユーザーが実際にテストで壊してしまった地形(バニラの
  ままクレーター化されず記録もされていない)は遡って直せない**(記録が存在しないため)。
  手動での地形修復(WorldEdit等)が必要である旨を伝達済み
- **その後(同日)このガード拡張ごと不要になった**: ユーザーの要望で復元方式自体を
  差分追跡(`recordDestroyedBlock`)からスナップショット方式(`StructureTemplate`)へ全面置き換え
  (上記「地形の復元方式をスナップショット方式に全面置き換え」参照)。新方式は個々のイベントを
  正しく拾えているかに依存しないため、このバグのクラス自体が構造的に起こり得なくなった。
  このガード拡張自体は無害なので戻していないが、実質的な意味は失っている

**破壊禁止ブロックのゲーム内管理も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- `/conquest protectblock add <ブロックID>`で追加したブロックが実際に爆発で壊れなくなるか
- `/conquest protectblock remove`でconfig既定のブロック(`minecraft:bedrock`等)を指定すると
  ちゃんと拒否される(解除できない)か
- `/conquest protectblock list`がconfig分・ゲーム内追加分を両方正しく表示するか
- 存在しないブロックIDを指定した時に`conquest.msg.unknown_block`で弾かれるか
- サーバー再起動後もゲーム内追加分がNBTから復元されるか

**試合終了時の集合も実プレイ未検証(ビルド成功のみ)。** 次回確認が必要な点:
- `/conquest gather set`で設定後、実際にラウンド終了の瞬間チームA/B全員がその場所へ飛ぶか
- `TeleportHelper.findSafeSpot`で全員が同じ着地点付近に安全に降りるか(窒息・落下等が無いか)
- 観戦チーム`ADMIN`が対象外のままであること
- `/conquest gather remove`後は従来通りその場に留まる(テレポートされない)こと
- ワールド跨ぎ(集合地点が別ディメンション)でも正しくテレポートされるか
- `/conquest gather list`未設定時のメッセージ、設定後の座標表示が正しいか

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

**AEDが敵チームのプレイヤーも蘇生できてしまうバグ → 対処済み(2026-08-08)。**
- 報告: ユーザーから「AEDが敵でも蘇生できてしまう」と報告。
- 根本原因: squadtp本体の蘇生許可判定(`ReviveSystem.handleInteract`)はコンクエストのチームを
  一切知らず、蘇生者が対象の**squadtp squadのメンバーかどうか**だけを見る
  (`allowNonSquadRevive`既定false、`requireSameTeam`既定trueでsquad参加自体は同一バニラチーム
  限定)。ただし`requireSameTeam`はsquad**参加/作成時**にしか効かず、参加後にコンクエストの
  チームだけ変わってもsquad側は追従しない。`ConquestManager.shuffleTeams`はチーム一括入れ替え時に
  `disbandSquadsOf`で対象プレイヤー全員のsquadを解散してから再編成しているが、個別の
  `/conquest team join`(→`joinTeam`)経由のチーム変更ではsquadに一切触れていなかった。そのため、
  同じsquadでチームAにいた2人のうち片方だけ`/conquest team join b`でチームBへ移ると、squadtp上は
  依然として同じsquadのままなので、コンクエスト上は敵同士になった後もAEDで蘇生し合えてしまっていた
- 対処: `ConquestManager.joinTeam`にチームが実際に変わった時だけ呼ばれる新規`leaveSquadIfAny(player)`
  を追加し、`SquadManager.removeMember`(squadtpの公開API)でそのプレイヤーだけを現在のsquadから
  離脱させるようにした(squad全体を解散する`disbandSquadsOf`と違い、残りのメンバーには影響しない)。
  squadtp本体は無改造のまま
- **未検証**: 実プレイで、チームを個別に切り替えた後にAEDで元squadメンバー(現在は敵)を蘇生
  できなくなったか、蘇生失敗メッセージ(`squadtp.msg.revive_not_allowed`)が出るかの確認が必要

**`/conquest spawn set`が実際のリスポーンには使われていなかった → 対処済み(2026-08-08)。**
- 報告: ユーザーから「Aスポーン Bスポーンを設定したときスポーンポイントも設定してほしい」と報告。
- 根本原因: `/conquest spawn set`で登録した`spawnA`/`spawnB`は`teleportToSpawns`(ラウンド開始時の
  一括テレポート、`ConquestManager`内)からしか参照されておらず、死亡後の実際のリスポーンでは
  一切使われていなかった。死亡後リスポーンは`ConquestRespawnChoiceProvider`がチームリスポーン
  ビーコン・(コンクエストのみ)保有拠点を選択肢としてsquadtpのリスポーン選択画面に出すだけで、
  どちらも無ければ(ビーコン未設置・拠点なしのTDM等)バニラのベッド/ワールドスポーンに戻っていた
- 対処: ラウンド開始時のテレポート先解決ロジック(`teleportToRoleSpawn`、ブレイクスルーでは
  アクティブセクターの攻撃側/防衛側スポーンを`spawnA`/`spawnB`より優先)を`resolveRoleSpawn`として
  切り出し、`ConquestRespawnChoiceProvider.getChoices`から呼んで「チームスポーン」という新規選択肢
  (`conquest.gui.respawn_choice_team_spawn`)として提示。選ばれたら同じ`teleportToRoleSpawn`で
  テレポートする(`onChosen`)。squadtp本体は無改造のまま
- 設計上の割り切り: squadtpのリスポーン選択画面はプレイヤーが能動的に選ぶ方式(ビーコン/拠点も同様)
  なので、これも「制限時間内に選ばないとバニラスポーンのまま」という既存の挙動を踏襲している
  ——`/conquest spawn set`を設定するだけで自動的にそこへ飛ばす動作ではない点に注意
- **未検証**: 実プレイで、死亡後のリスポーン選択画面に「チームスポーン」が表示され、選択すると
  `/conquest spawn set`(またはブレイクスルーのセクタースポーン)の位置へ実際にテレポートするか

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
- `maxBlocksPerExplosion`超過時(大量TNT同時起爆等)にラグ・クラッシュしないか
- 自陣ゾーンと同じワイヤーフレームパーティクルで破壊禁止ゾーンが視認できるか
- **(新)地形の自動復元(スナップショット方式)**: `/conquest boundary set`で範囲設定後、
  STARTING中・IN_PROGRESS中を問わず境界内で起きた破壊が、ラウンド終了(結果タイトル表示・
  集合地点テレポートと同じタイミング)で確実に元へ戻るか。境界の外の破壊は戻らない(想定通り)ことも
  合わせて確認。境界未設定時は何も起きない(エラーも出ない)ことの確認。境界が広い場合の
  開始/終了時のラグの体感(上限キャップ無しなので特に確認したい)。`/conquest stop`では
  従来通り復元されないことの確認

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
  `squadtp_version`(末尾のバージョン番号部分)と一致させること。不一致だと
  `Could not find uk.iwaservice:squadtp:x.x.x`でビルド失敗する(このセッション中も
  0.1.2→0.1.3→0.1.4、後に命名規則自体の変更で再度、と複数回発生)。ivyの
  `patternLayout`(`build.gradle`)はsquadtp側の`archivesName`規則
  (`squadtp-${minecraft_version}-forge-[revision].[ext]`)に合わせてあるので、squadtpが
  MCバージョン/ローダー名を変えない限りバージョン番号の更新だけで済むはず
  (2026-08-12、squadtp側のファイル名変更で一度この前提が崩れて対応した)。
  `gradlew printSquadtpCp`で実際に解決されているjarを確認できる
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
   索敵マーキング(スポット)、試合終了時の集合、破壊禁止ブロックのゲーム内管理、地形の
   スナップショット復元、最大HPのconfig化、破壊禁止ブロック/エリアの通常破壊からの保護、
   SuperbWarfare RPG等の大きい爆発での破壊禁止判定の実プレイテスト(上記「未検証」参照、最優先。
   稼働中サーバー/クライアントは新jarでの
   **再起動が必要**な変更を含むので注意)
2. ブレイクスルーモードの実プレイテスト(2人以上、上記の未検証項目を中心に)
3. TDMキル計上修正が実プレイで効いているかの確認(上記参照)
4. TODO.mdの「優先度高」(スポーン安全確認)
5. TODO.mdの「未検証」項目全般(実プレイでの動作確認がまだ大半未実施)
