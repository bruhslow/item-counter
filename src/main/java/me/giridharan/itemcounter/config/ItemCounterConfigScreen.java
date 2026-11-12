package me.giridharan.itemcounter.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ItemCounterConfigScreen {

    public static Screen create(Screen parent) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("§6§lItem Counter Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ==================== Category 1: General & Keys ====================
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        // 1. Keybind for Opening GUI (Starts UNBOUND / NONE)
        general.addEntry(entryBuilder.startModifierKeyCodeField(
                        Component.literal("Open Config Menu Keybind"),
                        config.getOpenGuiKey()
                )
                .setAllowModifiers(true)
                .setAllowKey(true)
                .setAllowMouse(true)
                .setDefaultValue(ModifierKeyCode.unknown())
                .setModifierSaveConsumer(config::setOpenGuiKey)
                .setTooltip(Component.literal("Custom keybind or multi-key combination (e.g. Ctrl + O). Starts Unbound (NONE)."))
                .build());

        // 2. Keybind for Quick Reset / Restart of Closest Counter (Starts UNBOUND / NONE)
        general.addEntry(entryBuilder.startModifierKeyCodeField(
                        Component.literal("Quick Restart Counter Keybind"),
                        config.getResetCounterKey()
                )
                .setAllowModifiers(true)
                .setAllowKey(true)
                .setAllowMouse(true)
                .setDefaultValue(ModifierKeyCode.unknown())
                .setModifierSaveConsumer(config::setResetCounterKey)
                .setTooltip(Component.literal("Optional keybind to instantly restart the nearest counter test. Starts Unbound (NONE)."))
                .build());

        // 3. Default Timer Duration
        general.addEntry(entryBuilder.startIntField(
                        Component.literal("Default Test Duration (Seconds)"),
                        config.defaultTimerSeconds
                )
                .setDefaultValue(30)
                .setMin(5)
                .setMax(3600)
                .setSaveConsumer(val -> config.defaultTimerSeconds = Math.max(5, val))
                .setTooltip(Component.literal("Default countdown timer in seconds for new counters (e.g. 10, 30, 60)."))
                .build());

        // 4. Show In-World 3D Hologram
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Show 3D In-World Hologram"),
                        config.showHologram
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showHologram = val)
                .setTooltip(Component.literal("Displays live floating 3D text directly above the sign board in the world."))
                .build());

        // 5. Strict In-Front Directional Detection
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Strict In-Front Detection Only"),
                        config.strictInFrontOnly
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.strictInFrontOnly = val)
                .setTooltip(Component.literal("Only counts items moving directly in the 1-block stream facing the sign board."))
                .build());

        // ==================== Category 2: Live Watch HUD ====================
        ConfigCategory hudCat = builder.getOrCreateCategory(Component.literal("Live Watch HUD"));

        // 6. Live Watch On-Screen HUD Toggle
        hudCat.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Enable Live Watch HUD"),
                        config.showLiveWatchHud
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showLiveWatchHud = val)
                .setTooltip(Component.literal("Displays a movable live on-screen box showing count, speed, and time remaining."))
                .build());

        // 7. Interactive Drag-and-Drop Explanation
        hudCat.addEntry(entryBuilder.startTextDescription(
                Component.literal("§e💡 Tip: §fType §b/counter hud §for §b/counter move §fin chat to visually drag the HUD box anywhere with your mouse!")
        ).build());

        // 8. Manual X Coordinate
        hudCat.addEntry(entryBuilder.startIntField(
                        Component.literal("HUD X Position (Pixels)"),
                        config.liveWatchX
                )
                .setDefaultValue(10)
                .setMin(0)
                .setMax(4000)
                .setSaveConsumer(val -> config.liveWatchX = val)
                .setTooltip(Component.literal("Horizontal pixel coordinate of the Live Watch HUD box."))
                .build());

        // 9. Manual Y Coordinate
        hudCat.addEntry(entryBuilder.startIntField(
                        Component.literal("HUD Y Position (Pixels)"),
                        config.liveWatchY
                )
                .setDefaultValue(10)
                .setMin(0)
                .setMax(4000)
                .setSaveConsumer(val -> config.liveWatchY = val)
                .setTooltip(Component.literal("Vertical pixel coordinate of the Live Watch HUD box."))
                .build());

        builder.setSavingRunnable(config::save);

        return builder.build();
    }
}
