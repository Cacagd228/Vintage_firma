package com.vintagefirma.network;

import com.vintagefirma.common.WorkData;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the complete voxel state of one anvil.
 */
public record S2CSyncWorkPacket(BlockPos pos, boolean present, byte[] voxels, String metal, ResourceLocation recipe,
                                int ingots, float temperature)
{
    public static S2CSyncWorkPacket of(BlockPos pos, WorkData data, float temperature)
    {
        return new S2CSyncWorkPacket(pos, true, data.voxels(), data.metal(), data.recipe(), data.ingots(),
            temperature);
    }

    public static S2CSyncWorkPacket clear(BlockPos pos)
    {
        return new S2CSyncWorkPacket(pos, false, new byte[0], "", new ResourceLocation("vintage_firma", "none"), 1, 0f);
    }

    public static void encode(S2CSyncWorkPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.present);
        if (packet.present)
        {
            buffer.writeByteArray(packet.voxels);
            buffer.writeUtf(packet.metal);
            buffer.writeResourceLocation(packet.recipe);
            buffer.writeVarInt(packet.ingots);
            buffer.writeFloat(packet.temperature);
        }
    }

    public static S2CSyncWorkPacket decode(FriendlyByteBuf buffer)
    {
        final BlockPos pos = buffer.readBlockPos();
        if (!buffer.readBoolean())
        {
            return clear(pos);
        }
        final byte[] voxels = buffer.readByteArray();
        final String metal = buffer.readUtf();
        final ResourceLocation recipe = buffer.readResourceLocation();
        final int ingots = buffer.readVarInt();
        final float temperature = buffer.readFloat();
        return new S2CSyncWorkPacket(pos, true, voxels, metal, recipe, ingots, temperature);
    }

    public static void handle(S2CSyncWorkPacket packet, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.vintagefirma.client.ClientNetworkHandler.handleSync(packet)));
        context.get().setPacketHandled(true);
    }
}
