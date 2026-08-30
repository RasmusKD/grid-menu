package com.rasmus.gridmenu;

import com.rasmus.gridmenu.config.GridMenuConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Config access, the screen-swap reentry guard, and the screen opener. */
public final class GridMenu {

    /**
     * While true the ScreenSwapMixin passes ModsScreen through untouched,
     * so the grid can deliberately open the real Mod Menu screen without
     * being swapped right back to itself.
     */
    public static volatile boolean suppressSwap = false;

    private static ConfigHolder<GridMenuConfig> holder;

    private GridMenu() {
    }

    public static void init() {
        holder = AutoConfig.register(GridMenuConfig.class,
                me.shedaniel.autoconfig.serializer.GsonConfigSerializer::new);
    }

    public static GridMenuConfig config() {
        return holder == null ? new GridMenuConfig() : holder.getConfig();
    }

    public static void saveConfig() {
        if (holder != null) {
            holder.save();
        }
    }

    public static boolean replaceModsScreen() {
        return holder != null && holder.getConfig().replaceModsScreen;
    }

    public static boolean preferGridView() {
        return holder != null && holder.getConfig().preferGridView;
    }

    /**
     * The one F8 privacy-lock toggle, shared by the grid and the classic
     * screen so the two can never drift. Locking parks hiddenMods in
     * lockedHidden (whose members then vanish everywhere) and leaves the
     * hide feature operating normally on the emptied set; unlocking
     * merges the parked set back. Both sets persist across restarts.
     */
    public static void togglePrivacyLock() {
        var config = config();
        if (config.privacyLock) {
            config.hiddenMods.addAll(config.lockedHidden);
            config.lockedHidden.clear();
            config.privacyLock = false;
        } else {
            config.lockedHidden.addAll(config.hiddenMods);
            config.hiddenMods.clear();
            config.privacyLock = true;
        }
        saveConfig();
    }

    /** True when the given mod id is parked invisible by the privacy lock. */
    public static boolean isLockedHidden(String modId) {
        var config = config();
        return config.privacyLock && config.lockedHidden.contains(modId);
    }

    /**
     * The screen setter was renamed setScreen -> setScreenAndShow in 26.2,
     * so resolve it by name at runtime; the runtime uses Mojang names, so
     * both spellings are reachable. Reflection is safer than signature
     * matching here, since clearClientLevel shares the same signature.
     */
    public static void openScreen(Minecraft minecraft, Screen screen) {
        for (String name : new String[] {"setScreenAndShow", "setScreen"}) {
            try {
                minecraft.getClass().getMethod(name, Screen.class).invoke(minecraft, screen);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
