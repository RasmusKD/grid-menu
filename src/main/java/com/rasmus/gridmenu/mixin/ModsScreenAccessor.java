package com.rasmus.gridmenu.mixin;

import com.terraformersmc.modmenu.gui.ModsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the screen Mod Menu was opened from, so the grid can return to it. */
@Pseudo
@Mixin(value = ModsScreen.class, remap = false)
public interface ModsScreenAccessor {

    @Accessor("previousScreen")
    Screen gridmenu$getPreviousScreen();
}
