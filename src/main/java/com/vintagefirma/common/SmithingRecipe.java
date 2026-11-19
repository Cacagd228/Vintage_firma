package com.vintagefirma.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vintagefirma.registry.VFRecipes;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A smithing recipe: an ingredient (a hot ingot), a layered voxel pattern and the resulting item.
 *
 * <p>The pattern is given as a list of layers, starting with the bottom layer. Every layer is a list of
 * strings where {@code #} marks a voxel that must be present and any other character is empty space.
 */
public class SmithingRecipe implements Recipe<Container>
{
    private static final int DEFAULT_PATTERN_FLOOR = 5;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<List<String>> pattern;
    private final ItemStack result;
    private final String metal;
    private final int patternFloor;
    private final int optimalHits;
    private final int tier;

    private byte @Nullable [] parsedPattern;

    public SmithingRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result,
                          String metal)
    {
        this(id, ingredient, pattern, result, metal, DEFAULT_PATTERN_FLOOR);
    }

    public SmithingRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result,
                          String metal, int patternFloor)
    {
        this(id, ingredient, pattern, result, metal, patternFloor, -1, 1);
    }

    public SmithingRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result,
                          String metal, int patternFloor, int optimalHits)
    {
        this(id, ingredient, pattern, result, metal, patternFloor, optimalHits, 1);
    }

    public SmithingRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result,
                          String metal, int patternFloor, int optimalHits, int tier)
    {
        this.id = id;
        this.ingredient = ingredient;
        this.pattern = pattern;
        this.result = result;
        this.metal = metal;
        this.patternFloor = patternFloor;
        this.optimalHits = optimalHits;
        this.tier = tier;
    }

    /** The anvil tier required to forge this recipe (1 = copper, 2 = bronze, 3 = wrought iron, ...). */
    public int tier()
    {
        return tier;
    }

    /**
     * The lowest voxel layer that the player starts at: one layer is filled with the ingot footprint, so the
     * authored pattern is placed {@code patternFloor} layers above the anvil surface to leave room for it.
     */
    public int patternFloor()
    {
        return patternFloor;
    }

    @Override
    public ResourceLocation getId()
    {
        return id;
    }

    public Ingredient getIngredient()
    {
        return ingredient;
    }

    public List<List<String>> getPattern()
    {
        return pattern;
    }

    public ItemStack getResult()
    {
        return result;
    }

    public String getMetal()
    {
        return metal;
    }

    public byte[] patternVoxels()
    {
        if (parsedPattern == null)
        {
            final byte[] raw = Voxels.parsePattern(pattern);
            parsedPattern = Voxels.empty();
            for (int i = 0; i < raw.length; i++)
            {
                if (raw[i] != Voxels.EMPTY)
                {
                    parsedPattern[i] = Voxels.METAL;
                }
            }

            // The pattern is authored with its bottom layer at y = 0; move it up to the working height.
            final int shift = patternFloor;
            if (shift > 0)
            {
                final byte[] shifted = Voxels.empty();
                for (int y = 0; y < Voxels.SIZE_Y; y++)
                {
                    final int sourceY = y - shift;
                    if (sourceY < 0) continue;
                    for (int z = 0; z < Voxels.SIZE_Z; z++)
                    {
                        for (int x = 0; x < Voxels.SIZE_X; x++)
                        {
                            shifted[Voxels.index(x, y, z)] = parsedPattern[Voxels.index(x, sourceY, z)];
                        }
                    }
                }
                parsedPattern = shifted;
            }
        }
        return parsedPattern;
    }

    public int patternVoxelCount()
    {
        return Voxels.patternVoxels(patternVoxels());
    }

    public int requiredIngots()
    {
        return Math.max(1, (patternVoxelCount() + Voxels.VOXELS_PER_INGOT - 1) / Voxels.VOXELS_PER_INGOT);
    }

    /**
     * The number of hammer strikes the recipe expects. TFC derives the forging bonus from
     * {@code steps taken / minimum required steps}, and this is the "minimum" for a voxel recipe: one
     * strike per voxel that has to be moved. Recipes can override it with {@code optimal_hits} in JSON.
     */
    public int optimalHits()
    {
        return optimalHits > 0 ? optimalHits : Math.max(1, patternVoxelCount());
    }

    @Override
    public boolean matches(Container container, Level level)
    {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access)
    {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height)
    {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access)
    {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients()
    {
        final NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return VFRecipes.SMITHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return VFRecipes.SMITHING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<SmithingRecipe>
    {
        @Override
        public SmithingRecipe fromJson(ResourceLocation id, JsonObject json)
        {
            final Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "ingredient"));

            final List<List<String>> pattern = new ArrayList<>();
            final JsonArray layers = GsonHelper.getAsJsonArray(json, "pattern");
            for (JsonElement layerElement : layers)
            {
                final List<String> layer = new ArrayList<>();
                for (JsonElement rowElement : layerElement.getAsJsonArray())
                {
                    layer.add(rowElement.getAsString());
                }
                pattern.add(layer);
            }

            final JsonObject resultObject = GsonHelper.getAsJsonObject(json, "result");
            final ResourceLocation resultId = new ResourceLocation(GsonHelper.getAsString(resultObject, "item"));
            final Item item = ForgeRegistries.ITEMS.getValue(resultId);
            if (item == null)
            {
                throw new IllegalArgumentException("Unknown result item " + resultId + " in smithing recipe " + id);
            }
            final ItemStack result = new ItemStack(item, GsonHelper.getAsInt(resultObject, "count", 1));

            final String metal = GsonHelper.getAsString(json, "metal");
            final int floor = GsonHelper.getAsInt(json, "pattern_floor", DEFAULT_PATTERN_FLOOR);
            final int optimal = GsonHelper.getAsInt(json, "optimal_hits", -1);
            final int tier = GsonHelper.getAsInt(json, "tier", 1);

            return new SmithingRecipe(id, ingredient, pattern, result, metal, floor, optimal, tier);
        }

        @Override
        public @Nullable SmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            final Ingredient ingredient = Ingredient.fromNetwork(buffer);

            final int layerCount = buffer.readVarInt();
            final List<List<String>> pattern = new ArrayList<>(layerCount);
            for (int layer = 0; layer < layerCount; layer++)
            {
                final int rowCount = buffer.readVarInt();
                final List<String> rows = new ArrayList<>(rowCount);
                for (int row = 0; row < rowCount; row++)
                {
                    rows.add(buffer.readUtf());
                }
                pattern.add(rows);
            }

            final ItemStack result = buffer.readItem();
            final String metal = buffer.readUtf();
            final int floor = buffer.readVarInt();
            final int optimal = buffer.readVarInt();
            final int tier = buffer.readVarInt();

            return new SmithingRecipe(id, ingredient, pattern, result, metal, floor, optimal, tier);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SmithingRecipe recipe)
        {
            recipe.ingredient.toNetwork(buffer);

            buffer.writeVarInt(recipe.pattern.size());
            for (List<String> layer : recipe.pattern)
            {
                buffer.writeVarInt(layer.size());
                for (String row : layer)
                {
                    buffer.writeUtf(row);
                }
            }

            buffer.writeItem(recipe.result);
            buffer.writeUtf(recipe.metal);
            buffer.writeVarInt(recipe.patternFloor);
            buffer.writeVarInt(recipe.optimalHits);
            buffer.writeVarInt(recipe.tier);
        }
    }
}
