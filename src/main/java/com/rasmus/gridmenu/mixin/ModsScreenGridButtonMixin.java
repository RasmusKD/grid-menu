package com.rasmus.gridmenu.mixin;

import com.rasmus.gridmenu.GridMenu;
import com.rasmus.gridmenu.gui.GridScreen;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the mirror of the grid's classic-view switch inside Mod Menu's own
 * screen: a compact button in the top-right corner (y=22, the search row;
 * their configure button sits below it at y=48, so nothing collides). A
 * plain vanilla Button, because Mod Menu 18 and 20 share no icon-button
 * class (18's LegacyTexturedButtonWidget is gone in 20). Clicking it
 * persists preferGridView and jumps to the grid with the classic screen's
 * previous screen as parent.
 */
@Mixin(value = ModsScreen.class)
public abstract class ModsScreenGridButtonMixin extends Screen {

    protected ModsScreenGridButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void gridmenu$addGridViewButton(CallbackInfo ci) {
        Screen previous = ((ModsScreenAccessor) this).gridmenu$getPreviousScreen();
        Button button = Button.builder(Component.literal("Grid"), b -> {
                    GridMenu.config().preferGridView = true;
                    GridMenu.saveConfig();
                    GridMenu.openScreen(Minecraft.getInstance(), new GridScreen(previous));
                })
                .bounds(this.width - 52, 22, 48, 20)
                .tooltip(Tooltip.create(Component.literal("Grid view")))
                .build();
        this.addRenderableWidget(button);
    }
}
