package com.vintagefirma;

import com.mojang.logging.LogUtils;
import com.vintagefirma.network.VFNetwork;
import com.vintagefirma.registry.VFItems;
import com.vintagefirma.registry.VFRecipes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VintageFirma.MOD_ID)
public final class VintageFirma
{
    public static final String MOD_ID = "vintage_firma";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VintageFirma()
    {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        VFItems.ITEMS.register(modBus);
        VFRecipes.RECIPE_TYPES.register(modBus);
        VFRecipes.RECIPE_SERIALIZERS.register(modBus);

        modBus.addListener(VFNetwork::register);
        modBus.addListener(VintageFirma::addCreativeTabs);

        LOGGER.info("Vintage Firma is loading");
    }

    private static void addCreativeTabs(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(VFItems.WORK_ITEM);
        }
    }
}
