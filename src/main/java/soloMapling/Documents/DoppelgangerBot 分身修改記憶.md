# 分身（DoppelgangerBot）修改記憶點

> 目的：完整記錄「分身」功能的改動、影響、新增，供未來修改 bot 時參考。
> 專案：SoloMapling（Java 伺服器 + JS 腳本）。JDK21，編譯：
> `cmd.exe /c "set JAVA_HOME=...\jdk-21&& mvnw.cmd -q -DskipTests compile"`

---

## 一、功能總覽

「分身」＝玩家綁定的**臨時伴侶假人**，獨立於本體假人（SocialBot / TrainingBot / FollowerBot）：

- **跟隨隊長**（跨圖跟隨、計程車閃現）—— 繼承 `FollowerBot` 的 `GCMovement.follow`。
- **每 2 秒打怪**，目標分散（每隻分身打不同怪）。
- **BOSS 協作**：地圖有 BOSS → 全體打 BOSS；怪少於分身數 → 自然重疊協作。
- **取消僱傭 → 立即刪除**：結束生命週期、不留伺服器紀錄、不 fallback 轉職。

**隔離原則（第一優先）**：不影響本體假人的行動模式，只改「最少必要」的本體程式。

---

## 二、新增檔案（3 個）

### 1. `src/main/java/soloMapling/ArtificialPlayer/BotTypes/DoppelgangerBot.java`

分身 AI，**繼承 `FollowerBot`**（重用跟隨 FSM），覆寫 3 個方法：

| 成員 | 說明 |
|---|---|
| `ATTACK_TICK_MS = 2000` | 2 秒打怪週期 |
| `lastAttackMs` | 打怪節流（**不用改 `checkPrioritySpeed`**，見陷阱） |
| `updateState()` | `super.updateState()`（跟隨 FSM）→ 檢查隊長同圖 → 節流打怪 |
| `attack(chr)` | 拿地圖活怪；有 BOSS → 打 BOSS；否則 `Math.floorMod(chr.getId(), mobs.size())` 分散目標 |
| `moveToward(chr, target)` | `GCMovement.move(chr, x, y)` 移向目標（讓目標變「最近怪」） |
| `fallbackConvert()` | **覆寫為刪除**：`botLeaveParty` → `GCMovement.stop` → `setRunning(false)` → `stopScheduledTask` → `removeBotFromServer` |

關鍵行為：
- 打怪前先 `resolveLeader()` 確認**與隊長同地圖**（換圖立刻交給跟隨，不打怪）。
- `botType = "DoppelgangerBot"`（寫在建構子，用於隔離判斷）。
- `dialoguePath = "FollowerBotDialogue.yaml"`（沿用，無對話需求時 `sayNode` 靜默失敗）。

### 2. `src/main/java/soloMapling/ArtificialPlayer/BotTypes/DoppelgangerManager.java`

招募／踢除管理器（`final` + 私有建構子，純 static）。4 個入口：

| 方法 | 說明 |
|---|---|
| `recruitSingle(player, baseClass)` | `createBot` → `joinLeaderParty`（**先入隊**）→ `convertBotType(DOPPELGANGER_BOT)`。滿 6 人則拒絕 |
| `recruitFill(player)` | 職業輪流招滿（`{1,2,3,4}` 循環，最多補到 6 人） |
| `kickBot(botId)` | `getChr` → 驗證 `isDoppelganger` → `deleteBot` |
| `kickAll(player)` | 遍歷隊伍成員，收集分身 → `deleteBot` |

私有輔助：
- `deleteBot(bot)` = `botLeaveParty` + 停 FSM + `removeBotFromServer`。
- `isDoppelganger(botId)` = `CharacterStorage.getAllBots().get(botId).getBotType().equals("DoppelgangerBot")` —— **隔離關鍵**，不誤傷本體。
- `joinLeaderParty(leader, bot)` = `Party.createParty(leader,false)`（若無隊）+ `Party.joinParty(bot, partyId, false)`。

