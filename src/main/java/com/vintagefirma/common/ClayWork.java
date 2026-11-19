package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The state of a clay piece being shaped on top of a block: its voxel shape, the recipe the player is
 * working towards and how many voxels of clay are still available (a single clay item is worth
 * {@link ClayRecipe#DEFAULT_VOXELS_PER_CLAY} voxels; removing clay refunds it).
 *
 * <p>Clay voxels reuse {@link Voxels#METAL} as "filled", so the pattern helpers and {@link Voxels#matches}
 * work unchanged.
 */
public record ClayWork(byte[] voxels, ResourceLocation recipe, int clay)
{
    public ClayWork
    {
        if (voxels.length != Voxels.LENGTH)
        {
            throw new IllegalArgumentException("Clay voxel array must have " + Voxels.LENGTH + " entries");
        }
    }

    public int voxelCount()
    {
        return Voxels.countMetal(voxels);
    }

    /**
     * @return the lowest layer that does not yet match the pattern (the only layer the player may edit),
     *         or {@link Voxels#SIZE_Y} when the whole piece matches.
     */
    public static int currentLayer(byte[] voxels, byte[] pattern)
    {
        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            if (!layerMatches(voxels, pattern, y)) return y;
        }
        return Voxels.SIZE_Y;
    }

    /** @return {@code true} if every cell of layer {@code y} matches the pattern (filled == required). */
    public static boolean layerMatches(byte[] voxels, byte[] pattern, int y)
    {
        for (int z = 0; z < Voxels.SIZE_Z; z++)
        {
            for (int x = 0; x < Voxels.SIZE_X; x++)
            {
                final int i = Voxels.index(x, y, z);
                if ((voxels[i] != Voxels.EMPTY) != (pattern[i] != Voxels.EMPTY)) return false;
            }
        }
        return true;
    }

    public ClayWork withVoxels(byte[] voxels, int clay)
    {
        return new ClayWork(voxels, recipe, clay);
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putByteArray("voxels", voxels);
        tag.putString("recipe", recipe.toString());
        tag.putInt("clay", clay);
        return tag;
    }

    @Nullable
    public static ClayWork load(CompoundTag tag)
    {
        final byte[] voxels = tag.getByteArray("voxels");
        if (voxels.length != Voxels.LENGTH) return null;

        final ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
        if (recipe == null) return null;

        return new ClayWork(voxels, recipe, Math.max(0, tag.getInt("clay")));
    }
}