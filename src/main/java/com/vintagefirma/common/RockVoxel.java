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
 * Geometry for knapping voxels. The 5x5 blank is drawn 1.5x larger than a normal voxel (easier to aim
 * at and chip), centred on top of the support block. The grid is still the shared 16x6x16 voxel array;
 * only the drawn/clickable size changes.
 */
public final class RockVoxel
{
    /** 1.5x a normal 1/16 voxel. */
    public static final float SIZE = 1.5f / 16f;

    private static final float PLATE = RockWork.BLANK_SIZE * SIZE;
    /** Left/top offset that centres the 5-voxel plate inside one block. */
    private static final float LEFT = (1f - PLATE) / 2f;

    private RockVoxel()
    {
    }

    /** Local (block-relative) box of a voxel. */
    public static AABB localBox(int x, int y, int z)
    {
        final float minX = LEFT + (x - RockWork.BLANK_X) * SIZE;
        final float minY = y * SIZE;
        final float minZ = LEFT + (z - RockWork.BLANK_Z) * SIZE;
        return new AABB(minX, minY, minZ, minX + SIZE, minY + SIZE, minZ + SIZE);
    }

    public record Hit(int x, int y, int z, double distanceSqr)
    {
    }

    /** Ray trace against the stone voxels of one site. */
    @Nullable
    public static Hit raycast(Level level, Player player, BlockPos pos, byte[] voxels)
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
                    if (!VoxelRaycast.isExposed(voxels, x, y, z)) continue;

                    final AABB box = localBox(x, y, z).move(pos.getX(), pos.getY(), pos.getZ());
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
}