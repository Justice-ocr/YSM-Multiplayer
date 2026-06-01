package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.upload.ModelUploadSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModelUploadScreen extends Screen implements ModelUploadSession.Listener {
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;
    private static final int MUTED = 0xFF9DA6AA;

    private final PlayerModelScreen parentScreen;
    private EditBox pathBox;
    private EditBox modelIdBox;
    private String status = "";

    public ModelUploadScreen(PlayerModelScreen parentScreen) {
        super(Component.literal("Upload Model"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int left = (this.width - 460) / 2;
        int top = (this.height - 230) / 2;
        clearWidgets();

        this.pathBox = new EditBox(this.font, left + 102, top + 58, 256, 18, Component.literal("File"));
        this.pathBox.setMaxLength(1024);
        addRenderableWidget(this.pathBox);

        this.modelIdBox = new EditBox(this.font, left + 102, top + 90, 256, 18, Component.literal("Model ID"));
        this.modelIdBox.setMaxLength(160);
        addRenderableWidget(this.modelIdBox);

        addRenderableWidget(new FlatColorButton(left + 366, top + 58, 70, 18, Component.literal("Browse"), button -> browseFile()));
        addRenderableWidget(new FlatColorButton(left + 102, top + 124, 124, 20, Component.literal("Upload"), button -> startUpload()));
        addRenderableWidget(new FlatColorButton(left + 234, top + 124, 124, 20, Component.literal("Clear"), button -> {
            ModelUploadSession.clearIfTerminal();
            this.status = "";
        }));
        addRenderableWidget(new FlatColorButton(left + 102, top + 184, 256, 20, Component.translatable("gui.yes_steve_model.model.return"), button -> Minecraft.getInstance().setScreen(this.parentScreen)));

        ModelUploadSession.removeListener(this);
        ModelUploadSession.addListener(this);
        onSessionUpdate(ModelUploadSession.getInstance());
    }

    @Override
    public void removed() {
        ModelUploadSession.removeListener(this);
    }

    private void browseFile() {
        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Select YSM model",
                "",
                null,
                "YSM model",
                false
        );
        if (StringUtils.isBlank(selected)) {
            return;
        }
        this.pathBox.setValue(selected);
        if (StringUtils.isBlank(this.modelIdBox.getValue())) {
            String fileName = Path.of(selected).getFileName().toString();
            this.modelIdBox.setValue(fileName);
        }
    }

    private void startUpload() {
        Path path;
        try {
            path = Path.of(this.pathBox.getValue());
        } catch (Exception e) {
            this.status = "Invalid path";
            return;
        }

        if (!Files.isRegularFile(path)) {
            this.status = "File does not exist";
            return;
        }

        String modelId = StringUtils.trimToEmpty(this.modelIdBox.getValue());
        if (StringUtils.isBlank(modelId)) {
            modelId = path.getFileName().toString();
            this.modelIdBox.setValue(modelId);
        }

        try {
            byte[] data = Files.readAllBytes(path);
            String error = ModelUploadSession.start(modelId, data);
            this.status = error == null ? "Waiting for server..." : error;
        } catch (IOException e) {
            this.status = "Read failed: " + e.getMessage();
        }
    }

    @Override
    public void onSessionUpdate(ModelUploadSession session) {
        if (session != null) {
            this.status = session.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int left = (this.width - 460) / 2;
        int top = (this.height - 230) / 2;

        guiGraphics.fill(left, top, left + 460, top + 220, PANEL_BG);
        guiGraphics.fill(left, top, left + 460, top + 2, ACCENT);
        guiGraphics.fill(left + 14, top + 42, left + 446, top + 172, PANEL_SOFT);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 16, TEXT);
        guiGraphics.drawString(this.font, "File", left + 28, top + 63, TEXT, false);
        guiGraphics.drawString(this.font, "Model ID", left + 28, top + 95, TEXT, false);

        ModelUploadSession session = ModelUploadSession.getInstance();
        String limitText = ModelUploadSession.hasServerLimits()
                ? "Server limit: " + ModelUploadSession.formatBytes(ModelUploadSession.getLastMaxTotalBytes()) + ", " + ModelUploadSession.getLastChunksPerTick() + " chunks/tick"
                : "Server limit: unknown until first upload request";
        guiGraphics.drawString(this.font, limitText, left + 102, top + 152, MUTED, false);

        if (session != null) {
            int barX = left + 102;
            int barY = top + 165;
            int barW = 256;
            int filled = (int) (barW * session.getProgress());
            guiGraphics.fill(barX, barY, barX + barW, barY + 8, 0xFF333333);
            guiGraphics.fill(barX, barY, barX + filled, barY + 8, session.getState() == ModelUploadSession.State.FAILED ? 0xFFAA3333 : 0xFF3A9E6E);
            guiGraphics.drawString(this.font, ModelUploadSession.formatBytes(session.getSentBytes()) + " / " + ModelUploadSession.formatBytes(session.getTotalBytes()), barX + barW + 8, barY, MUTED, false);
        }

        if (StringUtils.isNotBlank(this.status)) {
            guiGraphics.drawString(this.font, this.status, left + 28, top + 208, TEXT, false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }
}
