package com.rasmus.gridmenu.mixin;

import com.rasmus.gridmenu.GridMenu;
import com.rasmus.gridmenu.gui.GridScreen;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 26.2 and later keep the current screen in Gui, and Mod Menu's pause menu
 * button calls Gui.setScreen directly, which never passes Minecraft's own
 * setters. 26.1's Gui is only the HUD and has no such method, so require = 0.
 */
@Mixin(Gui.class)
public class GuiScreenSwapMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen gridmenu$swapOnSetScreen(Screen screen) {
        if (screen instanceof ModsScreen modsScreen
                && GridMenu.replaceModsScreen()
                && GridMenu.preferGridView()
                && !GridMenu.suppressSwap) {
            return new GridScreen(((ModsScreenAccessor) modsScreen).gridmenu$getPreviousScreen());
        }
        return screen;
    }
}
