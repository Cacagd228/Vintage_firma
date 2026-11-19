package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

/**
 * Ray tracing against the individual voxels of a work item. The voxels live inside one block, so the
 * vanilla block hit is used only to reject cases where another block is in the way.
 */
    /**
     * Ray tracing against the individual voxels of a work item. Only voxels that are not enclosed by other
     * voxels count as hittable, which is what makes a compact ingot behave like a solid block.
     */
    public final class VoxelRaycast
    {
        public static final float VOXEL_SIZE = 1f / 16f;
        /**
     * The lowest voxel layer rests exactly on top of the anvil: TFC's anvil is 11/16 blocks tall, and the
     * voxel grid starts at y = 0 there. Anything lower would bury the first layer inside the block.
     */
    public static final float VOXEL_Y_OFFSET = 11f / 16f;

        private VoxelRaycast()
        {
        }

        public record Hit(int x, int y, int z, double distanceSqr)
        {
        }

        @Nullable
        public static Hit raycast(Level level, Player player, BlockPos pos, byte[] voxels)
        {
            return raycast(level, player, pos, voxels, VOXEL_Y_OFFSET);
        }

        @Nullable
        public static Hit raycast(Level level, Player player, BlockPos pos, byte[] voxels, float yOffset)
        {
            final Vec3 eye = player.getEyePosition(1f);
            final double reach = Math.max(3.0, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));
            final Vec3 end = eye.add(player.getViewVector(1f).scale(reach));

            Hit best = null;
            double bestDistance = Double.MAX_VALUE;

            for (int y = 0; y < Voxels.SIZE_Y; y++)
            {
                for (int z = 0; z < Voxels.SIZE_Z; z++)
                {
                    for (int x = 0; x < Voxels.SIZE_X; x++)
                    {
                        if (voxels[Voxels.index(x, y, z)] == Voxels.EMPTY) continue;
                        if (!isExposed(voxels, x, y, z)) continue;

                        final AABB box = voxelBox(pos, x, y, z, yOffset);
                        final var intersection = box.clip(eye, end);
                        if (intersection.isPresent())
                        {
                            final double distance = intersection.get().distanceToSqr(eye);
                            if (distance < bestDistance)
                            {
                                bestDistance = distance;
                                best = new Hit(x, y, z, distance);
                            }
                        }
                    }
                }
            }

            if (best == null)
            {
                return null;
            }

            // Reject the voxel when a different block is closer to the eye than the voxel itself. This cannot be
            // done up front: the voxels sit above the anvil shape, so the vanilla ray often passes them and hits
            // the ground behind the anvil.
            final BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK && !blockHit.getBlockPos().equals(pos))
            {
                final double blockDistance = blockHit.getLocation().distanceToSqr(eye);
                if (blockDistance < bestDistance)
                {
                    return null;
                }
            }

            return best;
        }

    /**
     * A voxel can only be hit from the outside. Interior voxels (and the underside of the bottom layer,
     * which rests on the anvil) are never valid targets.
     */
    public static boolean isExposed(byte[] voxels, int x, int y, int z)
    {
        if (y == 0)
        {
            // The underside of the work item rests on the anvil and is never visible
            return !isMetal(voxels, x, 1, z) || !isMetal(voxels, x - 1, 0, z) || !isMetal(voxels, x + 1, 0, z)
                || !isMetal(voxels, x, 0, z - 1) || !isMetal(voxels, x, 0, z + 1);
        }

        return !isMetal(voxels, x, y + 1, z)
            || !isMetal(voxels, x, y - 1, z)
            || !isMetal(voxels, x, y, z - 1)
            || !isMetal(voxels, x, y, z + 1)
            || !isMetal(voxels, x - 1, y, z)
            || !isMetal(voxels, x + 1, y, z);
    }

    private static boolean isMetal(byte[] voxels, int x, int y, int z)
    {
        return Voxels.inBounds(x, y, z) && voxels[Voxels.index(x, y, z)] != Voxels.EMPTY;
    }

    public static AABB voxelBox(BlockPos pos, int x, int y, int z)
    {
        return voxelBox(pos, x, y, z, VOXEL_Y_OFFSET);
    }

    public static AABB voxelBox(BlockPos pos, int x, int y, int z, float yOffset)
    {
        final double minX = pos.getX() + x * VOXEL_SIZE;
        final double minY = pos.getY() + yOffset + y * VOXEL_SIZE;
        final double minZ = pos.getZ() + z * VOXEL_SIZE;
        return new AABB(minX, minY, minZ, minX + VOXEL_SIZE, minY + VOXEL_SIZE, minZ + VOXEL_SIZE);
    }
}
