package com.rasmus.gridmenu.mixin;

import com.rasmus.gridmenu.GridMenu;
import com.rasmus.gridmenu.gui.GridScreen;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps Mod Menu's ModsScreen for the grid the moment any code sets it.
 * 26.1.x has setScreen, 26.2 renamed it setScreenAndShow, so both handlers
 * carry require = 0; whichever exists on the running version applies.
 */
@Mixin(Minecraft.class)
public class ScreenSwapMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen gridmenu$swapOnSetScreen(Screen screen) {
        return gridmenu$swap(screen);
    }

    @ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen gridmenu$swapOnSetScreenAndShow(Screen screen) {
        return gridmenu$swap(screen);
    }

    @Unique
    private static Screen gridmenu$swap(Screen screen) {
        if (screen instanceof ModsScreen modsScreen
                && GridMenu.replaceModsScreen()
                && GridMenu.preferGridView()
                && !GridMenu.suppressSwap) {
            return new GridScreen(((ModsScreenAccessor) modsScreen).gridmenu$getPreviousScreen());
        }
        return screen;
    }
}
