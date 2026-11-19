package com.vintagefirma.network;

import com.vintagefirma.common.ClayHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: shift + right click a block with clay in hand — open the "what to shape" dialog.
 */
public record C2SClayInteractPacket(BlockPos pos)
{
    public static void encode(C2SClayInteractPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
    }

    public static C2SClayInteractPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SClayInteractPacket(buffer.readBlockPos());
    }

    public static void handle(C2SClayInteractPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> ClayHandler.handleInteract(player, packet.pos));
        }
        context.get().setPacketHandled(true);
    }
}