**職業 baseClass 對應**（`BotDecorate.selectJobForClass`）：`1=戰士 / 2=法師 / 3=弓手 / 4=飛俠`（v83 無海盜）。

### 3. `scripts/Special/BotRecruitManager.js`

4 選項 NPC 選單，經 `Java.type('soloMapling.ArtificialPlayer.BotTypes.DoppelgangerManager')` 呼叫 manager：
- `#L0` 招募單兵（自選職業，二級選單）
- `#L1` 隨機招滿 → `recruitFill(cm.getPlayer())`
- `#L2` 踢除指定 ID → `cm.sendGetText` + `cm.getText()` + `kickBot(parseInt(...))`
- `#L3` 全隊剔除 → `kickAll(cm.getPlayer())`

---

## 三、修改檔案（3 個）

### 1. `src/main/java/soloMapling/ArtificialPlayer/BotTypeManager.java`

- **新增 import**：`import soloMapling.ArtificialPlayer.BotTypes.DoppelgangerBot;`
- **新增 enum 值**（仿 `FOLLOWER_BOT`）：
```java
DOPPELGANGER_BOT {
    @Override
    public void createAndSetBot(Character character) {
        DoppelgangerBot bot = new DoppelgangerBot(character);
        CharacterStorage.addActiveBot(character.getId(), bot);
    }
},
```

### 2. `src/main/java/soloMapling/ArtificialPlayer/BotTypes/FollowerBot.java`

**僅改存取權限，不改行為**（讓子類可覆寫/讀取）：
- `private void fallbackConvert()` → `protected void fallbackConvert()`
- `private Character resolveLeader()` → `protected Character resolveLeader()`

### 3. `scripts/npc/9010004.js`

- 選單項 `#L6`：`敬請期待…` → `#dBOT招募管理（分身）#k`。
- `doSelect` 加 `case 6: openNpc("BotRecruitManager"); break;`

---

## 四、關鍵 API 契約（未來改 bot 必看）

| API | 簽名 | 用途 / 注意 |
|---|---|---|
| `BotGeneration.createBot(pos, map, baseClass, minLevel, maxLevel)` | 回傳 `int botId` | 建立 bot Character（同步註冊），回傳 `BOT_BASE_ID + counter` |
| `BotGeneration.removeBotFromServer(chr)` | void | 刪除：map/channel/world/`CharacterStorage.removeActiveBot`/buff。**不離隊、不停排程** |
| `SoloMaplingUtilities.getChr(int id)` | 回傳 `Character` | `mainChannel.getPlayerStorage().getCharacterById(id)`。**id 已含 20000，直接查** |
| `BotTypeManager.convertBotType(chr, BotType)` | boolean | stop 舊 FSM → `createAndSetBot` → `manuallyStartBot` |
| `BotTypeManager.manuallyStartBot/StopBot(chr)` | void | 啟動/停止 FSM（start 含 spawn 延遲；stop 含 `stopScheduledTask`） |
| `Party.createParty(player, silent)` / `Party.joinParty(chr, id, silent)` | boolean | 建立/加入隊伍 |
| `BotPartyCommands.botLeaveParty(chr)` | void | **伺服器端乾淨離隊**（LEAVE/DISBAND + `setParty(null)`），無 party 則跳過 |
| `GCMovement.follow/move/stop/isFollowing(chr, ...)` | — | 跟隨/移動/停止。`move(chr,x,y)` 設移動路徑（非瞬移） |
| `BotAttackDriver.botAttack(chr)` | `AttackResult` | **只轉向最近怪+攻擊，不移動**。移動需另用 `GCMovement` |
| `Monster.isBoss()/isAlive()/getPosition()` | — | 怪判定；`Point.distanceSq(Point)` 算距離 |
| `map.getAllMonsters()` | `Collection<Monster>` | 全圖怪 |
| `BotSM.getBotType()` / `CharacterStorage.getAllBots().get(id)` | — | **判斷 bot 型別（隔離用）** |
| `BotHelpers.isBot(chr)` | boolean | 認得所有 bot（含分身，免 autoban） |

