package com.vintagefirma.common;

import com.vintagefirma.smithing.Voxels;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ItemWorkItem extends Item
{
    public ItemWorkItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack)
    {
        final WorkData data = WorkData.get(stack);
        if (data == null)
        {
            return super.getName(stack);
        }
        return Component.translatable("item.vintage_firma.work_item.named", metalName(data.metal()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        // Temperature is already rendered by TerraFirmaCraft itself (tooltip + heat bar), so only the
        // voxel specific information is added here.
        final WorkData data = WorkData.get(stack);
        if (data == null)
        {
            return;
        }

        tooltip.add(Component.translatable("vintage_firma.tooltip.metal", metalName(data.metal()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("vintage_firma.tooltip.voxels", data.voxelCount(),
            data.totalPieces()).withStyle(ChatFormatting.DARK_GRAY));

        // How the work item is doing: the strike count and how often it cooled down, both of which decide
        // the quality of the finished item (TFC's forging bonus).
        if (level instanceof ServerLevel serverLevel)
        {
            final SmithingRecipe recipe = AnvilHandler.findRecipe(serverLevel, data.recipe());
            if (recipe != null)
            {
                tooltip.add(Component.translatable("vintage_firma.tooltip.quality", data.hits(),
                    recipe.optimalHits(), data.coolings()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static Component metalName(String metal)
    {
        return Component.translatable("vintage_firma.metal." + metal.toLowerCase(Locale.ROOT));
    }

    public static int voxelCapacity(int ingots)
    {
        return ingots * Voxels.VOXELS_PER_INGOT;
    }
}
