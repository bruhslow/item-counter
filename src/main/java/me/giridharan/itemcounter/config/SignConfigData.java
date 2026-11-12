package me.giridharan.itemcounter.config;

public class SignConfigData {

    public int x;
    public int y;
    public int z;
    public String customName = "";
    public boolean showInHud = true;
    public String itemFilter = ""; // Comma-separated item names e.g. "iron_ingot, poppy"

    public SignConfigData() {}

    public SignConfigData(int x, int y, int z, String customName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.customName = customName;
        this.showInHud = true;
        this.itemFilter = "";
    }

    public String getDisplayName() {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName.trim();
        }
        return "Sign (" + x + ", " + y + ", " + z + ")";
    }

    public String getKey() {
        return x + "," + y + "," + z;
    }
}
