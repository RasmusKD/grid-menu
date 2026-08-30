package com.rasmus.gridmenu.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "gridmenu")
public class GridMenuConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public boolean replaceModsScreen = true;

    @ConfigEntry.Gui.Excluded
    public boolean showHidden = false;

    /** Persisted view switch: false means the user chose the classic list view. */
    @ConfigEntry.Gui.Excluded
    public boolean preferGridView = true;

    @ConfigEntry.Gui.Excluded
    public java.util.Set<String> hiddenMods = new java.util.HashSet<>();

    @ConfigEntry.Gui.Excluded
    public java.util.Set<String> favoriteMods = new java.util.HashSet<>();

    /**
     * F8 toggle: while true, the mods in lockedHidden are invisible and
     * uncounted everywhere, while the regular hide feature keeps working
     * normally on hiddenMods so nothing looks missing.
     */
    @ConfigEntry.Gui.Excluded
    public boolean privacyLock = false;

    /** hiddenMods content parked here while the privacy lock is on. */
    @ConfigEntry.Gui.Excluded
    public java.util.Set<String> lockedHidden = new java.util.HashSet<>();
}
