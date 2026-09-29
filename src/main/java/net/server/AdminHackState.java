package net.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-character hack toggles, flipped by the admin panel over the local HTTP API.
 *
 * <p>Kept here (rather than as fields on {@code client.Character}) so the hot character
 * class does not grow a persistence/copy surface for a debug-only feature, and so the
 * flag is process-local and automatically cleared on restart. Keyed by character id.
 */
public final class AdminHackState {

    private static final Set<Integer> noCooldown = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> blink = ConcurrentHashMap.newKeySet();

    private AdminHackState() {
    }

    public static void setNoCooldown(int charId, boolean on) {
        if (on) {
            noCooldown.add(charId);
        } else {
            noCooldown.remove(charId);
        }
    }

    public static boolean isNoCooldown(int charId) {
        return noCooldown.contains(charId);
    }

    public static void setBlink(int charId, boolean on) {
        if (on) {
            blink.add(charId);
        } else {
            blink.remove(charId);
        }
    }

    public static boolean isBlink(int charId) {
        return blink.contains(charId);
    }

}
