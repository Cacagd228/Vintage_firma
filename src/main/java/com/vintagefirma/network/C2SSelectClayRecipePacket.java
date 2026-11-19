package com.vintagefirma.network;

import com.vintagefirma.common.ClayHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: the player picked a clay recipe to start shaping on top of a block.
 */
public record C2SSelectClayRecipePacket(BlockPos pos, ResourceLocation recipe)
{
    public static void encode(C2SSelectClayRecipePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeResourceLocation(packet.recipe);
    }

    public static C2SSelectClayRecipePacket decode(FriendlyByteBuf buffer)
    {
        return new C2SSelectClayRecipePacket(buffer.readBlockPos(), buffer.readResourceLocation());
    }

    public static void handle(C2SSelectClayRecipePacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(
                () -> ClayHandler.handleSelect(player, packet.pos, packet.recipe));
        }
        context.get().setPacketHandled(true);
    }
}