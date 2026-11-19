package com.vintagefirma.common;

import net.minecraft.core.Direction;

/**
 * The six hammer modes of Vintage Story. Upset directions are relative to the player: "forward" is the
 * direction the player is looking at.
 */
public enum HammerMode
{
    HEAVY_HIT("heavy_hit"),
    UPSET_FORWARD("upset_forward"),
    UPSET_RIGHT("upset_right"),
    UPSET_BACK("upset_back"),
    UPSET_LEFT("upset_left"),
    SPLIT("split");

    private final String key;

    HammerMode(String key)
    {
        this.key = key;
    }

    public String translationKey()
    {
        return "vintage_firma.hammer_mode." + key;
    }

    public HammerMode next()
    {
        final HammerMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * @return the horizontal {@code {dx, dz}} direction of this mode for a player facing the given direction,
     *         or {@code null} for HEAVY_HIT and SPLIT
     */
    public int[] direction(Direction facing)
    {
        return switch (this)
        {
            case UPSET_FORWARD -> new int[] {facing.getStepX(), facing.getStepZ()};
            case UPSET_BACK -> new int[] {-facing.getStepX(), -facing.getStepZ()};
            case UPSET_RIGHT -> new int[] {clockWise(facing).getStepX(), clockWise(facing).getStepZ()};
            case UPSET_LEFT -> new int[] {-clockWise(facing).getStepX(), -clockWise(facing).getStepZ()};
            default -> null;
        };
    }

    private static Direction clockWise(Direction direction)
    {
        return switch (direction)
        {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.NORTH;
        };
    }

    public static HammerMode byOrdinal(int ordinal)
    {
        final HammerMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
