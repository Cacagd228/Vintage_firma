package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import net.dries007.tfc.util.KnappingPattern;

/**
 * Converts TFC's own {@link KnappingPattern} (a 5x5 grid) into the voxel grid used for knapping. The
 * pattern is centred horizontally, exactly like the smithing patterns.
 */
public final class RockPattern
{
    private RockPattern()
    {
    }

    public static byte[] toVoxels(KnappingPattern pattern)
    {
        final byte[] voxels = Voxels.empty();

        final int width = Math.min(pattern.getWidth(), Voxels.SIZE_X);
        final int height = Math.min(pattern.getHeight(), Voxels.SIZE_Z);
        final int offsetX = (Voxels.SIZE_X - width) / 2;
        final int offsetZ = (Voxels.SIZE_Z - height) / 2;

        for (int z = 0; z < height; z++)
        {
            for (int x = 0; x < width; x++)
            {
                if (pattern.get(x, z))
                {
                    Voxels.set(voxels, offsetX + x, 0, offsetZ + z, Voxels.METAL);
                }
            }
        }

        return voxels;
    }

    public static int count(KnappingPattern pattern)
    {
        int n = 0;
        for (int z = 0; z < pattern.getHeight(); z++)
        {
            for (int x = 0; x < pattern.getWidth(); x++)
            {
                if (pattern.get(x, z)) n++;
            }
        }
        return n;
    }
}