package com.vintagefirma.network;

import com.vintagefirma.VintageFirma;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VFNetwork
{
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(VintageFirma.MOD_ID, "main"))
        .networkProtocolVersion(() -> PROTOCOL)
        .clientAcceptedVersions(PROTOCOL::equals)
        .serverAcceptedVersions(PROTOCOL::equals)
        .simpleChannel();

    private VFNetwork()
    {
    }

    public static void register(FMLCommonSetupEvent event)
    {
        event.enqueueWork(VFNetwork::registerMessages);
    }

    private static void registerMessages()
    {
        int id = 0;

        CHANNEL.messageBuilder(S2CSyncWorkPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(S2CSyncWorkPacket::encode)
            .decoder(S2CSyncWorkPacket::decode)
            .consumerMainThread(S2CSyncWorkPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2COpenSelectionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(S2COpenSelectionPacket::encode)
            .decoder(S2COpenSelectionPacket::decode)
            .consumerMainThread(S2COpenSelectionPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SHammerHitPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SHammerHitPacket::encode)
            .decoder(C2SHammerHitPacket::decode)
            .consumerMainThread(C2SHammerHitPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAnvilInteractPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SAnvilInteractPacket::encode)
            .decoder(C2SAnvilInteractPacket::decode)
            .consumerMainThread(C2SAnvilInteractPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SSelectRecipePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SSelectRecipePacket::encode)
            .decoder(C2SSelectRecipePacket::decode)
            .consumerMainThread(C2SSelectRecipePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SSetHammerModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SSetHammerModePacket::encode)
            .decoder(C2SSetHammerModePacket::decode)
            .consumerMainThread(C2SSetHammerModePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2COpenClaySelectionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(S2COpenClaySelectionPacket::encode)
            .decoder(S2COpenClaySelectionPacket::decode)
            .consumerMainThread(S2COpenClaySelectionPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SClayInteractPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SClayInteractPacket::encode)
            .decoder(C2SClayInteractPacket::decode)
            .consumerMainThread(C2SClayInteractPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SSelectClayRecipePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SSelectClayRecipePacket::encode)
            .decoder(C2SSelectClayRecipePacket::decode)
            .consumerMainThread(C2SSelectClayRecipePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SClaySculptPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SClaySculptPacket::encode)
            .decoder(C2SClaySculptPacket::decode)
            .consumerMainThread(C2SClaySculptPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CSyncClayPacket.class, id, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(S2CSyncClayPacket::encode)
            .decoder(S2CSyncClayPacket::decode)
            .consumerMainThread(S2CSyncClayPacket::handle)
            .add();
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.sendToServer(message);
    }
}
