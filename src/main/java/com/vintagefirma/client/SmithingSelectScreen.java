package com.vintagefirma.client;

import com.vintagefirma.network.C2SSelectRecipePacket;
import com.vintagefirma.network.S2COpenSelectionPacket;
import com.vintagefirma.network.VFNetwork;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Recipe selection screen, opened when the player places a hot ingot on the anvil. Two columns, so a
 * long list still fits on the screen.
 */
public class SmithingSelectScreen extends Screen
{
    private static final int BUTTON_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 22;
    private static final int GAP = 2;
    private static final int COLUMNS = 2;
    private static final int TITLE_HEIGHT = 26;

    private final BlockPos pos;
    private final List<S2COpenSelectionPacket.Entry> entries;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public SmithingSelectScreen(BlockPos pos, List<S2COpenSelectionPacket.Entry> entries)
    {
        super(Component.translatable("vintage_firma.screen.select_recipe"));
        this.pos = pos;
        this.entries = entries;
    }

    @Override
    protected void init()
    {
        final int columns = Math.min(COLUMNS, Math.max(1, entries.size()));
        final int rows = (entries.size() + columns - 1) / columns;

        panelWidth = columns * BUTTON_WIDTH + GAP * (columns + 1);
        panelHeight = TITLE_HEIGHT + rows * (BUTTON_HEIGHT + GAP) + GAP + BUTTON_HEIGHT + GAP;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, (height - panelHeight) / 2);

        for (int i = 0; i < entries.size(); i++)
        {
            final int column = i % columns;
            final int row = i / columns;
            final int x = panelX + GAP + column * (BUTTON_WIDTH + GAP);
            final int y = panelY + TITLE_HEIGHT + row * (BUTTON_HEIGHT + GAP);
            addRenderableWidget(new RecipeButton(x, y, entries.get(i)));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelX + GAP, panelY + panelHeight - BUTTON_HEIGHT - GAP, panelWidth - GAP * 2,
                BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0101010);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFAA7A3A);
        graphics.drawString(font, title, panelX + GAP + 4, panelY + 8, 0xFFE8C080, false);
        graphics.drawString(font, Component.translatable("vintage_firma.screen.recipe_hint"),
            panelX + GAP + 4, panelY + 18, 0xFFB0B0B0, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void select(S2COpenSelectionPacket.Entry entry)
    {
        VFNetwork.sendToServer(new C2SSelectRecipePacket(pos, entry.id()));
        onClose();
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(null);
    }

    private final class RecipeButton extends Button
    {
        private final S2COpenSelectionPacket.Entry entry;

        private RecipeButton(int x, int y, S2COpenSelectionPacket.Entry entry)
        {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(), button -> select(entry),
                DEFAULT_NARRATION);
            this.entry = entry;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);

            graphics.renderItem(entry.result(), getX() + 3, getY() + 3);
            graphics.drawString(font, entry.result().getHoverName(), getX() + 24, getY() + 3, 0xFFFFFFFF, false);
            graphics.drawString(font,
                Component.translatable("vintage_firma.screen.recipe_info", entry.voxels(), entry.ingots()),
                getX() + 24, getY() + 12, 0xFFAAAAAA, false);
        }
    }
}