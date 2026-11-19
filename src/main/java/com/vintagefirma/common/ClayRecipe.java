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
 * A clay forming recipe: a clay ingredient, a layered voxel pattern and the resulting (unfired) item.
 *
 * <p>The pattern uses the same format as the smithing recipes: a list of layers starting with the bottom
 * layer, every layer a list of strings where {@code #} marks a clay voxel that must be present and any
 * other character is empty space. Unlike smithing the pattern sits directly on top of the support block,
 * so the bottom layer is {@code y = 0}.
 */
public class ClayRecipe implements Recipe<Container>
{
    public static final int DEFAULT_VOXELS_PER_CLAY = 25;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<List<String>> pattern;
    private final ItemStack result;
    private final int voxelsPerClay;

    private byte @Nullable [] parsedPattern;

    public ClayRecipe(ResourceLocation id, Ingredient ingredient, List<List<String>> pattern, ItemStack result,
                      int voxelsPerClay)
    {
        this.id = id;
        this.ingredient = ingredient;
        this.pattern = pattern;
        this.result = result;
        this.voxelsPerClay = voxelsPerClay > 0 ? voxelsPerClay : DEFAULT_VOXELS_PER_CLAY;
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

    /** How many voxels a single clay item provides while shaping this recipe. */
    public int voxelsPerClay()
    {
        return voxelsPerClay;
    }

    /** @return how many clay items the finished piece needs at minimum */
    public int requiredClay()
    {
        return Math.max(1, (patternVoxelCount() + voxelsPerClay - 1) / voxelsPerClay);
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
        return VFRecipes.CLAY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return VFRecipes.CLAY_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ClayRecipe>
    {
        @Override
        public ClayRecipe fromJson(ResourceLocation id, JsonObject json)
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
                throw new IllegalArgumentException("Unknown result item " + resultId + " in clay recipe " + id);
            }
            final ItemStack result = new ItemStack(item, GsonHelper.getAsInt(resultObject, "count", 1));

            final int perClay = GsonHelper.getAsInt(json, "voxels_per_clay", DEFAULT_VOXELS_PER_CLAY);

            return new ClayRecipe(id, ingredient, pattern, result, perClay);
        }

        @Override
        public @Nullable ClayRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
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
            final int perClay = buffer.readVarInt();

            return new ClayRecipe(id, ingredient, pattern, result, perClay);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ClayRecipe recipe)
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
            buffer.writeVarInt(recipe.voxelsPerClay);
        }
    }
}