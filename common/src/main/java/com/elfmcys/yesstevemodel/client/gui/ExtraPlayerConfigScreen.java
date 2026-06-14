package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.ConfigCheckBoxForge;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.button.LoadingStateButton;
import com.elfmcys.yesstevemodel.client.gui.button.RangedSliderWidget;
import com.elfmcys.yesstevemodel.client.gui.button.RenderOrderButton;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import com.elfmcys.yesstevemodel.config.LoadingStateConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ExtraPlayerConfigScreen extends Screen {
    private static final int DESIRED_PANEL_WIDTH = 440;
    private static final int DESIRED_PANEL_HEIGHT = 290;
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MIN_PANEL_HEIGHT = 140;
    private static final int SCREEN_MARGIN = 10;
    private static final int CONTENT_TOP_OFFSET = 42;
    private static final int CONTENT_BOTTOM_PADDING = 14;
    private static final int CONTENT_LEFT_PADDING = 16;
    private static final int ROW_SPACING = 22;
    private static final int BUTTON_ROW_SPACING = 26;

    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;
    private static final int MUTED = 0xFF9DA6AA;

    @Nullable
    private final PlayerModelScreen parentScreen;

    private int guiLeft;

    private int guiTop;

    private int panelWidth;

    private int panelHeight;

    private int contentTop;

    private int contentBottom;

    private int scrollOffset;

    private int maxScroll;

    public ExtraPlayerConfigScreen(@Nullable PlayerModelScreen modelScreen) {
        super(Component.literal("YSM Config GUI"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        clearWidgets();
        setupLayout();

        int leftX = guiLeft + CONTENT_LEFT_PADDING;
        int contentWidth = Math.max(1, panelWidth - (CONTENT_LEFT_PADDING * 2));
        boolean twoColumns = panelWidth >= DESIRED_PANEL_WIDTH;
        int rightX = twoColumns ? guiLeft + 230 : leftX;
        int sliderWidth = Math.min(320, contentWidth);

        addRenderableWidget(new FlatColorButton(guiLeft + 16, guiTop + 16, 80, 18, Component.translatable("gui.yes_steve_model.model.return"), button -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }));

        int sliderY = contentTop + 4 - scrollOffset;
        addIfVisible(new RangedSliderWidget(leftX, sliderY, sliderWidth, 18, Component.translatable("gui.yes_steve_model.config.sound_volume"), Component.literal("%"), 0.0d, 100.0d, GeneralConfig.SOUND_VOLUME.get().doubleValue(), 1D, 0, true) {
            @Override
            protected void applyValue() {
                GeneralConfig.SOUND_VOLUME.set(Double.valueOf(getValue()));
            }
        });

        int leftY = contentTop + 32 - scrollOffset;
        addConfigCheckbox(leftX, leftY, 0, "disable_self_model", GeneralConfig.DISABLE_SELF_MODEL);
        addConfigCheckbox(leftX, leftY, 1, "disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL);
        addConfigCheckbox(leftX, leftY, 2, "print_animation_roulette_msg", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG);
        addConfigCheckbox(leftX, leftY, 3, "disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS);
        addConfigCheckbox(leftX, leftY, 4, "disable_player_render", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER);
        addConfigCheckbox(leftX, leftY, 5, "disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL);
        addConfigCheckbox(leftX, leftY, 6, "disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL);
        addConfigCheckbox(leftX, leftY, 7, "disable_external_first_person_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM);
        addConfigCheckbox(leftX, leftY, 8, "disable_loading_state_screen", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN);

        int rightY = twoColumns ? leftY : leftY + (9 * ROW_SPACING);
        addConfigCheckbox(rightX, rightY, 0, "use_compatibility_renderer", GeneralConfig.USE_COMPATIBILITY_RENDERER);
        addConfigCheckbox(rightX, rightY, 1, "use_native_renderer", GeneralConfig.USE_NATIVE_RENDERER);
        addConfigCheckbox(rightX, rightY, 2, "use_experimental_gpu_renderer", GeneralConfig.USE_EXPERIMENTAL_GPU_RENDERER);
        addConfigCheckbox(rightX, rightY, 3, "render_profiling", GeneralConfig.RENDER_PROFILING);
        addIfVisible(new LoadingStateButton(rightX, rightY + (4 * BUTTON_ROW_SPACING)));
        addIfVisible(new RenderOrderButton(rightX, rightY + (5 * BUTTON_ROW_SPACING), RenderOrderButton.Target.SELF));
        addIfVisible(new RenderOrderButton(rightX, rightY + (6 * BUTTON_ROW_SPACING), RenderOrderButton.Target.OTHER));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + panelWidth, this.guiTop + panelHeight, PANEL_BG);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + panelWidth, this.guiTop + 2, ACCENT);
        guiGraphics.fill(this.guiLeft + 10, this.contentTop, this.guiLeft + panelWidth - 10, this.contentBottom, PANEL_SOFT);
        guiGraphics.drawString(this.font, "YSM Config", this.guiLeft + 108, this.guiTop + 21, TEXT, false);
        renderScrollIndicator(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(net.minecraft.client.gui.GuiGraphics guiGraphics) {

    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0 || !isMouseInPanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int nextOffset = clamp(scrollOffset - (int) Math.round(scrollY * 24.0d), 0, maxScroll);
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            init();
        }
        return true;
    }

    private void setupLayout() {
        int availableWidth = Math.max(1, this.width - (SCREEN_MARGIN * 2));
        int availableHeight = Math.max(1, this.height - (SCREEN_MARGIN * 2));
        this.panelWidth = Math.min(DESIRED_PANEL_WIDTH, Math.max(Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth));
        this.panelHeight = Math.min(DESIRED_PANEL_HEIGHT, Math.max(Math.min(MIN_PANEL_HEIGHT, availableHeight), availableHeight));
        this.guiLeft = (this.width - panelWidth) / 2;
        this.guiTop = (this.height - panelHeight) / 2;
        this.contentTop = this.guiTop + CONTENT_TOP_OFFSET;
        this.contentBottom = this.guiTop + panelHeight - CONTENT_BOTTOM_PADDING;

        boolean twoColumns = panelWidth >= DESIRED_PANEL_WIDTH;
        int leftContentHeight = 32 + (9 * ROW_SPACING);
        int rightContentHeight = 32 + (7 * BUTTON_ROW_SPACING);
        int totalContentHeight = twoColumns ? Math.max(leftContentHeight, rightContentHeight) : leftContentHeight + rightContentHeight - 32;
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        this.maxScroll = Math.max(0, totalContentHeight - viewportHeight);
        this.scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    private void addConfigCheckbox(int x, int baseY, int row, String key, net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        addIfVisible(ConfigCheckBoxForge.create(x, baseY + (row * ROW_SPACING), key, value));
    }

    private <T extends AbstractWidget> void addIfVisible(T widget) {
        if (isWidgetVisible(widget.getY(), widget.getHeight())) {
            addRenderableWidget(widget);
        }
    }

    private boolean isWidgetVisible(int y, int height) {
        return y >= contentTop && y + height <= contentBottom;
    }

    private boolean isMouseInPanel(double mouseX, double mouseY) {
        return mouseX >= guiLeft && mouseX <= guiLeft + panelWidth && mouseY >= guiTop && mouseY <= guiTop + panelHeight;
    }

    private void renderScrollIndicator(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = guiLeft + panelWidth - 7;
        int trackTop = contentTop + 4;
        int trackBottom = contentBottom - 4;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(18, (trackHeight * trackHeight) / (trackHeight + maxScroll));
        int thumbY = trackTop + ((trackHeight - thumbHeight) * scrollOffset / maxScroll);
        guiGraphics.fill(trackX, trackTop, trackX + 3, trackBottom, MUTED & 0x66FFFFFF);
        guiGraphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
