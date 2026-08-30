package com.rasmus.gridmenu.gui;

import com.rasmus.gridmenu.GridMenu;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.config.ModMenuConfig;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.fabric.FabricIconHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compact grid-of-tiles replacement for Mod Menu's mods list. Full-width
 * tile grid in a scissored viewport under a fixed header band holding the
 * title, search box and Done button. Shows the same top-level mod set Mod
 * Menu's own list would show.
 */
public class GridScreen extends Screen {

    private static final int HEADER_H = 44;
    private static final int MARGIN = 10;
    private static final int TILE_W = 180;
    private static final int TILE_H = 24;
    private static final int GAP = 4;
    private static final int ICON_SIZE = 16;
    private static final int GEAR_SIZE = 20;
    private static final int DIVIDER_H = 18;
    private static final int SCROLL_STEP = 18;

    /** Mod Menu's own fallback icon for packs/mods without a usable one. */
    private static final Identifier FALLBACK_ICON =
            Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");

    /**
     * Mod Menu 18.0.0 predates the gui sprite atlas for this button: the
     * jar only ships plain textures/gui/configure_button.png, a 32x64
     * sheet its ModsScreen draws as a 20x20 button with the normal state
     * at v=0 and the hovered state at v=20. Draw it with a full-texture
     * blit, not blitSprite.
     */
    private static final Identifier CONFIGURE_BUTTON_TEXTURE =
            Identifier.fromNamespaceAndPath("modmenu", "textures/gui/configure_button.png");

    /** Mod Menu 20 dropped the sheet for gui sprites; picked at runtime, see useLegacyGearSheet. */
    private static final Identifier CONFIGURE_SPRITE_ENABLED =
            Identifier.fromNamespaceAndPath("modmenu", "configure/enabled");
    private static final Identifier CONFIGURE_SPRITE_FOCUSED =
            Identifier.fromNamespaceAndPath("modmenu", "configure/focused");

    /**
     * The mod's own eye icons, full 16x16 canvases: an open green eye,
     * and the same eye with a red diagonal strike ("hidden").
     */
    private static final Identifier OPEN_EYE_TEXTURE =
            Identifier.fromNamespaceAndPath("gridmenu", "textures/gui/eye_open.png");
    private static final Identifier HIDDEN_EYE_TEXTURE =
            Identifier.fromNamespaceAndPath("gridmenu", "textures/gui/eye_hidden.png");
    private static final int EYE_SIZE = 16;

    /** The mod's own star icons: filled gold for favorites, muted outline otherwise. */
    private static final Identifier STAR_FILLED_TEXTURE =
            Identifier.fromNamespaceAndPath("gridmenu", "textures/gui/star_filled.png");
    private static final Identifier STAR_OUTLINE_TEXTURE =
            Identifier.fromNamespaceAndPath("gridmenu", "textures/gui/star_outline.png");
    private static final int STAR_SIZE = 16;

    /**
     * Created lazily on first icon resolution and nulled in removed(), so
     * icons rebuild themselves after any number of config-screen round
     * trips: removed() tears everything down, the next render re-creates
     * the handler and re-registers the textures on demand.
     */
    private FabricIconHandler iconHandler;
    /** Resolved texture id per mod id; cleared in removed() so tiles re-register lazily. */
    private final Map<String, Identifier> iconCache = new HashMap<>();
    /**
     * Mods whose icon failed once; they stay on the fallback for this
     * showing of the screen so a broken icon never crash-loops per frame.
     * Cleared in removed(): early-startup failures (resource reload still
     * in flight) are transient, so a reopened screen retries everything.
     */
    private final java.util.Set<String> brokenIcons = new java.util.HashSet<>();
    /** Cached per mod id like ModsScreen.modHasConfigScreen; a throwing factory counts as none. */
    private final Map<String, Boolean> hasConfigCache = new HashMap<>();

    /** Probed once per screen: does the running Mod Menu still ship the legacy button sheet? */
    private Boolean legacyGearSheet;

    private EditBox searchBox;
    private String query = "";
    private List<Mod> visible = List.of();
    /** Mods [0, favoriteCount) are the labeled Favorites section. */
    private int favoriteCount = 0;
    /**
     * Mods [0, aboveDividerCount) are the Favorites section followed by
     * the unlabeled main group of configurable non-favorites; the
     * "No settings" divider and group follow.
     */
    private int aboveDividerCount = 0;
    private double yOffset = 0;
    /** The screen Mod Menu was opened from (read off the swapped ModsScreen); may be null. */
    private final Screen parent;

