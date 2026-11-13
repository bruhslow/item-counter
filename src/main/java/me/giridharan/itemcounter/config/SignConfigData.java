package me.giridharan.itemcounter.config;

public class SignConfigData {

    public int x;
    public int y;
    public int z;

    // 1. Rename & Identity
    public String customName = "";

    // 2. Pricing system for this sign board
    public boolean useSignCustomPrice = true; // Use this sign board's custom price
    public double pricePerItem = 10.0; // Custom price for each item/entity counted here

    // 3. Live View Feed / Outer GUI (Sidebar HUD)
    public boolean showInHud = true; // Show on outer GUI: Yes / No
    public boolean showMoneyInHud = true; // Show money on outer GUI: Yes / No

    // 4. 3D In-World Hologram setup for this sign board
    public boolean showHologram = true; // Enable 3D hologram: Yes / No
    public double hologramHeight = 1.25; // Vertical height offset (0.0 to 10.0 blocks)
    public double hologramScale = 1.0; // Size/scale (0.4x to 3.0x)
    public boolean showMoneyInHologram = true; // Yes = Items + Money | No = Only entities/items

    // 5. Item Filter & Test Duration
    public String itemFilter = ""; // Comma-separated item names e.g. "iron_ingot, poppy"
    public int testDurationSeconds = 0; // 0 = Live Rate (Default), 10, 30, 60, etc.

    // Backwards-compatibility field for older configs
    public double customHologramHeight = -1.0;

    public SignConfigData() {}

    public SignConfigData(int x, int y, int z, String customName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.customName = customName;
        this.useSignCustomPrice = true;
        this.pricePerItem = 10.0;
        this.showInHud = true;
        this.showMoneyInHud = true;
        this.showHologram = true;
        this.hologramHeight = 1.25;
        this.hologramScale = 1.0;
        this.showMoneyInHologram = true;
        this.itemFilter = "";
        this.testDurationSeconds = 0;
        this.customHologramHeight = -1.0;
    }

    public double getEffectiveHeight(double globalHeight) {
        if (customHologramHeight >= 0) return customHologramHeight;
        return hologramHeight >= 0 ? hologramHeight : globalHeight;
    }

    public double getEffectiveScale(double globalScale) {
        return hologramScale > 0 ? hologramScale : globalScale;
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
