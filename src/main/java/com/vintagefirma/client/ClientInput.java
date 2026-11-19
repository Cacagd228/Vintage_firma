package com.vintagefirma.client;

import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.AnvilHandler;
import com.vintagefirma.common.ClayHandler;
import com.vintagefirma.common.HammerMode;
import com.vintagefirma.network.C2SAnvilInteractPacket;
import com.vintagefirma.network.C2SClayInteractPacket;
import com.vintagefirma.network.C2SClaySculptPacket;
import com.vintagefirma.network.C2SHammerHitPacket;
import com.vintagefirma.network.C2SSetHammerModePacket;
import com.vintagefirma.network.VFNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.dries007.tfc.common.TFCTags;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInput
{
    public static final KeyMapping CYCLE_MODE = new KeyMapping("key.vintage_firma.cycle_mode",
        InputConstants.KEY_V, "key.categories.vintage_firma");

    /** Hammer mode selected on the client. The server keeps its own copy per player. */
    public static HammerMode mode = HammerMode.HEAVY_HIT;

    /** Set to {@code true} while the player is holding the attack key down. */
    private static boolean attackHeld = false;

    private ClientInput()
    {
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) return;

        // Clay forming: shape voxels by hand. Aim at the piece / the floor and right click to add clay,
        // left click to remove the top voxel of the aimed column (like Vintage Story).
        if (!player.isShiftKeyDown() && ClayHandler.isClay(player.getMainHandItem()))
        {
            final ClientTargeting.ClayColumn column = ClientTargeting.findClayColumn(player);
            if (column != null && event.isAttack())
            {
                event.setCanceled(true);
                player.swing(InteractionHand.MAIN_HAND);
                VFNetwork.sendToServer(new C2SClaySculptPacket(column.pos(), column.x(), 0, column.z(), false));
                return;
            }
            if (column != null && event.isUseItem())
            {
                event.setCanceled(true);
                VFNetwork.sendToServer(new C2SClaySculptPacket(column.pos(), column.x(), 0, column.z(), true));
                return;
            }
        }

        if (event.isAttack())
        {
            attackHeld = true;

            if (!player.getMainHandItem().is(TFCTags.Items.HAMMERS)) return;

            final ClientTargeting.Target target = ClientTargeting.findVoxelTarget(player);
            if (target == null) return;

            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND);
            VFNetwork.sendToServer(new C2SHammerHitPacket(target.pos(), target.x(), target.y(), target.z()));
            return;
        }

        if (event.isUseItem())
        {
            // Clay forming: right click a block while holding clay opens the "what to shape" menu.
            // This runs before the vanilla item use, so it also cancels TFC's clay knapping on that click.
            if (ClayHandler.isClay(player.getMainHandItem())
                && minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)
            {
                event.setCanceled(true);
                VFNetwork.sendToServer(new C2SClayInteractPacket(blockHit.getBlockPos()));
                return;
            }

            final ClientTargeting.Target voxelTarget = ClientTargeting.findVoxelTarget(player);
            final net.minecraft.core.BlockPos blockTarget = voxelTarget != null
                ? voxelTarget.pos()
                : ClientTargeting.findAnvilBlock(player);

            if (blockTarget == null) return;

            if (player.isShiftKeyDown())
            {
                event.setCanceled(true);
                VFNetwork.sendToServer(new C2SAnvilInteractPacket(blockTarget, false));
            }
            else if (voxelTarget != null)
            {
                // Aiming at the work item voxels must not use whatever block is behind them
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (!AnvilHandler.isOurAnvil(event.getLevel().getBlockState(event.getPos()))) return;
        if (!event.getEntity().getMainHandItem().is(TFCTags.Items.HAMMERS)) return;

        // Holding the attack key keeps hammering, so the block must never be damaged on the client either
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;

        // Holding the attack key down keeps hammering, like holding the use key in Vintage Story
        if (attackHeld && player != null && minecraft.screen == null
            && player.getMainHandItem().is(TFCTags.Items.HAMMERS))
        {
            if (minecraft.options.keyAttack.isDown())
            {
                final ClientTargeting.Target target = ClientTargeting.findVoxelTarget(player);
                if (target != null)
                {
                    VFNetwork.sendToServer(new C2SHammerHitPacket(target.pos(), target.x(), target.y(), target.z()));
                }
            }
            else
            {
                attackHeld = false;
            }
        }

        // The hammer mode is chosen from a menu, so the key only opens it
        if (minecraft.screen == null && player != null && isHoldingHammer(player)
            && CYCLE_MODE.consumeClick())
        {
            minecraft.setScreen(new HammerModeScreen(null, mode));
        }
    }

    private static boolean isHoldingHammer(Player player)
    {
        return player.getMainHandItem().is(TFCTags.Items.HAMMERS)
            || player.getOffhandItem().is(TFCTags.Items.HAMMERS);
    }

    /** Resets the cached hammer mode when the player leaves the world. */
    public static void reset()
    {
        mode = HammerMode.HEAVY_HIT;
        attackHeld = false;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ClientLevel)
        {
            ClientWorkCache.clear();
            ClientClayCache.clear();
            reset();
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        if (!isHoldingHammer(player)) return;

        final Component text = Component.translatable("vintage_firma.hud.mode",
            Component.translatable(mode.translationKey()));
        final int width = minecraft.font.width(text);
        event.getGuiGraphics().drawString(minecraft.font, text,
            (event.getWindow().getGuiScaledWidth() - width) / 2,
            event.getWindow().getGuiScaledHeight() - 62, 0xFFE0C080, true);
    }
}
