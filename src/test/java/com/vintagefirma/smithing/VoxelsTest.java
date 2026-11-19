package com.vintagefirma.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelsTest
{
    @Test
    void ingotAddsExactly42Voxels()
    {
        final byte[] voxels = Voxels.createIngot();
        assertEquals(Voxels.VOXELS_PER_INGOT, Voxels.countMetal(voxels));
        assertEquals(2, Voxels.height(voxels));
    }

    @Test
    void addIngotTopsUpEveryColumnToOneOfTwoVoxels()
    {
        final byte[] voxels = Voxels.empty();

        final int added = Voxels.addIngot(voxels);

        assertEquals(Voxels.VOXELS_PER_INGOT, added);
        assertEquals(2, Voxels.height(voxels));
        assertTrue(Voxels.isMetal(voxels, Voxels.INGOT_X, 0, Voxels.INGOT_Z));
        assertTrue(Voxels.isMetal(voxels, Voxels.INGOT_X, 1, Voxels.INGOT_Z));
        assertFalse(Voxels.isMetal(voxels, Voxels.INGOT_X, 2, Voxels.INGOT_Z));
    }

    @Test
    void addingIngotToFullColumnsAddsNothing()
    {
        final byte[] voxels = Voxels.empty();
        for (int x = 0; x < 7; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                for (int y = 0; y < Voxels.SIZE_Y; y++)
                {
                    Voxels.set(voxels, Voxels.INGOT_X + x, y, Voxels.INGOT_Z + z, Voxels.METAL);
                }
            }
        }

        final int before = Voxels.countMetal(voxels);
        assertEquals(0, Voxels.addIngot(voxels));
        assertEquals(before, Voxels.countMetal(voxels));
    }

    @Test
    void sheetCreatesASingleNineByNineLayer()
    {
        final byte[] voxels = Voxels.createSheet();
        assertEquals(Voxels.VOXELS_PER_SHEET, Voxels.countMetal(voxels));
        assertEquals(1, Voxels.height(voxels));

        // Centered: 3 .. 11 in both horizontal axes
        assertTrue(Voxels.isMetal(voxels, 3, 0, 3));
        assertTrue(Voxels.isMetal(voxels, 11, 0, 11));
        assertFalse(Voxels.isMetal(voxels, 2, 0, 2));
        assertFalse(Voxels.isMetal(voxels, 12, 0, 12));
    }

    @Test
    void addingASheetFillsOneVoxelPerColumn()
    {
        final byte[] voxels = Voxels.empty();

        final int added = Voxels.addSheet(voxels);

        // A sheet is exactly one voxel thick, so it fills the 9 x 9 footprint with one voxel per column
        assertEquals(Voxels.VOXELS_PER_SHEET, added);
        for (int x = 0; x < Voxels.SHEET_SIZE; x++)
        {
            for (int z = 0; z < Voxels.SHEET_SIZE; z++)
            {
                int filled = 0;
                for (int y = 0; y < Voxels.SIZE_Y; y++)
                {
                    if (Voxels.isMetal(voxels, Voxels.SHEET_X + x, y, Voxels.SHEET_Z + z)) filled++;
                }
                assertEquals(1, filled, "column " + x + "," + z + " should hold exactly one sheet voxel");
            }
        }

        // A second sheet adds another voxel per column, stacking on top of the first one
        assertEquals(Voxels.VOXELS_PER_SHEET, Voxels.addSheet(voxels));
        assertEquals(2, Voxels.height(voxels));
    }

    @Test
    void sheetOnTopOfAnIngotStacksOneVoxelPerColumn()
    {
        final byte[] voxels = Voxels.createIngot();

        assertEquals(Voxels.VOXELS_PER_SHEET, Voxels.addSheet(voxels));

        // The ingot columns already hold two voxels, the sheet adds the third one
        for (int x = 0; x < 7; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                assertEquals(3, countColumn(voxels, Voxels.INGOT_X + x, Voxels.INGOT_Z + z));
            }
        }
    }

    private static int countColumn(byte[] voxels, int x, int z)
    {
        int filled = 0;
        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            if (Voxels.isMetal(voxels, x, y, z)) filled++;
        }
        return filled;
    }

    @Test
    void splitRemovesOneVoxel()
    {
        final byte[] voxels = Voxels.createIngot();
        final int before = Voxels.countMetal(voxels);
        assertTrue(Voxels.isMetal(voxels, Voxels.INGOT_X, 0, Voxels.INGOT_Z));

        Voxels.split(voxels, Voxels.INGOT_X, 0, Voxels.INGOT_Z);

        assertEquals(before - 1, Voxels.countMetal(voxels));
        assertFalse(Voxels.isMetal(voxels, Voxels.INGOT_X, 0, Voxels.INGOT_Z));
    }

    @Test
    void upsetMovesSupportedVoxelSideways()
    {
        final byte[] voxels = Voxels.empty();
        // A 3x1 row: the middle voxel can be pushed right onto supported empty space
        Voxels.set(voxels, 6, 0, 8, Voxels.METAL);
        Voxels.set(voxels, 7, 1, 8, Voxels.METAL);
        Voxels.set(voxels, 8, 0, 8, Voxels.METAL);

        Voxels.upset(voxels, 7, 1, 8, 1, 0);

        assertFalse(Voxels.isMetal(voxels, 7, 1, 8));
        assertTrue(Voxels.isMetal(voxels, 8, 1, 8));
    }

    @Test
    void bottomLayerMetalAtTheFrontierMovesUp()
    {
        final byte[] voxels = Voxels.empty();
        Voxels.set(voxels, 5, 0, 5, Voxels.METAL);
        Voxels.set(voxels, 6, 0, 5, Voxels.METAL);
        Voxels.set(voxels, 4, 0, 5, Voxels.EMPTY);

        Voxels.upset(voxels, 5, 0, 5, 1, 0);

        assertFalse(Voxels.isMetal(voxels, 5, 0, 5));
        assertTrue(Voxels.isMetal(voxels, 6, 1, 5));
    }

    @Test
    void coveredMetalCannotBeUpset()
    {
        final byte[] voxels = Voxels.empty();
        Voxels.set(voxels, 5, 0, 5, Voxels.METAL);
        Voxels.set(voxels, 5, 1, 5, Voxels.METAL);
        Voxels.set(voxels, 5, 2, 5, Voxels.METAL);

        Voxels.upset(voxels, 5, 1, 5, 1, 0);

        assertTrue(Voxels.isMetal(voxels, 5, 1, 5));
    }

    @Test
    void heavyHitDropsUnsupportedMetalDown()
    {
        final byte[] voxels = Voxels.empty();
        Voxels.set(voxels, 6, 1, 6, Voxels.METAL);

        Voxels.heavyHit(voxels, 6, 1, 6);

        assertFalse(Voxels.isMetal(voxels, 6, 1, 6));
        assertEquals(1, Voxels.countMetal(voxels));
        assertTrue(Voxels.isMetal(voxels, 7, 0, 6) || Voxels.isMetal(voxels, 5, 0, 6)
            || Voxels.isMetal(voxels, 6, 0, 7) || Voxels.isMetal(voxels, 6, 0, 5));
    }

    @Test
    void heavyHitOnBottomLayerDoesNothing()
    {
        final byte[] voxels = Voxels.createIngot();
        final byte[] before = Voxels.copy(voxels);

        Voxels.heavyHit(voxels, 7, 0, 7);

        assertTrue(Voxels.matches(before, voxels));
    }

    @Test
    void hammerOperationsNeverCreateOrDestroyMetal()
    {
        final Random random = new Random(1234L);
        for (int iteration = 0; iteration < 500; iteration++)
        {
            final byte[] voxels = randomGrid(random);
            final int x = random.nextInt(Voxels.SIZE_X);
            final int y = random.nextInt(Voxels.SIZE_Y);
            final int z = random.nextInt(Voxels.SIZE_Z);

            final boolean targetWasMetal = Voxels.isMetal(voxels, x, y, z);
            final int before = Voxels.countMetal(voxels);

            final int operation = random.nextInt(3);
            switch (operation)
            {
                case 0 -> Voxels.heavyHit(voxels, x, y, z);
                case 1 -> Voxels.upset(voxels, x, y, z, random.nextInt(3) - 1, random.nextInt(3) - 1);
                default -> Voxels.split(voxels, x, y, z);
            }

            final int expected = operation == 2 && targetWasMetal ? before - 1 : before;
            assertEquals(expected, Voxels.countMetal(voxels), "Voxel count changed unexpectedly");

            for (byte voxel : voxels)
            {
                assertTrue(voxel == Voxels.EMPTY || voxel == Voxels.METAL || voxel == Voxels.SLAG);
            }
        }
    }

    @Test
    void patternIsCenteredAndMatchesExactly()
    {
        final List<List<String>> layers = new ArrayList<>();
        layers.add(List.of("#######", "#######"));
        layers.add(List.of("###"));

        final byte[] pattern = Voxels.parsePattern(layers);
        assertEquals(17, Voxels.patternVoxels(pattern));

        final byte[] work = Voxels.copy(pattern);
        assertTrue(Voxels.matches(work, pattern));

        Voxels.set(work, 0, 0, 0, Voxels.METAL);
        assertFalse(Voxels.matches(work, pattern));
        assertTrue(Voxels.hasExcess(work, pattern));
    }

    @Test
    void oversizedPatternIsRejected()
    {
        final List<List<String>> layers = new ArrayList<>();
        layers.add(List.of("##################"));
        assertThrows(IllegalArgumentException.class, () -> Voxels.parsePattern(layers));
    }

    private static byte[] randomGrid(Random random)
    {
        final byte[] voxels = Voxels.empty();
        for (int i = 0; i < Voxels.LENGTH; i++)
        {
            if (random.nextInt(4) == 0)
            {
                voxels[i] = Voxels.METAL;
            }
        }
        return voxels;
    }
}
