package com.vintagefirma.common;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vintagefirma.VintageFirma;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Testing commands, mostly useful to get a hot ingot without setting up a forge.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VFCommands
{
    private VFCommands()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("vintagefirma")
            .then(Commands.literal("heat")
                .executes(context -> heat(context.getSource(), 1000f, "hand"))
                .then(Commands.argument("temperature", DoubleArgumentType.doubleArg(0, 2000))
                    .executes(context -> heat(context.getSource(),
                        (float) DoubleArgumentType.getDouble(context, "temperature"), "hand"))
                    .then(Commands.argument("target", StringArgumentType.word())
                        .executes(context -> heat(context.getSource(),
                            (float) DoubleArgumentType.getDouble(context, "temperature"),
                            StringArgumentType.getString(context, "target")))
                        .then(Commands.literal("slot")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(0, 35))
                                .executes(context -> heatSlot(context.getSource(),
                                    (float) DoubleArgumentType.getDouble(context, "temperature"),
                                    IntegerArgumentType.getInteger(context, "slot"))))))))
            .then(Commands.literal("ingot")
                .executes(context -> giveIngot(context.getSource()))));
    }

    /**
     * Heats a single item:
     * <ul>
     *   <li>{@code hand} / {@code mainhand} - the main hand,</li>
     *   <li>{@code offhand} - the off hand,</li>
     *   <li>{@code anvil} - the work item of the anvil the player is looking at,</li>
     *   <li>{@code slot [index]} - a specific inventory slot.</li>
     * </ul>
     */
    private static int heat(CommandSourceStack source, float temperature, String target)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        if (target.equalsIgnoreCase("offhand"))
        {
            return applyHeat(source, player, player.getOffhandItem(), temperature, "offhand");
        }
        if (target.equalsIgnoreCase("anvil"))
        {
            return heatAnvil(source, player, temperature);
        }
        if (target.equalsIgnoreCase("hand") || target.equalsIgnoreCase("mainhand"))
        {
            return applyHeat(source, player, player.getMainHandItem(), temperature, "main hand");
        }
        source.sendFailure(Component.translatable("vintage_firma.command.unknown_target", target));
        return 0;
    }

    private static int heatSlot(CommandSourceStack source, float temperature, int slot)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        if (slot < 0 || slot >= player.getInventory().getContainerSize())
        {
            source.sendFailure(Component.translatable("vintage_firma.command.bad_slot", slot));
            return 0;
        }

        return applyHeat(source, player, player.getInventory().getItem(slot), temperature, "slot " + slot);
    }

    private static int heatAnvil(CommandSourceStack source, ServerPlayer player, float temperature)
    {
        final ServerLevel level = player.serverLevel();
        final BlockHitResult hit = (BlockHitResult) player.pick(6.0, 1f, false);
        if (hit.getType() != HitResult.Type.BLOCK)
        {
            source.sendFailure(Component.translatable("vintage_firma.command.no_anvil"));
            return 0;
        }

        final BlockPos pos = hit.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        if (!AnvilHandler.isOurAnvil(state))
        {
            source.sendFailure(Component.translatable("vintage_firma.command.no_anvil"));
            return 0;
        }

        final AnvilBlockEntity anvil = WorkSync.getAnvil(level, pos);
        if (anvil == null)
        {
            source.sendFailure(Component.translatable("vintage_firma.command.no_anvil"));
            return 0;
        }

        final ItemStack work = WorkSync.getWork(anvil);
        if (work.isEmpty())
        {
            source.sendFailure(Component.translatable("vintage_firma.command.no_work"));
            return 0;
        }

        return applyHeat(source, player, work, temperature, "anvil");
    }

    private static int applyHeat(CommandSourceStack source, ServerPlayer player, ItemStack stack,
                                 float temperature, String where)
    {
        if (!HeatCapability.has(stack))
        {
            source.sendFailure(Component.translatable("vintage_firma.command.cannot_heat"));
            return 0;
        }

        HeatCapability.setTemperature(stack, temperature);
        source.sendSuccess(() -> Component.translatable("vintage_firma.command.heated", where,
            Math.round(temperature)), false);
        return 1;
    }

    private static int giveIngot(CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        final Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc", "metal/ingot/wrought_iron"));
        if (item == null) return 0;

        final ItemStack stack = new ItemStack(item);
        HeatCapability.setTemperature(stack, 1000f);
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("vintage_firma.command.ingot"), false);
        return 1;
    }
}