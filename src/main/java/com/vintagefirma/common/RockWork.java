package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The state of a stone piece being knapped on top of a block: its voxel shape and the recipe the player
 * is working towards. Unlike clay, knapping is purely subtractive (you only chip voxels off), so there is
 * no material budget.
 *
 * <p>Stone voxels reuse {@link Voxels#METAL} as "filled".
 */
public record RockWork(byte[] voxels, ResourceLocation recipe)
{
    /** The flat blank a loose rock turns into: a 5 x 5 plate, one voxel thick, centred on the block. */
    public static final int BLANK_SIZE = 5;
    public static final int BLANK_X = (Voxels.SIZE_X - BLANK_SIZE) / 2;
    public static final int BLANK_Z = (Voxels.SIZE_Z - BLANK_SIZE) / 2;

    public RockWork
    {
        if (voxels.length != Voxels.LENGTH)
        {
            throw new IllegalArgumentException("Rock voxel array must have " + Voxels.LENGTH + " entries");
        }
    }

    public static byte[] blank()
    {
        final byte[] voxels = Voxels.empty();
        for (int z = 0; z < BLANK_SIZE; z++)
        {
            for (int x = 0; x < BLANK_SIZE; x++)
            {
                Voxels.set(voxels, BLANK_X + x, 0, BLANK_Z + z, Voxels.METAL);
            }
        }
        return voxels;
    }

    public int voxelCount()
    {
        return Voxels.countMetal(voxels);
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putByteArray("voxels", voxels);
        tag.putString("recipe", recipe.toString());
        return tag;
    }

    @Nullable
    public static RockWork load(CompoundTag tag)
    {
        final byte[] voxels = tag.getByteArray("voxels");
        if (voxels.length != Voxels.LENGTH) return null;

        final ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
        if (recipe == null) return null;

        return new RockWork(voxels, recipe);
    }
}