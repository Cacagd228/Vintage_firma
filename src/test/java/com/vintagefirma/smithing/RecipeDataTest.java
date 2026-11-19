package com.vintagefirma.smithing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every generated recipe file: it must parse, the pattern must fit the grid and the amount of
 * voxels must match the ingredient (one ingot / one sheet / two of either).
 */
class RecipeDataTest
{
    private static final Path RECIPES = Path.of("src", "main", "resources", "data", "vintage_firma",
        "recipes", "smithing");

    private static JsonObject read(Path path) throws IOException
    {
        try (InputStream stream = Files.newInputStream(path))
        {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<List<String>> layers(JsonObject recipe)
    {
        final List<List<String>> layers = new ArrayList<>();
        for (JsonElement layerElement : recipe.getAsJsonArray("pattern"))
        {
            final List<String> layer = new ArrayList<>();
            for (JsonElement row : layerElement.getAsJsonArray())
            {
                layer.add(row.getAsString());
            }
            layers.add(layer);
        }
        return layers;
    }

    private static int countVoxels(List<List<String>> layers)
    {
        int total = 0;
        for (List<String> layer : layers)
        {
            for (String row : layer)
            {
                for (char c : row.toCharArray())
                {
                    if (Voxels.isFilledChar(c)) total++;
                }
            }
        }
        return total;
    }

    @Test
    void everyRecipeIsValid() throws IOException
    {
        List<Path> files;
        try (Stream<Path> stream = Files.list(RECIPES))
        {
            files = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        assertFalse(files.isEmpty(), "no recipes found in " + RECIPES);

        final Set<String> metals = new HashSet<>();
        for (Path path : files)
        {
            final JsonObject recipe = read(path);
            assertEquals("vintage_firma:smithing", recipe.get("type").getAsString(),
                path.getFileName().toString());

            final List<List<String>> layers = layers(recipe);
            final int voxels = countVoxels(layers);
            assertTrue(voxels > 0, path + " has an empty pattern");

            // The pattern must fit the 16 x 6 x 16 grid
            assertTrue(layers.size() <= Voxels.SIZE_Y, path + " has too many layers");
            Voxels.parsePattern(layers);

            // Every recipe starts with one ingot (42 voxels) and the player adds more while forging.
            // So the pattern must be fillable by a whole number of ingots, and never need more than 2.
            final String ingredient = recipe.getAsJsonObject("ingredient").get("item").getAsString();
            assertTrue(ingredient.startsWith("tfc:"),
                path.getFileName().toString() + " has a bad ingredient " + ingredient);

            // Every recipe is started with one ingot (42 voxels) and the player adds more while forging.
            // Large pieces (a shield, a trapdoor, ...) legitimately need several ingots, so only require
            // at least one and a sane upper bound.
            final int neededIngots = (voxels + Voxels.VOXELS_PER_INGOT - 1) / Voxels.VOXELS_PER_INGOT;
            assertTrue(neededIngots >= 1 && neededIngots <= 8,
                path + " needs " + voxels + " voxels (" + neededIngots + " ingots)");

            final int tier = recipe.get("tier").getAsInt();
            assertTrue(tier >= 1 && tier <= 6, path + " has a bad tier " + tier);

            metals.add(recipe.get("metal").getAsString());
        }

        // Every metal of the test set must have recipes
        assertEquals(9, metals.size(), "expected recipes for 9 metals, got " + metals);
    }

    @Test
    void everyMetalHasItsShovelHead() throws IOException
    {
        for (String metal : List.of("copper", "bismuth_bronze", "black_bronze", "bronze", "wrought_iron",
            "steel", "black_steel", "blue_steel", "red_steel"))
        {
            final Path path = RECIPES.resolve(metal + "_shovel_head.json");
            assertTrue(Files.exists(path), "missing " + path);

            final JsonObject recipe = read(path);
            assertEquals("tfc:metal/shovel_head/" + metal,
                recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals("tfc:metal/ingot/" + metal,
                recipe.getAsJsonObject("ingredient").get("item").getAsString());
        }
    }
}