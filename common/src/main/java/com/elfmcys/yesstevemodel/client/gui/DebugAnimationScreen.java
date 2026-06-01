package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DebugAnimationScreen extends Screen {
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;

    private final PlayerModelScreen parentScreen;
    private int guiLeft;
    private int guiTop;

    public DebugAnimationScreen(PlayerModelScreen modelScreen) {
        super(Component.literal("YSM Config GUI"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        addRenderableWidget(new FlatColorButton(this.guiLeft + 5, this.guiTop, 80, 18, Component.translatable("gui.yes_steve_model.model.return"), button -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 420, this.guiTop + 120, PANEL_BG);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 420, this.guiTop + 2, ACCENT);
        guiGraphics.fill(this.guiLeft + 20, this.guiTop + 34, this.guiLeft + 400, this.guiTop + 104, PANEL_SOFT);
        guiGraphics.drawCenteredString(this.font, "Coming Soon", this.width / 2, this.guiTop + 62, TEXT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }
}