    public GridScreen(Screen parent) {
        super(Component.literal("Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, this.width / 2 - 90, (HEADER_H - 18) / 2, 180, 18,
                Component.literal("Search"));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width - MARGIN - 76, (HEADER_H - 20) / 2, 76, 20)
                .build());

        rebuildList();
    }

    /**
     * Mirrors ModListWidget.filter and honors Mod Menu's own list-shaping
     * settings wholesale: only top-level mods (never children from
     * PARENT_MAP), mod.isHidden() (which reads Mod Menu's HIDDEN_MODS
     * list), libraries per SHOW_LIBRARIES (a plain BooleanConfigOption in
     * Mod Menu 18, libraries marked by the LIBRARY badge), display names
     * via getTranslatedName() (which checks TRANSLATE_NAMES itself), and
     * ordering straight from SORTING's comparator. Our own
     * hiddenMods/showHidden layers on top of all that.
     */
    private void rebuildList() {
        var config = GridMenu.config();
        int libraryMode = libraryVisibilityMode();
        String needle = query.toLowerCase(Locale.ROOT);
        List<Mod> mods = new ArrayList<>();
        for (Mod mod : ModMenu.MODS.values()) {
            if (mod.isHidden()) {
                continue;
            }
            if (ModMenu.PARENT_MAP.containsValue(mod)) {
                continue;
            }
            if (libraryMode < LIBRARIES_SHOW && mod.getBadges().contains(Mod.Badge.LIBRARY)
                    && (libraryMode == LIBRARIES_HIDE || !hasConfig(mod.getId()))) {
                continue;
            }
            if (config.privacyLock && config.lockedHidden.contains(mod.getId())) {
                continue;
            }
            if (!config.showHidden && config.hiddenMods.contains(mod.getId())) {
                continue;
            }
            if (!matchesSearch(mod, needle)) {
                continue;
            }
            mods.add(mod);
        }
        // Three tiers: a labeled Favorites section (config screen or not),
        // then the unlabeled main group of configurable non-favorites, then
        // the "No settings" group, Mod Menu's comparator within each tier.
        // hiddenMods has already won above: a hidden favorite only exists
        // here in show-hidden view.
        List<Mod> favorites = new ArrayList<>();
        List<Mod> withConfig = new ArrayList<>();
        List<Mod> withoutConfig = new ArrayList<>();
        for (Mod mod : mods) {
            if (config.favoriteMods.contains(mod.getId())) {
                favorites.add(mod);
            } else {
                (hasConfig(mod.getId()) ? withConfig : withoutConfig).add(mod);
            }
        }
        var comparator = ModMenuConfig.SORTING.getValue().getComparator();
        favorites.sort(comparator);
        withConfig.sort(comparator);
        withoutConfig.sort(comparator);
        favoriteCount = favorites.size();
        favorites.addAll(withConfig);
        aboveDividerCount = favorites.size();
        favorites.addAll(withoutConfig);
        visible = favorites;
        clampScroll();
    }

    /**
     * Mod Menu's ModSearch semantics, our own implementation:
     * case-insensitive contains on translated name, mod id, authors and
     * summary/description. Metadata getters on foreign mods can throw;
     * a throwing field just does not match.
     */
    private boolean matchesSearch(Mod mod, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        try {
            if (mod.getTranslatedName().toLowerCase(Locale.ROOT).contains(needle)
                    || mod.getId().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (String author : mod.getAuthors()) {
                if (author != null && author.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String summary = mod.getTranslatedSummary();
            if (summary != null && summary.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            String description = mod.getTranslatedDescription();
            if (description != null && description.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static final int LIBRARIES_HIDE = 0;
    private static final int LIBRARIES_WITH_CONFIG = 1;
    private static final int LIBRARIES_SHOW = 2;

    /**
     * SHOW_LIBRARIES changed shape between Mod Menu versions: a
     * BooleanConfigOption in 18, an EnumConfigOption of LibraryVisibility
     * (FALSE / WITH_CONFIG / TRUE) in 20. Even the field type differs, so
     * the read goes through reflection to keep one jar working on both;
     * WITH_CONFIG replicates LibraryVisibility.hideMod (a library shows
     * only when it has a config screen). Unknown values show libraries.
     */
    private int libraryVisibilityMode() {
        try {
            Object option = ModMenuConfig.class.getField("SHOW_LIBRARIES").get(null);
            Object value = option.getClass().getMethod("getValue").invoke(option);
            if (value instanceof Boolean showLibraries) {
                return showLibraries ? LIBRARIES_SHOW : LIBRARIES_HIDE;
            }
            return switch (String.valueOf(value)) {
                case "FALSE" -> LIBRARIES_HIDE;
                case "WITH_CONFIG" -> LIBRARIES_WITH_CONFIG;
                default -> LIBRARIES_SHOW;
            };
        } catch (Throwable ignored) {
            return LIBRARIES_SHOW;
        }
    }

    /** Mirrors ModsScreen.getModHasConfigScreen: cached per id, a throwing check counts as none. */
    private boolean hasConfig(String modId) {
        return hasConfigCache.computeIfAbsent(modId, id -> {
            try {
                return ModMenu.hasConfigScreen(id);
            } catch (Throwable broken) {
                return false;
            }
        });
    }

    private int columns() {
        return Math.max(1, (this.width - 2 * MARGIN + GAP) / (TILE_W + GAP));
    }

    private int gridLeft() {
        int cols = columns();
        return (this.width - (cols * TILE_W + (cols - 1) * GAP)) / 2;
    }

    private int viewportTop() {
        return HEADER_H + GAP;
    }

    private int viewportBottom() {
        return this.height - MARGIN;
    }

    private int rowsFor(int count) {
        int cols = columns();
        return (count + cols - 1) / cols;
    }

    /** The Favorites label band; only present while the favorites tier has members. */
    private int favoriteHeaderHeight() {
        return favoriteCount > 0 ? DIVIDER_H : 0;
    }

    /**
     * The "Settings" label opening the main tier; only while favorites AND
     * the main tier have members. With no favorites the main group keeps
     * starting unlabeled at the top.
     */
    private int favoriteSeparatorHeight() {
        return favoriteCount > 0 && aboveDividerCount > favoriteCount ? DIVIDER_H : 0;
    }

    /** The "No settings" divider only exists while both sides have members. */
    private int dividerHeight() {
        return aboveDividerCount > 0 && aboveDividerCount < visible.size() ? DIVIDER_H : 0;
    }

    private int contentHeight() {
        int favRows = rowsFor(favoriteCount);
        int mainRows = rowsFor(aboveDividerCount - favoriteCount);
        int restRows = rowsFor(visible.size() - aboveDividerCount);
        return favoriteHeaderHeight() + favoriteSeparatorHeight() + dividerHeight()
                + (favRows + mainRows + restRows) * (TILE_H + GAP) - GAP;
    }

    private void clampScroll() {
        int viewport = viewportBottom() - viewportTop();
        double max = Math.max(0, contentHeight() - viewport);
        yOffset = Math.max(0, Math.min(yOffset, max));
    }

    /** Index of this tile within its own tier, for column math. */
    private int tierIndex(int index) {
        if (index < favoriteCount) {
            return index;
        }
        if (index < aboveDividerCount) {
            return index - favoriteCount;
        }
        return index - aboveDividerCount;
    }

    private int tileX(int index) {
        return gridLeft() + (tierIndex(index) % columns()) * (TILE_W + GAP);
    }

    private int tileY(int index) {
        int y = viewportTop() - (int) yOffset + favoriteHeaderHeight();
        int row = tierIndex(index) / columns();
        if (index >= favoriteCount) {
            y += rowsFor(favoriteCount) * (TILE_H + GAP) + favoriteSeparatorHeight();
        }
        if (index >= aboveDividerCount) {
            y += rowsFor(aboveDividerCount - favoriteCount) * (TILE_H + GAP) + dividerHeight();
        }
        return y + row * (TILE_H + GAP);
    }

    /** Y of the Favorites label band; only meaningful while favoriteHeaderHeight() > 0. */
    private int favoriteHeaderY() {
        return viewportTop() - (int) yOffset;
    }

    /** Y of the plain favorites-end line; only meaningful while favoriteSeparatorHeight() > 0. */
    private int favoriteSeparatorY() {
        return viewportTop() - (int) yOffset + favoriteHeaderHeight()
                + rowsFor(favoriteCount) * (TILE_H + GAP);
    }

    /** Y of the "No settings" divider band; only meaningful while dividerHeight() > 0. */
    private int dividerY() {
        return viewportTop() - (int) yOffset + favoriteHeaderHeight() + favoriteSeparatorHeight()
                + (rowsFor(favoriteCount) + rowsFor(aboveDividerCount - favoriteCount))
                * (TILE_H + GAP);
    }

    /** Header Show Hidden toggle box (20x20), left of the Done button. */
    private int headerEyeX() {
        return this.width - MARGIN - 76 - 26;
    }

    private int headerEyeY() {
        return (HEADER_H - 20) / 2;
    }

    /** Classic list view button (20x20), left of the Show Hidden eye. */
    private int headerListX() {
        return headerEyeX() - 26;
    }

    /**
     * All custom painting happens in the background pass: the base
     * extractRenderState renders extractBackground first, then a new
     * stratum with the widgets, so the search box and Done button always
     * sit above the header band drawn here.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        // Dark translucent wash so the world/panorama never bleeds through the tiles.
        graphics.fill(0, 0, this.width, this.height, 0xB0101014);

        // Tile viewport, clipped so scrolling can never overlap the header.
        graphics.enableScissor(0, viewportTop(), this.width, viewportBottom());
        for (int i = 0; i < visible.size(); i++) {
            int y = tileY(i);
            if (y + TILE_H <= viewportTop() || y >= viewportBottom()) {
                continue;
            }
            renderTile(graphics, visible.get(i), tileX(i), y, mouseX, mouseY);
        }
        if (favoriteHeaderHeight() > 0) {
            renderDivider(graphics, "Favorites", favoriteHeaderY());
        }
        if (favoriteSeparatorHeight() > 0) {
            renderDivider(graphics, "Settings", favoriteSeparatorY());
        }
        if (dividerHeight() > 0) {
            renderDivider(graphics, "No settings", dividerY());
        }
        graphics.disableScissor();

        // Fixed header band, Mod Menu's dark panel look.
        boolean showHidden = GridMenu.config().showHidden;
        graphics.fill(0, 0, this.width, HEADER_H, 0xE8161A1E);
        graphics.fill(0, HEADER_H, this.width, HEADER_H + 1, 0xFF000000);

        // Left cluster: "Mods" plus live counts for the current view. In
        // show-hidden view the hidden count is skipped, they are on screen.
        int hiddenCount = GridMenu.config().hiddenMods.size();
        String counts = visible.size() + " shown";
        if (!showHidden && hiddenCount > 0) {
            counts += " · " + hiddenCount + " hidden";
        }
        int titleY = (HEADER_H - 8) / 2;
        graphics.text(this.font, "Mods", MARGIN + 2, titleY, 0xFFFFFFFF);
        graphics.text(this.font, counts,
                MARGIN + 2 + this.font.width("Mods") + 8, titleY, 0xFF8A9098);

        // Classic list view button: three bars with leading dots.
        int lx = headerListX();
        int ly = headerEyeY();
        boolean listHovered = mouseX >= lx && mouseX < lx + 20 && mouseY >= ly && mouseY < ly + 20;
        graphics.fill(lx, ly, lx + 20, ly + 20, 0xC02A2E36);
        graphics.outline(lx, ly, 20, 20, listHovered ? 0xFFFFFFFF : 0xFF5A5F6A);
        int glyphColor = listHovered ? 0xFFFFFFFF : 0xFFB8BCC4;
        for (int row = 0; row < 3; row++) {
            int ry = ly + 5 + row * 4;
            graphics.fill(lx + 4, ry, lx + 6, ry + 2, glyphColor);
            graphics.fill(lx + 8, ry, lx + 16, ry + 2, glyphColor);
        }
        if (listHovered) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.literal("Classic list view"), mouseX, mouseY);
        }

        // Show Hidden toggle: open eye while active, struck eye while not.
        int ex = headerEyeX();
        int ey = headerEyeY();
        boolean eyeHovered = mouseX >= ex && mouseX < ex + 20 && mouseY >= ey && mouseY < ey + 20;
        graphics.fill(ex, ey, ex + 20, ey + 20, showHidden ? 0xE8404550 : 0xC02A2E36);
        graphics.outline(ex, ey, 20, 20,
                showHidden ? 0xFFFFD75F : (eyeHovered ? 0xFFFFFFFF : 0xFF5A5F6A));
        blitControl(graphics, showHidden ? OPEN_EYE_TEXTURE : HIDDEN_EYE_TEXTURE,
                ex + 2, ey + 2, 0xFFFFFFFF);
        if (eyeHovered) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.literal("Show hidden mods"), mouseX, mouseY);
        }
    }

    /** True when the running Mod Menu still ships the legacy button sheet (18.x on 26.1). */
    private boolean useLegacyGearSheet() {
        if (legacyGearSheet == null) {
            boolean present;
            try {
                present = Minecraft.getInstance().getResourceManager()
                        .getResource(CONFIGURE_BUTTON_TEXTURE).isPresent();
            } catch (Throwable ignored) {
                present = false;
            }
            legacyGearSheet = present;
        }
        return legacyGearSheet;
    }

    /** Muted divider band: centered label between lines, or one plain line when unlabeled. */
    private void renderDivider(GuiGraphicsExtractor graphics, String label, int dy) {
        if (dy + DIVIDER_H <= viewportTop() || dy >= viewportBottom()) {
            return;
        }
        int lineY = dy + DIVIDER_H / 2;
        int left = gridLeft();
        int right = this.width - left;
        if (label.isEmpty()) {
            graphics.fill(left, lineY, right, lineY + 1, 0xFF3A3F47);
            return;
        }
        int labelWidth = this.font.width(label);
        int labelLeft = (this.width - labelWidth) / 2;
        graphics.fill(left, lineY, labelLeft - 6, lineY + 1, 0xFF3A3F47);
        graphics.fill(labelLeft + labelWidth + 6, lineY, right, lineY + 1, 0xFF3A3F47);
        graphics.text(this.font, label, labelLeft, lineY - 4, 0xFF7A8088);
    }

    /** Draws one of the mod's 16x16 control icons (eyes, stars); they fill their canvas. */
    private static void blitControl(GuiGraphicsExtractor graphics, Identifier texture,
            int x, int y, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, EYE_SIZE, EYE_SIZE, EYE_SIZE, EYE_SIZE, color);
    }

    private int gearX(int tileX) {
        return tileX + TILE_W - GEAR_SIZE - 2;
    }

    /**
     * Hover control order, right to left: gear, star, eye. The persistent
     * star on favorited tiles thus sits adjacent to the gear at the right
     * edge instead of floating mid-tile.
     */
    private int tileStarX(int tileX, boolean hasConfig) {
        return hasConfig ? gearX(tileX) - STAR_SIZE - 4 : tileX + TILE_W - STAR_SIZE - 4;
    }

    private int tileEyeX(int tileX, boolean hasConfig) {
        return tileStarX(tileX, hasConfig) - EYE_SIZE - 4;
    }

    private void renderTile(GuiGraphicsExtractor graphics, Mod mod, int x, int y,
            int mouseX, int mouseY) {
        boolean inViewport = mouseY >= viewportTop() && mouseY < viewportBottom();
        boolean hovered = inViewport && mouseX >= x && mouseX < x + TILE_W
                && mouseY >= y && mouseY < y + TILE_H;
        boolean hiddenListed = GridMenu.config().hiddenMods.contains(mod.getId());
        graphics.fill(x, y, x + TILE_W, y + TILE_H, 0xC0202329);
        graphics.outline(x, y, TILE_W, TILE_H, hovered ? 0xFFFFFFFF : 0xFF5A5F6A);

        // Hidden-listed tiles (only on screen while Show Hidden is on) render dimmed.
        int iconColor = hiddenListed ? 0x80FFFFFF : 0xFFFFFFFF;
        int nameColor = hiddenListed ? 0x80E0E0E0 : 0xFFE0E0E0;
        blitIcon(graphics, mod, x + 4, y + (TILE_H - ICON_SIZE) / 2, iconColor);

        boolean hasConfig = hasConfig(mod.getId());
        boolean favorited = GridMenu.config().favoriteMods.contains(mod.getId());
        int eyeLeft = tileEyeX(x, hasConfig);
        int starLeft = tileStarX(x, hasConfig);
        int textLeft = x + 4 + ICON_SIZE + 4;
        int textRight = hovered ? eyeLeft - 2
                : (hasConfig ? gearX(x) - 2 : x + TILE_W - 4);
        String name = this.font.plainSubstrByWidth(mod.getTranslatedName(), textRight - textLeft);
        graphics.text(this.font, name, textLeft, y + (TILE_H - 8) / 2, nameColor);

        if (hasConfig) {
            int gx = gearX(x);
            int gy = y + (TILE_H - GEAR_SIZE) / 2;
            boolean gearHovered = inViewport && mouseX >= gx && mouseX < gx + GEAR_SIZE
                    && mouseY >= gy && mouseY < gy + GEAR_SIZE;
            if (useLegacyGearSheet()) {
                // Mod Menu 18's 20x20 configure button from its 32x64 sheet, hovered row at v=20.
                graphics.blit(RenderPipelines.GUI_TEXTURED, CONFIGURE_BUTTON_TEXTURE,
                        gx, gy, 0.0F, gearHovered ? 20.0F : 0.0F,
                        GEAR_SIZE, GEAR_SIZE, 32, 64);
            } else {
                // Mod Menu 20 ships gui sprites instead.
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        gearHovered ? CONFIGURE_SPRITE_FOCUSED : CONFIGURE_SPRITE_ENABLED,
                        gx, gy, GEAR_SIZE, GEAR_SIZE);
            }
            if (gearHovered) {
                graphics.setTooltipForNextFrame(this.font,
                        Component.literal("Configure " + mod.getTranslatedName()), mouseX, mouseY);
            }
        }

        // Hover-only eye: struck eye hides a visible mod, open eye unhides a hidden one.
        if (hovered) {
            int ey = y + (TILE_H - EYE_SIZE) / 2;
            boolean eyeHovered = mouseX >= eyeLeft && mouseX < eyeLeft + EYE_SIZE
                    && mouseY >= ey && mouseY < ey + EYE_SIZE;
            blitControl(graphics, hiddenListed ? OPEN_EYE_TEXTURE : HIDDEN_EYE_TEXTURE,
                    eyeLeft, ey, eyeHovered ? 0xFFFFFFFF : 0xB0FFFFFF);
            if (eyeHovered) {
                graphics.setTooltipForNextFrame(this.font,
                        Component.literal(hiddenListed ? "Unhide" : "Hide"), mouseX, mouseY);
            }
        }

        // Star: hover-only toggle (outline when unfavorited, filled when
        // favorited). Section membership marks favorites at a glance.
        if (hovered) {
            int sy = y + (TILE_H - STAR_SIZE) / 2;
            boolean starHovered = mouseX >= starLeft && mouseX < starLeft + STAR_SIZE
                    && mouseY >= sy && mouseY < sy + STAR_SIZE;
            blitControl(graphics, favorited ? STAR_FILLED_TEXTURE : STAR_OUTLINE_TEXTURE,
                    starLeft, sy, starHovered ? 0xFFFFFFFF : 0xB0FFFFFF);
            if (starHovered) {
                graphics.setTooltipForNextFrame(this.font,
                        Component.literal(favorited ? "Unfavorite" : "Favorite"), mouseX, mouseY);
            }
        }
    }

    /**
     * Some mods hand back a DynamicTexture whose GPU view never
     * initializes (broken or missing icon file); the first blit then
     * throws from AbstractTexture.getTextureView. Any failure demotes the
     * mod to the vanilla unknown-pack icon permanently, so a broken icon
     * neither crashes the render loop nor retries every frame.
     */
    private void blitIcon(GuiGraphicsExtractor graphics, Mod mod, int x, int y, int color) {
        Identifier icon = iconFor(mod);
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, color);
        } catch (Throwable broken) {
            brokenIcons.add(mod.getId());
            iconCache.remove(mod.getId());
            graphics.blit(RenderPipelines.GUI_TEXTURED, FALLBACK_ICON, x, y,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, color);
        }
    }

    /** Registers the mod icon with the texture manager once, like ModListEntry.getIconTexture. */
    private Identifier iconFor(Mod mod) {
        String id = mod.getId();
        if (brokenIcons.contains(id)) {
            return FALLBACK_ICON;
        }
        Identifier cached = iconCache.get(id);
        if (cached != null) {
            return cached;
        }
        Identifier location = FALLBACK_ICON;
        try {
            if (iconHandler == null) {
                iconHandler = new FabricIconHandler();
            }
            DynamicTexture texture = mod.getIcon(iconHandler, 64);
            if (texture != null) {
                location = Identifier.fromNamespaceAndPath("gridmenu",
                        id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_") + "_icon");
                Minecraft.getInstance().getTextureManager().register(location, texture);
            }
        } catch (Throwable ignored) {
            brokenIcons.add(id);
            return FALLBACK_ICON;
        }
        iconCache.put(id, location);
        return location;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        double mx = event.x();
        double my = event.y();

        // Classic list view: a persistent switch. The mixin respects the
        // saved preference from now on; the guard is kept as a harmless
        // belt while this very call swaps screens.
        int lx = headerListX();
        int ly = headerEyeY();
        if (mx >= lx && mx < lx + 20 && my >= ly && my < ly + 20) {
            GridMenu.config().preferGridView = false;
            GridMenu.saveConfig();
            GridMenu.suppressSwap = true;
            try {
                GridMenu.openScreen(this.minecraft, new ModsScreen(parent));
            } finally {
                GridMenu.suppressSwap = false;
            }
            return true;
        }

        // Header Show Hidden toggle.
        int hx = headerEyeX();
        int hy = headerEyeY();
        if (mx >= hx && mx < hx + 20 && my >= hy && my < hy + 20) {
            GridMenu.config().showHidden = !GridMenu.config().showHidden;
            GridMenu.saveConfig();
            rebuildList();
            return true;
        }

        if (my < viewportTop() || my >= viewportBottom()) {
            return false;
        }
        for (int i = 0; i < visible.size(); i++) {
            int x = tileX(i);
            int y = tileY(i);
            if (mx < x || mx >= x + TILE_W || my < y || my >= y + TILE_H) {
                continue;
            }
            Mod mod = visible.get(i);
            boolean hasConfig = hasConfig(mod.getId());

            if (hasConfig) {
                int gx = gearX(x);
                int gy = y + (TILE_H - GEAR_SIZE) / 2;
                if (mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE) {
                    Screen configScreen = ModMenu.getConfigScreen(mod.getId(), this);
                    if (configScreen != null) {
                        GridMenu.openScreen(this.minecraft, configScreen);
                    }
                    return true;
                }
            }

            // The eye and star only render while the tile is hovered, which a click implies.
            int ex = tileEyeX(x, hasConfig);
            int ey = y + (TILE_H - EYE_SIZE) / 2;
            if (mx >= ex && mx < ex + EYE_SIZE && my >= ey && my < ey + EYE_SIZE) {
                var hiddenMods = GridMenu.config().hiddenMods;
                if (!hiddenMods.remove(mod.getId())) {
                    hiddenMods.add(mod.getId());
                }
                GridMenu.saveConfig();
                rebuildList();
                return true;
            }

            int sx = tileStarX(x, hasConfig);
            int sy = y + (TILE_H - STAR_SIZE) / 2;
            if (mx >= sx && mx < sx + STAR_SIZE && my >= sy && my < sy + STAR_SIZE) {
                var favoriteMods = GridMenu.config().favoriteMods;
                if (!favoriteMods.remove(mod.getId())) {
                    favoriteMods.add(mod.getId());
                }
                GridMenu.saveConfig();
                rebuildList();
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * F8 toggles the privacy lock, deliberately unnamed anywhere in the UI.
     * Locking parks the current hiddenMods content in lockedHidden, whose
     * members then vanish entirely (uncounted, unsearchable, absent from
     * show-hidden view) while the hide feature keeps operating normally on
     * the now-empty hiddenMods set, so nothing looks missing to a viewer.
     * Unlocking merges the parked set back. Both sets persist, so the
     * state resumes correctly across restarts; double-toggling is a clean
     * cycle and an empty move is fine.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F8) {
            GridMenu.togglePrivacyLock();
            rebuildList();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (super.mouseScrolled(mouseX, mouseY, amountX, amountY)) {
            return true;
        }
        yOffset -= amountY * SCROLL_STEP;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        GridMenu.openScreen(this.minecraft, parent);
    }

    @Override
    public void removed() {
        // Symmetric teardown: the cleared caches and nulled handler make the
        // next render (after a config-screen round trip) rebuild everything
        // lazily, and transient icon failures get a fresh retry.
        for (Identifier location : iconCache.values()) {
            if (location != null && "gridmenu".equals(location.getNamespace())) {
                Minecraft.getInstance().getTextureManager().release(location);
            }
        }
        iconCache.clear();
        brokenIcons.clear();
        if (iconHandler != null) {
            iconHandler.close();
            iconHandler = null;
        }
        super.removed();
    }
}
