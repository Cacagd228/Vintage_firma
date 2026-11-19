package com.vintagefirma.client;

import com.vintagefirma.common.AnvilHandler;
import com.vintagefirma.common.VoxelRaycast;
import com.vintagefirma.smithing.Voxels;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

/**
 * Client side targeting that, unlike the vanilla block ray trace, understands the individual work item
 * voxels (which stick out above the anvil block shape).
 */
public final class ClientTargeting
{
    public record Target(BlockPos pos, int x, int y, int z)
    {
    }

    /** A targeted clay column (x, z); the server stacks clay from the bottom of that column up. */
    public record ClayColumn(BlockPos pos, int x, int z)
    {
    }

    /** Height of the clay grid above the block position (clay rests on top of a full block). */
    private static final float CLAY_Y_OFFSET = 1.0f;

    private ClientTargeting()
    {
    }

    @Nullable
    public static Target findVoxelTarget(Player player)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;

        Target best = null;
        double bestDistance = Double.MAX_VALUE;
        final double reach = Math.max(3.0, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));

        for (Map.Entry<BlockPos, ClientWorkCache.ClientWork> entry : ClientWorkCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > (reach + 1.5) * (reach + 1.5)) continue;
            if (!minecraft.level.isLoaded(pos)) continue;

            final VoxelRaycast.Hit hit = VoxelRaycast.raycast(minecraft.level, player, pos, entry.getValue().voxels());
            if (hit != null && hit.distanceSqr() < bestDistance)
            {
                bestDistance = hit.distanceSqr();
                best = new Target(pos, hit.x(), hit.y(), hit.z());
            }
        }

        return best;
    }

    /**
     * @return the clay cell (x, z) in the current layer the player is aiming at, or {@code null}.
     *
     * <p>Only the current (lowest unfinished) layer is editable. Aiming at a filled cell in that layer
     * raycasts it; aiming at an empty cell intersects the look ray with the layer's floor. The server
     * applies the edit at that (x, z) on its own current layer.
     */
    @Nullable
    public static ClayColumn findClayColumn(Player player)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;

        final double reach = Math.max(3.0, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));
        final Vec3 eye = player.getEyePosition(1f);
        final Vec3 dir = player.getViewVector(1f);

        ClayColumn best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, ClientClayCache.ClayWork> entry : ClientClayCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > (reach + 1.5) * (reach + 1.5)) continue;
            if (!minecraft.level.isLoaded(pos)) continue;

            final byte[] voxels = entry.getValue().voxels();
            final byte[] pattern = entry.getValue().pattern();
            if (pattern == null) continue;

            final int layer = com.vintagefirma.common.ClayWork.currentLayer(voxels, pattern);
            if (layer >= Voxels.SIZE_Y) continue; // already complete

            final float layerY = CLAY_Y_OFFSET + layer * (1f / 16f);

            // 1. Raycast the current layer's clay (temp grid with only that layer filled).
            final byte[] layerVoxels = new byte[Voxels.LENGTH];
            for (int z = 0; z < Voxels.SIZE_Z; z++)
            {
                for (int x = 0; x < Voxels.SIZE_X; x++)
                {
                    if (voxels[Voxels.index(x, layer, z)] != Voxels.EMPTY)
                    {
                        layerVoxels[Voxels.index(x, 0, z)] = Voxels.METAL;
                    }
                }
            }

            final VoxelRaycast.Hit hit = VoxelRaycast.raycast(minecraft.level, player, pos, layerVoxels, layerY);
            if (hit != null)
            {
                if (hit.distanceSqr() < bestDistance)
                {
                    bestDistance = hit.distanceSqr();
                    best = new ClayColumn(pos, hit.x(), hit.z());
                }
                continue;
            }

            // 2. Empty cell: intersect the look ray with the floor of the current layer.
            if (dir.y >= -0.0001) continue; // must be looking downwards
            final double planeY = pos.getY() + layerY;
            final double t = (planeY - eye.y) / dir.y;
            if (t <= 0 || t > reach) continue;

            final double px = eye.x + dir.x * t;
            final double pz = eye.z + dir.z * t;
            final int gx = (int) Math.floor(px - pos.getX());
            final int gz = (int) Math.floor(pz - pos.getZ());
            if (gx < 0 || gx >= Voxels.SIZE_X || gz < 0 || gz >= Voxels.SIZE_Z) continue;

            if (t * t < bestDistance)
            {
                bestDistance = t * t;
                best = new ClayColumn(pos, gx, gz);
            }
        }
        return best;
    }

    /**
     * @return the anvil block the player is currently looking at, if it is one of our anvils
     */
    @Nullable
    public static BlockPos findAnvilBlock(Player player)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;

        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK)
        {
            final BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
            if (AnvilHandler.isOurAnvil(minecraft.level.getBlockState(pos)))
            {
                return pos;
            }
        }
        return null;
    }
}
