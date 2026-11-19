package com.vintagefirma.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side helpers for hot work items:
 *
 * <ul>
 *   <li>{@link #displayTemperature(BlockPos, float)} smooths the temperature, so the metal does not
 *       change colour in one jump between two server syncs.</li>
 *   <li>{@link #tickLights} places an invisible {@code minecraft:light} block above a hot anvil, so the
 *       work item actually lights up its surroundings like a torch. The block is client only: no
 *       collision, not rendered, never sent to the server, but it feeds the light engine.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.vintagefirma.VintageFirma.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorkItemLight
{
    /** Below this temperature the work item does not light anything. */
    private static final float MIN_TEMPERATURE = 500f;
    /** Temperature at which the light reaches its maximum. */
    private static final float MAX_TEMPERATURE = 1400f;

    /** How many blocks above the anvil the invisible light block sits. */
    private static final int LIGHT_HEIGHT = 2;

    /** Positions where we currently keep a client side light block. */
    private static final Set<BlockPos> PLACED = new HashSet<>();

    private WorkItemLight()
    {
    }

    /**
     * Places an invisible {@code minecraft:light} block above a hot anvil, so the work item actually
     * lights up its surroundings like a torch. The block is client only: no collision, not rendered,
     * never sent to the server, but it feeds the light engine.
     *
     * <p>The temperature comes from the anvil's own inventory ({@link ClientWorkCache#temperature}), which
     * is what the server has, so the light never disagrees with the colour of the metal.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null)
        {
            PLACED.clear();
            return;
        }

        final Set<BlockPos> wanted = new HashSet<>();

        for (Map.Entry<BlockPos, ClientWorkCache.ClientWork> entry : ClientWorkCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            final float temperature = ClientWorkCache.temperature(level, pos, entry.getValue());
            if (temperature < MIN_TEMPERATURE) continue;

            final BlockPos lightPos = pos.above(LIGHT_HEIGHT);
            if (!level.isLoaded(lightPos)) continue;

            final int light = Mth.clamp(Math.round(
                LightBlock.MAX_LEVEL * (temperature - MIN_TEMPERATURE) / (MAX_TEMPERATURE - MIN_TEMPERATURE)),
                1, LightBlock.MAX_LEVEL);
            final BlockState state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light);

            final BlockState existing = level.getBlockState(lightPos);
            if (existing == state)
            {
                wanted.add(lightPos);
                continue;
            }

            // Only ever replace plain air or one of our own light blocks
            if (!existing.isAir() && !existing.is(Blocks.LIGHT)) continue;

            level.setBlock(lightPos, state, 2 | 16);
            wanted.add(lightPos);
        }

        for (BlockPos pos : PLACED)
        {
            if (wanted.contains(pos)) continue;
            if (level.getBlockState(pos).is(Blocks.LIGHT))
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }

        PLACED.clear();
        PLACED.addAll(wanted);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ClientLevel level)
        {
            for (BlockPos pos : PLACED)
            {
                if (level.getBlockState(pos).is(Blocks.LIGHT))
                {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            PLACED.clear();
        }
    }
}