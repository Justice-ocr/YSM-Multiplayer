package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.ConfigCheckBoxForge;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.button.LoadingStateButton;
import com.elfmcys.yesstevemodel.client.gui.button.RangedSliderWidget;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import com.elfmcys.yesstevemodel.config.LoadingStateConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ExtraPlayerConfigScreen extends Screen {
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;

    @Nullable
    private final PlayerModelScreen parentScreen;

    private int guiLeft;

    private int guiTop;

    public ExtraPlayerConfigScreen(@Nullable PlayerModelScreen modelScreen) {
        super(Component.literal("YSM Config GUI"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        this.guiLeft = (this.width - 440) / 2;
        this.guiTop = (this.height - 290) / 2;
        addRenderableWidget(new FlatColorButton(guiLeft + 16, guiTop + 16, 80, 18, Component.translatable("gui.yes_steve_model.model.return"), button -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }));
        addRenderableWidget(new RangedSliderWidget(guiLeft + 16, guiTop + 46, 320, 18, Component.translatable("gui.yes_steve_model.config.sound_volume"), Component.literal("%"), 0.0d, 100.0d, GeneralConfig.SOUND_VOLUME.get().doubleValue(), 1D, 0, true) {
            @Override
            protected void applyValue() {
                GeneralConfig.SOUND_VOLUME.set(Double.valueOf(getValue()));
            }
        });
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 74, "disable_self_model", GeneralConfig.DISABLE_SELF_MODEL));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 96, "disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 118, "print_animation_roulette_msg", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 140, "disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 162, "disable_player_render", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 184, "disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 206, "disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 228, "disable_external_first_person_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 16, guiTop + 250, "disable_loading_state_screen", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 230, guiTop + 74, "use_compatibility_renderer", GeneralConfig.USE_COMPATIBILITY_RENDERER));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 230, guiTop + 96, "use_native_renderer", GeneralConfig.USE_NATIVE_RENDERER));
        addRenderableWidget(ConfigCheckBoxForge.create(guiLeft + 230, guiTop + 118, "render_profiling", GeneralConfig.RENDER_PROFILING));
        addRenderableWidget(new LoadingStateButton(guiLeft + 230, guiTop + 144));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 440, this.guiTop + 290, PANEL_BG);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 440, this.guiTop + 2, ACCENT);
        guiGraphics.fill(this.guiLeft + 10, this.guiTop + 42, this.guiLeft + 430, this.guiTop + 276, PANEL_SOFT);
        guiGraphics.drawString(this.font, "YSM Config", this.guiLeft + 108, this.guiTop + 21, TEXT, false);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(net.minecraft.client.gui.GuiGraphics guiGraphics) {

    }

}
