package com.vintagefirma.network;

import com.vintagefirma.common.ClayWork;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the complete clay state of one shaping site.
 */
public record S2CSyncClayPacket(BlockPos pos, boolean present, byte[] voxels, ResourceLocation recipe, int clay)
{
    public static S2CSyncClayPacket of(BlockPos pos, ClayWork work)
    {
        return new S2CSyncClayPacket(pos, true, work.voxels(), work.recipe(), work.clay());
    }

    public static S2CSyncClayPacket clear(BlockPos pos)
    {
        return new S2CSyncClayPacket(pos, false, new byte[0], new ResourceLocation("vintage_firma", "none"), 0);
    }

    public static void encode(S2CSyncClayPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.present);
        if (packet.present)
        {
            buffer.writeByteArray(packet.voxels);
            buffer.writeResourceLocation(packet.recipe);
            buffer.writeVarInt(packet.clay);
        }
    }

    public static S2CSyncClayPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        if (!buffer.readBoolean())
        {
            return clear(pos);
        }
        final byte[] voxels = buffer.readByteArray();
        final ResourceLocation recipe = buffer.readResourceLocation();
        final int clay = buffer.readVarInt();
        return new S2CSyncClayPacket(pos, true, voxels, recipe, clay);
    }

    public static void handle(S2CSyncClayPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleClaySync(packet)));
        context.get().setPacketHandled(true);
    }
}