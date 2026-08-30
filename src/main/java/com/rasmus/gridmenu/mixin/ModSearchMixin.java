package com.rasmus.gridmenu.mixin;

import com.rasmus.gridmenu.GridMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.ModSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * While the privacy lock is on, lockedHidden mods must not appear in Mod
 * Menu's classic list or its search either. Everything the classic list
 * shows flows through ModSearch.search (same signature in Mod Menu 18 and
 * 20), so post-filtering its return covers both the list and searching.
 * Regular hiddenMods stay visible there, classic view has no show-hidden
 * concept and stealth only concerns the locked set.
 */
@Mixin(value = ModSearch.class, remap = false)
public class ModSearchMixin {

    @Inject(method = "search", at = @At("RETURN"), cancellable = true)
    private static void gridmenu$dropLockedHidden(ModsScreen screen, String query, List<Mod> mods,
            CallbackInfoReturnable<List<Mod>> cir) {
        List<Mod> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        List<Mod> filtered = null;
        for (Mod mod : result) {
            if (GridMenu.isLockedHidden(mod.getId())) {
                if (filtered == null) {
                    filtered = new ArrayList<>(result);
                }
                filtered.remove(mod);
            }
        }
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}
