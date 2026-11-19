package com.vintagefirma.client;

import com.vintagefirma.network.S2COpenClaySelectionPacket;
import com.vintagefirma.network.S2COpenSelectionPacket;
import com.vintagefirma.network.S2CSyncClayPacket;
import com.vintagefirma.network.S2CSyncWorkPacket;
import net.minecraft.client.Minecraft;

/**
 * Client only handlers for packets coming from the server.
 */
public final class ClientNetworkHandler
{
    private ClientNetworkHandler()
    {
    }

    public static void handleSync(S2CSyncWorkPacket packet)
    {
        if (packet.present())
        {
            ClientWorkCache.put(packet.pos(), new ClientWorkCache.ClientWork(packet.voxels(), packet.metal(),
                packet.recipe(), packet.ingots(), packet.temperature()));
        }
        else
        {
            ClientWorkCache.remove(packet.pos());
        }
    }

    public static void handleOpenSelection(S2COpenSelectionPacket packet)
    {
        Minecraft.getInstance().setScreen(new SmithingSelectScreen(packet.pos(), packet.entries()));
    }

    public static void handleClaySync(S2CSyncClayPacket packet)
    {
        if (packet.present())
        {
            ClientClayCache.put(packet.pos(),
                new ClientClayCache.ClayWork(packet.voxels(), packet.recipe(), packet.clay()));
        }
        else
        {
            ClientClayCache.remove(packet.pos());
        }
    }

    public static void handleOpenClaySelection(S2COpenClaySelectionPacket packet)
    {
        Minecraft.getInstance().setScreen(new ClaySelectScreen(packet.pos(), packet.entries()));
    }
}
