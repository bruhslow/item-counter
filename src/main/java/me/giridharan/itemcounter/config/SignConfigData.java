package me.giridharan.itemcounter.config;

public class SignConfigData {

    public int x;
    public int y;
    public int z;
    public String customName = "";
    public boolean showInHud = true;
    public String itemFilter = ""; // Comma-separated item names e.g. "iron_ingot, poppy"
    public double customHologramHeight = -1.0; // -1 = use global config

    public SignConfigData() {}

    public SignConfigData(int x, int y, int z, String customName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.customName = customName;
        this.showInHud = true;
        this.itemFilter = "";
        this.customHologramHeight = -1.0;
    }

    public String getDisplayName(boolean showCoords) {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName.trim();
        }
        return showCoords ? ("Sign (" + x + ", " + y + ", " + z + ")") : "Sign Counter";
    }

    public String getDisplayName() {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        return getDisplayName(config.showCoordinatesInChat);
    }

    public String getKey() {
        return x + "," + y + "," + z;
    }
}
