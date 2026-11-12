package me.giridharan.itemcounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ItemCounterConfig {

    private static ItemCounterConfig instance;

    public int defaultTimerSeconds = 30; // 10, 30, 60 or custom seconds
    public boolean showHologram = true;
    public boolean showOnScreenHud = true;
    public boolean strictInFrontOnly = true;

    // Multi-key combination support (e.g. Ctrl + O), starts UNBOUND (NONE)
    public int openGuiKeyCode = -1;
    public short openGuiModifier = 0;
    public boolean openGuiKeyIsMouse = false;

    // Quick reset keybind, starts UNBOUND (NONE)
    public int resetCounterKeyCode = -1;
    public short resetCounterModifier = 0;
    public boolean resetCounterKeyIsMouse = false;

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("item_counter.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ItemCounterConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public ModifierKeyCode getOpenGuiKey() {
        if (openGuiKeyCode == -1) {
            return ModifierKeyCode.unknown();
        }
        InputConstants.Type type = openGuiKeyIsMouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        return ModifierKeyCode.of(type.getOrCreate(openGuiKeyCode), Modifier.of(openGuiModifier));
    }

    public void setOpenGuiKey(ModifierKeyCode mkc) {
        if (mkc == null || mkc.isUnknown()) {
            openGuiKeyCode = -1;
            openGuiModifier = 0;
            openGuiKeyIsMouse = false;
        } else {
            openGuiKeyCode = mkc.getKeyCode().getValue();
            openGuiModifier = mkc.getModifier().getValue();
            openGuiKeyIsMouse = (mkc.getType() == InputConstants.Type.MOUSE);
        }
    }

    public ModifierKeyCode getResetCounterKey() {
        if (resetCounterKeyCode == -1) {
            return ModifierKeyCode.unknown();
        }
        InputConstants.Type type = resetCounterKeyIsMouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        return ModifierKeyCode.of(type.getOrCreate(resetCounterKeyCode), Modifier.of(resetCounterModifier));
    }

    public void setResetCounterKey(ModifierKeyCode mkc) {
        if (mkc == null || mkc.isUnknown()) {
            resetCounterKeyCode = -1;
            resetCounterModifier = 0;
            resetCounterKeyIsMouse = false;
        } else {
            resetCounterKeyCode = mkc.getKeyCode().getValue();
            resetCounterModifier = mkc.getModifier().getValue();
            resetCounterKeyIsMouse = (mkc.getType() == InputConstants.Type.MOUSE);
        }
    }

    public static ItemCounterConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ItemCounterConfig config = GSON.fromJson(json, ItemCounterConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ItemCounterConfig def = new ItemCounterConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
