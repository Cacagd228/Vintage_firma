package com.vintagefirma.network;

import com.vintagefirma.common.RockHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: the player picked a stone tool to knap on top of a loose rock.
 */
public record C2SSelectRockRecipePacket(BlockPos pos, ResourceLocation recipe)
{
    public static void encode(C2SSelectRockRecipePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeResourceLocation(packet.recipe);
    }

    public static C2SSelectRockRecipePacket decode(FriendlyByteBuf buffer)
    {
        return new C2SSelectRockRecipePacket(buffer.readBlockPos(), buffer.readResourceLocation());
    }

    public static void handle(C2SSelectRockRecipePacket packet, Supplier<NetworkEvent.Context> context)
    {
        final ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            context.get().enqueueWork(
                () -> RockHandler.handleSelect(player, packet.pos, packet.recipe));
        }
        context.get().setPacketHandled(true);
    }
}