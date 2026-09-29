package server;

import client.Client;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily Check-In reward table.
 *
 * <p>Rewards are ADMIN-CONFIGURABLE through a plain text file {@code checkin_rewards.txt}
 * in the server working directory (edited by the standalone admin panel). One line per day,
 * tab-separated:
 *
 * <pre>
 *   day \t iconItemId \t mesos \t enabled(0|1) \t itemId:qty,itemId:qty,...
 * </pre>
 *
 * <p>The table is read on demand and re-read automatically whenever the file's mtime
 * changes, so an admin's edits are picked up without a restart. {@link #reload()} forces it
 * immediately (wired to the local admin HTTP {@code /checkin/reload} endpoint). If the file
 * is missing or unreadable, each day falls back to the built-in default (50,000 mesos,
 * placeholder icon) so the window keeps working.
 *
 * <p>No database tables are used here: the streak state already lives on {@code characters}
 * (checkinDay / checkinClaimed / checkinLastClaim); only the reward definition is external.
 */
public final class DailyCheckinRewards {

    private static final long ONE_DAY_MS = 86_400_000L;
    public static final int CYCLE_DAYS = 28;
    /** Characters below this level can't see or use the Daily Check-In window. */
    public static final int MIN_LEVEL = 10;

    /** Path to the reward config file, relative to the server working directory. */
    public static final Path REWARDS_FILE = Path.of("checkin_rewards.txt");

    /** A single item grant. expireDays 0 = permanent; >0 = expires that many days from claim. */
    public record Grant(int itemId, int qty, int expireDays) {
        public Grant(int itemId, int qty) {
            this(itemId, qty, 0);
        }
    }

    /**
     * One day's reward. {@code enabled=false} means the day can still be claimed to advance
     * the streak, but grants nothing.
     */
    public record Reward(int iconItemId, int mesos, Grant[] grants, int slotType, int slotCount, boolean enabled) {
        Reward(int iconItemId, int mesos, Grant[] grants, int slotType, int slotCount) {
            this(iconItemId, mesos, grants, slotType, slotCount, true);
        }

        Reward(int iconItemId, int mesos, Grant[] grants) {
            this(iconItemId, mesos, grants, 0, 0, true);
        }
    }

    private static final Grant[] NONE = new Grant[0];

    // Placeholder icon for the fallback (a basic potion, present in every v83 client).
    private static final int PLACEHOLDER_ICON = 2000000;

    private static final Reward[] DEFAULT_DAYS;
    static {
        DEFAULT_DAYS = new Reward[CYCLE_DAYS];
        for (int d = 0; d < CYCLE_DAYS; d++) {
            DEFAULT_DAYS[d] = new Reward(PLACEHOLDER_ICON, 50000, NONE);
        }
    }

    private static volatile Reward[] DAYS = DEFAULT_DAYS;
    private static volatile long loadedMtime = 0L;

    private DailyCheckinRewards() {
    }

    private static Reward[] days() {
        long mtime = fileMtime();
        if (mtime != loadedMtime) {
            reload();
        }
        return DAYS;
    }

    /** Re-read the reward file now. Any read/parse failure leaves the current table in place. */
    public static synchronized void reload() {
        Reward[] loaded = loadFromFile();
        if (loaded != null) {
            DAYS = loaded;
        }
        loadedMtime = fileMtime();
    }

    private static long fileMtime() {
        try {
            return Files.getLastModifiedTime(REWARDS_FILE).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Reward[] loadFromFile() {
        if (!Files.isRegularFile(REWARDS_FILE)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(REWARDS_FILE, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            Reward[] out = new Reward[CYCLE_DAYS];
            System.arraycopy(DEFAULT_DAYS, 0, out, 0, CYCLE_DAYS);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 4) {
                    continue;
                }
                int day = parseInt(f[0], -1);
                if (day < 1 || day > CYCLE_DAYS) {
                    continue;
                }
                int icon = parseInt(f[1], PLACEHOLDER_ICON);
                int mesos = parseInt(f[2], 0);
                boolean enabled = !"0".equals(f[3].trim());
                List<Grant> grants = new ArrayList<>();
                if (f.length >= 5 && !f[4].trim().isEmpty()) {
                    for (String part : f[4].split(",")) {
                        String[] g = part.split(":", -1);
                        int itemId = parseInt(g[0], 0);
                        int qty = g.length >= 2 ? parseInt(g[1], 1) : 1;
                        if (itemId > 0 && qty > 0) {
                            grants.add(new Grant(itemId, qty));
                        }
                    }
                }
                out[day - 1] = new Reward(icon > 0 ? icon : PLACEHOLDER_ICON, mesos,
                        grants.isEmpty() ? NONE : grants.toArray(new Grant[0]), 0, 0, enabled);
            }
            return out;
        } catch (IOException e) {
            return null;
        }
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

    private static String slotTabName(int t) {
        switch (t) {
            case 1:  return "Equip";
            case 2:  return "Use";
            case 3:  return "Set-up";
            case 4:  return "Etc";
            case 5:  return "Cash";
            default: return "";
        }
    }

    /** Item id whose icon the client draws for {@code day} (1-based), or 0 if out of range. */
    public static int iconItemId(int day) {
        Reward[] table = days();
        if (day < 1 || day > table.length) {
            return 0;
        }
        return table[day - 1].iconItemId();
    }

    /**
     * Grant the full reward for {@code day} (1-based). Best-effort: each piece is granted
     * independently. A disabled day grants nothing but still returns true so the streak
     * advances. Returns false only if the day is out of range.
     */
    public static boolean grantDay(Client c, int day) {
        Reward[] table = days();
        if (day < 1 || day > table.length) {
            return false;
        }
        Reward r = table[day - 1];
        if (!r.enabled()) {
            return true;
        }
        if (r.mesos() > 0) {
            c.getPlayer().gainMeso(r.mesos(), true);
        }
        for (Grant g : r.grants()) {
            long expiration = g.expireDays() > 0 ? System.currentTimeMillis() + g.expireDays() * ONE_DAY_MS : -1;
            InventoryManipulator.addById(c, g.itemId(), (short) g.qty(), expiration);
        }
        if (r.slotType() > 0 && r.slotCount() > 0) {
            c.getPlayer().gainSlots(r.slotType(), r.slotCount());
        }
        return true;
    }

    /** Human-readable multi-line breakdown for {@code day}, used as the client's hover tooltip. */
    public static String tooltip(int day) {
        Reward[] table = days();
        if (day < 1 || day > table.length) {
            return "";
        }
        Reward r = table[day - 1];
        if (!r.enabled()) {
            return "Day " + day + "\n（本日獎勵已停用）";
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("Day ").append(day).append('\n');
        for (Grant g : r.grants()) {
            String name = ii.getName(g.itemId());
            if (name == null || name.isBlank()) {
                name = "Item " + g.itemId();
            }
            sb.append("- ").append(name);
            if (g.qty() > 1) {
                sb.append(" x").append(g.qty());
            }
            if (g.expireDays() > 0) {
                sb.append(" (").append(g.expireDays()).append(g.expireDays() == 1 ? " day)" : " days)");
            }
            sb.append('\n');
        }
        if (r.mesos() > 0) {
            sb.append("- ").append(String.format("%,d", r.mesos())).append(" mesos\n");
        }
        if (r.slotType() > 0 && r.slotCount() > 0) {
            sb.append("- +").append(r.slotCount()).append(' ').append(slotTabName(r.slotType())).append(" slots\n");
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\n') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }
}
