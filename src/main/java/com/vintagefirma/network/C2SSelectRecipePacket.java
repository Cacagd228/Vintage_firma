package com.vintagefirma.network;

import com.vintagefirma.common.AnvilHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record C2SSelectRecipePacket(BlockPos pos, ResourceLocation recipe)
{
    public static void encode(C2SSelectRecipePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeResourceLocation(packet.recipe);
    }

    public static C2SSelectRecipePacket decode(FriendlyByteBuf buffer)
    {
        return new C2SSelectRecipePacket(buffer.readBlockPos(), buffer.readResourceLocation());
    }

    public static void handle(C2SSelectRecipePacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> AnvilHandler.handleSelectRecipe(player, packet.pos, packet.recipe));
        }
        context.get().setPacketHandled(true);
    }
}
