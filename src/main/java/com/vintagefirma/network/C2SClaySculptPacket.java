package com.vintagefirma.network;

import com.vintagefirma.common.ClayHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: add ({@code add = true}) or remove ({@code add = false}) a single clay voxel.
 */
public record C2SClaySculptPacket(BlockPos pos, int x, int y, int z, boolean add)
{
    public static void encode(C2SClaySculptPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.x);
        buffer.writeByte(packet.y);
        buffer.writeByte(packet.z);
        buffer.writeBoolean(packet.add);
    }

    public static C2SClaySculptPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SClaySculptPacket(buffer.readBlockPos(), buffer.readUnsignedByte(), buffer.readUnsignedByte(),
            buffer.readUnsignedByte(), buffer.readBoolean());
    }

    public static void handle(C2SClaySculptPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> ClayHandler.handleSculpt(player, packet.pos, packet.x, packet.y,
                packet.z, packet.add));
        }
        context.get().setPacketHandled(true);
    }
}