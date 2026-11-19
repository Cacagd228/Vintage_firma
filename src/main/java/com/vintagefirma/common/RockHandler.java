package com.vintagefirma.common;

import com.vintagefirma.VintageFirma;
import com.vintagefirma.network.S2COpenRockSelectionPacket;
import com.vintagefirma.network.S2CSyncRockPacket;
import com.vintagefirma.network.VFNetwork;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dries007.tfc.common.blocks.rock.LooseRockBlock;
import net.dries007.tfc.common.recipes.KnappingRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Server side handler for Vintage Story style rock knapping. The player places a loose rock (a
 * {@link LooseRockBlock}), holds another loose rock, and shift + right clicks the block to pick a stone
 * tool. The loose rock turns into a flat 5x5 blank which the player chips away at (left click) until it
 * matches the tool pattern.
 *
 * <p>This replaces TFC's own 2D rock knapping: right clicking with a loose rock no longer opens it.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RockHandler
{
    private static final double REACH_SQR = 64.0;
    private static final double SYNC_RANGE_SQR = 64.0 * 64.0;

    /** The knapping type id for rock knapping (TFC's own 5x5 recipes). */
    private static final ResourceLocation ROCK_KNAPPING_TYPE = new ResourceLocation("tfc", "rock");

    private RockHandler()
    {
    }

    // ---------------------------------------------------------------------------------------------
    // Loose rocks
    // ---------------------------------------------------------------------------------------------

    /** @return {@code true} if the stack is a loose rock (tfc:rock/loose/* or mossy_loose). */
    public static boolean isLooseRock(ItemStack stack)
    {
        if (stack.isEmpty()) return false;

        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("tfc")) return false;

        final String path = id.getPath();
        return path.startsWith("rock/loose/") || path.startsWith("rock/mossy_loose/");
    }

    /** @return {@code true} if the block state is a loose rock placed on the ground. */
    public static boolean isLooseRockBlock(BlockState state)
    {
        return state.getBlock() instanceof LooseRockBlock;
    }

    @Nullable
    private static ItemStack heldRock(ServerPlayer player)
    {
        if (isLooseRock(player.getMainHandItem())) return player.getMainHandItem();
        if (isLooseRock(player.getOffhandItem())) return player.getOffhandItem();
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Recipes
    // ---------------------------------------------------------------------------------------------

    public static List<KnappingRecipe> rockRecipes(ServerLevel level)
    {
        final List<KnappingRecipe> recipes = new ArrayList<>();
        for (KnappingRecipe recipe : level.getRecipeManager().getAllRecipesFor(TFCRecipeTypes.KNAPPING.get()))
        {
            if (recipe.getKnappingType().getId().equals(ROCK_KNAPPING_TYPE))
            {
                recipes.add(recipe);
            }
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        return recipes;
    }

    @Nullable
    public static KnappingRecipe findRock(ServerLevel level, ResourceLocation id)
    {
        for (KnappingRecipe recipe : level.getRecipeManager().getAllRecipesFor(TFCRecipeTypes.KNAPPING.get()))
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

    public static void handleInteract(ServerPlayer player, BlockPos pos)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        if (!isLooseRockBlock(level.getBlockState(pos))) return;

        // A piece is already being knapped here.
        if (RockSites.get(level).has(pos)) return;

        final ItemStack held = heldRock(player);
        if (held == null) return;

        // Only the recipes whose rock class matches the held loose rock.
        final List<KnappingRecipe> recipes = new ArrayList<>();
        for (KnappingRecipe recipe : rockRecipes(level))
        {
            if (recipe.getIngredient().test(held))
            {
                recipes.add(recipe);
            }
        }

        if (recipes.isEmpty()) return;

        VFNetwork.sendTo(player, S2COpenRockSelectionPacket.of(pos, recipes, level.registryAccess()));
    }

    public static void handleSelect(ServerPlayer player, BlockPos pos, ResourceLocation recipeId)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        if (!isLooseRockBlock(level.getBlockState(pos))) return;

        final RockSites sites = RockSites.get(level);
        if (sites.has(pos)) return;

        final KnappingRecipe recipe = findRock(level, recipeId);
        if (recipe == null) return;

        // The loose rock on the ground becomes the blank; the block itself is consumed.
        final byte[] voxels = RockWork.blank();
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        final RockWork work = new RockWork(voxels, recipe.getId());
        sites.put(pos, work);
        broadcast(level, pos);

        level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.7f, 0.9f);
    }

    // ---------------------------------------------------------------------------------------------
    // Knapping (chipping)
    // ---------------------------------------------------------------------------------------------

    public static void handleChip(ServerPlayer player, BlockPos pos, int x, int z)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        final RockSites sites = RockSites.get(level);
        final RockWork work = sites.get(pos);
        if (work == null) return;

        final KnappingRecipe recipe = findRock(level, work.recipe());
        if (recipe == null) return;

        if (x < 0 || x >= Voxels.SIZE_X || z < 0 || z >= Voxels.SIZE_Z) return;

        final byte[] voxels = work.voxels();
        if (Voxels.get(voxels, x, 0, z) == Voxels.EMPTY) return; // nothing to chip there

        // Rate limit: 0.3 s between actions (shared with the hammer and clay).
        if (!Cooldowns.ready(player.getUUID())) return;

        final byte[] updated = voxels.clone();
        Voxels.set(updated, x, 0, z, Voxels.EMPTY);

        final RockWork newWork = new RockWork(updated, work.recipe());
        sites.put(pos, newWork);
        Cooldowns.trigger(player.getUUID());
        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), Cooldowns.ACTION_TICKS);
        broadcast(level, pos);

        level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.4f, 1.4f);
        checkComplete(player, level, pos, newWork, recipe);
    }

    private static void checkComplete(ServerPlayer player, ServerLevel level, BlockPos pos, RockWork work,
        KnappingRecipe recipe)
    {
        if (!Voxels.matches(work.voxels(), RockPattern.toVoxels(recipe.getPattern()))) return;

        RockSites.get(level).remove(pos);
        broadcast(level, pos);

        final ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (!player.getInventory().add(result))
        {
            player.drop(result, false);
        }

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // ---------------------------------------------------------------------------------------------
    // Syncing
    // ---------------------------------------------------------------------------------------------

    public static void broadcast(ServerLevel level, BlockPos pos)
    {
        final RockWork work = RockSites.get(level).get(pos);
        final S2CSyncRockPacket packet = work != null ? S2CSyncRockPacket.of(pos, work) : S2CSyncRockPacket.clear(pos);

        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= SYNC_RANGE_SQR)
            {
                VFNetwork.sendTo(player, packet);
            }
        }
    }

    private static void sendTo(ServerPlayer player, BlockPos pos, RockWork work)
    {
        final S2CSyncRockPacket packet = work != null ? S2CSyncRockPacket.of(pos, work) : S2CSyncRockPacket.clear(pos);
        VFNetwork.sendTo(player, packet);
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    /** Replace TFC's 2D rock knapping: right clicking with a loose rock must not open it. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (!isLooseRock(event.getItemStack())) return;

        event.setCanceled(true);
    }

    /** Do not let a loose rock be stacked on top of another loose rock (that cell is the knapping site). */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (!isLooseRock(event.getItemStack())) return;
        if (!isLooseRockBlock(level.getBlockState(event.getPos()))) return;

        // Shift clicks are handled by the client (it sends C2SClay... no, C2SRockInteractPacket).
        if (player.isShiftKeyDown()) return;

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event)
    {
        final ServerLevel level = event.getLevel();
        final ServerPlayer player = event.getPlayer();
        final ChunkPos chunk = event.getChunk().getPos();

        for (var entry : RockSites.get(level).all().entrySet())
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
        for (var entry : RockSites.get(level).all().entrySet())
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

        final BlockPos broken = event.getPos();

        // Legacy/other keying: the site sits on the broken block itself.
        if (RockSites.get(level).has(broken))
        {
            RockSites.get(level).remove(broken);
            broadcast(level, broken);
            event.setCanceled(true);
            return;
        }

        // Protect the support block while a piece is being knapped on top of it (like the anvil).
        if (RockSites.get(level).has(broken.above()))
        {
            event.setCanceled(true);
        }
    }

    /** Removes a knapping piece (Shift + right click), nothing drops. */
    public static void handleRemove(ServerPlayer player, BlockPos pos)
    {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) return;

        if (RockSites.get(level).remove(pos) != null)
        {
            broadcast(level, pos);
            level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.4f, 1.2f);
        }
    }

    public static void message(Player player, String key, Object... args)
    {
    }
}