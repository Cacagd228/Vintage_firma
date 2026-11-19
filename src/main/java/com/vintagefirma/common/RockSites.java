package com.vintagefirma.common;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent, per-level storage of all stone pieces currently being knapped, keyed by the position of the
 * block they rest on (a normal block has no block entity).
 */
public class RockSites extends SavedData
{
    private static final String NAME = "vintage_firma_rock_sites";

    private final Map<BlockPos, RockWork> works = new HashMap<>();

    public RockSites()
    {
    }

    public static RockSites get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(RockSites::load, RockSites::new, NAME);
    }

    @Nullable
    public RockWork get(BlockPos pos)
    {
        return works.get(pos);
    }

    public boolean has(BlockPos pos)
    {
        return works.containsKey(pos);
    }

    public Map<BlockPos, RockWork> all()
    {
        return works;
    }

    public void put(BlockPos pos, RockWork work)
    {
        works.put(pos.immutable(), work);
        setDirty();
    }

    @Nullable
    public RockWork remove(BlockPos pos)
    {
        final RockWork removed = works.remove(pos);
        if (removed != null)
        {
            setDirty();
        }
        return removed;
    }

    public static RockSites load(CompoundTag tag)
    {
        final RockSites sites = new RockSites();

        final ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            final BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            final RockWork work = RockWork.load(entry.getCompound("work"));
            if (work != null)
            {
                sites.works.put(pos, work);
            }
        }

        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        final ListTag list = new ListTag();
        for (Map.Entry<BlockPos, RockWork> entry : works.entrySet())
        {
            final BlockPos pos = entry.getKey();

            final CompoundTag node = new CompoundTag();
            node.putInt("x", pos.getX());
            node.putInt("y", pos.getY());
            node.putInt("z", pos.getZ());
            node.put("work", entry.getValue().save());
            list.add(node);
        }
        tag.put("sites", list);
        return tag;
    }
}