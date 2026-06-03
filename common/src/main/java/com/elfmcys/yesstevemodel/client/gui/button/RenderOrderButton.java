package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;

public class RenderOrderButton extends Button {

    private static final String[] SELF_ORDERS = {
            "LOCAL_YSM",
            "VANILLA",
            "VANILLA,LOCAL_YSM",
            "LOCAL_YSM,VANILLA"
    };

    private static final String[] OTHER_ORDERS = {
            "SERVER_YSM",
            "VANILLA",
            "VANILLA,SERVER_YSM",
            "SERVER_YSM,VANILLA"
    };

    private final Target target;
    private final ForgeConfigSpec.ConfigValue<String> configValue;

    public RenderOrderButton(int x, int y, Target target) {
        super(x, y, 118, 20, Component.empty(), button -> {
        }, DEFAULT_NARRATION);
        this.target = target;
        this.configValue = target == Target.SELF ? GeneralConfig.SELF_PLAYER_RENDER_ORDER : GeneralConfig.OTHER_PLAYER_RENDER_ORDER;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.translatable(target.labelKey), getX() + 123, getY() + 6, -1, false);
    }

    @Override
    public Component getMessage() {
        return Component.translatable(labelForIndex(currentIndex()));
    }

    @Override
    public void onPress() {
        String[] orders = orders();
        int next = (currentIndex() + 1) % orders.length;
        this.configValue.set(orders[next]);
    }

    private int currentIndex() {
        String current = normalize(this.configValue.get());
        String[] orders = orders();
        for (int i = 0; i < orders.length; i++) {
            if (normalize(orders[i]).equals(current)) {
                return i;
            }
        }
        return 0;
    }

    private String[] orders() {
        return this.target == Target.SELF ? SELF_ORDERS : OTHER_ORDERS;
    }

    private String labelForIndex(int index) {
        return switch (index) {
            case 1 -> "gui.yes_steve_model.config.render_order.vanilla_only";
            case 2 -> "gui.yes_steve_model.config.render_order.vanilla_then_ysm";
            case 3 -> "gui.yes_steve_model.config.render_order.ysm_then_vanilla";
            default -> "gui.yes_steve_model.config.render_order.ysm_only";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    public enum Target {
        SELF("gui.yes_steve_model.config.self_render_order"),
        OTHER("gui.yes_steve_model.config.other_render_order");

        private final String labelKey;

        Target(String labelKey) {
            this.labelKey = labelKey;
        }
    }
}
