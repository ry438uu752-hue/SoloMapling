package net.server;

import client.Character;
import client.Client;
import client.inventory.manipulator.InventoryManipulator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import config.YamlConfig;
import constants.id.MobId;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.DailyCheckinRewards;
import server.life.Monster;
import server.life.MonsterInformationProvider;
import server.maps.MapDataScanner;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import server.maps.Portal;
import server.weather.WeatherPackets;
import server.weather.WeatherService;
import soloMapling.ArtificialPlayer.BotHelpers;
import soloMapling.ArtificialPlayer.BotMessagingSystem.CharacterStorage;
import soloMapling.ArtificialPlayer.BotSM;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/**
 * Local, loopback-only HTTP API for the standalone admin panel (SoloMaplingAdmin.py).
 *
 * <p>Deliberately NOT a remote-admin surface: it binds to 127.0.0.1 and carries no auth,
 * so it is only reachable from the machine running the server. It exposes the live-only
 * state the panel cannot reach through MySQL or config.yaml — the world weather override
 * and the bot population — plus a check-in reward reload trigger.
 *
 * <p>Requests use query parameters; responses are tiny hand-written JSON. There is no
 * JSON body parsing and no reflection, keeping the surface closed to anything but the
 * fixed keys below.
 */
public final class AdminHttpServer {

    private static final Logger log = LoggerFactory.getLogger(AdminHttpServer.class);

    private static HttpServer server;

    private AdminHttpServer() {
    }

    public static void start() {
        int port = YamlConfig.config.server.ADMIN_HTTP_PORT;
        if (port <= 0) {
            log.info("Admin HTTP API disabled (ADMIN_HTTP_PORT = {}).", port);
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/health", AdminHttpServer::handleHealth);
            server.createContext("/weather", AdminHttpServer::handleWeather);
            server.createContext("/weather/effect", AdminHttpServer::handleWeatherEffect);
            server.createContext("/bots", AdminHttpServer::handleBots);
            server.createContext("/online", AdminHttpServer::handleOnline);
            server.createContext("/give", AdminHttpServer::handleGive);
            server.createContext("/drops/reload", AdminHttpServer::handleDropsReload);
            server.createContext("/hack/killall", AdminHttpServer::handleHackKillAll);
            server.createContext("/hack/itemvac", AdminHttpServer::handleHackItemVac);
            server.createContext("/hack/toggle_cooldown", AdminHttpServer::handleToggleCooldown);
            server.createContext("/hack/toggle_blink", AdminHttpServer::handleToggleBlink);
            server.createContext("/map/get_portals", AdminHttpServer::handleGetPortals);
            server.createContext("/hack/warp_to_portal", AdminHttpServer::handleWarpToPortal);
            server.createContext("/hack/warp_map", AdminHttpServer::handleWarpToMap);
            server.createContext("/maps/list", AdminHttpServer::handleMapsList);
            server.createContext("/checkin/reload", AdminHttpServer::handleCheckinReload);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            log.info("Admin HTTP API listening on http://127.0.0.1:{}", port);
        } catch (IOException e) {
            log.error("Failed to start admin HTTP API on port {}: {}", port, e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ------------------------------------------------------------ handlers

    private static void handleHealth(HttpExchange ex) throws IOException {
        respond(ex, 200, "{\"ok\":true}");
    }

    private static void handleWeather(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 200, weatherJson());
            return;
        }
        Map<String, String> q = parseQuery(ex);
        String action = q.getOrDefault("action", "");
        World world = firstWorld();
        switch (action) {
            case "auto" -> {
                WeatherService.clearTimeOverride();
                WeatherService.clearSkyOverride();
            }
            case "time" -> {
                int m = parseInt(q.get("minute"), -1);
                if (m >= 0) {
                    WeatherService.setTime(m);
                }
            }
            case "cleartime" -> WeatherService.clearTimeOverride();
            case "sky" -> {
                int sky = parseInt(q.get("sky"), -1);
                if (sky >= 0) {
                    WeatherService.setSky((byte) sky, WeatherService.OVERRIDE_HOLD_MS);
                }
            }
            case "layers" -> {
                int layers = parseInt(q.get("layers"), -1);
                if (layers >= 0) {
                    WeatherService.setWeatherLayers(layers, WeatherService.OVERRIDE_HOLD_MS);
                }
            }
            case "baresky" -> WeatherService.setBareSky("1".equals(q.get("on")));
            default -> {
                respond(ex, 400, "{\"error\":\"unknown action\"}");
                return;
            }
        }
        if (world != null) {
            WeatherPackets.broadcast(world, false);
        }
        respond(ex, 200, weatherJson());
    }

    private static void handleBots(HttpExchange ex) throws IOException {
        // Read-only. Bot startup is configured in config.yaml
        // (SPAWN_BOTS_ON_STARTUP + POPULATION_SCALE) and applied at server start.
        respond(ex, 200, botsJson());
    }

    /** List every online non-bot character (id, name, level, mapId). */
    private static void handleOnline(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{\"players\":[");
        boolean first = true;
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds != null) {
            for (World w : worlds) {
                if (w == null || w.getPlayerStorage() == null) {
                    continue;
                }
                for (Character chr : w.getPlayerStorage().getAllCharacters()) {
                    if (chr == null || BotHelpers.isBot(chr)) {
                        continue;
                    }
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append("{\"id\":").append(chr.getId())
                            .append(",\"name\":\"").append(jsonEscape(chr.getName())).append("\"")
                            .append(",\"level\":").append(chr.getLevel())
                            .append(",\"mapId\":").append(chr.getMapId())
                            .append('}');
                }
            }
        }
        sb.append("]}");
        respond(ex, 200, sb.toString());
    }

