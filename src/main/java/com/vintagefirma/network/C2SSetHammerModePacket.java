package com.vintagefirma.network;

import com.vintagefirma.common.AnvilHandler;
import com.vintagefirma.common.HammerMode;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record C2SSetHammerModePacket(int mode)
{
    public static void encode(C2SSetHammerModePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeByte(packet.mode);
    }

    public static C2SSetHammerModePacket decode(FriendlyByteBuf buffer)
    {
        return new C2SSetHammerModePacket(buffer.readUnsignedByte());
    }

    public static void handle(C2SSetHammerModePacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(() -> AnvilHandler.setMode(player, HammerMode.byOrdinal(packet.mode)));
        }
        context.get().setPacketHandled(true);
    }
}
