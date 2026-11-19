package com.vintagefirma.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A simple per-player action cooldown, shared by hammer strikes, clay additions and stone chipping.
 * After an action the player must wait a few ticks before the next one.
 */
public final class Cooldowns
{
    /** 0.3 s (1 tick = 0.05 s). */
    public static final int ACTION_TICKS = 6;

    private static final Map<UUID, Integer> TICKS = new HashMap<>();

    private Cooldowns()
    {
    }

    public static boolean ready(UUID player)
    {
        return TICKS.getOrDefault(player, 0) <= 0;
    }

    public static void trigger(UUID player)
    {
        TICKS.put(player, ACTION_TICKS);
    }

    /** Called every server tick. */
    public static void tick()
    {
        if (TICKS.isEmpty()) return;
        TICKS.replaceAll((uuid, ticks) -> ticks - 1);
        TICKS.values().removeIf(ticks -> ticks <= 0);
    }

    public static void clear(UUID player)
    {
        TICKS.remove(player);
    }
}