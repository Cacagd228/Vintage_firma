package com.vintagefirma.common;

import com.vintagefirma.VintageFirma;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.HeatHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives work items a heat capability. TFC only attaches heat to items that have a
 * {@code tfc:item_heats} definition in a datapack, so the work item has to do it itself.
 *
 * <p>The values match a wrought iron ingot: heat capacity {@code 2.857143}, forging temperature
 * {@code 921 °C} and welding temperature {@code 1228 °C}.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapabilityEvents
{
    private static final float HEAT_CAPACITY = 2.857143f;
    private static final float FORGING_TEMPERATURE = 921f;
    private static final float WELDING_TEMPERATURE = 1228f;

    private CapabilityEvents()
    {
    }

    @SubscribeEvent
    public static void onAttachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event)
    {
        if (!(event.getObject().getItem() instanceof ItemWorkItem)) return;
        if (event.getCapabilities().containsKey(HeatCapability.KEY)) return;

        final ICapabilityProvider provider = new HeatHandler(HEAT_CAPACITY, FORGING_TEMPERATURE,
            WELDING_TEMPERATURE);
        event.addCapability(HeatCapability.KEY, provider);
    }
}
