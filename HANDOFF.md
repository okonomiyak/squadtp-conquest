# squadtp-conquest 引き継ぎメモ (2026-07-25 更新)

前回(2026-07-21)からの主な変化: マッププリセット機能(`/conquest preset`)追加、
そして本日ブレイクスルーモード(攻撃側/防衛側非対称・複数セクター)を新規実装。
実装の副産物として、長らく棚上げだったTDMキル計上バグの根本原因への対処
(squadtp蘇生システムの自動無効化)も入った。詳細は下記および**README.md**参照。

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
- スコアボード(右Alt)2ページ目: 累計スコア+K/D比率
- HUD/GUIのチーム色を自分/敵視点から**チーム固定色**(A=青・B=赤)に変更
- 管理用GUI(Lキー)・BF風HUD(常時表示)・adjustable config(`/conquest config set`)

## ⚠️ 既知の問題・積み残し

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
  上げること。現在値は`10`(ブレイクスルー実装で`8`→`9`、GUIセクター管理追加で`9`→`10`)。
  上げ忘れると新旧クライアント/サーバー混在時に`IndexOutOfBoundsException`で原因不明の切断が起きる
- **サーバー多重起動**: `runServer`を起動したまま次の`runServer`を叩くとポート競合で失敗する。
  `Get-NetTCPConnection -LocalPort 25565`(または`Get-CimInstance Win32_Process -Filter "Name='java.exe'"`)
  で残留プロセスを確認してから起動すること
- **squadtp本体は改造しない**: 公開API(`SquadManager`/`Squad`/`ReviveSystem`/`TeleportHelper`)
  経由の読み取り専用利用のみ。squadtp側のコード・configに手を入れたことは一度もない

## 次にやること候補

1. ブレイクスルーモードの実プレイテスト(2人以上、上記の未検証項目を中心に)
2. TDMキル計上修正が実プレイで効いているかの確認(上記参照)
3. TODO.mdの「優先度高」2件(拠点からのリスポーン選択・スポーン安全確認)
4. TODO.mdの「未検証」項目全般(実プレイでの動作確認がまだ大半未実施)