**ID 常量**：`BOT_BASE_ID = 20000`、`WORLD_SCANIA = 0`、`CHANNEL_1 = 1`（`getChannel(0,1)` == `mainChannel`）。

---

## 五、重要陷阱（gotchas，改 bot 前必讀）

1. **`removeBotFromServer` 不停排程、不離隊** —— 刪除前必須手動 `stopScheduledTask()` + `setRunning(false)`，且先 `botLeaveParty()`（否則 party 留 ghost member）。`World.removePlayer` 不會觸發離隊。
2. **doInit 解析隊長的時序** —— `FollowerBot.doInit` 從「pending leader」或「隊伍」解析隊長。**先 `joinLeaderParty` 再 `convertBotType`**，否則轉型後 doInit 找不到隊長。
3. **打怪節奏不要改 `checkPrioritySpeed`** —— 覆寫它會拖慢整個 FSM（含跟隨）。打怪 2 秒節奏用 `lastAttackMs` 在 `updateState` 內節流即可。
4. **`botAttack` 只轉向+攻擊，不移動** —— 想讓 bot 靠近怪，要用 `GCMovement.move`。「分散目標」是：每 tick 移向自己分到的怪，數 tick 後該怪變「最近怪」才被打到（近似，非硬性鎖定）。
5. **`getChr(int)` 直接查 `getCharacterById`，不要 +20000** —— `createBot` 回傳的 `botId` 已含 `BOT_BASE_ID(20000)`。`BotHelpers.getCharFromChannelStorage` 則會對 `<1000` 的值 +20000，兩者語義不同。
6. **`convertBotType` 的 `createAndSetBot` 用 `addActiveBot` 註冊** —— 新建 bot 不是 active bot（`createBot` 只註冊 channel/world），`convertBotType` 的 `getBotById` 會回 null，這是正常的（跳過 stop 舊 FSM）。
7. **隔離判斷必須用 `getBotType()`，不是 `Character` 的方法** —— `getBotType()` 在 `BotSM` 上；要拿 `CharacterStorage.getAllBots().get(id)` 再取型別。

---

## 六、影響範圍

- **不影響本體假人行動模式**：唯一動到的本體程式是 `FollowerBot` 兩個方法 `private`→`protected`（純存取權限）。
- **`BotHelpers.isBot` 認得分身** → 分身免 autoban；但本體社交招募/對話不會碰它（靠獨立 type 區分）。
- **分身有獨立生命週期**：取消僱傭（離隊/踢除）→ `fallbackConvert` 或 `deleteBot` → 徹底刪除，不轉 TrainingBot/SocialBot、不留紀錄。
- **on-demand**：不依賴 `SPAWN_BOTS_ON_STARTUP`，腳本呼叫 `createBot` 即生成。

---

## 七、未完成（後續可選）

1. **收費**：玩家招募分身尚未扣費（參考 V95 `分身自定义参数.js` / 083假人2 `9000055.js` 的收費公式）。
2. **髮型/臉型/膚色自訂**：目前由 `setBotVariables` 隨機裝扮；自訂需把參考腳本的清單轉 YAML。
3. **顯式分散目標的硬性鎖定**：目前是 `moveToward` 漂移近似（見陷阱 4），未做 sticky target + 死亡重選。

---

## 八、參考腳本位置（對照來源）

- `C:\Users\ry438\Downloads\083假人2\服务端\scripts-zh-CN\npc\9000055.js`（779 行，招募/戰鬥/行軍/離隊/拾取模式）。
- `C:\Users\ry438\Downloads\drive-download-20260928T030841Z-1-001\【完整版】V95一键端_2026-06-12\服务端\脚本\special\分身自定义参数.js`（收費/髮型/臉型/膚色清單）。
