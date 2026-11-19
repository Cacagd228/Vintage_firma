package com.vintagefirma.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import com.vintagefirma.smithing.Voxels;
import org.jetbrains.annotations.Nullable;

/**
 * Client side cache of all anvils that currently hold a work item. Filled by {@code S2CSyncWorkPacket}.
 */
public final class ClientWorkCache
{
    private static final Map<BlockPos, ClientWork> WORKS = new HashMap<>();

    private ClientWorkCache()
    {
    }

    public static Map<BlockPos, ClientWork> map()
    {
        return Collections.unmodifiableMap(WORKS);
    }

    public static ClientWork get(BlockPos pos)
    {
        return WORKS.get(pos);
    }

    public static void put(BlockPos pos, ClientWork work)
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

    /**
     * The temperature that is currently shown.
     *
     * <p>The packet only carries the temperature at the moment it was sent, but TFC's heat handler cools
     * a stack lazily: every call to {@link HeatCapability#getTemperature(ItemStack)} recomputes the value
     * from the elapsed calendar ticks. The anvil's inventory is synchronised to the client, so reading the
     * temperature from that stack gives exactly the same number the server has, without any extra packets.
     */
    public static float temperature(ClientLevel level, BlockPos pos, ClientWork work)
    {
        if (level.getBlockEntity(pos) instanceof AnvilBlockEntity anvil)
        {
            final var inventory = anvil.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
            if (inventory != null)
            {
                final ItemStack stack = inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN);
                if (isSameWork(stack, work))
                {
                    return HeatCapability.getTemperature(stack);
                }
            }
        }
        return work.temperature();
    }

    /** Compares the voxel shape and the recipe, ignoring the temperature. */
    private static boolean isSameWork(ItemStack stack, ClientWork work)
    {
        if (stack.isEmpty() || !(stack.getItem() instanceof com.vintagefirma.common.ItemWorkItem))
        {
            return false;
        }

        final com.vintagefirma.common.WorkData data = com.vintagefirma.common.WorkData.get(stack);
        if (data == null)
        {
            return false;
        }

        return data.recipe().equals(work.recipe()) && data.voxelCount() == Voxels.countMetal(work.voxels());
    }

    public record ClientWork(byte[] voxels, String metal, ResourceLocation recipe, int ingots, float temperature)
    {
        @Nullable
        public byte[] pattern()
        {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return null;

            final var holder = minecraft.level.getRecipeManager().byKey(recipe);
            if (holder.isPresent() && holder.get() instanceof com.vintagefirma.common.SmithingRecipe smithing)
            {
                return smithing.patternVoxels();
            }
            return null;
        }
    }
}