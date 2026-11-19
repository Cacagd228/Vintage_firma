package com.vintagefirma.registry;

import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.ClayRecipe;
import com.vintagefirma.common.SmithingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VFRecipes
{
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, VintageFirma.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, VintageFirma.MOD_ID);

    public static final ResourceLocation SMITHING_ID = new ResourceLocation(VintageFirma.MOD_ID, "smithing");

    public static final RegistryObject<RecipeType<SmithingRecipe>> SMITHING_TYPE =
        RECIPE_TYPES.register("smithing", () -> RecipeType.simple(SMITHING_ID));

    public static final RegistryObject<RecipeSerializer<SmithingRecipe>> SMITHING_SERIALIZER =
        RECIPE_SERIALIZERS.register("smithing", SmithingRecipe.Serializer::new);

    public static final ResourceLocation CLAY_ID = new ResourceLocation(VintageFirma.MOD_ID, "clay_forming");

    public static final RegistryObject<RecipeType<ClayRecipe>> CLAY_TYPE =
        RECIPE_TYPES.register("clay_forming", () -> RecipeType.simple(CLAY_ID));

    public static final RegistryObject<RecipeSerializer<ClayRecipe>> CLAY_SERIALIZER =
        RECIPE_SERIALIZERS.register("clay_forming", ClayRecipe.Serializer::new);

    private VFRecipes() {}
}
