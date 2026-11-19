package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The persistent state of a forging work item: its voxel shape, the metal it is made of, the recipe the
 * player is currently working towards and how much metal has been added.
 */
public record WorkData(byte[] voxels, String metal, ResourceLocation recipe, int ingots, int sheets,
                       int hits, int coolings, boolean cooledSinceLastHit)
{
    public static final String KEY = "vf_work";

    public WorkData
    {
        if (voxels.length != Voxels.LENGTH)
        {
            throw new IllegalArgumentException("Voxel array must have " + Voxels.LENGTH + " entries, got " + voxels.length);
        }
    }

    public int voxelCount()
    {
        return Voxels.countMetal(voxels);
    }

    /** How many pieces of metal (ingots and sheets) went into this work item. */
    public int totalPieces()
    {
        return ingots + sheets;
    }

    public WorkData withIngot()
    {
        return new WorkData(voxels, metal, recipe, ingots + 1, sheets, hits, coolings, cooledSinceLastHit);
    }

    public WorkData withSheet()
    {
        return new WorkData(voxels, metal, recipe, ingots, sheets + 1, hits, coolings, cooledSinceLastHit);
    }

    public WorkData withVoxels(byte[] voxels)
    {
        return new WorkData(voxels, metal, recipe, ingots, sheets, hits, coolings, cooledSinceLastHit);
    }

    public WorkData withHits(int hits)
    {
        return new WorkData(voxels, metal, recipe, ingots, sheets, hits, coolings, false);
    }

    /**
     * Called when the work item is too cold to be worked: as soon as it is hammered again it counts as a
     * cooling event.
     */
    public WorkData markCooled()
    {
        return new WorkData(voxels, metal, recipe, ingots, sheets, hits, coolings, true);
    }

    /** @return a copy with one cooling event counted, if the work item had cooled down */
    public WorkData countCoolingIfAny()
    {
        if (!cooledSinceLastHit)
        {
            return this;
        }
        return new WorkData(voxels, metal, recipe, ingots, sheets, hits, coolings + 1, false);
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putByteArray("voxels", voxels);
        tag.putString("metal", metal);
        tag.putString("recipe", recipe.toString());
        tag.putInt("ingots", ingots);
        tag.putInt("sheets", sheets);
        tag.putInt("hits", hits);
        tag.putInt("coolings", coolings);
        tag.putBoolean("cooled", cooledSinceLastHit);
        return tag;
    }

    @Nullable
    public static WorkData load(CompoundTag tag)
    {
        final byte[] voxels = tag.getByteArray("voxels");
        if (voxels.length != Voxels.LENGTH) return null;

        final String metal = tag.getString("metal");
        final ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
        if (metal.isEmpty() || recipe == null) return null;

        // Old work items did not store sheets, hits or coolings
        final int sheets = Math.max(0, tag.getInt("sheets"));
        final int ingots = Math.max(1, tag.getInt("ingots"));

        return new WorkData(voxels, metal, recipe, ingots, sheets,
            Math.max(0, tag.getInt("hits")), Math.max(0, tag.getInt("coolings")), tag.getBoolean("cooled"));
    }

    @Nullable
    public static WorkData get(ItemStack stack)
    {
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY)) return null;
        return load(tag.getCompound(KEY));
    }

    public static boolean has(ItemStack stack)
    {
        return get(stack) != null;
    }

    public static void set(ItemStack stack, WorkData data)
    {
        stack.getOrCreateTag().put(KEY, data.save());
    }

    public static void remove(ItemStack stack)
    {
        final CompoundTag tag = stack.getTag();
        if (tag != null)
        {
            tag.remove(KEY);
        }
    }
}