package com.vintagefirma.network;

import com.vintagefirma.common.AnvilHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

/**
 * Shift + right click on the wrought iron anvil.
 */
public record C2SAnvilInteractPacket(BlockPos pos, boolean offHand)
{
    public static void encode(C2SAnvilInteractPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.offHand);
    }

    public static C2SAnvilInteractPacket decode(FriendlyByteBuf buffer)
    {
        return new C2SAnvilInteractPacket(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(C2SAnvilInteractPacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            final InteractionHand hand = packet.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            context.get().enqueueWork(() -> AnvilHandler.handleInteract(player, packet.pos, hand));
        }
        context.get().setPacketHandled(true);
    }
}
