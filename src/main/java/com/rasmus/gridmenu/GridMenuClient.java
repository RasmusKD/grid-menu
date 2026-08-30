package com.rasmus.gridmenu;

import net.fabricmc.api.ClientModInitializer;

public class GridMenuClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GridMenu.init();
    }
}
