package soloMapling.ArtificialPlayer.BotTypes;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import soloMapling.ArtificialPlayer.BotGeneration;
import soloMapling.ArtificialPlayer.BotHelpers;
import soloMapling.ArtificialPlayer.BotMessagingSystem.CharacterStorage;
import soloMapling.ArtificialPlayer.BotPartySystem.BotPartyCommands;
import soloMapling.ArtificialPlayer.BotTypeManager;
import soloMapling.ArtificialPlayer.BotSM;

import static soloMapling.server.SoloMaplingUtilities.getChr;

/**
 * 分身（Doppelganger）的招募／踢除管理器 —— 完全獨立於本體 SocialBot/TrainingBot 招募流程。
 *
 * 只提供「招募單兵 / 隨機招滿 / 踢除指定 / 全隊剔除」四個入口，全部走
 * {@code createBot} + {@code DOPPELGANGER_BOT}，移除時 {@code removeBotFromServer} 立即刪除、
 * 不留紀錄、不碰本體 bot 運作。
 */
public final class DoppelgangerManager {

    private DoppelgangerManager() {
    }

    /** 招募一名分身（自選職業 baseClass：1戰士/2法師/3弓手/4飛俠）加入玩家隊伍。 */
    public static boolean recruitSingle(Character player, int baseClass) {
        if (player == null || player.getMap() == null || partySize(player) >= 6) {
            return false;
        }
        MapleMap map = player.getMap();
        Point pos = player.getPosition();
        int lvl = player.getLevel();
        int botId = BotGeneration.createBot(pos, map, baseClass,
                Math.max(1, lvl - 3), lvl + 3);
        Character bot = getChr(botId);
        if (bot == null) {
            return false;
        }
        // 先入隊再轉型：DoppelgangerBot（繼承 FollowerBot）的 doInit 從隊伍解析隊長。
        joinLeaderParty(player, bot);
        BotTypeManager.convertBotType(bot, BotTypeManager.BotType.DOPPELGANGER_BOT);
        return true;
    }

    /** 隨機招滿：依職業輪流招募，直到隊伍滿（6 人，即玩家 + 5 分身）。 */
    public static int recruitFill(Character player) {
        if (player == null) {
            return 0;
        }
        int[] classes = {1, 2, 3, 4};
        int added = 0;
        for (int i = 0; i < 6 && partySize(player) < 6; i++) {
            if (recruitSingle(player, classes[i % classes.length])) {
                added++;
            }
        }
        return added;
    }

    /** 踢除指定 bot ID：立即刪除、結束生命週期（只踢分身，不碰本體 bot）。 */
    public static boolean kickBot(int botId) {
        Character bot = getChr(botId);
        if (bot == null || !BotHelpers.isBot(bot) || !isDoppelganger(botId)) {
            return false;
        }
        deleteBot(bot);
        return true;
    }

    /** 全隊剔除：把玩家隊伍內的所有分身 bot 立即刪除、不留紀錄。 */
    public static int kickAll(Character player) {
        if (player == null) {
            return 0;
        }
        Party party = player.getParty();
        if (party == null) {
            return 0;
        }
        List<Character> bots = new ArrayList<>();
        for (PartyCharacter pc : party.getMembers()) {
            if (pc == null) {
                continue;
            }
            Character p = pc.getPlayer();
            if (p != null && BotHelpers.isBot(p) && isDoppelganger(p.getId())) {
                bots.add(p);
            }
        }
        for (Character bot : bots) {
            deleteBot(bot);
        }
        return bots.size();
    }

    /** 離隊 + 停 FSM + 從伺服器刪除（removeBotFromServer 本身不離隊也不停排程）。 */
    private static void deleteBot(Character bot) {
        BotPartyCommands.botLeaveParty(bot);   // 先離隊，避免 party 留 ghost member
        BotSM sm = CharacterStorage.getBotById(bot.getId());
        if (sm != null) {
            sm.setRunning(false);
            sm.stopScheduledTask();
        }
        BotGeneration.removeBotFromServer(bot);
    }

    /** 依 bot id 判斷是否為分身（隔離：不誤傷本體 bot）。 */
    private static boolean isDoppelganger(int botId) {
        BotSM bot = CharacterStorage.getAllBots().get(botId);
        return bot != null && "DoppelgangerBot".equals(bot.getBotType());
    }

    private static void joinLeaderParty(Character leader, Character bot) {
        Party party = leader.getParty();
        if (party == null) {
            Party.createParty(leader, false);
            party = leader.getParty();
        }
        if (party != null) {
            Party.joinParty(bot, party.getId(), false);
        }
    }

    private static int partySize(Character player) {
        Party party = player.getParty();
        return party == null ? 0 : party.getMembers().size();
    }
}
