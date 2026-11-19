package com.vintagefirma.network;

import com.vintagefirma.common.RockHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: shift + right click a loose rock on the ground with a loose rock in hand.
 */
public record C2SRockInteractPacket(BlockPos pos)
{
    public static void encode(C2SRockInteractPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
    }

    public static C2SRockInteractPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SRockInteractPacket(buffer.readBlockPos());
    }

    public static void handle(C2SRockInteractPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> RockHandler.handleInteract(player, packet.pos));
        }
        context.get().setPacketHandled(true);
    }
}