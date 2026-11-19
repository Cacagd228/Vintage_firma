package com.vintagefirma.network;

import com.vintagefirma.common.SmithingRecipe;
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
 * Server to client: list of recipes the player can start on the anvil.
 */
public record S2COpenSelectionPacket(BlockPos pos, List<Entry> entries)
{
    public record Entry(ResourceLocation id, ItemStack result, int voxels, int ingots)
    {
    }

    public static S2COpenSelectionPacket of(BlockPos pos, List<SmithingRecipe> recipes)
    {
        final List<Entry> entries = new ArrayList<>(recipes.size());
        for (SmithingRecipe recipe : recipes)
        {
            entries.add(new Entry(recipe.getId(), recipe.getResult().copy(), recipe.patternVoxelCount(),
                recipe.requiredIngots()));
        }
        return new S2COpenSelectionPacket(pos, entries);
    }

    public static void encode(S2COpenSelectionPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries)
        {
            buffer.writeResourceLocation(entry.id);
            buffer.writeItem(entry.result);
            buffer.writeVarInt(entry.voxels);
            buffer.writeVarInt(entry.ingots);
        }
    }

    public static S2COpenSelectionPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        final int size = buffer.readVarInt();
        final List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            entries.add(new Entry(buffer.readResourceLocation(), buffer.readItem(), buffer.readVarInt(),
                buffer.readVarInt()));
        }
        return new S2COpenSelectionPacket(pos, entries);
    }

    public static void handle(S2COpenSelectionPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleOpenSelection(packet)));
        context.get().setPacketHandled(true);
    }
}