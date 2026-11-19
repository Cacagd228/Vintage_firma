package com.vintagefirma.common;

import com.vintagefirma.network.S2COpenSelectionPacket;
import com.vintagefirma.network.VFNetwork;
import com.vintagefirma.registry.VFRecipes;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgingBonus;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import com.vintagefirma.VintageFirma;

/**
 * Replaces the TFC anvil interaction for the wrought iron anvil with the Vintage Story style voxel
 * smithing. Other anvils keep their normal behaviour.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnvilHandler
{
    public static final ResourceLocation WROUGHT_IRON_ANVIL = new ResourceLocation("tfc", "metal/anvil/wrought_iron");

    private static final Map<UUID, HammerMode> MODES = new HashMap<>();

    /** Anvil slot 0 is the main input, slot 1 the second one. */
    private static final int SLOT_INPUT = AnvilBlockEntity.SLOT_INPUT_MAIN;
    private static final int SLOT_SECOND = AnvilBlockEntity.SLOT_INPUT_SECOND;

    private AnvilHandler()
    {
    }

    /**
     * @return {@code true} for any TerraFirmaCraft anvil (metal or rock) that this mod takes over
     */
    public static boolean isOurAnvil(BlockState state)
    {
        return state.getBlock() instanceof net.dries007.tfc.common.blocks.devices.AnvilBlock
            || state.getBlock() instanceof net.dries007.tfc.common.blocks.rock.RockAnvilBlock;
    }

    /**
     * The tier of an anvil, as an index into {@code Metal.Tier}: 0 for rock anvils, 1 for copper, 2 for the
     * bronzes, 3 for wrought iron, 4 for steel, 5 for black steel and 6 for blue / red steel.
     */
    public static int tierOf(BlockState state)
    {
        return state.getBlock() instanceof net.dries007.tfc.common.blocks.devices.Tiered tiered
            ? tiered.getTier()
            : 0;
    }

    public static HammerMode getMode(Player player)
    {
        return MODES.getOrDefault(player.getUUID(), HammerMode.HEAVY_HIT);
    }

    public static void setMode(Player player, HammerMode mode)
    {
        MODES.put(player.getUUID(), mode);
    }

    // ---------------------------------------------------------------------------------------------
    // Server side interaction
    // ---------------------------------------------------------------------------------------------

    /** Shift + right click on the anvil: place an ingot / sheet, add metal, take the work item or weld. */
    public static void handleInteract(ServerPlayer player, BlockPos pos, InteractionHand hand)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !isInReach(player, pos)) return;

        final BlockState state = level.getBlockState(pos);
        if (!isOurAnvil(state)) return;

        final AnvilBlockEntity anvil = WorkSync.getAnvil(level, pos);
        if (anvil == null) return;

        final ItemStack held = player.getItemInHand(hand);
        final ItemStack work = WorkSync.getWork(anvil);

        // A work item already on the anvil: add metal, or take it with an empty hand
        if (!work.isEmpty())
        {
            if (WorkData.get(work) == null) return;

            if (held.isEmpty())
            {
                takeWork(player, level, anvil, work);
            }
            else
            {
                addIngot(player, level, anvil, work, held);
            }
            return;
        }

        if (held.isEmpty())
        {
            // Nothing on the anvil: shift + empty hand takes any ingots that are lying there
            takeLooseIngots(player, level, anvil);
            return;
        }

        // Putting an existing work item back on the anvil, for example after reheating it in a forge
        if (held.getItem() instanceof ItemWorkItem)
        {
            if (WorkData.get(held) == null) return;

            final ItemStack placed = held.copy();
            placed.setCount(1);
            held.shrink(1);
            WorkSync.setWork(anvil, placed);
            WorkSync.broadcast(level, pos);
            message(player, "work_placed");
            return;
        }

        // Welding: two hot ingots of the same metal, activated with flux
        if (isFlux(held))
        {
            tryWeld(player, level, anvil, pos);
            return;
        }

        // Bloom / pig iron: placing it on the anvil immediately starts the conversion work item
        final Conversion conversion = conversionFor(held);
        if (conversion != null)
        {
            startConversion(player, level, anvil, held, conversion);
            return;
        }

        if (!isMetalStack(held))
        {
            message(player, "not_smithable");
            return;
        }

        // No work item yet: the held ingot / sheet starts one
        final List<SmithingRecipe> recipes = matchingRecipes(level, held, tierOf(state));
        if (recipes.isEmpty())
        {
            // Distinguish "wrong item" from "this anvil is not good enough"
            if (isSmithable(level, held))
            {
                message(player, "anvil_too_low_tier");
            }
            else
            {
                message(player, "not_smithable");
            }
            return;
        }

        VFNetwork.sendTo(player, S2COpenSelectionPacket.of(pos, recipes));
    }

    /** @return {@code true} if the stack is an ingot or a sheet of some metal */
    public static boolean isMetalStack(ItemStack stack)
    {
        return metalOf(stack) != null;
    }

    /**
     * A bloom / pig iron conversion: placing the hot item on the anvil immediately creates a work item
     * with the full amount of metal plus a cap of slag, and a single recipe that turns it into an ingot
     * once all the slag is hammered off.
     */
    private record Conversion(ResourceLocation recipe, String resultItem, String metal, int slagLayers)
    {
    }

    /**
     * Bloom / pig iron conversions. Each one has a single smithing recipe (see the data folder), so the
     * player only has to hammer the slag off the cap and the work item becomes the ingot.
     */
    private static final Map<String, Conversion> CONVERSIONS = Map.of(
        "raw_iron_bloom", new Conversion(
            new ResourceLocation(VintageFirma.MOD_ID, "bloom/wrought_iron_from_raw"),
            "tfc:metal/ingot/wrought_iron", "wrought_iron", 2),
        "refined_iron_bloom", new Conversion(
            new ResourceLocation(VintageFirma.MOD_ID, "bloom/wrought_iron_from_refined"),
            "tfc:metal/ingot/wrought_iron", "wrought_iron", 1),
        "pig_iron", new Conversion(
            new ResourceLocation(VintageFirma.MOD_ID, "bloom/steel_from_pig_iron"),
            "tfc:metal/ingot/steel", "steel", 2)
    );

    @Nullable
    private static Conversion conversionFor(ItemStack stack)
    {
        if (stack.isEmpty()) return null;
        final ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : CONVERSIONS.get(id.getPath().substring(id.getPath().lastIndexOf('/') + 1));
    }

    private static void startConversion(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil,
                                        ItemStack stack, Conversion conversion)
    {
        if (!isHot(stack))
        {
            message(player, "ingot_too_cold");
            return;
        }

        final BlockPos pos = anvil.getBlockPos();
        final byte[] voxels = Voxels.createBloom(conversion.slagLayers());
        final ItemStack work = new ItemStack(com.vintagefirma.registry.VFItems.WORK_ITEM.get());

        WorkData.set(work, new WorkData(voxels, conversion.metal(), conversion.recipe(), 1, 0, 0, 0, false));
        HeatCapability.setTemperature(work, HeatCapability.getTemperature(stack));

        stack.shrink(1);
        WorkSync.setWork(anvil, work);
        WorkSync.broadcast(level, pos);

        message(player, "conversion_started", Component.translatable("vintage_firma.metal." + conversion.metal()));
        player.displayClientMessage(Component.translatable("vintage_firma.message.conversion_hint"), true);
    }

    public static void handleSelectRecipe(ServerPlayer player, BlockPos pos, ResourceLocation recipeId)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !isInReach(player, pos)) return;
        if (!isOurAnvil(level.getBlockState(pos))) return;

        final AnvilBlockEntity anvil = WorkSync.getAnvil(level, pos);
        if (anvil == null || !WorkSync.getWork(anvil).isEmpty()) return;

        final SmithingRecipe recipe = findRecipe(level, recipeId);
        if (recipe == null) return;

        // The anvil must be of a high enough tier for this recipe
        if (tierOf(level.getBlockState(pos)) < recipe.tier())
        {
            message(player, "anvil_too_low_tier", recipe.getResult().getHoverName());
            return;
        }

        final InteractionHand hand = findIngotHand(player, recipe);
        if (hand == null)
        {
            message(player, "no_ingot");
            return;
        }

        final ItemStack ingot = player.getItemInHand(hand);
        if (!isHot(ingot))
        {
            message(player, "ingot_too_cold");
            return;
        }

        // A sheet starts from a 9 x 9 plate, an ingot from the compact 7 x 3 x 2 footprint
        final boolean sheet = isSheet(ingot);
        final byte[] voxels = sheet ? Voxels.createSheet() : Voxels.createIngot();

        final ItemStack work = new ItemStack(com.vintagefirma.registry.VFItems.WORK_ITEM.get());
        final WorkData data = sheet
            ? new WorkData(voxels, recipe.getMetal(), recipeId, 1, 1, 0, 0, false)
            : new WorkData(voxels, recipe.getMetal(), recipeId, 1, 0, 0, 0, false);
        WorkData.set(work, data);
        HeatCapability.setTemperature(work, HeatCapability.getTemperature(ingot));

        ingot.shrink(1);
        WorkSync.setWork(anvil, work);
        WorkSync.broadcast(level, pos);

        message(player, "work_started", recipe.getResult().getHoverName());
    }

    public static void handleHammerHit(ServerPlayer player, BlockPos pos, int x, int y, int z)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !isInReach(player, pos)) return;
        if (!isOurAnvil(level.getBlockState(pos))) return;

        final AnvilBlockEntity anvil = WorkSync.getAnvil(level, pos);
        if (anvil == null) return;

        final ItemStack work = WorkSync.getWork(anvil);
        final WorkData data = WorkData.get(work);
        if (data == null) return;
        if (!player.getMainHandItem().is(TFCTags.Items.HAMMERS)) return;

        if (!Cooldowns.ready(player.getUUID())) return;

        if (!Voxels.inBounds(x, y, z) || Voxels.get(data.voxels(), x, y, z) == Voxels.EMPTY) return;

        // A single ingot can only be worked while it is still a compact stack: as soon as the player starts
        // spreading the metal out, there is no metal left to hit. This is handled by the client, which only
        // sends a hit for a voxel it can actually see, so no extra check is needed here.

        if (!isHot(work))
        {
            // Remember that the work item cooled down completely; the penalty is only applied if the
            // player heats it up again and keeps working instead of starting over.
            WorkData.set(work, data.markCooled());
            WorkSync.setWork(anvil, work);
            WorkSync.broadcast(level, pos);
            message(player, "work_too_cold");
            return;
        }

        Cooldowns.trigger(player.getUUID());
        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), Cooldowns.ACTION_TICKS);

        // A strike after the work item cooled down counts as a cooling event, and the strike itself counts
        // as one step towards the work (TFC's forging bonus is based on the strike-to-optimum ratio).
        final WorkData cooled = data.countCoolingIfAny();
        final WorkData hitData = cooled.withHits(cooled.hits() + 1);
        WorkData.set(work, hitData);

        final HammerMode mode = getMode(player);
        switch (mode)
        {
            case HEAVY_HIT -> Voxels.heavyHit(data.voxels(), x, y, z);
            case SPLIT -> Voxels.split(data.voxels(), x, y, z);
            default ->
            {
                final Direction facing = Direction.getNearest(player.getViewVector(1f).x, 0,
                    player.getViewVector(1f).z);
                final int[] direction = mode.direction(facing);
                if (direction != null)
                {
                    Voxels.upset(data.voxels(), x, y, z, direction[0], direction[1]);
                }
            }
        }

        // Every strike costs the hammer one point of durability, like in Vintage Story
        final ItemStack hammer = player.getMainHandItem();
        if (hammer.isDamageableItem())
        {
            hammer.hurtAndBreak(1, player, item -> player.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }

        WorkData.set(work, hitData);
        WorkSync.setWork(anvil, work);
        WorkSync.broadcast(level, pos);

        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35f, 1.5f);
        spawnSparks(level, pos, x, y, z, HeatCapability.getTemperature(work));

        final SmithingRecipe recipe = findRecipe(level, hitData.recipe());
        if (recipe != null)
        {
            checkFinished(player, level, anvil, work, pos, recipe);
        }
    }

    private static void takeWork(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil, ItemStack work)
    {
        WorkSync.clearWork(anvil);
        if (!player.getInventory().add(work))
        {
            player.drop(work, false);
        }
        WorkSync.broadcast(level, anvil.getBlockPos());
        message(player, "work_taken");
    }

    /** Shift + empty hand takes the ingots / sheets that lie on the anvil. */
    private static void takeLooseIngots(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil)
    {
        final var inventory = WorkSync.inventory(anvil);
        if (inventory == null) return;

        boolean took = false;
        for (int slot = AnvilBlockEntity.SLOT_INPUT_MAIN; slot <= AnvilBlockEntity.SLOT_INPUT_SECOND; slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            final ItemStack taken = stack.copy();
            if (player.getInventory().add(taken))
            {
                WorkSync.setStack(anvil, slot, ItemStack.EMPTY);
                took = true;
            }
            else
            {
                player.drop(taken, false);
                WorkSync.setStack(anvil, slot, ItemStack.EMPTY);
                took = true;
            }
        }

        if (took)
        {
            WorkSync.broadcast(level, anvil.getBlockPos());
        }
    }

    /** @return {@code true} if the stack is flux (used for welding) */
    public static boolean isFlux(ItemStack stack)
    {
        return !stack.isEmpty() && stack.is(net.dries007.tfc.common.TFCTags.Items.FLUX);
    }

    /**
     * Welding: two hot ingots (or two sheets) of the same metal become one double ingot / double sheet.
     * Both have to be at welding temperature and the player has to hold flux.
     *
     * <p>Only a rock anvil (tier 0) is restricted: it can weld copper and nothing else.
     */
    private static void tryWeld(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil, BlockPos pos)
    {
        final int tier = tierOf(level.getBlockState(pos));

        final var inventory = WorkSync.inventory(anvil);
        if (inventory == null) return;

        final ItemStack first = inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN);
        final ItemStack second = inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_SECOND);
        if (first.isEmpty() || second.isEmpty())
        {
            message(player, "weld_need_two");
            return;
        }

        final String metal = metalOf(first);
        if (metal == null || !metal.equals(metalOf(second)))
        {
            message(player, "weld_different_metal");
            return;
        }

        if (tier == 0 && !"copper".equals(metal))
        {
            message(player, "weld_stone_anvil_copper_only");
            return;
        }

        if (!canWeld(first) || !canWeld(second))
        {
            message(player, "weld_too_cold");
            return;
        }

        final boolean ingots = isIngot(first) && isIngot(second);
        final boolean sheets = isSheet(first) && isSheet(second);
        if (!ingots && !sheets)
        {
            message(player, "weld_need_pair");
            return;
        }

        final String resultPath = "metal/" + (ingots ? "double_ingot/" : "double_sheet/") + metal;
        final Item resultItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tfc", resultPath));
        if (resultItem == null)
        {
            message(player, "weld_no_result");
            return;
        }

        final ItemStack result = new ItemStack(resultItem);
        final float temperature = Math.max(HeatCapability.getTemperature(first),
            HeatCapability.getTemperature(second));
        if (HeatCapability.has(result))
        {
            HeatCapability.setTemperature(result, temperature);
        }

        WorkSync.setStack(anvil, AnvilBlockEntity.SLOT_INPUT_SECOND, ItemStack.EMPTY);
        WorkSync.setStack(anvil, AnvilBlockEntity.SLOT_INPUT_MAIN, result);

        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
        message(player, "weld_done", result.getHoverName());
        WorkSync.broadcast(level, pos);
    }

    /** @return {@code true} if the stack is an ingot (not a sheet, not a double ingot) */
    public static boolean isIngot(ItemStack stack)
    {
        return isItemId(stack, "metal/ingot/");
    }

    /** @return {@code true} if the stack is a double ingot */
    public static boolean isDoubleIngot(ItemStack stack)
    {
        return isItemId(stack, "metal/double_ingot/");
    }

    /** @return {@code true} if the stack is a sheet */
    public static boolean isSheet(ItemStack stack)
    {
        return isItemId(stack, "metal/sheet/");
    }

    private static boolean isItemId(ItemStack stack, String pathPart)
    {
        if (stack.isEmpty()) return false;
        final ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getPath().startsWith(pathPart);
    }

    /** @return {@code true} if the stack is at welding temperature */
    public static boolean canWeld(ItemStack stack)
    {
        return HeatCapability.has(stack) && HeatCapability.get(stack).canWeld();
    }

    private static void addIngot(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil, ItemStack work, ItemStack ingot)
    {
        final WorkData data = WorkData.get(work);
        if (data == null) return;

        final SmithingRecipe recipe = findRecipe(level, data.recipe());
        if (recipe == null) return;

        if (!recipe.getIngredient().test(ingot))
        {
            message(player, "wrong_metal");
            return;
        }

        if (!isHot(ingot))
        {
            message(player, "ingot_too_cold");
            return;
        }

        final boolean sheet = isSheet(ingot);
        final int before = data.voxelCount();
        final int added = sheet ? Voxels.addSheet(data.voxels()) : Voxels.addIngot(data.voxels());
        if (added == 0)
        {
            message(player, sheet ? "needs_flattening_sheet" : "needs_flattening");
            return;
        }

        final float ingotTemperature = HeatCapability.getTemperature(ingot);
        final float workTemperature = HeatCapability.getTemperature(work);
        final float temperature = (workTemperature * before + ingotTemperature * added) / (before + added);

        final WorkData newData = sheet ? data.withSheet() : data.withIngot();
        WorkData.set(work, newData);
        HeatCapability.setTemperature(work, temperature);

        ingot.shrink(1);
        WorkSync.setWork(anvil, work);
        WorkSync.broadcast(level, anvil.getBlockPos());

        message(player, sheet ? "sheet_added" : "ingot_added", added);
        checkFinished(player, level, anvil, work, anvil.getBlockPos(), recipe);
    }

    private static void checkFinished(ServerPlayer player, ServerLevel level, AnvilBlockEntity anvil, ItemStack work,
        BlockPos pos, SmithingRecipe recipe)
    {
        final WorkData data = WorkData.get(work);
        if (data == null) return;

        // Slag has to be gone before the work item can be called done
        if (Voxels.countSlag(data.voxels()) > 0) return;

        if (!Voxels.matches(data.voxels(), recipe.patternVoxels())) return;

        final ItemStack result = recipe.getResult().copy();
        final float temperature = HeatCapability.getTemperature(work);

        // Quality is TFC's own forging bonus: the ratio of strikes taken to the optimum, with one extra
        // "step" counted per cooling. TFC already handles the tooltip, the durability and the damage.
        final float ratio = (float) (data.hits() + data.coolings()) / recipe.optimalHits();
        ForgingBonus.set(result, ForgingBonus.byRatio(ratio));

        WorkSync.clearWork(anvil);
        if (HeatCapability.has(result))
        {
            HeatCapability.setTemperature(result, temperature);
        }
        if (!player.getInventory().add(result))
        {
            player.drop(result, false);
        }

        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9f, 0.9f);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.4f);

        final ForgingBonus bonus = ForgingBonus.get(result);
        message(player, "completed", result.getHoverName());
        if (bonus != ForgingBonus.NONE)
        {
            player.displayClientMessage(net.dries007.tfc.util.Helpers.translateEnum(bonus), true);
        }
        WorkSync.broadcast(level, pos);
    }

    /**
     * The ratio TFC's forging bonus is derived from: the number of strikes (plus a cooling as one extra
     * step) divided by the optimum for this recipe.
     */
    public static float computeRatio(WorkData data, SmithingRecipe recipe)
    {
        return (float) (data.hits() + data.coolings()) / recipe.optimalHits();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    public static boolean isHot(ItemStack stack)
    {
        return HeatCapability.has(stack) && HeatCapability.get(stack).canWork();
    }

    private static boolean isInReach(ServerPlayer player, BlockPos pos)
    {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }

    private static void spawnSparks(ServerLevel level, BlockPos pos, int x, int y, int z, float temperature)
    {
        if (temperature < 800) return;
        final double px = pos.getX() + (x + 0.5) / 16.0;
        final double py = pos.getY() + VoxelRaycast.VOXEL_Y_OFFSET + (y + 0.5) / 16.0;
        final double pz = pos.getZ() + (z + 0.5) / 16.0;
        level.sendParticles(ParticleTypes.LAVA, px, py, pz, 3, 0.03, 0.03, 0.03, 0.01);
    }

    @Nullable
    private static InteractionHand findIngotHand(ServerPlayer player, SmithingRecipe recipe)
    {
        if (isWorkable(recipe, player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isWorkable(recipe, player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    /**
     * @return {@code true} if the stack can be used to start this recipe: either it is the ingredient itself
     *         (an ingot), or it is a sheet of the same metal (a sheet can be forged like the ingot it came from).
     */
    public static boolean isWorkable(SmithingRecipe recipe, ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        if (recipe.getIngredient().test(stack)) return true;

        final String metal = metalOf(stack);
        return metal != null && metal.equals(recipe.getMetal());
    }

    /** @return the metal id (e.g. {@code wrought_iron}) of an ingot or sheet stack, or {@code null}. */
    @Nullable
    public static String metalOf(ItemStack stack)
    {
        if (stack.isEmpty()) return null;

        final net.dries007.tfc.util.Metal ingot = net.dries007.tfc.util.Metal.getFromIngot(stack);
        if (ingot != null) return ingot.getId().getPath();

        final net.dries007.tfc.util.Metal sheet = net.dries007.tfc.util.Metal.getFromSheet(stack);
        return sheet != null ? sheet.getId().getPath() : null;
    }

    public static List<SmithingRecipe> matchingRecipes(ServerLevel level, ItemStack stack, int tier)
    {
        final boolean sheet = isSheet(stack);
        final String metal = metalOf(stack);
        final List<SmithingRecipe> recipes = new ArrayList<>();

        for (SmithingRecipe recipe : level.getRecipeManager().getAllRecipesFor(VFRecipes.SMITHING_TYPE.get()))
        {
            // The anvil tier has to cover the recipe's tier, like in TFC ("tier n can forge everything
            // up to tier n")
            if (recipe.tier() > tier) continue;

            if (recipe.getIngredient().test(stack))
            {
                recipes.add(recipe);
            }
            else if (sheet && metal != null && metal.equals(recipe.getMetal())
                && !recipe.getResult().is(stack.getItem()))
            {
                // A sheet can be worked like the ingot of the same metal, but you cannot forge a sheet into itself
                recipes.add(recipe);
            }
        }

        recipes.sort(Comparator.comparing(recipe -> recipe.getResult().getHoverName().getString()));
        return recipes;
    }

    /** @return {@code true} if any recipe (of any tier) accepts this stack, i.e. the item is smithable at all */
    public static boolean isSmithable(ServerLevel level, ItemStack stack)
    {
        final boolean sheet = isSheet(stack);
        final String metal = metalOf(stack);

        for (SmithingRecipe recipe : level.getRecipeManager().getAllRecipesFor(VFRecipes.SMITHING_TYPE.get()))
        {
            if (recipe.getIngredient().test(stack)) return true;
            if (sheet && metal != null && metal.equals(recipe.getMetal())) return true;
        }
        return false;
    }

    @Nullable
    public static SmithingRecipe findRecipe(ServerLevel level, ResourceLocation id)
    {
        for (SmithingRecipe recipe : level.getRecipeManager().getAllRecipesFor(VFRecipes.SMITHING_TYPE.get()))
        {
            if (recipe.getId().equals(id))
            {
                return recipe;
            }
        }
        return null;
    }

    public static void message(Player player, String key, Object... args)
    {
        player.displayClientMessage(Component.translatable("vintage_firma.message." + key, args)
            .withStyle(ChatFormatting.GRAY), true);
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        if (!isOurAnvil(level.getBlockState(pos))) return;

        final Player player = event.getEntity();
        if (player.isSpectator()) return;

        // Shift interactions are handled through our own packet on the client
        if (player.isShiftKeyDown()) return;

        // Never open the TFC anvil screen on this block, but do allow placing blocks against it
        if (event.getItemStack().getItem() instanceof BlockItem) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event)
    {
        WorkSync.syncChunk(event.getPlayer(), event.getLevel(), event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!isOurAnvil(event.getLevel().getBlockState(event.getPos()))) return;

        // Holding the attack key would otherwise keep breaking the anvil once the work item is gone.
        // With a hammer in hand the anvil is never damaged; use a pickaxe to actually break it.
        if (!event.getEntity().getMainHandItem().is(TFCTags.Items.HAMMERS)) return;

        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isOurAnvil(level.getBlockState(event.getPos()))) return;

        final AnvilBlockEntity anvil = WorkSync.getAnvil(level, event.getPos());
        if (anvil != null && WorkData.get(WorkSync.getWork(anvil)) != null)
        {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player)
            {
                message(player, "take_work_first");
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;

        Cooldowns.tick();

        final MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            for (ServerLevel level : server.getAllLevels())
            {
                WorkSync.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)
    {
        MODES.remove(event.getEntity().getUUID());
        Cooldowns.clear(event.getEntity().getUUID());
    }
}
