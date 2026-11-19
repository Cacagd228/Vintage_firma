package com.vintagefirma.common;

import com.vintagefirma.network.S2CSyncWorkPacket;
import com.vintagefirma.network.VFNetwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server side helpers for reading, writing and syncing the work item of an anvil.
 */
public final class WorkSync
{
    /** How often the anvil is re-synced while its work item slowly cools down. */
    private static final int RESYNC_TICKS = 10;

    /** The temperature at which the work item can be worked (wrought iron forging temperature). */
    private static final float FORGING_TEMPERATURE = 921f;

    /** Anvils that currently hold a work item. */
    private static final Set<BlockPos> ACTIVE = new HashSet<>();

    private WorkSync()
    {
    }

    /** Called from the server tick: keeps the anvil's synced state (and thus the temperature) fresh. */
    public static void tick(ServerLevel level)
    {
        if (ACTIVE.isEmpty()) return;
        if (level.getGameTime() % RESYNC_TICKS != 0) return;

        final List<BlockPos> done = new ArrayList<>();
        for (BlockPos pos : ACTIVE)
        {
            final AnvilBlockEntity anvil = getAnvil(level, pos);
            if (anvil == null || WorkData.get(getWork(anvil)) == null)
            {
                done.add(pos);
                continue;
            }

            final ItemStack work = getWork(anvil);
            // Reading the temperature applies the cooling that happened since the last read
            final float temperature = HeatCapability.getTemperature(work);
            final WorkData data = WorkData.get(work);

            // The work item counts as "cooled" once it can no longer be worked. The penalty is only applied
            // when the player actually heats it up again and continues, so simply taking a break is free.
            if (data != null && !data.cooledSinceLastHit() && !isWorkable(temperature))
            {
                WorkData.set(work, data.markCooled());
                anvil.setChanged();
            }

            // The vanilla update packet carries the stack (including its heat NBT) to the client
            anvil.sendVanillaUpdatePacket();
        }
        ACTIVE.removeAll(done);
    }

    @Nullable
    public static AnvilBlockEntity getAnvil(ServerLevel level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof AnvilBlockEntity anvil ? anvil : null;
    }

    @Nullable
    public static IItemHandler inventory(AnvilBlockEntity anvil)
    {
        final var sided = anvil.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        return sided.resolve().orElse(null);
    }

    public static ItemStack getWork(AnvilBlockEntity anvil)
    {
        final IItemHandler inventory = inventory(anvil);
        return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN);
    }

    public static void setWork(AnvilBlockEntity anvil, ItemStack stack)
    {
        final IItemHandler inventory = inventory(anvil);
        if (inventory instanceof IItemHandlerModifiable modifiable)
        {
            modifiable.setStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN, stack);
        }
        else if (inventory != null)
        {
            inventory.extractItem(AnvilBlockEntity.SLOT_INPUT_MAIN, inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN).getCount(), false);
            if (!stack.isEmpty())
            {
                inventory.insertItem(AnvilBlockEntity.SLOT_INPUT_MAIN, stack, false);
            }
        }
        anvil.setChanged();
    }

    public static void clearWork(AnvilBlockEntity anvil)
    {
        setWork(anvil, ItemStack.EMPTY);
    }

    /** Writes a single slot of the anvil inventory, bypassing the item validity checks. */
    public static void setStack(AnvilBlockEntity anvil, int slot, ItemStack stack)
    {
        final IItemHandler inventory = inventory(anvil);
        if (inventory instanceof IItemHandlerModifiable modifiable)
        {
            modifiable.setStackInSlot(slot, stack);
        }
        else if (inventory != null)
        {
            inventory.extractItem(slot, inventory.getStackInSlot(slot).getCount(), false);
            if (!stack.isEmpty())
            {
                inventory.insertItem(slot, stack, false);
            }
        }
        anvil.setChanged();
    }

    public static void broadcast(ServerLevel level, BlockPos pos)
    {
        final AnvilBlockEntity anvil = getAnvil(level, pos);
        final ItemStack stack = anvil == null ? ItemStack.EMPTY : getWork(anvil);
        final WorkData data = WorkData.get(stack);
        final S2CSyncWorkPacket packet = packet(pos, stack, data);

        if (data == null)
        {
            ACTIVE.remove(pos);
        }
        else
        {
            ACTIVE.add(pos.immutable());
        }
        if (anvil != null)
        {
            // Keeps the anvil's synced inventory (and with it the heat NBT of the work item) up to date
            anvil.sendVanillaUpdatePacket();
        }

        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64 * 64)
            {
                VFNetwork.sendTo(player, packet);
            }
        }
    }

    public static void sendTo(ServerPlayer player, ServerLevel level, BlockPos pos)
    {
        final AnvilBlockEntity anvil = getAnvil(level, pos);
        final ItemStack stack = anvil == null ? ItemStack.EMPTY : getWork(anvil);
        VFNetwork.sendTo(player, packet(pos, stack, WorkData.get(stack)));
    }

    /** @return {@code true} if the work item is hot enough to be worked right now */
    public static boolean isWorkable(float temperature)
    {
        return temperature >= FORGING_TEMPERATURE;
    }

    private static S2CSyncWorkPacket packet(BlockPos pos, ItemStack stack, WorkData data)
    {
        if (data == null)
        {
            return S2CSyncWorkPacket.clear(pos);
        }
        final float temperature = HeatCapability.has(stack) ? HeatCapability.getTemperature(stack) : 0f;
        return S2CSyncWorkPacket.of(pos, data, temperature);
    }

    public static void syncChunk(ServerPlayer player, ServerLevel level, ChunkPos chunkPos)
    {
        final LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        for (BlockEntity blockEntity : chunk.getBlockEntities().values())
        {
            if (blockEntity instanceof AnvilBlockEntity && AnvilHandler.isOurAnvil(blockEntity.getBlockState()))
            {
                sendTo(player, level, blockEntity.getBlockPos());
            }
        }
    }
}
