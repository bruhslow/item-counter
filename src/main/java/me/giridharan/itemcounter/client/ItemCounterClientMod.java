package me.giridharan.itemcounter.client;

import me.giridharan.itemcounter.ItemCounterMod;
import net.fabricmc.api.ClientModInitializer;

public class ItemCounterClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemCounterMod.LOGGER.info("[Item Counter] Client initialized successfully for Fabric 26.2!");
    }
}
