package com.vintagefirma.network;

import com.vintagefirma.common.AnvilHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record C2SHammerHitPacket(BlockPos pos, int x, int y, int z)
{
    public static void encode(C2SHammerHitPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.x);
        buffer.writeByte(packet.y);
        buffer.writeByte(packet.z);
    }

    public static C2SHammerHitPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SHammerHitPacket(buffer.readBlockPos(), buffer.readUnsignedByte(), buffer.readUnsignedByte(),
            buffer.readUnsignedByte());
    }

    public static void handle(C2SHammerHitPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> AnvilHandler.handleHammerHit(player, packet.pos, packet.x, packet.y, packet.z));
        }
        context.get().setPacketHandled(true);
    }
}
