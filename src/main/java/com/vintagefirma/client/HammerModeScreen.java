package com.vintagefirma.client;

import com.vintagefirma.common.HammerMode;
import com.vintagefirma.network.C2SSetHammerModePacket;
import com.vintagefirma.network.VFNetwork;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A plain list screen with the six hammer modes, opened by the hammer mode key. The current mode is
 * marked with a bullet and highlighted, Escape / the cancel button just closes it.
 */
public class HammerModeScreen extends Screen
{
    private static final int BUTTON_WIDTH = 168;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 2;
    private static final int TITLE_HEIGHT = 22;

    private final Screen parent;
    private final List<HammerMode> modes = List.of(HammerMode.values());
    private final HammerMode current;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public HammerModeScreen(Screen parent, HammerMode current)
    {
        super(Component.translatable("vintage_firma.screen.hammer_mode"));
        this.parent = parent;
        this.current = current;
    }

    @Override
    protected void init()
    {
        panelWidth = BUTTON_WIDTH + GAP * 2;
        // Title, one row per mode, then the cancel button
        panelHeight = TITLE_HEIGHT + modes.size() * (BUTTON_HEIGHT + GAP) + GAP + BUTTON_HEIGHT + GAP;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(8, (height - panelHeight) / 2);

        for (int i = 0; i < modes.size(); i++)
        {
            final HammerMode mode = modes.get(i);
            addRenderableWidget(new ModeButton(panelX + GAP, panelY + TITLE_HEIGHT + i * (BUTTON_HEIGHT + GAP),
                mode));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelX + GAP, panelY + panelHeight - BUTTON_HEIGHT - GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0101010);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFAA7A3A);
        graphics.drawString(font, title, panelX + GAP + 4, panelY + 7, 0xFFE8C080, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void select(HammerMode mode)
    {
        ClientInput.mode = mode;
        VFNetwork.sendToServer(new C2SSetHammerModePacket(mode.ordinal()));
        onClose();
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    /**
     * The button draws its own label (left aligned, with a bullet on the active mode), so the vanilla
     * centred label is suppressed by passing an empty component.
     */
    private final class ModeButton extends Button
    {
        private final HammerMode mode;

        private ModeButton(int x, int y, HammerMode mode)
        {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(), button -> select(mode),
                DEFAULT_NARRATION);
            this.mode = mode;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);

            final boolean selected = mode == current;
            final Component label = Component.translatable(mode.translationKey());
            final String text = (selected ? "> " : "  ") + label.getString();

            graphics.drawString(font, text, getX() + 6, getY() + (BUTTON_HEIGHT - 8) / 2,
                selected ? 0xFFFFE9A8 : 0xFFC8C8C8, false);
        }
    }
}