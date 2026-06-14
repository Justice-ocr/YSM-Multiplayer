package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.event.ModScreenEvent;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.client.gui.button.*;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import java.util.Optional;
import dev.architectury.platform.Platform;
import com.elfmcys.yesstevemodel.mixin.client.ScreenAccessor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class PlayerModelScreen extends Screen implements IGuiWidget {
    private static final int GUI_WIDTH = 500;
    private static final int GUI_HEIGHT = 265;
    private static final int SCREEN_MARGIN = 8;
    private static final int MIN_PANEL_WIDTH = 360;
    private static final int MIN_PANEL_HEIGHT = 230;
    private static final int LEFT_PANE_MIN_WIDTH = 132;
    private static final int LEFT_PANE_MAX_WIDTH = 168;
    private static final int RIGHT_PANE_GAP = 8;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 33;
    private static final int SLOT_WIDTH = 52;
    private static final int SLOT_HEIGHT = 90;
    private static final int SLOT_X_SPACING = 55;
    private static final int SLOT_Y_SPACING = 93;
    private static final int PANEL_BG = 0xCC14171A;
    private static final int PANEL_SOFT = 0xAA20252A;
    private static final int PANEL_DARK = 0xAA0D0F12;
    private static final int ACCENT = 0xFF5CC8A7;
    private static final int TEXT = 0xFFF3F0E0;
    private static final int MUTED = 0xFF9DA6AA;

    private static final String AUTHOR_SEARCH_PREFIX = "@";

    private static final String TAG_SEARCH_PREFIX = "#";

    private final HashSet<String> hiddenModels;

    private final Map<String, ModelPackData> modelPackMap;

    private Map<String, ModelAssembly> filteredModels;

    private Map<String, ModelPackData> filteredPacks;

    private List<String> sortedModelKeys;

    private List<String> sortedPackKeys;

    private List<String> sortedLocalModelKeys;

    private List<String> sortedServerModelKeys;

    private List<String> sortedLocalPackKeys;

    private List<String> sortedServerPackKeys;

    public int guiLeft;

    public int guiTop;

    private int panelWidth;

    private int panelHeight;

    private int leftPaneWidth;

    private int rightPaneLeft;

    private int rightPaneRight;

    private int gridTop;

    private int gridBottom;

    private int gridColumns;

    private int gridRows;

    private int gridPageSize = 10;

    private int maxPage;

    private EditBox searchBox;

    private Category category;

    private static PlayerPreviewEntity[] previewHolders = new PlayerPreviewEntity[10];

    private static final Object2IntMap<String> pageIndexMap = new Object2IntOpenHashMap();

    private static String currentPath = StringPool.EMPTY;

    private static ModelSource currentModelSource = ModelSource.LOCAL;

    static {
        for (int i = 0; i < previewHolders.length; i++) {
            previewHolders[i] = new PlayerPreviewEntity();
        }
    }

    public PlayerModelScreen() {
        super(Component.literal("YSM Player Model GUI"));
        this.hiddenModels = Sets.newHashSet();
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        this.category = Category.ALL;
        if (NetworkHandler.isClientConnected()) {
            this.hiddenModels.addAll(ServerConfig.CLIENT_NOT_DISPLAY_MODELS.get());
        }
        ClientModelManager.registerGuiWidget(this);
        this.modelPackMap = new Object2ReferenceOpenHashMap<>(ClientModelManager.getModelPackMap());
    }

    public ModelButton createModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        return new ModelButton(x, y, isAuthLocked, previewEntity, modelAssembly);
    }

    public PlayerTextureScreen createTextureScreen(PlayerModelScreen other, String str, ModelAssembly modelAssembly) {
        return new PlayerTextureScreen(other, str, modelAssembly);
    }

    public ModelInfoScreen createModelInfoScreen(PlayerModelScreen other, ModelAssembly modelAssembly) {
        return new ModelInfoScreen(other, modelAssembly);
    }

    private Map<String, ModelAssembly> buildFilteredModelMap() {
        HashMap mapNewHashMap = Maps.newHashMap();
        if (StringUtils.isBlank(currentPath)) {
            mapNewHashMap.putAll(ClientModelManager.getModelAssemblyMap());
        }
        ClientModelManager.getModelAssemblyMap().forEach((str, modelAssembly) -> {
            if (str.startsWith(currentPath)) {
                mapNewHashMap.put(str, modelAssembly);
            }
            String str2 = FileTypeUtil.splitFileNameAndParentDir(str).right();
            if (StringUtils.isNotBlank(str2)) {
                ensurePackHierarchy(str2, this.modelPackMap);
            }
        });
        return mapNewHashMap;
    }

    private static void ensurePackHierarchy(String str, Map<String, ModelPackData> map) {
        if (StringUtils.isBlank(str) || !str.contains("/")) {
            return;
        }
        String[] strArrSplit = str.split("/");
        StringBuilder sb = new StringBuilder();
        for (String str2 : strArrSplit) {
            if (!str2.isEmpty()) {
                sb.append(str2).append("/");
                String string = sb.toString();
                map.putIfAbsent(string, new ModelPackData(string, FileTypeUtil.getFinalPathSegment(string), StringPool.EMPTY, null, null));
            }
        }
    }

    private Map<String, ModelPackData> buildFilteredPackMap() {
        HashMap<String, ModelPackData> mapNewHashMap = Maps.newHashMap();
        if (StringUtils.isBlank(currentPath)) {
            return Maps.newHashMap(this.modelPackMap);
        }
        this.modelPackMap.forEach((str, c0616x1389bc7f) -> {
            if (str.startsWith(currentPath)) {
                mapNewHashMap.put(str, c0616x1389bc7f);
            }
        });
        return mapNewHashMap;
    }

    private void refreshModelList() {
        String lowerCase;
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        LocalPlayer localPlayer = this.minecraft.player;
        if (this.category == Category.ALL) {
            this.filteredModels = buildFilteredModelMap();
            this.filteredPacks = buildFilteredPackMap();
        }
        if (this.category == Category.AUTH) {
            AuthModelsCapability.get(localPlayer).ifPresent(cap -> {
                for (Map.Entry<String, ModelAssembly> entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
                    if (cap.containsModel(entry.getKey()) || !entry.getValue().getTextureRegistry().isAuthModel()) {
                        this.filteredModels.put(entry.getKey(), entry.getValue());
                    }
                }
            });
        }
        if (this.category == Category.STAR) {
            StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                for (Map.Entry<String, ModelAssembly> entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
                    if (cap2.containsModel(entry.getKey())) {
                        this.filteredModels.put(entry.getKey(), entry.getValue());
                    }
                }
            });
        }
        if (this.searchBox != null) {
            lowerCase = this.searchBox.getValue().toLowerCase(Locale.ENGLISH);
        } else {
            lowerCase = StringPool.EMPTY;
        }
        if (StringUtils.isBlank(lowerCase)) {
            this.filteredModels.entrySet().removeIf(entry -> {
                Pair<String, String> pair = FileTypeUtil.splitFileNameAndParentDir(entry.getKey());
                return this.hiddenModels.contains(pair.left()) || !pair.right().equals(currentPath);
            });
            this.filteredPacks.entrySet().removeIf(entry2 -> {
                return !isDirectChild(currentPath, entry2.getKey());
            });
        } else {
            String str = lowerCase;
            this.filteredModels.entrySet().removeIf(entry3 -> {
                return shouldFilterModel(FileTypeUtil.splitFileNameAndParentDir(entry3.getKey()).left(), entry3.getValue(), str);
            });
            String str2 = lowerCase;
            this.filteredPacks.entrySet().removeIf(entry4 -> {
                return shouldFilterPack(FileTypeUtil.splitFileNameAndParentDir(entry4.getKey()).left(), entry4.getValue(), str2);
            });
        }
        this.sortedModelKeys = Lists.newArrayList(this.filteredModels.keySet());
        this.sortedModelKeys.sort((v0, v1) -> {
            return v0.compareTo(v1);
        });
        this.sortedPackKeys = Lists.newArrayList(this.filteredPacks.keySet());
        this.sortedPackKeys.sort((v0, v1) -> {
            return v0.compareTo(v1);
        });
        refreshSourceLists();
        this.maxPage = Math.max(0, pageCount(getCurrentSourceModelsAndPacksSize(), this.gridPageSize) - 1);
    }

    private void refreshSourceLists() {
        this.sortedLocalModelKeys = Lists.newArrayList();
        this.sortedServerModelKeys = Lists.newArrayList();
        for (String modelKey : this.sortedModelKeys) {
            if (ClientModelManager.isServerModel(modelKey)) {
                this.sortedServerModelKeys.add(modelKey);
            } else {
                this.sortedLocalModelKeys.add(modelKey);
            }
        }

        this.sortedLocalPackKeys = Lists.newArrayList();
        this.sortedServerPackKeys = Lists.newArrayList();
        for (String packKey : this.sortedPackKeys) {
            if (hasSourceModelUnderPack(packKey, false)) {
                this.sortedLocalPackKeys.add(packKey);
            }
            if (hasSourceModelUnderPack(packKey, true)) {
                this.sortedServerPackKeys.add(packKey);
            }
        }
    }

    private boolean hasSourceModelUnderPack(String packKey, boolean serverModel) {
        for (Map.Entry<String, ModelAssembly> entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
            String modelKey = entry.getKey();
            if (modelKey.startsWith(packKey)
                    && ClientModelManager.isServerModel(modelKey) == serverModel
                    && matchesCurrentCategory(modelKey, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCurrentCategory(String modelKey, ModelAssembly modelAssembly) {
        Pair<String, String> pair = FileTypeUtil.splitFileNameAndParentDir(modelKey);
        if (this.hiddenModels.contains(pair.left())) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return false;
        }
        if (this.category == Category.AUTH) {
            return AuthModelsCapability.get(localPlayer)
                    .map(cap -> cap.containsModel(modelKey) || !modelAssembly.getTextureRegistry().isAuthModel())
                    .orElse(false);
        }
        if (this.category == Category.STAR) {
            return StarModelsCapability.get(localPlayer)
                    .map(cap -> cap.containsModel(modelKey))
                    .orElse(false);
        }
        return true;
    }

    private int pageCount(int size, int pageSize) {
        if (size <= 0) {
            return 1;
        }
        return ((size - 1) / pageSize) + 1;
    }

    private int getCurrentSourceModelsAndPacksSize() {
        return getCurrentSourceModelKeys().size() + getCurrentSourcePackKeys().size();
    }

    private List<String> getCurrentSourceModelKeys() {
        return currentModelSource == ModelSource.SERVER ? this.sortedServerModelKeys : this.sortedLocalModelKeys;
    }

    private List<String> getCurrentSourcePackKeys() {
        return currentModelSource == ModelSource.SERVER ? this.sortedServerPackKeys : this.sortedLocalPackKeys;
    }

    private boolean isDirectChild(String str, String str2) {
        String strSubstring;
        int iIndexOf;
        if (str.equals(str2)) {
            return false;
        }
        if (!StringUtils.isBlank(str)) {
            return str2.startsWith(str) && (iIndexOf = (strSubstring = str2.substring(str.length())).indexOf(47)) == strSubstring.length() - 1 && strSubstring.lastIndexOf(47) == iIndexOf;
        }
        int iIndexOf2 = str2.indexOf(47);
        return iIndexOf2 == str2.length() - 1 && str2.lastIndexOf(47) == iIndexOf2;
    }

    private boolean shouldFilterPack(String str, ModelPackData packData, String str2) {
        if (StringUtils.isBlank(str2)) {
            return false;
        }
        if (str2.startsWith(TAG_SEARCH_PREFIX)) {
            str2 = str2.substring(TAG_SEARCH_PREFIX.length());
        }
        if (str.toLowerCase(Locale.ENGLISH).contains(str2)) {
            return false;
        }
        if (packData.getTranslations() != null) {
            if (ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName()).toLowerCase(Locale.ENGLISH).contains(str2)) {
                return false;
            }
            String str3 = packData.getDescription();
            return str3 == null || !ModelMetadataPresenter.getLocalizedString(packData, "description", str3).toLowerCase(Locale.ENGLISH).contains(str2);
        }
        return true;
    }

    private boolean shouldFilterModel(String str, ModelAssembly modelAssembly, String str2) {
        if (this.hiddenModels.contains(str)) {
            return true;
        }
        if (StringUtils.isBlank(str2)) {
            return false;
        }
        if (str2.startsWith(TAG_SEARCH_PREFIX)) {
            return true;
        }
        if (str2.startsWith(AUTHOR_SEARCH_PREFIX)) {
            String strSubstring = str2.substring(AUTHOR_SEARCH_PREFIX.length());
            Metadata metadata2 = modelAssembly.getModelData().getExtraInfo();
            if (metadata2 != null) {
                return matchesAuthorSearch(modelAssembly, strSubstring, metadata2);
            }
            return true;
        }
        if (str.toLowerCase(Locale.ENGLISH).contains(str2)) {
            return false;
        }
        Metadata metadata3 = modelAssembly.getModelData().getExtraInfo();
        if (metadata3 != null) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name", metadata3.getName()).toLowerCase(Locale.ENGLISH).contains(str2) || ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.tips", metadata3.getTips()).toLowerCase(Locale.ENGLISH).contains(str2)) {
                return false;
            }
            return matchesAuthorSearch(modelAssembly, str2, metadata3);
        }
        return true;
    }

    public String getParentPath(String str) {
        if (str == null || str.isEmpty()) {
            return StringPool.EMPTY;
        }
        String strSubstring = str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
        int iLastIndexOf = strSubstring.lastIndexOf(47);
        if (iLastIndexOf < 0) {
            return StringPool.EMPTY;
        }
        return strSubstring.substring(0, iLastIndexOf + 1);
    }

    private boolean matchesAuthorSearch(ModelAssembly modelAssembly, String str, Metadata metadata2) {
        int i = 0;
        Iterator<AuthorInfo> it = metadata2.getAuthors().iterator();
        while (it.hasNext()) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.authors.%d.name".formatted(Integer.valueOf(i)), it.next().getName()).toLowerCase(Locale.ENGLISH).contains(str)) {
                return false;
            }
            i++;
        }
        return true;
    }

    public void init() {
        String value = StringPool.EMPTY;
        boolean zIsFocused = false;
        if (this.searchBox != null) {
            value = this.searchBox.getValue();
            zIsFocused = this.searchBox.isFocused();
        }
        clearWidgets();
        setupLayout();
        refreshModelList();
        if (getCurrentPage() > this.maxPage) {
            resetCurrentPage();
        }
        int actionX = this.rightPaneRight - 18;
        int categoryX = Math.max(this.rightPaneLeft + 156, actionX - 122);
        int searchX = this.rightPaneLeft + 8;
        int searchWidth = Math.max(90, Math.min(260, categoryX - searchX - 8));
        this.searchBox = new EditBox(Minecraft.getInstance().font, searchX, this.guiTop + 12, searchWidth, 16, Component.literal("YSM Search Box"));
        this.searchBox.setValue(value);
        this.searchBox.setTextColor(0xFFF3F0E0);
        this.searchBox.setFocused(zIsFocused);
        this.searchBox.moveCursorToEnd(false);
        addWidget(this.searchBox);
        addRenderableWidget(new IconButton(this.guiLeft + 14, this.guiTop + 14, 20, 20, 80, 16, button -> {
            if (Minecraft.getInstance().player != null) {
                PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
                    ModelAssembly modelAssembly = cap.getModelAssembly();
                    if (modelAssembly.getModelData().getExtraInfo() != null) {
                        Minecraft.getInstance().setScreen(createModelInfoScreen(this, modelAssembly));
                    }
                });
            }
        })).setTooltipText("gui.yes_steve_model.model.info");
        addRenderableWidget(new IconButton(this.guiLeft + 38, this.guiTop + 14, 82, 20, 32, 16, button2 -> {
            if (Minecraft.getInstance().player != null) {
                PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
                    Minecraft.getInstance().setScreen(createTextureScreen(this, cap.getModelId(), cap.getModelAssembly()));
                });
            }
        }).setTooltipText("gui.yes_steve_model.model.texture"));
        addRenderableWidget(new ModIconButton(this.guiLeft + 124, this.guiTop + 14));
        if (StringUtils.isNotBlank(currentPath)) {
            addRenderableWidget(new IconButton(this.guiLeft + 148, this.guiTop + 14, 20, 20, 0, 32, button3 -> {
                navigateUp();
            }).setTooltipText("gui.back"));
        }
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.yes_steve_model.show_model_id_first"), Minecraft.getInstance().font)
                .pos(this.guiLeft + 14, this.guiTop + this.panelHeight - 27)
                .selected(GeneralConfig.SHOW_MODEL_ID_FIRST.get())
                .onValueChange((cb, newValue) -> {
                    GeneralConfig.SHOW_MODEL_ID_FIRST.set(newValue);
                    GeneralConfig.SHOW_MODEL_ID_FIRST.save();
                })
                .build());
        addRenderableWidget(new IconButton(categoryX, this.guiTop + 11, 18, 18, 32, 0, button4 -> {
            if (this.category != Category.ALL) {
                this.category = Category.ALL;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.all_models"));
        addRenderableWidget(new IconButton(categoryX + 22, this.guiTop + 11, 18, 18, 48, 0, button5 -> {
            if (this.category != Category.AUTH) {
                this.category = Category.AUTH;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.auth_models"));
        addRenderableWidget(new IconButton(categoryX + 44, this.guiTop + 11, 18, 18, 0, 0, button6 -> {
            if (this.category != Category.STAR) {
                this.category = Category.STAR;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.star_models"));
        addRenderableWidget(new IconButton(actionX, this.guiTop + 11, 18, 18, 16, 16, button7 -> {
            Minecraft.getInstance().setScreen(new ExtraPlayerConfigScreen(this));
        }).setTooltipText("gui.yes_steve_model.config"));
        addRenderableWidget(new IconButton(actionX - 22, this.guiTop + 11, 18, 18, 0, 16, button8 -> {
            ModScreenEvent.openScreen(this);
        }).setTooltipText("gui.yes_steve_model.download"));
        addRenderableWidget(new IconButton(actionX - 44, this.guiTop + 11, 18, 18, 80, 0, button9 -> {
            Minecraft.getInstance().setScreen(new OpenModelFolderScreen(this));
        }).setTooltipText("gui.yes_steve_model.open_model_folder.open"));
        int tabY = this.guiTop + 36;
        int localTabX = this.rightPaneLeft + 8;
        int serverTabX = localTabX + 86;
        int uploadX = this.rightPaneRight - 62;
        if (uploadX - serverTabX < 112) {
            serverTabX = Math.max(localTabX + 86, uploadX - 112);
        }
        addRenderableWidget(new FlatColorButton(localTabX, tabY, 82, 18, Component.translatable("gui.yes_steve_model.model.local_models"), button -> {
            if (currentModelSource != ModelSource.LOCAL) {
                currentModelSource = ModelSource.LOCAL;
                resetCurrentPage();
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(serverTabX, tabY, 108, 18, Component.translatable("gui.yes_steve_model.model.server_models"), button -> {
            if (currentModelSource != ModelSource.SERVER) {
                currentModelSource = ModelSource.SERVER;
                resetCurrentPage();
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(uploadX, tabY, 62, 18, Component.translatable("gui.yes_steve_model.model.upload"), button -> {
            Minecraft.getInstance().setScreen(new ModelUploadScreen(this));
        }));
        int footerY = this.guiTop + this.panelHeight - 21;
        int footerCenter = (this.rightPaneLeft + this.rightPaneRight) / 2;
        addRenderableWidget(new FlatColorButton(footerCenter - 84, footerY, 52, 14, Component.translatable("gui.yes_steve_model.pre_page"), button10 -> {
            int currentPage = getCurrentPage();
            if (currentPage > 0) {
                setCurrentPage(currentPage - 1);
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(footerCenter + 32, footerY, 52, 14, Component.translatable("gui.yes_steve_model.next_page"), button11 -> {
            int currentPage = getCurrentPage();
            if (currentPage < this.maxPage) {
                setCurrentPage(currentPage + 1);
                init();
            }
        }));
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Optional<AuthModelsCapability> capability = AuthModelsCapability.get(this.minecraft.player);
        addModelSourceGrid(getCurrentSourcePackKeys(), getCurrentSourceModelKeys(), capability);
    }

    private void addModelSourceGrid(List<String> packKeys, List<String> modelKeys, Optional<AuthModelsCapability> capability) {
        ensurePreviewHolderCapacity(this.gridPageSize);
        for (int i = 0; i < this.gridPageSize; i++) {
            int slotIndex = i + (getCurrentPage() * this.gridPageSize);
            int slotX = this.rightPaneLeft + 8 + (SLOT_X_SPACING * (i % this.gridColumns));
            int slotY = this.gridTop + (SLOT_Y_SPACING * (i / this.gridColumns));
            if (slotIndex < packKeys.size()) {
                String str = packKeys.get(slotIndex);
                getPackData(str).ifPresent(value2 -> {
                    addRenderableWidget(new PackIconButton(slotX, slotY, SLOT_WIDTH, SLOT_HEIGHT, value2, button12 -> {
                        currentPath = str;
                        resetCurrentPage();
                        init();
                    }));
                });
            }
            int size = slotIndex - packKeys.size();
            if (0 <= size && size < modelKeys.size()) {
                String str2 = modelKeys.get(size);
                PlayerPreviewEntity previewEntity = previewHolders[i];
                previewEntity.resetModel();
                capability.ifPresent(value3 -> {
                    ModelAssembly modelAssembly2 = this.filteredModels.get(str2);
                    boolean isAuthLocked = modelAssembly2.getTextureRegistry().isAuthModel() && !value3.getAuthModels().contains(str2);
                    previewEntity.initModelWithTexture(str2, modelAssembly2.getAnimationBundle().getDefaultTextureName());
                    previewEntity.getAnimationStateMachine().setCurrentAnimation(modelAssembly2.getModelData().getModelProperties().getPreviewAnimation());
                    addRenderableWidget(createModelButton(slotX, slotY, isAuthLocked, previewEntity, modelAssembly2));
                });
            }
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderModernFrame(guiGraphics);
        guiGraphics.guiRenderState.nextStratum();
        renderModelPreview(guiGraphics, mouseX, mouseY, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        guiGraphics.guiRenderState.nextStratum();
        this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.guiRenderState.nextStratum();
        if (this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.yes_steve_model.search").withStyle(ChatFormatting.ITALIC), this.searchBox.getX() + 4, this.guiTop + 16, 0xFF777777);
        }
        String str = String.format("%d/%d", getCurrentPage() + 1, Integer.valueOf(this.maxPage + 1));
        Font font = this.font;
        int iWidth = ((this.rightPaneLeft + this.rightPaneRight) / 2) - (this.font.width(str) / 2);
        int pageY = this.guiTop + this.panelHeight - 14;
        Objects.requireNonNull(this.font);
        guiGraphics.drawString(font, str, iWidth, pageY - (9 / 2), TEXT);
        String strVersionString = Platform.getMod(YesSteveModel.MOD_ID).getVersion();
        guiGraphics.drawString(this.font, strVersionString, this.guiLeft + 15, this.guiTop + this.panelHeight - 43, MUTED);
        if (StringUtils.isNotBlank(currentPath)) {
            int lineIndex = 0;
            List listSplit = this.font.split(Component.literal("📂 " + currentPath).withStyle(ChatFormatting.GRAY), 270);
            Iterator it = listSplit.iterator();
            while (it.hasNext()) {
                guiGraphics.drawString(this.font, (FormattedCharSequence) it.next(), this.rightPaneLeft + 8, this.guiTop + 39 + (lineIndex * 10), TEXT);
                lineIndex++;
            }
        }
        renderSyncStatus(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> {
            return renderable instanceof IconButton;
        }).forEach(renderable2 -> {
            ((IconButton) renderable2).renderTooltip(guiGraphics, this, mouseX, mouseY);
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable3 -> {
            return renderable3 instanceof ModelButton;
        }).forEach(renderable4 -> {
            ((ModelButton) renderable4).renderTooltip(guiGraphics, this, mouseX, mouseY);
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable5 -> {
            return renderable5 instanceof PackIconButton;
        }).forEach(renderable6 -> {
            ((PackIconButton) renderable6).renderDescription(guiGraphics, this, mouseX, mouseY);
        });
        if (this.searchBox.isHovered()) {
            MutableComponent mutableComponentWithStyle = Component.translatable("gui.yes_steve_model.search.tip").withStyle(ChatFormatting.GRAY);
            guiGraphics.setTooltipForNextFrame(this.font, this.font.split(mutableComponentWithStyle, 320), mouseX, mouseY);
        }
    }

    private void renderModernFrame(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + this.panelWidth, this.guiTop + this.panelHeight, PANEL_BG);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + this.panelWidth, this.guiTop + 2, ACCENT);
        guiGraphics.fill(this.guiLeft + 8, this.guiTop + 42, this.guiLeft + this.leftPaneWidth, this.gridBottom, PANEL_SOFT);
        guiGraphics.fill(this.rightPaneLeft, this.guiTop + 42, this.rightPaneRight, this.gridBottom, PANEL_SOFT);
        guiGraphics.fill(this.rightPaneLeft, this.gridBottom + 4, this.rightPaneRight, this.guiTop + this.panelHeight - 5, PANEL_DARK);
        int activeTabLeft = currentModelSource == ModelSource.SERVER ? this.rightPaneLeft + 94 : this.rightPaneLeft + 8;
        int activeTabRight = currentModelSource == ModelSource.SERVER ? activeTabLeft + 108 : activeTabLeft + 82;
        guiGraphics.fill(activeTabLeft, this.guiTop + 55, activeTabRight, this.guiTop + 57, ACCENT);
        guiGraphics.drawString(this.font, "YSM Models", this.guiLeft + 14, this.guiTop + 4, TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(category.name()).withStyle(ChatFormatting.GRAY), this.rightPaneLeft + 8, this.guiTop + 34, MUTED, false);
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {

    }

    private void renderSyncStatus(GuiGraphics guiGraphics) {
        MutableComponent mutableComponentLiteral;
        ClientModelManager.SyncStatus currentState = ClientModelManager.getSyncStatus();
        switch (currentState.getCurrentState()) {
            case WAITING:
                mutableComponentLiteral = Component.translatable("gui.yes_steve_model.sync_hint.waiting");
                break;
            case LOADING:
                mutableComponentLiteral = Component.translatable("gui.yes_steve_model.sync_hint.loading");
                break;
            case PREPARING:
                mutableComponentLiteral = Component.translatable("gui.yes_steve_model.sync_hint.preparing");
                break;
            case SYNCING:
                if (currentState.getSyncedModels() == 0) {
                    mutableComponentLiteral = Component.translatable("gui.yes_steve_model.sync_hint.syncing");
                    break;
                } else {
                    mutableComponentLiteral = Component.literal(String.format("%s/%s", currentState.getSyncedModels(), currentState.getTotalModels()));
                    break;
                }
            default:
                return;
        }
        int iWidth = (this.rightPaneRight - 6) - this.font.width(mutableComponentLiteral);
        int i = this.guiTop + this.panelHeight - 22;
        Objects.requireNonNull(this.font);
        guiGraphics.drawString(this.font, mutableComponentLiteral, iWidth, i + Math.round((14 - 9) / 2.0f), MUTED);
    }

    public void renderModelPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            guiGraphics.guiRenderState.nextStratum();
            int previewLeft = this.guiLeft + 18;
            int previewRight = this.guiLeft + this.leftPaneWidth - 10;
            int previewTop = this.guiTop + 52;
            int previewBottom = Math.max(previewTop + 60, this.gridBottom - 34);
            int previewCenterX = (previewLeft + previewRight) / 2;
            int previewScale = Math.max(48, Math.min(88, (previewBottom - previewTop) / 2));
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, previewLeft, previewTop, previewRight, previewBottom, previewScale, 0.0625F, previewCenterX - mouseX, ((previewBottom - 20) - 95) - mouseY, localPlayer);
            guiGraphics.guiRenderState.nextStratum();

//            guiGraphics.disableScissor();
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                List<FormattedCharSequence> listSplit = this.font.split(FormattedText.of(ClientModelManager.getModelContext(cap.getModelId()).map(it -> {
                    Metadata metadata2 = it.getModelData().getExtraInfo();
                    if (metadata2 != null) {
                        return ModelMetadataPresenter.getLocalizedModelString(it, "metadata.name", metadata2.getName());
                    }
                    return StringPool.EMPTY;
                }).filter(charSequence -> {
                    return StringUtils.isNoneBlank(charSequence);
                }).orElse(FileTypeUtil.getNameWithoutArchiveExtension(cap.getModelId()))), Math.max(80, this.leftPaneWidth - 24));
                int lineY = Math.min(this.gridBottom - 26, previewBottom + 6);
                for (FormattedCharSequence formattedCharSequence : listSplit) {
                    guiGraphics.drawString(this.font, formattedCharSequence, this.guiLeft + 8 + (((this.leftPaneWidth - 16) - this.font.width(formattedCharSequence)) / 2), lineY, TEXT);
                    lineY += 10;
                }
            });
        }
    }

    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.searchBox == null ? StringPool.EMPTY : this.searchBox.getValue();
        super.resize(minecraft, width, height);
        if (this.searchBox != null) {
            this.searchBox.setValue(value);
        }
    }

    public void tick() {
        super.tick();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.searchBox);
            return true;
        }
        if (this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        boolean zMouseClicked = super.mouseClicked(mouseX, mouseY, button);
        if (!zMouseClicked && button == 1 && StringUtils.isNotBlank(currentPath)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            navigateUp();
            zMouseClicked = true;
        }
        return zMouseClicked;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox == null) {
            return false;
        }
        String value = this.searchBox.getValue();
        if (this.searchBox.charTyped(codePoint, modifiers)) {
            if (!Objects.equals(value, this.searchBox.getValue())) {
                resetCurrentPage();
                init();
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleToggleKey(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean zIsPresent = InputConstants.getKey(keyCode, scanCode).getNumericKeyValue().isPresent();
        String value = this.searchBox.getValue();
        if (zIsPresent) {
            return true;
        }
        if (!this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return (this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!Objects.equals(value, this.searchBox.getValue())) {
            resetCurrentPage();
            init();
            return true;
        }
        return true;
    }

    private boolean handleToggleKey(int keyCode, int scanCode, int modifiers) {
        if (PlayerModelToggleKey.KEY_MAPPING.matches(keyCode, scanCode) && !this.searchBox.isFocused()) {
            onClose();
            return true;
        }
        return false;
    }

    public void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.searchBox.setValue(text);
        } else {
            this.searchBox.insertText(text);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.minecraft == null) {
            return false;
        }
        if (scrollY != 0.0d && isInModelArea(mouseX, mouseY)) {
            return handleScrollPage(scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInModelArea(double mouseX, double mouseY) {
        return mouseX > this.rightPaneLeft && mouseX < this.rightPaneRight && mouseY > this.guiTop + 42 && mouseY < this.gridBottom;
    }

    private void navigateUp() {
        String str2 = getParentPath(currentPath);
        if (!currentPath.equals(str2)) {
            String str = currentPath;
            currentPath = str2;
            pageIndexMap.removeInt(str);
            init();
        }
    }

    private boolean handleScrollPage(double delta) {
        int currentPage = getCurrentPage();
        if (delta > 0.0d && currentPage > 0) {
            setCurrentPage(currentPage - 1);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            init();
        }
        if (delta < 0.0d && currentPage < this.maxPage) {
            setCurrentPage(currentPage + 1);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            init();
            return true;
        }
        return true;
    }

    private void setupLayout() {
        int availableWidth = Math.max(1, this.width - (SCREEN_MARGIN * 2));
        int availableHeight = Math.max(1, this.height - (SCREEN_MARGIN * 2));
        this.panelWidth = Math.max(availableWidth, Math.min(GUI_WIDTH, availableWidth));
        this.panelHeight = Math.max(availableHeight, Math.min(GUI_HEIGHT, availableHeight));
        this.guiLeft = (this.width - this.panelWidth) / 2;
        this.guiTop = (this.height - this.panelHeight) / 2;
        this.leftPaneWidth = Math.min(LEFT_PANE_MAX_WIDTH, Math.max(LEFT_PANE_MIN_WIDTH, this.panelWidth / 3));
        this.rightPaneLeft = this.guiLeft + this.leftPaneWidth + RIGHT_PANE_GAP;
        this.rightPaneRight = this.guiLeft + this.panelWidth - 8;
        this.gridTop = this.guiTop + HEADER_HEIGHT;
        this.gridBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT;

        int gridWidth = Math.max(SLOT_WIDTH, this.rightPaneRight - this.rightPaneLeft - 8);
        int gridHeight = Math.max(SLOT_HEIGHT, this.gridBottom - this.gridTop);
        this.gridColumns = Math.max(1, ((gridWidth - SLOT_WIDTH) / SLOT_X_SPACING) + 1);
        this.gridRows = Math.max(1, ((gridHeight - SLOT_HEIGHT) / SLOT_Y_SPACING) + 1);
        this.gridPageSize = Math.max(1, this.gridColumns * this.gridRows);
        ensurePreviewHolderCapacity(this.gridPageSize);
    }

    private static void ensurePreviewHolderCapacity(int size) {
        if (previewHolders.length >= size) {
            return;
        }
        int oldLength = previewHolders.length;
        previewHolders = Arrays.copyOf(previewHolders, size);
        for (int i = oldLength; i < previewHolders.length; i++) {
            previewHolders[i] = new PlayerPreviewEntity();
        }
    }

    public int getCurrentPage() {
        return pageIndexMap.getOrDefault(currentPath, 0);
    }

    public void setCurrentPage(int i) {
        pageIndexMap.put(currentPath, i);
    }

    public void resetCurrentPage() {
        pageIndexMap.put(currentPath, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onModelsLoaded(Map<String, ModelAssembly> map) {
        init();
    }

    @Override
    public void onModelsUpdated(Map<String, ModelAssembly> map) {
        init();
    }

    private Optional<ModelPackData> getPackData(String str) {
        return Optional.ofNullable(this.modelPackMap.get(str));
    }

    private enum Category {
        ALL,
        AUTH,
        STAR
    }

    private enum ModelSource {
        LOCAL,
        SERVER
    }
}
