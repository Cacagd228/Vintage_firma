package com.vintagefirma.common;

import com.vintagefirma.VintageFirma;
import com.vintagefirma.network.S2COpenClaySelectionPacket;
import com.vintagefirma.network.S2CSyncClayPacket;
import com.vintagefirma.network.VFNetwork;
import com.vintagefirma.registry.VFRecipes;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Server side handler for Vintage Story style clay forming. The player holds a clay item, shift + right
 * clicks the top of a block to pick what to shape, then adds and removes voxels by hand until the piece
 * matches the recipe. The finished (unfired) item drops and can be fired in a kiln.
 *
 * <p>Clay pieces live on top of ordinary blocks, which have no block entity, so they are stored in
 * {@link ClaySites} and synced to clients with {@link S2CSyncClayPacket}.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClayHandler
{
    private static final double REACH_SQR = 64.0;
    private static final double SYNC_RANGE_SQR = 64.0 * 64.0;

    /** TFC's own clay items (clay blocks' items) and the vanilla clay ball used for knapping. */
    private static final TagKey<Item> CLAY_ITEM_TAG =
        TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "clay"));
    private static final TagKey<Item> CLAY_KNAPPING_TAG =
        TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "clay_knapping"));

    private ClayHandler()
    {
    }

    // ---------------------------------------------------------------------------------------------
    // Clay items
    // ---------------------------------------------------------------------------------------------

    /**
     * @return {@code true} if the stack is a clay item that can be shaped.
     *
     * <p>TFC's clay knapping is driven by the {@code tfc:clay_knapping} tag, which contains the vanilla
     * {@code minecraft:clay_ball} — that is the item a player actually holds and knaps with. TFC's own
     * clay items live in the {@code tfc:clay} tag. Both are accepted here (plus a path fallback).
     */
    public static boolean isClay(ItemStack stack)
    {
        if (stack.isEmpty()) return false;

        if (stack.is(CLAY_ITEM_TAG) || stack.is(CLAY_KNAPPING_TAG)) return true;

        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("tfc")) return false;

        final String path = id.getPath();
        return path.startsWith("clay/") || path.startsWith("clay_grass/") || path.equals("fire_clay");
    }

    /** @return {@code true} if the stack is fire clay (used for crucibles / fire bricks). */
    public static boolean isFireClay(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("tfc") && id.getPath().equals("fire_clay");
    }

    @Nullable
    private static ItemStack heldClay(ServerPlayer player)
    {
        if (isClay(player.getMainHandItem())) return player.getMainHandItem();
        if (isClay(player.getOffhandItem())) return player.getOffhandItem();
        return null;
    }

    /** Takes one clay item from the main hand or the offhand. */
    private static boolean consumeClay(ServerPlayer player)
    {
        final InteractionHand hand = isClay(player.getMainHandItem())
            ? InteractionHand.MAIN_HAND
            : isClay(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return false;

        player.getItemInHand(hand).shrink(1);
        return true;
    }

    private static boolean holdingClay(ServerPlayer player)
    {
        return isClay(player.getMainHandItem()) || isClay(player.getOffhandItem());
    }

    // ---------------------------------------------------------------------------------------------
    // Recipes
    // ---------------------------------------------------------------------------------------------

    public static List<ClayRecipe> clayRecipes(ServerLevel level)
    {
        final List<ClayRecipe> recipes = new ArrayList<>(
            level.getRecipeManager().getAllRecipesFor(VFRecipes.CLAY_TYPE.get()));
        recipes.sort(Comparator.comparing(recipe -> recipe.getResult().getHoverName().getString()));
        return recipes;
    }

    @Nullable
    public static ClayRecipe findClay(ServerLevel level, ResourceLocation id)
    {
        for (ClayRecipe recipe : level.getRecipeManager().getAllRecipesFor(VFRecipes.CLAY_TYPE.get()))
        {
            if (recipe.getId().equals(id))
            {
                return recipe;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Starting a piece
    // ---------------------------------------------------------------------------------------------

    public static void handleSelect(ServerPlayer player, BlockPos pos, ResourceLocation recipeId)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        final ClaySites sites = ClaySites.get(level);
        if (sites.has(pos)) return;

        final ClayRecipe recipe = findClay(level, recipeId);
        if (recipe == null) return;

        if (!holdingClay(player))
        {
            message(player, "clay_need_clay");
            return;
        }

        if (!consumeClay(player))
        {
            message(player, "clay_need_clay");
            return;
        }

        // Start with an empty grid; the player builds the piece up voxel by voxel (no starter blob).
        final byte[] voxels = Voxels.empty();
        final int clay = recipe.voxelsPerClay();

        final ClayWork work = new ClayWork(voxels, recipe.getId(), clay);
        sites.put(pos, work);
        broadcast(level, pos);

        level.playSound(null, pos, SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
        message(player, "clay_started", recipe.getResult().getHoverName());
    }

    // ---------------------------------------------------------------------------------------------
    // Shaping
    // ---------------------------------------------------------------------------------------------

    public static void handleSculpt(ServerPlayer player, BlockPos pos, int x, int y, int z, boolean add)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        final ClaySites sites = ClaySites.get(level);
        final ClayWork work = sites.get(pos);
        if (work == null) return;

        final ClayRecipe recipe = findClay(level, work.recipe());
        if (recipe == null) return;

        // The client sends a column (x, z); y is ignored. Only the current (lowest unfinished) layer may
        // be edited - the next layer unlocks once this one matches, exactly like Vintage Story.
        if (x < 0 || x >= Voxels.SIZE_X || z < 0 || z >= Voxels.SIZE_Z) return;

        final byte[] voxels = work.voxels();
        final byte[] pattern = recipe.patternVoxels();
        final int layer = ClayWork.currentLayer(voxels, pattern);
        VintageFirma.LOGGER.info("[clay] sculpt add={} col=({},{}) layer={} clay={}", add, x, z, layer, work.clay());
        if (layer >= Voxels.SIZE_Y) return; // the piece already matches

        // Rate limit: 0.3 s between actions (shared with the hammer and stone chipping).
        if (!Cooldowns.ready(player.getUUID())) return;

        if (add)
        {
            if (Voxels.get(voxels, x, layer, z) != Voxels.EMPTY) return;

            int clay = work.clay();
            if (clay <= 0)
            {
                if (!consumeClay(player))
                {
                    message(player, "clay_need_clay");
                    return;
                }
                clay += recipe.voxelsPerClay();
            }

            final byte[] updated = voxels.clone();
            Voxels.set(updated, x, layer, z, Voxels.METAL);

            final ClayWork newWork = new ClayWork(updated, work.recipe(), clay - 1);
            sites.put(pos, newWork);
            broadcast(level, pos);

            level.playSound(null, pos, SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.5f, 1.3f);
            Cooldowns.trigger(player.getUUID());
            player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), Cooldowns.ACTION_TICKS);
            checkComplete(player, level, pos, newWork, recipe);
            return;
        }

        if (Voxels.get(voxels, x, layer, z) == Voxels.EMPTY) return;

        final byte[] updated = voxels.clone();
        Voxels.set(updated, x, layer, z, Voxels.EMPTY);

        final ClayWork newWork = new ClayWork(updated, work.recipe(), work.clay() + 1);
        sites.put(pos, newWork);
        broadcast(level, pos);

        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.4f, 1.2f);
        Cooldowns.trigger(player.getUUID());
        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), Cooldowns.ACTION_TICKS);
        checkComplete(player, level, pos, newWork, recipe);
    }

    private static void checkComplete(ServerPlayer player, ServerLevel level, BlockPos pos, ClayWork work,
        ClayRecipe recipe)
    {
        if (!Voxels.matches(work.voxels(), recipe.patternVoxels())) return;

        ClaySites.get(level).remove(pos);
        broadcast(level, pos);

        final ItemStack result = recipe.getResult().copy();
        if (!player.getInventory().add(result))
        {
            player.drop(result, false);
        }

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.2f);
        message(player, "clay_completed", result.getHoverName());
    }

    // ---------------------------------------------------------------------------------------------
    // Syncing
    // ---------------------------------------------------------------------------------------------

    public static void broadcast(ServerLevel level, BlockPos pos)
    {
        final ClayWork work = ClaySites.get(level).get(pos);
        final S2CSyncClayPacket packet = work != null ? S2CSyncClayPacket.of(pos, work) : S2CSyncClayPacket.clear(pos);

        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= SYNC_RANGE_SQR)
            {
                VFNetwork.sendTo(player, packet);
            }
        }
    }

    private static void sendTo(ServerPlayer player, BlockPos pos, ClayWork work)
    {
        final S2CSyncClayPacket packet = work != null ? S2CSyncClayPacket.of(pos, work) : S2CSyncClayPacket.clear(pos);
        VFNetwork.sendTo(player, packet);
    }

    public static void message(Player player, String key, Object... args)
    {
        // Clay action-bar messages are intentionally disabled (per user request).
        // Clay forming gives feedback through sound and the world visuals instead.
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    /**
     * Right click the top of a block with clay in hand: open the "what to shape" dialog.
     *
     * <p>Handled on the server at {@link EventPriority#HIGH} — before TFC's own (lowest priority) handler —
     * and the event is always cancelled when clay meets a block top. That is what stops TFC's 2D clay
     * knapping from opening (it is registered as an item-use interaction and would otherwise trigger on
     * right click regardless of where the player looks).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        final ItemStack held = event.getItemStack();
        if (!isClay(held)) return;

        final BlockPos pos = event.getPos();
        final Direction face = event.getHitVec().getDirection();
        VintageFirma.LOGGER.info("[clay] RightClickBlock at {} face={} item={}", pos, face,
            ForgeRegistries.ITEMS.getKey(held.getItem()));

        // We own this interaction: cancel it so TFC's knapping / item use never runs.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        handleInteract(player, pos);
    }

    /**
     * Shift + right click a block with clay in hand: open the "what to shape" dialog. Called from
     * {@code C2SClayInteractPacket} (the client intercepts the shift click, exactly like the anvil).
     */
    public static void handleInteract(ServerPlayer player, BlockPos pos)
    {
        final ServerLevel level = player.serverLevel();
        VintageFirma.LOGGER.info("[clay] handleInteract pos={} loaded={}", pos, level.isLoaded(pos));
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        // A piece is already being shaped here; let the normal sculpting handle it.
        if (ClaySites.get(level).has(pos))
        {
            VintageFirma.LOGGER.info("[clay] a piece already exists at {}", pos);
            return;
        }

        final BlockState state = level.getBlockState(pos);
        final boolean sturdy = state.isFaceSturdy(level, pos, Direction.UP);
        VintageFirma.LOGGER.info("[clay] block={} sturdyUp={}", state, sturdy);
        if (!sturdy)
        {
            message(player, "clay_need_surface");
            return;
        }

        final ItemStack held = heldClay(player);
        if (held == null) return;
        final boolean heldFire = isFireClay(held);

        // Only show recipes for the kind of clay in hand: fire clay -> fire-clay items (crucible, ...),
        // regular clay -> everything else.
        final var fireClayItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc", "fire_clay"));
        final ItemStack fireClayStack = fireClayItem != null ? new ItemStack(fireClayItem) : ItemStack.EMPTY;

        final List<ClayRecipe> recipes = new ArrayList<>();
        for (ClayRecipe recipe : clayRecipes(level))
        {
            final boolean recipeFire = !fireClayStack.isEmpty() && recipe.getIngredient().test(fireClayStack);
            if (recipeFire == heldFire)
            {
                recipes.add(recipe);
            }
        }

        VintageFirma.LOGGER.info("[clay] clay recipes available: {}", recipes.size());
        if (recipes.isEmpty())
        {
            message(player, "clay_no_recipe");
            return;
        }

        VFNetwork.sendTo(player, S2COpenClaySelectionPacket.of(pos, recipes));
        VintageFirma.LOGGER.info("[clay] sent selection with {} recipes", recipes.size());
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event)
    {
        final ServerLevel level = event.getLevel();
        final ServerPlayer player = event.getPlayer();
        final ChunkPos chunk = event.getChunk().getPos();

        for (var entry : ClaySites.get(level).all().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if ((pos.getX() >> 4) != chunk.x || (pos.getZ() >> 4) != chunk.z) continue;
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > SYNC_RANGE_SQR) continue;
            sendTo(player, pos, entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        final ServerLevel level = player.serverLevel();
        for (var entry : ClaySites.get(level).all().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > SYNC_RANGE_SQR) continue;
            sendTo(player, pos, entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        final BlockPos pos = event.getPos();
        if (!ClaySites.get(level).has(pos)) return;

        // The support block is gone: drop the clay piece (its voxels are lost) rather than leave it floating.
        ClaySites.get(level).remove(pos);
        broadcast(level, pos);
    }
}