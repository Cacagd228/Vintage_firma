package com.vintagefirma.network;

import com.vintagefirma.common.RockHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: remove the knapping piece (Shift + right click). Nothing drops.
 */
public record C2SRockRemovePacket(BlockPos pos)
{
    public static void encode(C2SRockRemovePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
    }

    public static C2SRockRemovePacket decode(FriendlyByteBuf buffer)
    {
        return new C2SRockRemovePacket(buffer.readBlockPos());
    }

    public static void handle(C2SRockRemovePacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> RockHandler.handleRemove(player, packet.pos));
        }
        context.get().setPacketHandled(true);
    }
}