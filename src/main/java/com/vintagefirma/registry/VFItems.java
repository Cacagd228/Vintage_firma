package com.vintagefirma.registry;

import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.ItemWorkItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VFItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, VintageFirma.MOD_ID);

    public static final RegistryObject<ItemWorkItem> WORK_ITEM = ITEMS.register("work_item",
        () -> new ItemWorkItem(new Item.Properties().stacksTo(1)));

    private VFItems() {}
}
