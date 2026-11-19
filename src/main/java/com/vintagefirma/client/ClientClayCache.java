package com.vintagefirma.client;

import com.vintagefirma.common.ClayRecipe;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client side cache of all clay pieces currently being shaped, keyed by the position of the block they
 * rest on. Filled by {@code S2CSyncClayPacket}.
 */
public final class ClientClayCache
{
    private static final Map<BlockPos, ClayWork> WORKS = new HashMap<>();

    private ClientClayCache()
    {
    }

    public static Map<BlockPos, ClayWork> map()
    {
        return Collections.unmodifiableMap(WORKS);
    }

    @Nullable
    public static ClayWork get(BlockPos pos)
    {
        return WORKS.get(pos);
    }

    public static void put(BlockPos pos, ClayWork work)
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

    public record ClayWork(byte[] voxels, ResourceLocation recipe, int clay)
    {
        @Nullable
        public byte[] pattern()
        {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return null;

            final var holder = minecraft.level.getRecipeManager().byKey(recipe);
            if (holder.isPresent() && holder.get() instanceof ClayRecipe clay)
            {
                return clay.patternVoxels();
            }
            return null;
        }
    }
}