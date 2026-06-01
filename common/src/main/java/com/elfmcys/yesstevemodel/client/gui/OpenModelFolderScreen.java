package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class OpenModelFolderScreen extends Screen {
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;

    private final PlayerModelScreen parentScreen;

    public OpenModelFolderScreen(PlayerModelScreen modelScreen) {
        super(Component.literal("Open Model Folder"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        int x = (this.width - 310) / 2;
        int y = (this.height / 2) + 60;
        clearWidgets();
        addRenderableWidget(new FlatColorButton(x, y, 150, 20, Component.translatable("gui.yes_steve_model.open_model_folder.open"), button -> {
            Util.getPlatform().openFile(ServerModelManager.CUSTOM.toFile());
        }));
        addRenderableWidget(new FlatColorButton(x + 160, y, 150, 20, Component.translatable("gui.yes_steve_model.model.return"), button2 -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int left = (this.width - 440) / 2;
        int top = (this.height - 210) / 2;
        guiGraphics.fill(left, top, left + 440, top + 190, PANEL_BG);
        guiGraphics.fill(left, top, left + 440, top + 2, ACCENT);
        guiGraphics.fill(left + 18, top + 28, left + 422, top + 136, PANEL_SOFT);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT);
        guiGraphics.drawWordWrap(this.font, Component.translatable("gui.yes_steve_model.open_model_folder.tips"), (this.width - 400) / 2, top + 38, 400, TEXT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(net.minecraft.client.gui.GuiGraphics guiGraphics) {

    }

}
