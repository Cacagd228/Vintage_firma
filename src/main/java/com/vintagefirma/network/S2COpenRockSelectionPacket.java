package com.vintagefirma.network;

import com.vintagefirma.common.RockPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.dries007.tfc.common.recipes.KnappingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the list of stone tools the player can knap from the held loose rock.
 */
public record S2COpenRockSelectionPacket(BlockPos pos, List<Entry> entries)
{
    public record Entry(ResourceLocation id, ItemStack result, int voxels)
    {
    }

    public static S2COpenRockSelectionPacket of(BlockPos pos, List<KnappingRecipe> recipes,
        RegistryAccess access)
    {
        final List<Entry> entries = new ArrayList<>(recipes.size());
        for (KnappingRecipe recipe : recipes)
        {
            entries.add(new Entry(recipe.getId(), recipe.getResultItem(access),
                RockPattern.count(recipe.getPattern())));
        }
        return new S2COpenRockSelectionPacket(pos, entries);
    }

    public static void encode(S2COpenRockSelectionPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries)
        {
            buffer.writeResourceLocation(entry.id);
            buffer.writeItem(entry.result);
            buffer.writeVarInt(entry.voxels);
        }
    }

    public static S2COpenRockSelectionPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        final int size = buffer.readVarInt();
        final List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            entries.add(new Entry(buffer.readResourceLocation(), buffer.readItem(), buffer.readVarInt()));
        }
        return new S2COpenRockSelectionPacket(pos, entries);
    }

    public static void handle(S2COpenRockSelectionPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleOpenRockSelection(packet)));
        context.get().setPacketHandled(true);
    }
}