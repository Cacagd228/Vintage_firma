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
 * A rock knapping recipe: a loose rock of a given rock class, a layered voxel pattern (the tool) and the
 * resulting stone tool. The player chips a flat blank down to the pattern, exactly like the clay forming
 * but subtractive and always a single layer.
 */
public class RockRecipe implements Recipe<Container>
{
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<List<String>> pattern;
    private final ItemStack result;

    private byte @Nullable [] parsedPattern;

    public RockRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result)
    {
        this.id = id;
        this.ingredient = ingredient;
        this.pattern = pattern;
        this.result = result;
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
        }
        return parsedPattern;
    }

    public int patternVoxelCount()
    {
        return Voxels.patternVoxels(patternVoxels());
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
        return VFRecipes.ROCK_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return VFRecipes.ROCK_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<RockRecipe>
    {
        @Override
        public RockRecipe fromJson(ResourceLocation id, JsonObject json)
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
                throw new IllegalArgumentException("Unknown result item " + resultId + " in rock recipe " + id);
            }
            final ItemStack result = new ItemStack(item, GsonHelper.getAsInt(resultObject, "count", 1));

            return new RockRecipe(id, ingredient, pattern, result);
        }

        @Override
        public @Nullable RockRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
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
            return new RockRecipe(id, ingredient, pattern, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RockRecipe recipe)
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
        }
    }
}