    private static void handleCheckinReload(HttpExchange ex) throws IOException {
        DailyCheckinRewards.reload();
        respond(ex, 200, "{\"ok\":true}");
    }

    /**
     * Trigger a stock cash weather-item effect (the 5120000-series, e.g. 5120006 flower
     * petals / 5120008 maple leaves) by calling the same server-side path the vanilla item
     * uses: {@code MapleMap.startMapEffect} -> {@code BLOW_WEATHER} (0x8E). This is the
     * no-hook answer to the ambient weather system sharing its single client weather slot
     * with these items.
     *
     * <pre>
     *   /weather/effect?itemid=5120006&msg=w&scope=global   (default: every map with players)
     *   /weather/effect?itemid=5120006&msg=w&scope=map&mapid=100000000
     *   /weather/effect?itemid=5120006&msg=w&scope=char&charname=NAME
     * </pre>
     */
    private static void handleWeatherEffect(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        int itemId = parseInt(q.get("itemid"), 0);
        String msg = q.getOrDefault("msg", "w");
        String scope = q.getOrDefault("scope", "global");
        int targetMapId = parseInt(q.get("mapid"), -1);
        String charName = q.get("charname");
        if (itemId <= 0) {
            respond(ex, 400, "{\"error\":\"missing itemid\"}");
            return;
        }
        Set<MapleMap> maps = new HashSet<>();
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds != null) {
            for (World w : worlds) {
                if (w == null || w.getPlayerStorage() == null) {
                    continue;
                }
                for (Character chr : w.getPlayerStorage().getAllCharacters()) {
                    if (chr == null || chr.getMap() == null) {
                        continue;
                    }
                    boolean add;
                    switch (scope) {
                        case "map" -> add = targetMapId >= 0 && chr.getMapId() == targetMapId;
                        case "char" -> add = charName != null && charName.equalsIgnoreCase(chr.getName());
                        default -> add = true; // global
                    }
                    if (add) {
                        maps.add(chr.getMap());
                    }
                }
            }
        }
        for (MapleMap map : maps) {
            map.startMapEffect(msg, itemId, 30000L);
        }
        respond(ex, 200, "{\"maps\":" + maps.size() + "}");
    }

    /** Reload the global/monster drop tables (re-runs MonsterInformationProvider.clearDrops). */
    private static void handleDropsReload(HttpExchange ex) throws IOException {
        MonsterInformationProvider.getInstance().clearDrops();
        respond(ex, 200, "{\"ok\":true}");
    }

    /** Kill every non-friendly, non-Horntail monster on the target character's map. */
    private static void handleHackKillAll(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        if (chr == null || chr.getMap() == null) {
            respond(ex, 200, "{\"killed\":0}");
            return;
        }
        MapleMap map = chr.getMap();
        int count = 0;
        for (MapObject mo : map.getMapObjectsInRange(chr.getPosition(), Double.POSITIVE_INFINITY, List.of(MapObjectType.MONSTER))) {
            Monster monster = (Monster) mo;
            if (!monster.getStats().isFriendly()
                    && !(monster.getId() >= MobId.DEAD_HORNTAIL_MIN && monster.getId() <= MobId.HORNTAIL)) {
                map.damageMonster(chr, monster, Integer.MAX_VALUE);
                count++;
            }
        }
        respond(ex, 200, "{\"killed\":" + count + "}");
    }

    /** Pick up every dropped item on the target character's map. */
    private static void handleHackItemVac(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        if (chr == null || chr.getMap() == null) {
            respond(ex, 200, "{\"picked\":0}");
            return;
        }
        MapleMap map = chr.getMap();
        int count = 0;
        for (MapObject item : map.getMapObjectsInRange(chr.getPosition(), Double.POSITIVE_INFINITY, List.of(MapObjectType.ITEM))) {
            chr.pickupItem(item);
            count++;
        }
        respond(ex, 200, "{\"picked\":" + count + "}");
    }

    /** Toggle the no-cooldown + no-autoban bypass for a character. */
    private static void handleToggleCooldown(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        if (chr == null) {
            respond(ex, 200, "{\"ok\":false}");
            return;
        }
        boolean on = "1".equals(q.get("enabled")) || "true".equalsIgnoreCase(q.get("enabled"));
        AdminHackState.setNoCooldown(chr.getId(), on);
        respond(ex, 200, "{\"ok\":true,\"enabled\":" + on + "}");
    }

    /** Toggle the click-to-blink bypass (Ctrl+click teleport) for a character. */
    private static void handleToggleBlink(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        if (chr == null) {
            respond(ex, 200, "{\"ok\":false}");
            return;
        }
        boolean on = "1".equals(q.get("enabled")) || "true".equalsIgnoreCase(q.get("enabled"));
        AdminHackState.setBlink(chr.getId(), on);
        respond(ex, 200, "{\"ok\":true,\"enabled\":" + on + "}");
    }

    /** List every portal on the target character's current map. */
    private static void handleGetPortals(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        if (chr == null || chr.getMap() == null) {
            respond(ex, 200, "{\"portals\":[]}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"portals\":[");
        boolean first = true;
        for (Portal portal : chr.getMap().getPortals()) {
            if (portal == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            java.awt.Point pos = portal.getPosition();
            sb.append("{\"id\":").append(portal.getId())
                    .append(",\"name\":\"").append(jsonEscape(portal.getName() == null ? "" : portal.getName())).append("\"")
                    .append(",\"x\":").append(pos == null ? 0 : pos.x)
                    .append(",\"y\":").append(pos == null ? 0 : pos.y)
                    .append(",\"targetMapId\":").append(portal.getTargetMapId())
                    .append('}');
            first = false;
        }
        sb.append("]}");
        respond(ex, 200, sb.toString());
    }

    /** Warp the target character to a portal (by id) on their current map. */
    private static void handleWarpToPortal(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        int portalId = parseInt(q.get("portalid"), -1);
        if (chr == null || chr.getMap() == null || portalId < 0) {
            respond(ex, 200, "{\"ok\":false}");
            return;
        }
        Portal target = null;
        for (Portal portal : chr.getMap().getPortals()) {
            if (portal != null && portal.getId() == portalId) {
                target = portal;
                break;
            }
        }
        if (target == null) {
            respond(ex, 200, "{\"ok\":false}");
            return;
        }
        chr.changeMap(chr.getMap(), target.getPosition());
        respond(ex, 200, "{\"ok\":true}");
    }

    /**
     * Force-warp a character to an arbitrary map id (validated against the dynamically
     * scanned {@code Map.wz/Map} list). Mirrors the teleport-rock bypass: skips
     * continent/FieldLimit checks and does not consume any item.
     */
    private static void handleWarpToMap(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        Character chr = findCharacterByName(q.getOrDefault("charname", ""));
        int mapId = parseInt(q.get("mapid"), -1);
        if (chr == null || chr.getMap() == null || chr.getClient() == null) {
            respond(ex, 200, "{\"ok\":false,\"error\":\"character not found\"}");
            return;
        }
        if (mapId < 0 || !MapDataScanner.isValidMap(mapId)) {
            respond(ex, 200, "{\"ok\":false,\"error\":\"invalid map id\"}");
            return;
        }
        MapleMap targetMap = chr.getClient().getChannelServer().getMapFactory().getMap(mapId);
        if (targetMap == null) {
            respond(ex, 200, "{\"ok\":false,\"error\":\"map could not be loaded\"}");
            return;
        }
        chr.forceChangeMap(targetMap, targetMap.getRandomPlayerSpawnpoint());
        respond(ex, 200, "{\"ok\":true}");
    }

    /** Return the dynamically-scanned full map id list from {@code wz/Map.wz/Map}. */
    private static void handleMapsList(HttpExchange ex) throws IOException {
        List<Integer> ids = MapDataScanner.getAllMapIds();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(ids.size()).append(",\"maps\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        sb.append("]}");
        respond(ex, 200, sb.toString());
    }

    /** Minimal JSON string escaping for the portal names. */
    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Grant experience or an item to one character or every online (non-bot) player.
     *
     * <pre>
     *   /give?target=all&type=exp&amount=N
     *   /give?target=char&charid=ID&type=item&itemid=ID&qty=N
     *   /give?target=char&charname=NAME&type=item&itemid=ID&qty=N
     * </pre>
     */
    private static void handleGive(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex);
        String target = q.getOrDefault("target", "char");
        String type = q.getOrDefault("type", "");
        int granted = 0;

        if ("exp".equals(type)) {
            int amount = parseInt(q.get("amount"), 0);
            if (amount > 0) {
                granted = forEachTarget(target, q, chr -> chr.gainExp(amount));
            }
        } else if ("item".equals(type)) {
            int itemId = parseInt(q.get("itemid"), 0);
            int qty = parseInt(q.get("qty"), 1);
            if (itemId > 0 && qty > 0 && qty <= 999) {
                granted = forEachTarget(target, q, chr -> {
                    Client c = chr.getClient();
                    if (c != null) {
                        InventoryManipulator.addById(c, itemId, (short) qty, -1);
                    }
                });
            }
        }

        respond(ex, 200, "{\"granted\":" + granted + "}");
    }

    /** Run {@code grant} against one character (by id/name) or all online non-bot players. */
    private static int forEachTarget(String target, Map<String, String> q, GrantFn grant) {
        if ("all".equals(target)) {
            int n = 0;
            List<World> worlds = Server.getInstance().getWorlds();
            if (worlds != null) {
                for (World w : worlds) {
                    if (w == null || w.getPlayerStorage() == null) {
                        continue;
                    }
                    for (Character chr : w.getPlayerStorage().getAllCharacters()) {
                        if (chr != null && !BotHelpers.isBot(chr)) {
                            grant.apply(chr);
                            n++;
                        }
                    }
                }
            }
            return n;
        }

        int charId = parseInt(q.get("charid"), -1);
        String name = q.get("charname");
        Character chr = null;
        if (charId > 0) {
            chr = findCharacterById(charId);
        } else if (name != null && !name.isBlank()) {
            chr = findCharacterByName(name.trim());
        }
        if (chr == null || BotHelpers.isBot(chr)) {
            return 0;
        }
        grant.apply(chr);
        return 1;
    }

    private static Character findCharacterById(int id) {
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds == null) {
            return null;
        }
        for (World w : worlds) {
            if (w == null || w.getPlayerStorage() == null) {
                continue;
            }
            Character chr = w.getPlayerStorage().getCharacterById(id);
            if (chr != null) {
                return chr;
            }
        }
        return null;
    }

    private static Character findCharacterByName(String name) {
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds == null) {
            return null;
        }
        for (World w : worlds) {
            if (w == null || w.getPlayerStorage() == null) {
                continue;
            }
            Character chr = w.getPlayerStorage().getCharacterByName(name);
            if (chr != null) {
                return chr;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface GrantFn {
        void apply(Character chr);
    }

    // ------------------------------------------------------------ payloads

    private static String weatherJson() {
        boolean overridden = WeatherService.isOverridden();
        boolean timeOverridden = WeatherService.isTimeOverridden();
        return "{\"sky\":" + WeatherService.currentSky()
                + ",\"skyName\":\"" + WeatherService.skyName(WeatherService.currentSky())
                + "\",\"overridden\":" + overridden
                + ",\"layers\":" + WeatherService.currentWorldLayers()
                + ",\"timeOverridden\":" + timeOverridden
                + ",\"minuteOfDay\":" + WeatherService.minuteOfDay()
                + ",\"clock\":\"" + WeatherService.clockString() + "\"}";
    }

    private static String botsJson() {
        Map<String, Integer> byType = new TreeMap<>();
        int botTotal = 0;
        int players = 0;
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds != null) {
            for (World w : worlds) {
                if (w == null || w.getPlayerStorage() == null) {
                    continue;
                }
                for (Character chr : w.getPlayerStorage().getAllCharacters()) {
                    if (chr == null) {
                        continue;
                    }
                    if (BotHelpers.isBot(chr)) {
                        botTotal++;
                        BotSM sm = CharacterStorage.getBotById(chr.getId());
                        String type = sm == null || sm.getBotType() == null ? "Unknown" : sm.getBotType();
                        byType.merge(type, 1, Integer::sum);
                    } else {
                        players++;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"total\":").append(botTotal)
                .append(",\"players\":").append(players)
                .append(",\"byType\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : byType.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    // ------------------------------------------------------------ plumbing

    private static World firstWorld() {
        List<World> worlds = Server.getInstance().getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0);
    }

    private static Map<String, String> parseQuery(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        // URL query string (used by GET, and harmless to read on POST too).
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null && !raw.isEmpty()) {
            parseInto(out, raw);
        }
        // POST body: the panel sends parameters as application/x-www-form-urlencoded
        // in the body, not in the query string.
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            try {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body != null && !body.isEmpty()) {
                    parseInto(out, body);
                }
            } catch (IOException ignored) {
                // unreadable body -> just use what the query string already gave us
            }
        }
        return out;
    }

    private static void parseInto(Map<String, String> out, String raw) {
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(decode(pair), "");
            } else {
                out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
