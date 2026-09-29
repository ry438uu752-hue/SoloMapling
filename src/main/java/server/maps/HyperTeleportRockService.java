package server.maps;

import client.Character;
import client.Client;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;

import java.util.Set;

/** Server-authoritative validation for the client's world-map Hyper Teleport Rock request. */
public final class HyperTeleportRockService {
    private static final Logger log = LoggerFactory.getLogger(HyperTeleportRockService.class);

    // Expedition instances without a reliable FieldLimit marker. Keep this list
    // deliberately separate from map-existence validation, which uses Map.wz.
    private static final Set<Integer> BLOCKED_BOSS_MAPS = Set.of(
            105100300, 105100301, 105100400, 105100401, // Balrog
            220080001,                                  // Papulatus
            280030000,                                  // Zakum
            240060000, 240060100, 240060200, 240060201, // Horntail
            270050100, 270050200, 270050300,             // Pink Bean
            211070100,                                  // Von Leon
            800040410,                                  // Toad
            801040100, 801040101,                       // Showa
            551030200,                                  // Scarlion and Targa
            541020800,                                  // Krexel
            610030100, 610030200, 610030300, 610030400, 610030500,
            610030510, 610030520, 610030521, 610030522, 610030530,
            610030540, 610030550, 610030600, 610030700, 610030800 // Crimsonwood expedition
    );

    private HyperTeleportRockService() {
    }

    public static boolean tryTeleport(Client client, int targetMapId) {
        Character player = client.getPlayer();
        if (player == null || !client.isLoggedIn() || !player.isLoggedinWorld()) {
            return false;
        }

        MapleMap currentMap = player.getMap();
        if (currentMap == null || player.isChangingMaps() || player.isBanned() || !player.isAlive()) {
            return reject(player, "You cannot use a Hyper Teleport Rock right now.");
        }

        if (player.getCashShop().isOpened() || player.getTrade() != null || player.getShop() != null
                || player.getPlayerShop() != null || player.getMiniGame() != null
                || player.getHiredMerchant() != null) {
            return reject(player, "You cannot use a Hyper Teleport Rock right now.");
        }

        EventInstanceManager eventInstance = player.getEventInstance();
        if (eventInstance != null) {
            return reject(player, "Hyper Teleport Rocks cannot be used inside event instances.");
        }

        if (!hasUnexpiredRock(player)) {
            return reject(player, "You need a valid Hyper Teleport Rock to use the World Map.");
        }

        if (isBlockedMap(currentMap.getId())
                || FieldLimit.CANNOTVIPROCK.check(currentMap.getFieldLimit())) {
            return reject(player, "You cannot use a Hyper Teleport Rock from this map.");
        }

        if (!MapDataScanner.isValidMap(targetMapId)) {
            log.warn("Rejected Hyper Teleport Rock from {}: map {} is not present in Map.wz",
                    player.getName(), targetMapId);
            return reject(player, "That map does not exist.");
        }

        MapleMap targetMap;
        try {
            targetMap = client.getChannelServer().getMapFactory().getMap(targetMapId);
        } catch (RuntimeException e) {
            log.warn("Failed to load Hyper Teleport Rock target map {} requested by {}",
                    targetMapId, player.getName(), e);
            return reject(player, "That map is currently unavailable.");
        }
        if (targetMap == null) {
            return reject(player, "That map is currently unavailable.");
        }

        if (targetMap.getEventInstance() != null) {
            return reject(player, "Hyper Teleport Rocks cannot be used to enter event instances.");
        }

        if (isBlockedMap(targetMapId)
                || FieldLimit.CANNOTVIPROCK.check(targetMap.getFieldLimit())
                || (targetMap.getForcedReturnId() != MapId.NONE && !MapId.isMapleIsland(targetMapId))) {
            return reject(player, "You cannot teleport to this map.");
        }

        Portal spawnPoint;
        try {
            spawnPoint = targetMap.getRandomPlayerSpawnpoint();
        } catch (RuntimeException e) {
            log.warn("Hyper Teleport Rock target map {} has no usable spawn point", targetMapId, e);
            return reject(player, "That map is currently unavailable.");
        }
        if (spawnPoint == null) {
            return reject(player, "That map is currently unavailable.");
        }

        String mapName = targetMap.getMapName();
        player.dropMessage(5, mapName == null || mapName.isBlank()
                ? "The Hyper Teleport Rock warps you to map " + targetMapId + "."
                : "The Hyper Teleport Rock warps you to " + mapName + ".");
        log.info("Hyper Teleport Rock accepted: character={} from={} to={}",
                player.getName(), currentMap.getId(), targetMapId);
        player.forceChangeMap(targetMap, spawnPoint);
        return true;
    }

    private static boolean hasUnexpiredRock(Character player) {
        long now = System.currentTimeMillis();
        for (Item item : player.getInventory(InventoryType.CASH).list()) {
            if (item.getItemId() == ItemId.HYPER_TELEPORT_ROCK
                    && (item.getExpiration() < 0 || item.getExpiration() > now)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedMap(int mapId) {
        // Time Temple region and the server's existing boss-rush arena range.
        return (mapId >= 270000000 && mapId < 271000000)
                || MapId.isBossRush(mapId)
                || BLOCKED_BOSS_MAPS.contains(mapId);
    }

    private static boolean reject(Character player, String message) {
        player.dropMessage(5, message);
        log.info("Hyper Teleport Rock rejected: character={} map={} reason={}",
                player.getName(), player.getMapId(), message);
        return false;
    }
}
