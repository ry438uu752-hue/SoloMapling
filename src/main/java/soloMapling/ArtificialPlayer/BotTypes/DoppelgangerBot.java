package soloMapling.ArtificialPlayer.BotTypes;

import client.Character;
import server.life.Monster;
import soloMapling.ArtificialPlayer.BotAttackSystem.BotAttackDriver;
import soloMapling.ArtificialPlayer.BotGeneration;
import soloMapling.ArtificialPlayer.BotPartySystem.BotPartyCommands;
import soloMapling.ArtificialPlayer.GCMoveSystem.GCMovement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static soloMapling.BotLogger.log;

/**
 * 分身 (Doppelganger) — 玩家綁定的伴侶假人：跟隨隊長 + 自動打怪。
 *
 * AI 三原則（依規格）：
 *   1. 跟隨隊長（繼承 {@link FollowerBot} 的 {@code GCMovement.follow}，含跨圖跟隨）— 第一優先，
 *      只在「與隊長同地圖」時才打怪。
 *   2. 打怪：每 ~2 秒偵測一次，攻擊最近的活怪。
 *   3. BOSS 協作：地圖上有 BOSS 時全體打 BOSS。
 *
 * 隔離：獨立 bot type。跟隨結束（隊長消失/離隊）時**直接刪除**（{@code removeBotFromServer}），
 * 不像 {@link FollowerBot} 那樣 fallback 轉 TrainingBot/SocialBot；不保留任何伺服器紀錄。
 */
public class DoppelgangerBot extends FollowerBot {

    /** 2 秒打怪偵測週期（規格第二原則）。 */
    private static final long ATTACK_TICK_MS = 2000;

    private long lastAttackMs = 0;

    public DoppelgangerBot(Character character) {
        super(character);
        botType = "DoppelgangerBot";
        dialoguePath = "FollowerBotDialogue.yaml"; // 沿用跟隨對話（無對話需求）
    }

    @Override
    public void updateState() {
        // 第一優先：跟隨（繼承 FollowerBot 的 FSM）。
        super.updateState();
        if (checkIfNotRunningOrPaused()) {
            return;
        }
        Character chr = getChr();
        if (chr == null || chr.getMap() == null) {
            return;
        }
        // 第一優先：只在「與隊長同地圖」時打怪；隊長換圖 → 交給跟隨邏輯，這裡直接跳過。
        Character leader = resolveLeader();
        if (leader == null || leader.getMap() == null || leader.getMap() != chr.getMap()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < ATTACK_TICK_MS) {
            return;
        }
        lastAttackMs = now;
        attack(chr);
    }

    /**
     * 打怪：
     *   第三原則 — 地圖上有 BOSS → 全體打 BOSS。
     *   第二原則 — 目標分散：依 bot id 對「依距離排序」的怪列表取模，每個分身分到不同怪；
     *              怪少於分身數時自然重疊（協作）。移向目標讓它成為「最近怪」再打。
     */
    private void attack(Character chr) {
        List<Monster> mobs = new ArrayList<>(chr.getMap().getAllMonsters());
        mobs.removeIf(m -> m == null || !m.isAlive());
        if (mobs.isEmpty()) {
            return;
        }
        for (Monster m : mobs) {
            if (m.isBoss()) {
                moveToward(chr, m);
                BotAttackDriver.botAttack(chr);
                return;
            }
        }
        mobs.sort(Comparator.comparingDouble(m -> m.getPosition().distanceSq(chr.getPosition())));
        Monster target = mobs.get(Math.floorMod(chr.getId(), mobs.size()));
        moveToward(chr, target);
        BotAttackDriver.botAttack(chr);
    }

    private void moveToward(Character chr, Monster target) {
        // 往 BOSS 靠過去，讓 BotAttackDriver 的「最近怪」判定命中 BOSS。
        GCMovement.move(chr, target.getPosition().x, target.getPosition().y);
    }

    /** 跟隨結束 → 立即刪除分身，結束生命週期、不留紀錄（不 fallback 轉職）。 */
    @Override
    protected void fallbackConvert() {
        Character chr = getChr();
        log("[DoppelgangerBot] ride ended, deleting bot: " + (chr != null ? chr.getName() : "?"));
        if (chr == null) {
            return;
        }
        BotPartyCommands.botLeaveParty(chr); // 離隊（若仍在隊）避免 party 留 ghost member
        GCMovement.stop(chr);   // 停止跟隨移動
        setRunning(false);       // 停止 FSM
        stopScheduledTask();     // 從 tick service 註銷（removeBotFromServer 不會做）
        BotGeneration.removeBotFromServer(chr);
    }
}
