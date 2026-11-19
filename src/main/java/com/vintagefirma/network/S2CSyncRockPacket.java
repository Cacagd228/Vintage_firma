package com.vintagefirma.network;

import com.vintagefirma.common.RockWork;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the complete stone state of one knapping site.
 */
public record S2CSyncRockPacket(BlockPos pos, boolean present, byte[] voxels, ResourceLocation recipe)
{
    public static S2CSyncRockPacket of(BlockPos pos, RockWork work)
    {
        return new S2CSyncRockPacket(pos, true, work.voxels(), work.recipe());
    }

    public static S2CSyncRockPacket clear(BlockPos pos)
    {
        return new S2CSyncRockPacket(pos, false, new byte[0], new ResourceLocation("vintage_firma", "none"));
    }

    public static void encode(S2CSyncRockPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.present);
        if (packet.present)
        {
            buffer.writeByteArray(packet.voxels);
            buffer.writeResourceLocation(packet.recipe);
        }
    }

    public static S2CSyncRockPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        if (!buffer.readBoolean())
        {
            return clear(pos);
        }
        final byte[] voxels = buffer.readByteArray();
        final ResourceLocation recipe = buffer.readResourceLocation();
        return new S2CSyncRockPacket(pos, true, voxels, recipe);
    }

    public static void handle(S2CSyncRockPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleRockSync(packet)));
        context.get().setPacketHandled(true);
    }
}