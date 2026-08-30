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
}
