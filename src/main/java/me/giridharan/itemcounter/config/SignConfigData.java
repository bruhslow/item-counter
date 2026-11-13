package me.giridharan.itemcounter.config;

public class SignConfigData {

    public int x;
    public int y;
    public int z;

    // 0. Server / World Scope (isolates signs per server or singleplayer world)
    public String serverId = "";

    // 1. Rename & Identity
    public String customName = "";

    // 2. Pricing system for this sign board
    public boolean useSignCustomPrice = true; // Use this sign board's custom price
    public double pricePerItem = 10.0; // Custom price for each item/entity counted here

    // 3. Live View Feed / Outer GUI (Sidebar HUD)
    public boolean showInHud = true; // Show on outer GUI: Yes / No
    public boolean showHudHeader = true; // Show sign name on HUD card
    public boolean showHudBadge = true; // Show mode badge [LIVE] / [10s]
    public boolean showHudCount = true; // Show entity passed count
    public boolean showHudRate = false; // Show flow rate (~items/min) (Default: No)
    public boolean showMoneyInHud = true; // Show total money earned on HUD
    public boolean showHudMoneyRate = false; // Show money per sec/min (DEFAULT FALSE: REMOVED as requested)
    public boolean showHudFooter = true; // Show monitor status footer

    // 4. 3D In-World Hologram setup for this sign board
    public boolean showHologram = true; // Enable 3D hologram: Yes / No
    public boolean showHoloHeader = true; // Show custom name in hologram
    public boolean showHoloBadge = true; // Show mode badge [LIVE RATE]
    public boolean showHoloCount = true; // Show entity passed count
    public boolean showHoloRate = false; // Show flow rate (~items/min) (Default: No)
    public boolean showMoneyInHologram = true; // Show total money earned in hologram
    public boolean showHoloMoneyRate = false; // Show money per sec/min (DEFAULT FALSE: REMOVED as requested)
    public boolean showHoloGuide = true; // Show interaction guide footer (Right-click reset)
    public boolean showHoloBackground = true; // Show dark see-through plate behind floating text
    public double hologramHeight = 1.25; // Vertical height offset (0.0 to 10.0 blocks)
    public double hologramScale = 1.0; // Size/scale (0.4x to 3.0x)

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
        this.showHudHeader = true;
        this.showHudBadge = true;
        this.showHudCount = true;
        this.showHudRate = false;
        this.showMoneyInHud = true;
        this.showHudMoneyRate = false;
        this.showHudFooter = true;

        this.showHologram = true;
        this.showHoloHeader = true;
        this.showHoloBadge = true;
        this.showHoloCount = true;
        this.showHoloRate = false;
        this.showMoneyInHologram = true;
        this.showHoloMoneyRate = false;
        this.showHoloGuide = true;
        this.showHoloBackground = true;
        this.hologramHeight = 1.25;
        this.hologramScale = 1.0;

        this.itemFilter = "";
        this.testDurationSeconds = 0;
        this.customHologramHeight = -1.0;
    }

    public double getEffectiveHeight(double globalHeight) {
        return hologramHeight;
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
