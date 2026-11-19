package com.vintagefirma.smithing;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure (Minecraft independent) implementation of the Vintage Story smithing voxel algorithms.
 *
 * <p>The work item is a fixed {@code 16 x 6 x 16} grid. Every cell is either {@link #EMPTY} or
 * {@link #METAL}. Voxel {@code (x, y, z)} with {@code y = 0} is the bottom layer that rests on the anvil.
 *
 * <p>The hammer operations ({@link #upset}, {@link #heavyHit}, {@link #split}) are a faithful port of the
 * behaviour described by the original game: metal can only be moved when it is the top voxel of its column,
 * and metal always tries to stay supported.
 */
public final class Voxels
{
    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 6;
    public static final int SIZE_Z = 16;

    public static final int LENGTH = SIZE_X * SIZE_Y * SIZE_Z;

    public static final byte EMPTY = 0;
    public static final byte METAL = 1;
    public static final byte SLAG = 2;

    /** Voxels provided by a single ingot, and the footprint it is placed in (7 x 3, 2 high). */
    public static final int VOXELS_PER_INGOT = 42;
    public static final int INGOT_X = 4;
    public static final int INGOT_Z = 6;

    /** Voxels provided by a single sheet, placed as a 9 x 9 x 1 plate in the middle of the grid. */
    public static final int VOXELS_PER_SHEET = 81;
    public static final int SHEET_SIZE = 9;
    public static final int SHEET_X = (SIZE_X - SHEET_SIZE) / 2;
    public static final int SHEET_Z = (SIZE_Z - SHEET_SIZE) / 2;

    private static final double CONE_DEGREES = 25.0;
    private static final double CONE_COS = Math.cos(Math.toRadians(CONE_DEGREES));

    private Voxels() {}

    // ---------------------------------------------------------------------------------------------
    // Grid helpers
    // ---------------------------------------------------------------------------------------------

    public static int index(int x, int y, int z)
    {
        return (y * SIZE_Z + z) * SIZE_X + x;
    }

    public static boolean inBounds(int x, int y, int z)
    {
        return x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z;
    }

    public static byte[] empty()
    {
        return new byte[LENGTH];
    }

    public static byte[] copy(byte[] voxels)
    {
        return voxels.clone();
    }

    public static byte get(byte[] voxels, int x, int y, int z)
    {
        return voxels[index(x, y, z)];
    }

    public static void set(byte[] voxels, int x, int y, int z, byte material)
    {
        voxels[index(x, y, z)] = material;
    }

    public static boolean isMetal(byte[] voxels, int x, int y, int z)
    {
        return inBounds(x, y, z) && get(voxels, x, y, z) != EMPTY;
    }

    public static int countMetal(byte[] voxels)
    {
        int count = 0;
        for (byte voxel : voxels)
        {
            if (voxel != EMPTY) count++;
        }
        return count;
    }

    /** @return the highest occupied layer + 1, or 0 for an empty work item. */
    public static int height(byte[] voxels)
    {
        for (int y = SIZE_Y - 1; y >= 0; y--)
        {
            for (int z = 0; z < SIZE_Z; z++)
            {
                for (int x = 0; x < SIZE_X; x++)
                {
                    if (get(voxels, x, y, z) != EMPTY) return y + 1;
                }
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Shaping operations
    // ---------------------------------------------------------------------------------------------

    public static byte[] createIngot()
    {
        final byte[] voxels = empty();
        addIngot(voxels);
        return voxels;
    }

    /** A fresh sheet: a 9 x 9 x 1 plate, centered on the anvil. */
    public static byte[] createSheet()
    {
        final byte[] voxels = empty();
        addSheet(voxels);
        return voxels;
    }

    /**
     * Adds the voxels of a heated sheet: one voxel in every column of the central {@code 9 x 9} footprint,
     * filling the lowest free cell. A sheet is exactly one voxel thick, matching the original game.
     *
     * @return the amount of voxels actually added (0 when the work item already covers every column)
     */
    public static int addSheet(byte[] voxels)
    {
        int added = 0;
        for (int x = 0; x < SHEET_SIZE; x++)
        {
            for (int z = 0; z < SHEET_SIZE; z++)
            {
                int y = 0;
                while (y < SIZE_Y)
                {
                    if (get(voxels, SHEET_X + x, y, SHEET_Z + z) == EMPTY)
                    {
                        set(voxels, SHEET_X + x, y, SHEET_Z + z, METAL);
                        added++;
                        break;
                    }
                    y++;
                }
            }
        }
        return added;
    }

    /**
     * Adds the voxels of a heated ingot on top of the existing work item, exactly like the original game:
     * every column of the central {@code 7 x 3} footprint receives up to two voxels, filling the lowest
     * available cells first (an empty cell above a filled one counts as available). Voxels that do not fit
     * below the height limit are lost.
     *
     * @return the amount of voxels actually added (0 when there is no room at all)
     */
    public static int addIngot(byte[] voxels)
    {
        int added = 0;
        for (int x = 0; x < 7; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                int y = 0;
                int inColumn = 0;
                while (y < SIZE_Y && inColumn < 2)
                {
                    if (get(voxels, INGOT_X + x, y, INGOT_Z + z) == EMPTY)
                    {
                        set(voxels, INGOT_X + x, y, INGOT_Z + z, METAL);
                        inColumn++;
                        added++;
                    }
                    y++;
                }
            }
        }
        return added;
    }

    /** Split: permanently removes the clicked voxel (a 3x3 patch for slag). */
    public static void split(byte[] voxels, int x, int y, int z)
    {
        if (!inBounds(x, y, z)) return;

        if (get(voxels, x, y, z) == SLAG)
        {
            // Slag is brittle: one strike chips away a 3x3 patch of it
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (inBounds(x + dx, y, z + dz) && get(voxels, x + dx, y, z + dz) == SLAG)
                    {
                        set(voxels, x + dx, y, z + dz, EMPTY);
                    }
                }
            }
        }
        set(voxels, x, y, z, EMPTY);
    }

    /**
     * A bloom / pig iron work item: the metal of one ingot plus a cap of slag that has to be hammered off
     * before the metal can be worked. This is what the original game does when a bloom is placed on the
     * anvil.
     */
    public static byte[] createBloom(int slagLayers)
    {
        final byte[] voxels = createIngot();
        addSlag(voxels, INGOT_X, INGOT_Z, 7, 3, 2, slagLayers);
        return voxels;
    }

    /**
     * Adds a cap of slag above the given footprint.
     *
     * @param baseY the first free layer above the footprint
     * @param count how many layers of slag to add
     */
    public static int addSlag(byte[] voxels, int originX, int originZ, int width, int depth, int baseY, int count)
    {
        int added = 0;
        for (int y = baseY; y < Math.min(SIZE_Y, baseY + count); y++)
        {
            for (int x = 0; x < width; x++)
            {
                for (int z = 0; z < depth; z++)
                {
                    final int px = originX + x;
                    final int pz = originZ + z;
                    if (!inBounds(px, y, pz)) continue;
                    if (get(voxels, px, y, pz) != EMPTY) continue;

                    set(voxels, px, y, pz, SLAG);
                    added++;
                }
            }
        }
        return added;
    }

    /** @return how many slag voxels the work item still holds */
    public static int countSlag(byte[] voxels)
    {
        int count = 0;
        for (byte voxel : voxels)
        {
            if (voxel == SLAG) count++;
        }
        return count;
    }

    /**
     * Removes every slag voxel, used when a conversion finishes.
     */
    public static int clearSlag(byte[] voxels)
    {
        int removed = 0;
        for (int i = 0; i < voxels.length; i++)
        {
            if (voxels[i] == SLAG)
            {
                voxels[i] = EMPTY;
                removed++;
            }
        }
        return removed;
    }

    /**
     * Upset: pushes the clicked voxel one cell in the given horizontal direction. At the edges the metal
     * flows upwards (bottom layer) or falls down / spreads (upper layers), matching the original game.
     */
    public static void upset(byte[] voxels, int x, int y, int z, int dx, int dz)
    {
        if (!inBounds(x, y, z) || get(voxels, x, y, z) != METAL) return;
        // Metal that is covered from above cannot move
        if (y < SIZE_Y - 1 && get(voxels, x, y + 1, z) != EMPTY) return;

        final int nx = x + dx;
        final int nz = z + dz;
        if (!inBounds(nx, y, nz)) return;

        if (y > 0)
        {
            // A simple sideways move, as long as the destination is supported
            if (get(voxels, nx, y, nz) == EMPTY && get(voxels, nx, y - 1, nz) != EMPTY)
            {
                set(voxels, nx, y, nz, METAL);
                set(voxels, x, y, z, EMPTY);
                return;
            }

            final int ny = y + 1;
            final int bx = x - dx;
            final int bz = z - dz;
            if (!inBounds(bx, y, bz)) return;

            // Push diagonally up while keeping the material behind the voxel free
            if (ny < SIZE_Y
                && get(voxels, nx, ny, nz) == EMPTY
                && get(voxels, nx, ny - 1, nz) != EMPTY
                && get(voxels, bx, y, bz) == EMPTY)
            {
                set(voxels, nx, ny, nz, METAL);
                set(voxels, x, y, z, EMPTY);
                return;
            }

            if (!moveVoxelDownwards(voxels, x, y, z, dx, dz, 1))
            {
                moveVoxelDownwards(voxels, x, y, z, dx, dz, 2);
            }
            return;
        }

        // Bottom layer: metal at the frontier gets pushed up one layer
        final int ny = 1;
        final int bx = x - dx;
        final int bz = z - dz;
        if (!inBounds(bx, y, bz)) return;

        if (get(voxels, nx, ny, nz) == EMPTY
            && get(voxels, nx, ny - 1, nz) != EMPTY
            && get(voxels, bx, y, bz) == EMPTY)
        {
            set(voxels, nx, ny, nz, METAL);
            set(voxels, x, y, z, EMPTY);
        }
    }

    /**
     * Heavy hit: flattens the metal in a 3x3 area by moving it down, and when there is no room, spreads it
     * outwards instead.
     */
    public static void heavyHit(byte[] voxels, int x, int y, int z)
    {
        if (!inBounds(x, y, z) || get(voxels, x, y, z) != METAL) return;
        if (y == 0) return;

        int moved = 0;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (dx == 0 && dz == 0) continue;
                final int nx = x + dx;
                final int nz = z + dz;
                if (!inBounds(nx, y, nz)) continue;

                if (get(voxels, nx, y, nz) == METAL && moveVoxelDownwards(voxels, nx, y, nz, 0, 0, 1))
                {
                    moved++;
                }
            }
        }

        if (get(voxels, x, y, z) == METAL && moveVoxelDownwards(voxels, x, y, z, 0, 0, 1))
        {
            moved++;
        }

        if (moved > 0) return;

        // No room to flatten: push the ring of metal outwards by one
        int emptyX = -1, emptyZ = -1;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (dx == 0 && dz == 0) continue;
                final int nx = x + dx;
                final int nz = z + dz;
                final int ox = x + 2 * dx;
                final int oz = z + 2 * dz;
                if (!inBounds(ox, y, oz) || !inBounds(nx, y, nz)) continue;

                final boolean spotEmpty = get(voxels, ox, y, oz) == EMPTY;
                if (get(voxels, nx, y, nz) == METAL && spotEmpty)
                {
                    set(voxels, nx, y, nz, EMPTY);
                    if (get(voxels, ox, y - 1, oz) == EMPTY)
                    {
                        set(voxels, ox, y - 1, oz, METAL);
                    }
                    else
                    {
                        set(voxels, ox, y, oz, METAL);
                    }
                }
                else if (spotEmpty)
                {
                    emptyX = nx;
                    emptyZ = nz;
                }
            }
        }

        if (emptyX >= 0 && get(voxels, x, y, z) == METAL)
        {
            set(voxels, x, y, z, EMPTY);
            if (get(voxels, emptyX, y - 1, emptyZ) == EMPTY)
            {
                set(voxels, emptyX, y - 1, emptyZ, METAL);
            }
            else
            {
                set(voxels, emptyX, y, emptyZ, METAL);
            }
        }
    }

    /**
     * Moves a voxel to the closest free spot on a lower layer, searching horizontally in the given
     * direction. This is the "metal flows down and outwards" part of the hammer behaviour.
     */
    private static boolean moveVoxelDownwards(byte[] voxels, int x, int y, int z, int dx, int dz, int maxDistance)
    {
        final int originY = y;

        while (y > 0)
        {
            y--;

            final int[] spot = closestFreeSpot(voxels, x, y, z, dx, dz, maxDistance);
            if (spot == null) continue;

            set(voxels, x, originY, z, EMPTY);
            for (int vy = 0; vy <= spot[1]; vy++)
            {
                if (get(voxels, spot[0], vy, spot[2]) == EMPTY)
                {
                    set(voxels, spot[0], vy, spot[2], METAL);
                    return true;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Breadth first search for the closest empty cell on the given layer, constrained to a cone in the
     * requested direction (the original game uses a 25 degree cone and a maximum horizontal distance).
     *
     * @return {@code {x, y, z}} of the found cell, or {@code null}
     */
    private static int[] closestFreeSpot(byte[] voxels, int x, int y, int z, int dx, int dz, int maxDistance)
    {
        final ArrayDeque<int[]> queue = new ArrayDeque<>();
        final Set<Integer> visited = new HashSet<>();

        queue.add(new int[] {x, y, z});
        visited.add(index(x, y, z));

        final double dirLength = Math.sqrt((double) (dx * dx + dz * dz));

        while (!queue.isEmpty())
        {
            final int[] node = queue.poll();

            for (int i = 0; i < 4; i++)
            {
                final int ndx = i == 0 ? 1 : i == 1 ? -1 : 0;
                final int ndz = i == 2 ? 1 : i == 3 ? -1 : 0;

                final int nx = node[0] + ndx;
                final int nz = node[2] + ndz;
                if (!inBounds(nx, y, nz)) continue;
                if (!visited.add(index(nx, y, nz))) continue;

                final double relX = nx - x;
                final double relZ = nz - z;
                final double length = Math.sqrt(relX * relX + relZ * relZ);
                if (length > maxDistance) continue;

                if (dirLength > 0 && length > 0)
                {
                    final double dot = (dx * relX + dz * relZ) / (dirLength * length);
                    if (dot < CONE_COS) continue;
                }

                if (get(voxels, nx, y, nz) == EMPTY)
                {
                    return new int[] {nx, y, nz};
                }

                if (get(voxels, nx, y, nz) == METAL)
                {
                    queue.add(new int[] {nx, y, nz});
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Patterns
    // ---------------------------------------------------------------------------------------------

    /**
     * Converts a layered pattern (index 0 is the bottom layer, {@code #} is filled) into a centered voxel
     * grid. Patterns are limited to 16x16 in the horizontal plane and 6 layers.
     */
    public static byte[] parsePattern(List<List<String>> layers)
    {
        if (layers.size() > SIZE_Y)
        {
            throw new IllegalArgumentException("Smithing pattern has " + layers.size() + " layers, max " + SIZE_Y);
        }

        int width = 0;
        int depth = 0;
        for (List<String> layer : layers)
        {
            depth = Math.max(depth, layer.size());
            for (String row : layer)
            {
                width = Math.max(width, row.length());
            }
        }

        if (width > SIZE_X || depth > SIZE_Z)
        {
            throw new IllegalArgumentException("Smithing pattern is too large: " + width + "x" + depth);
        }

        final int offsetX = (SIZE_X - width) / 2;
        final int offsetZ = (SIZE_Z - depth) / 2;

        final byte[] pattern = empty();
        for (int y = 0; y < layers.size(); y++)
        {
            final List<String> layer = layers.get(y);
            for (int z = 0; z < layer.size(); z++)
            {
                final String row = layer.get(z);
                for (int x = 0; x < row.length(); x++)
                {
                    if (isFilledChar(row.charAt(x)))
                    {
                        set(pattern, offsetX + x, y, offsetZ + z, METAL);
                    }
                }
            }
        }
        return pattern;
    }

    public static boolean isFilledChar(char c)
    {
        return c == '#' || c == 'X' || c == 'x' || c == '1';
    }

    public static boolean matches(byte[] voxels, byte[] pattern)
    {
        for (int i = 0; i < LENGTH; i++)
        {
            final boolean filled = voxels[i] != EMPTY;
            final boolean required = pattern[i] != EMPTY;
            if (filled != required) return false;
        }
        return true;
    }

    /**
     * @return {@code true} if the work item contains at least one voxel that is not part of the pattern
     */
    public static boolean hasExcess(byte[] voxels, byte[] pattern)
    {
        for (int i = 0; i < LENGTH; i++)
        {
            if (voxels[i] != EMPTY && pattern[i] == EMPTY) return true;
        }
        return false;
    }

    /**
     * @return the amount of pattern voxels that are still empty
     */
    public static int missing(byte[] voxels, byte[] pattern)
    {
        int count = 0;
        for (int i = 0; i < LENGTH; i++)
        {
            if (voxels[i] == EMPTY && pattern[i] != EMPTY) count++;
        }
        return count;
    }

    public static int patternVoxels(byte[] pattern)
    {
        return countMetal(pattern);
    }
}
