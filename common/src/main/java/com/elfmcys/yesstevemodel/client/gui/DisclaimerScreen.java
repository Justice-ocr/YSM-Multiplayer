package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public class DisclaimerScreen extends Screen {
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;

    private Checkbox checkbox;

    private int textY;

    private int textHeight;

    public DisclaimerScreen() {
        super(Component.literal("Disclaimer GUI"));
    }

    public void init() {
        clearWidgets();
        int size = this.font.split(Component.translatable("gui.yes_steve_model.disclaimer.text"), 400).size();
        Objects.requireNonNull(this.font);
        int i = (size * 9) + 20 + 20 + 10 + 20;
        this.textY = (this.width - 400) / 2;
        this.textHeight = (this.height - i) / 2;
        MutableComponent mutableComponentTranslatable = Component.translatable("gui.yes_steve_model.disclaimer.read");
        int iWidth = this.font.width(mutableComponentTranslatable);
        this.checkbox = Checkbox.builder(mutableComponentTranslatable, this.font)
                .pos((this.width - iWidth) / 2, (this.textHeight + i) - 50)
                .selected(!GeneralConfig.DISCLAIMER_SHOW.get().booleanValue())
                .build();
        addRenderableWidget(this.checkbox);
        addRenderableWidget(new FlatColorButton((this.width - 300) / 2, (this.textHeight + i) - 20, 300, 20, Component.translatable("gui.yes_steve_model.disclaimer.close"), button -> {
            if (this.checkbox.selected()) {
                GeneralConfig.DISCLAIMER_SHOW.set(false);
                Minecraft.getInstance().setScreen(new PlayerModelScreen());
            } else {
                Minecraft.getInstance().setScreen(null);
            }
        }));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int panelLeft = (this.width - 440) / 2;
        int panelTop = this.textHeight - 16;
        int panelBottom = this.height - 24;
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 440, panelBottom, PANEL_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 440, panelTop + 2, ACCENT);
        guiGraphics.fill(panelLeft + 18, this.textHeight - 2, panelLeft + 422, panelBottom - 58, PANEL_SOFT);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 7, TEXT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawWordWrap(this.font, Component.translatable("gui.yes_steve_model.disclaimer.text"), this.textY, this.textHeight, 400, TEXT);
    }

    @Override
    protected void renderBlurredBackground(net.minecraft.client.gui.GuiGraphics guiGraphics) {

    }

}
