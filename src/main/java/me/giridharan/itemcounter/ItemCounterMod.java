package me.giridharan.itemcounter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemCounterMod implements ModInitializer {
    public static final String MOD_ID = "itemcounter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Item Counter] Mod initialized successfully for Fabric 26.2!");
    }
}
