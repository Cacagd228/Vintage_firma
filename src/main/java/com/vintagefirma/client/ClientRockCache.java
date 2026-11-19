package com.vintagefirma.client;

import com.vintagefirma.common.RockPattern;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.dries007.tfc.common.recipes.KnappingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client side cache of all stone pieces currently being knapped, keyed by the block position they rest
 * on. Filled by {@code S2CSyncRockPacket}.
 */
public final class ClientRockCache
{
    private static final Map<BlockPos, RockWork> WORKS = new HashMap<>();

    private ClientRockCache()
    {
    }

    public static Map<BlockPos, RockWork> map()
    {
        return Collections.unmodifiableMap(WORKS);
    }

    public static boolean has(BlockPos pos)
    {
        return WORKS.containsKey(pos);
    }

    @Nullable
    public static RockWork get(BlockPos pos)
    {
        return WORKS.get(pos);
    }

    public static void put(BlockPos pos, RockWork work)
    {
        WORKS.put(pos, work);
    }

    public static void remove(BlockPos pos)
    {
        WORKS.remove(pos);
    }

    public static void clear()
    {
        WORKS.clear();
    }

    public record RockWork(byte[] voxels, ResourceLocation recipe)
    {
        @Nullable
        public byte[] pattern()
        {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return null;

            final var holder = minecraft.level.getRecipeManager().byKey(recipe);
            if (holder.isPresent() && holder.get() instanceof KnappingRecipe knapping)
            {
                return RockPattern.toVoxels(knapping.getPattern());
            }
            return null;
        }
    }
}