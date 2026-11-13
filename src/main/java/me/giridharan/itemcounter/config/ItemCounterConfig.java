package me.giridharan.itemcounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ItemCounterConfig {

    private static ItemCounterConfig instance;

    // Countdown / Mode defaults
    public int defaultTimerSeconds = 60; // 0 = Live Rate, 60 = 60s (or custom seconds)
    public boolean showHologram = true;
    public boolean strictInFrontOnly = true;

    // 3D In-World Hologram Customization
    public boolean showMoneyInHologram = true; // true = Items + Money, false = Only Items/Entities
    public double hologramHeight = 0.5; // Vertical height offset (-10.0 to 10.0 blocks)
    public double hologramScale = 1.0; // Text size/scale (0.4 to 3.0)

    // Anti-Leak Privacy: Coordinates hidden from chat messages (DISABLED BY DEFAULT!)
    public boolean showCoordinatesInChat = false;

    // Live Watch On-Screen Sidebar HUD (ENABLED BY DEFAULT)
    public boolean showLiveWatchHud = true;
    public boolean showMoneyInHud = true;
    public String currencySymbol = "$";
    public int liveWatchX = 10;
    public int liveWatchY = 10;

    // Multi-key combination support (e.g. Ctrl + O), starts UNBOUND (NONE)
    public int openGuiKeyCode = -1;
    public short openGuiModifier = 0;
    public boolean openGuiKeyIsMouse = false;

    // Quick restart keybind, starts UNBOUND (NONE)
    public int resetCounterKeyCode = -1;
    public short resetCounterModifier = 0;
    public boolean resetCounterKeyIsMouse = false;

    // Sign Boards registered / configured (Key: "x,y,z")
    public Map<String, SignConfigData> signConfigs = new LinkedHashMap<>();

    // Entity & Item Price Map (Key: itemId e.g. "iron_ingot" -> price)
    public double defaultItemPrice = 1.0;
    public Map<String, Double> itemPrices = new LinkedHashMap<>();

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

    private void initDefaultPrices() {
        if (itemPrices.isEmpty()) {
            itemPrices.put("iron_ingot", 10.0);
            itemPrices.put("gold_ingot", 25.0);
            itemPrices.put("diamond", 100.0);
            itemPrices.put("emerald", 50.0);
            itemPrices.put("netherite_ingot", 500.0);
            itemPrices.put("copper_ingot", 2.0);
            itemPrices.put("redstone", 3.0);
            itemPrices.put("lapis_lazuli", 4.0);
            itemPrices.put("bamboo", 0.5);
            itemPrices.put("sugar_cane", 1.0);
            itemPrices.put("slime_ball", 5.0);
            itemPrices.put("gunpowder", 4.0);
            itemPrices.put("rotten_flesh", 1.0);
            itemPrices.put("bone", 2.0);
            itemPrices.put("string", 2.0);
            itemPrices.put("ender_pearl", 15.0);
        }
    }

    public double getItemPrice(String rawItemId) {
        if (rawItemId == null) return defaultItemPrice;
        String clean = rawItemId.toLowerCase().trim();
        if (clean.contains(":")) {
            clean = clean.substring(clean.indexOf(":") + 1);
        }
        return itemPrices.getOrDefault(clean, defaultItemPrice);
    }

    public void setItemPrice(String rawItemId, double price) {
        if (rawItemId == null) return;
        String clean = rawItemId.toLowerCase().trim();
        if (clean.contains(":")) {
            clean = clean.substring(clean.indexOf(":") + 1);
        }
        itemPrices.put(clean, price);
    }

    public static String getCurrentServerOrWorldId() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null) return "global";
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null && !client.getCurrentServer().ip.isBlank()) {
            return client.getCurrentServer().ip.toLowerCase().trim();
        }
        if (client.getConnection() != null && client.getConnection().getServerData() != null && client.getConnection().getServerData().ip != null && !client.getConnection().getServerData().ip.isBlank()) {
            return client.getConnection().getServerData().ip.toLowerCase().trim();
        }
        if (client.isLocalServer() || client.hasSingleplayerServer()) {
            try {
                if (client.getSingleplayerServer() != null && client.getSingleplayerServer().getWorldData() != null) {
                    String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
                    if (levelName != null && !levelName.isBlank()) {
                        return "sp:" + levelName.trim().toLowerCase();
                    }
                }
            } catch (Throwable ignored) {}
            return "sp:singleplayer";
        }
        return "global";
    }

    public static boolean isSameServer(String id1, String id2) {
        if (id1 == null || id2 == null) return false;
        String a = id1.toLowerCase().trim();
        String b = id2.toLowerCase().trim();
        if (a.equals(b)) return true;

        // If either is a singleplayer world (e.g. sp:worldName), require exact match
        if (a.startsWith("sp:") || b.startsWith("sp:")) {
            return a.equals(b);
        }

        // Strip port only if host:port has a numeric port
        if (a.matches(".+:\\d+")) a = a.substring(0, a.lastIndexOf(':'));
        if (b.matches(".+:\\d+")) b = b.substring(0, b.lastIndexOf(':'));
        if (a.equals(b)) return true;

        // Domain / subdomain matching (e.g. play.priyosmp.com and priyosmp.com)
        if (a.endsWith("." + b) || b.endsWith("." + a)) return true;
        return false;
    }

    public static String makeSignKey(String serverId, int x, int y, int z) {
        String s = (serverId != null && !serverId.isBlank()) ? serverId.trim() : "global";
        return s + "@" + x + "," + y + "," + z;
    }

    public SignConfigData getOrCreateSignConfig(int x, int y, int z) {
        String currentWorld = getCurrentServerOrWorldId();
        String scopedKey = makeSignKey(currentWorld, x, y, z);
        SignConfigData data = signConfigs.get(scopedKey);
        if (data != null) {
            if (data.serverId == null || data.serverId.isBlank()) {
                data.serverId = currentWorld;
            }
            return data;
        }

        // Check if there is an un-scoped legacy key "x,y,z"
        String legacyKey = x + "," + y + "," + z;
        SignConfigData legacy = signConfigs.get(legacyKey);
        if (legacy != null) {
            if (legacy.serverId == null || legacy.serverId.isBlank() || isSameServer(legacy.serverId, currentWorld)) {
                legacy.serverId = currentWorld;
                signConfigs.remove(legacyKey);
                signConfigs.put(scopedKey, legacy);
                save();
                return legacy;
            }
        }

        int signNum = getSignsForCurrentServer().size() + 1;
        data = new SignConfigData(x, y, z, "Counter " + signNum);
        data.serverId = currentWorld;
        signConfigs.put(scopedKey, data);
        save();
        return data;
    }

    public SignConfigData getSignConfig(int x, int y, int z) {
        String currentWorld = getCurrentServerOrWorldId();
        String scopedKey = makeSignKey(currentWorld, x, y, z);
        SignConfigData data = signConfigs.get(scopedKey);
        if (data != null) return data;

        String legacyKey = x + "," + y + "," + z;
        SignConfigData legacy = signConfigs.get(legacyKey);
        if (legacy != null && (legacy.serverId == null || legacy.serverId.isBlank() || isSameServer(legacy.serverId, currentWorld))) {
            return legacy;
        }
        return null;
    }

    public void addCustomSign(int x, int y, int z, String customName) {
        String currentWorld = getCurrentServerOrWorldId();
        String key = makeSignKey(currentWorld, x, y, z);
        int signNum = getSignsForCurrentServer().size() + 1;
        String defaultName = (customName != null && !customName.isBlank()) ? customName.trim() : ("Counter " + signNum);
        SignConfigData data = new SignConfigData(x, y, z, defaultName);
        data.serverId = currentWorld;
        signConfigs.put(key, data);
        save();
    }

    public void removeSignConfig(int x, int y, int z) {
        String currentWorld = getCurrentServerOrWorldId();
        signConfigs.remove(makeSignKey(currentWorld, x, y, z));
        signConfigs.remove(x + "," + y + "," + z);
        save();
    }

    public Map<String, SignConfigData> getSignsForCurrentServer() {
        String currentWorld = getCurrentServerOrWorldId();
        Map<String, SignConfigData> result = new LinkedHashMap<>();
        if (signConfigs == null) return result;

        for (Map.Entry<String, SignConfigData> entry : signConfigs.entrySet()) {
            SignConfigData sc = entry.getValue();
            if (sc == null) continue;
            if (sc.serverId != null && !sc.serverId.isBlank()) {
                if (isSameServer(sc.serverId, currentWorld)) {
                    result.put(entry.getKey(), sc);
                }
            } else {
                // If legacy entry has no serverId, show if global or prefix matches
                if (currentWorld.equalsIgnoreCase("global") || entry.getKey().startsWith(currentWorld + "@")) {
                    result.put(entry.getKey(), sc);
                }
            }
        }
        return result;
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
                    if (config.signConfigs == null) config.signConfigs = new LinkedHashMap<>();
                    if (config.itemPrices == null) config.itemPrices = new LinkedHashMap<>();
                    config.initDefaultPrices();
                    return config;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ItemCounterConfig def = new ItemCounterConfig();
        def.initDefaultPrices();
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
