package com.vintagefirma.network;

import com.vintagefirma.common.ClayRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the list of clay recipes the player can start shaping on top of a block.
 */
public record S2COpenClaySelectionPacket(BlockPos pos, List<Entry> entries)
{
    public record Entry(ResourceLocation id, ItemStack result, int voxels, int clay)
    {
    }

    public static S2COpenClaySelectionPacket of(BlockPos pos, List<ClayRecipe> recipes)
    {
        final List<Entry> entries = new ArrayList<>(recipes.size());
        for (ClayRecipe recipe : recipes)
        {
            entries.add(new Entry(recipe.getId(), recipe.getResult().copy(), recipe.patternVoxelCount(),
                recipe.requiredClay()));
        }
        return new S2COpenClaySelectionPacket(pos, entries);
    }

    public static void encode(S2COpenClaySelectionPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries)
        {
            buffer.writeResourceLocation(entry.id);
            buffer.writeItem(entry.result);
            buffer.writeVarInt(entry.voxels);
            buffer.writeVarInt(entry.clay);
        }
    }

    public static S2COpenClaySelectionPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        final int size = buffer.readVarInt();
        final List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            entries.add(new Entry(buffer.readResourceLocation(), buffer.readItem(), buffer.readVarInt(),
                buffer.readVarInt()));
        }
        return new S2COpenClaySelectionPacket(pos, entries);
    }

    public static void handle(S2COpenClaySelectionPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleOpenClaySelection(packet)));
        context.get().setPacketHandled(true);
    }
}