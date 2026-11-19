package com.vintagefirma.network;

import com.vintagefirma.common.RockHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: chip one voxel off the stone piece (left click).
 */
public record C2SRockChipPacket(BlockPos pos, int x, int z)
{
    public static void encode(C2SRockChipPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.x);
        buffer.writeByte(packet.z);
    }

    public static C2SRockChipPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SRockChipPacket(buffer.readBlockPos(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }

    public static void handle(C2SRockChipPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(
                () -> RockHandler.handleChip(player, packet.pos, packet.x, packet.z));
        }
        context.get().setPacketHandled(true);
    }
}