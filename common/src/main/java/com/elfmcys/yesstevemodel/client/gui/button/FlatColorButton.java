package com.elfmcys.yesstevemodel.client.gui.button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public class FlatColorButton extends Button {
    private static final int BUTTON_BG = 0xCC242A30;
    private static final int BUTTON_SELECTED = 0xFF2F6E62;
    private static final int BUTTON_HOVER = 0xFF5CC8A7;
    private static final int TEXT_COLOR = 0xFFF3F0E0;

    private boolean selected;

    private List<Component> tooltip;

    public FlatColorButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
        this.selected = false;
    }

    public FlatColorButton setTooltipText(String str) {
        this.tooltip = Collections.singletonList(Component.translatable(str));
        return this;
    }

    public FlatColorButton setTooltipLines(List<Component> list) {
        this.tooltip = list;
        return this;
    }

    public void renderTooltip(GuiGraphics guiGraphics, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered && this.tooltip != null) {
            guiGraphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
        }
    }

    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (this.selected) {
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, BUTTON_SELECTED, BUTTON_SELECTED);
        } else {
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, BUTTON_BG, BUTTON_BG);
        }
        if (isHoveredOrFocused()) {
            guiGraphics.fillGradient(getX(), getY() + 1, getX() + 1, (getY() + this.height) - 1, BUTTON_HOVER, BUTTON_HOVER);
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + 1, BUTTON_HOVER, BUTTON_HOVER);
            guiGraphics.fillGradient((getX() + this.width) - 1, getY() + 1, getX() + this.width, (getY() + this.height) - 1, BUTTON_HOVER, BUTTON_HOVER);
            guiGraphics.fillGradient(getX(), (getY() + this.height) - 1, getX() + this.width, getY() + this.height, BUTTON_HOVER, BUTTON_HOVER);
        }
        renderScrollingString(guiGraphics, font, 2, TEXT_COLOR);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
