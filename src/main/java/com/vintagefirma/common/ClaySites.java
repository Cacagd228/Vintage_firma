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
 * Persistent, per-level storage of all clay pieces that are currently being shaped. A normal block has no
 * block entity, so the work is kept here, keyed by the position of the block it rests on.
 */
public class ClaySites extends SavedData
{
    private static final String NAME = "vintage_firma_clay_sites";

    private final Map<BlockPos, ClayWork> works = new HashMap<>();

    public ClaySites()
    {
    }

    public static ClaySites get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(ClaySites::load, ClaySites::new, NAME);
    }

    @Nullable
    public ClayWork get(BlockPos pos)
    {
        return works.get(pos);
    }

    public boolean has(BlockPos pos)
    {
        return works.containsKey(pos);
    }

    public Map<BlockPos, ClayWork> all()
    {
        return works;
    }

    public void put(BlockPos pos, ClayWork work)
    {
        works.put(pos.immutable(), work);
        setDirty();
    }

    @Nullable
    public ClayWork remove(BlockPos pos)
    {
        final ClayWork removed = works.remove(pos);
        if (removed != null)
        {
            setDirty();
        }
        return removed;
    }

    public static ClaySites load(CompoundTag tag)
    {
        final ClaySites sites = new ClaySites();

        final ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            final BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            final ClayWork work = ClayWork.load(entry.getCompound("work"));
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
        for (Map.Entry<BlockPos, ClayWork> entry : works.entrySet())
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