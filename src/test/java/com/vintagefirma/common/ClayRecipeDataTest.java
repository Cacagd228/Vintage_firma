package com.vintagefirma.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vintagefirma.smithing.Voxels;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every clay forming recipe file: it must parse, the pattern must fit the grid and it must
 * produce an unfired TFC pottery item.
 */
class ClayRecipeDataTest
{
    private static final Path RECIPES = Path.of("src", "main", "resources", "data", "vintage_firma",
        "recipes", "clay");

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

    @Test
    void everyRecipeIsValid() throws IOException
    {
        List<Path> files;
        try (Stream<Path> stream = Files.list(RECIPES))
        {
            files = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        assertFalse(files.isEmpty(), "no clay recipes found in " + RECIPES);

        for (Path path : files)
        {
            final JsonObject recipe = read(path);
            assertEquals("vintage_firma:clay_forming", recipe.get("type").getAsString(),
                path.getFileName().toString());

            final List<List<String>> layers = layers(recipe);
            assertFalse(layers.isEmpty(), path + " has no layers");
            assertTrue(layers.size() <= Voxels.SIZE_Y, path + " has too many layers");

            // Must not throw: the pattern has to fit the 16 x 6 x 16 grid
            Voxels.parsePattern(layers);
            assertTrue(Voxels.countMetal(Voxels.parsePattern(layers)) > 0, path + " has an empty pattern");

            final String result = recipe.getAsJsonObject("result").get("item").getAsString();
            assertTrue(result.startsWith("tfc:ceramic/unfired_"),
                path.getFileName().toString() + " has a bad result " + result);
        }
    }
}