package me.giridharan.itemcounter.config;

import me.giridharan.itemcounter.counter.CounterManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class ItemCounterConfigScreen {

    public static Screen create(Screen parent) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();

        // Sync any active in-world signs into config
        for (BlockPos pos : CounterManager.getActiveCounters().keySet()) {
            config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
        }

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("§6§lItem Counter Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ==================== Category 1: Sign Boards & Farms ====================
        ConfigCategory signsCat = builder.getOrCreateCategory(Component.literal("Sign Boards & Farms"));

        signsCat.addEntry(entryBuilder.startTextDescription(
                Component.literal("§6✦ Select any sign board below to tweak all settings for that farm in one place: rename it, set its duration, price per item, 3D hologram (height/scale/money), outer GUI feed, and item filter!")
        ).build());

        if (config.signConfigs.isEmpty()) {
            signsCat.addEntry(entryBuilder.startTextDescription(
                    Component.literal("§7No sign boards registered yet. Place a sign with 'counter' in-world or add one by coordinates below!")
            ).build());
        } else {
            for (SignConfigData data : config.signConfigs.values()) {
                String locTag = config.showCoordinatesInChat ? (" §7(" + data.x + ", " + data.y + ", " + data.z + ")") : "";
                SubCategoryBuilder signSub = entryBuilder.startSubCategory(
                        Component.literal("§6✦ " + data.getDisplayName() + locTag)
                );

                // 1. Rename Sign Board
                signSub.add(entryBuilder.startStrField(
                                Component.literal("Sign Board Name"),
                                data.customName
                        )
                        .setDefaultValue(data.getDisplayName(true))
                        .setSaveConsumer(val -> data.customName = (val != null ? val.trim() : ""))
                        .setTooltip(Component.literal("Rename this sign board (e.g. Twisting Vine, Iron Farm, Gold Pipeline)."))
                        .build());

                // 2. Test Duration for this specific sign board (0 = Live Continuous Feed default)
                signSub.add(entryBuilder.startIntField(
                                Component.literal("Test Duration Seconds (0 = Live Continuous)"),
                                data.testDurationSeconds
                        )
                        .setDefaultValue(0)
                        .setMin(0)
                        .setMax(3600)
                        .setSaveConsumer(val -> data.testDurationSeconds = Math.max(0, val))
                        .setTooltip(Component.literal("Test countdown duration for this sign (0 = Live continuous feed, or 10, 30, 60s)."))
                        .build());

                // 3. Pricing System per Sign Board
                signSub.add(entryBuilder.startDoubleField(
                                Component.literal("Price Per Item / Entity (" + config.currencySymbol + ")"),
                                data.pricePerItem
                        )
                        .setDefaultValue(10.0)
                        .setMin(0.0)
                        .setMax(1000000.0)
                        .setSaveConsumer(val -> {
                            data.pricePerItem = Math.max(0.0, val);
                            data.useSignCustomPrice = true;
                        })
                        .setTooltip(Component.literal("Dollar/economy value earned for each item or entity counted at this specific sign board."))
                        .build());

                // 4. Live View Feed / Outer GUI (Sidebar HUD)
                signSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show on Outer GUI (Sidebar HUD)"),
                                data.showInHud
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showInHud = val)
                        .setTooltip(Component.literal("YES = Display this sign board's live view feed on the outer GUI sidebar. NO = Hide from outer GUI."))
                        .build());

                signSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Money in Outer GUI Card"),
                                data.showMoneyInHud
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showMoneyInHud = val)
                        .setTooltip(Component.literal("YES = Display calculated total earnings and per-minute rate on the outer GUI. NO = Count and speed only."))
                        .build());

                // 5. 3D In-World Hologram Setup
                signSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Enable 3D Hologram for this Sign"),
                                data.showHologram
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHologram = val)
                        .setTooltip(Component.literal("YES = Project floating 3D text in-world directly above this sign board. NO = Disable hologram."))
                        .build());

                signSub.add(entryBuilder.startDoubleField(
                                Component.literal("Hologram Vertical Height (Blocks)"),
                                data.hologramHeight
                        )
                        .setDefaultValue(1.25)
                        .setMin(0.0)
                        .setMax(10.0)
                        .setSaveConsumer(val -> data.hologramHeight = Math.max(0.0, val))
                        .setTooltip(Component.literal("Vertical distance of the floating hologram above this sign board (e.g. 1.25 just above, 2.5 higher up)."))
                        .build());

                signSub.add(entryBuilder.startDoubleField(
                                Component.literal("Hologram Text Size / Scale"),
                                data.hologramScale
                        )
                        .setDefaultValue(1.0)
                        .setMin(0.4)
                        .setMax(3.0)
                        .setSaveConsumer(val -> data.hologramScale = Math.max(0.4, Math.min(3.0, val)))
                        .setTooltip(Component.literal("Text scaling factor for this sign's hologram (e.g. 0.8x smaller, 1.0x normal, 1.5x larger)."))
                        .build());

                signSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Money in Hologram"),
                                data.showMoneyInHologram
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showMoneyInHologram = val)
                        .setTooltip(Component.literal("YES = Show item count AND calculated money generated. NO = Show ONLY items count & flow rate."))
                        .build());

                // 6. Item Filter
                signSub.add(entryBuilder.startStrField(
                                Component.literal("Filter Items (e.g. twisting_vines)"),
                                data.itemFilter
                        )
                        .setDefaultValue("")
                        .setSaveConsumer(val -> data.itemFilter = (val != null ? val.trim() : ""))
                        .setTooltip(Component.literal("Comma-separated item IDs to count at this sign (e.g. twisting_vines, iron_ingot). Leave blank for ALL items."))
                        .build());

                // 7. Coordinates Display
                signSub.add(entryBuilder.startTextDescription(
                        Component.literal(config.showCoordinatesInChat ? ("§7World Position: §eX=" + data.x + ", Y=" + data.y + ", Z=" + data.z) : "§8[Location Coordinates Hidden for Privacy]")
                ).build());

                signsCat.addEntry(signSub.build());
            }
        }

        // Add Sign Board by Coordinates section
        SubCategoryBuilder addSub = entryBuilder.startSubCategory(Component.literal("§a➕ Add Sign Board by Coordinates"));
        final int[] newCoords = new int[]{0, 64, 0};
        final String[] newName = new String[]{""};

        addSub.add(entryBuilder.startIntField(Component.literal("Coordinate X"), 0)
                .setSaveConsumer(val -> newCoords[0] = val).build());
        addSub.add(entryBuilder.startIntField(Component.literal("Coordinate Y"), 64)
                .setSaveConsumer(val -> newCoords[1] = val).build());
        addSub.add(entryBuilder.startIntField(Component.literal("Coordinate Z"), 0)
                .setSaveConsumer(val -> newCoords[2] = val).build());
        addSub.add(entryBuilder.startStrField(Component.literal("Sign Name"), "")
                .setSaveConsumer(val -> newName[0] = val)
                .setTooltip(Component.literal("Leave empty to name automatically."))
                .build());

        addSub.add(entryBuilder.startBooleanToggle(Component.literal("Confirm Add Sign on Save"), false)
                .setSaveConsumer(confirm -> {
                    if (confirm) {
                        config.addCustomSign(newCoords[0], newCoords[1], newCoords[2], newName[0]);
                    }
                })
                .setTooltip(Component.literal("Toggle ON and click Save to add this sign board to your manager."))
                .build());

        signsCat.addEntry(addSub.build());

        // ==================== Category 2: General & Privacy ====================
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General & Privacy"));

        // Tracking variable for 2-step confirmation when enabling coordinates
        final boolean[] requestedShowCoords = new boolean[]{ config.showCoordinatesInChat };

        // 1. Coordinates Privacy Toggle (DISABLED BY DEFAULT!)
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Show Coordinates in Chat"),
                        config.showCoordinatesInChat
                )
                .setDefaultValue(false)
                .setSaveConsumer(val -> requestedShowCoords[0] = val)
                .setTooltip(Component.literal("DISABLED BY DEFAULT to prevent leaking farm coordinates in chat. Turning this ON requires 2 confirmations."))
                .build());

        // 2. Keybind for Opening GUI (Starts UNBOUND / NONE)
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

        // 3. Keybind for Quick Reset / Restart of Closest Counter (Starts UNBOUND / NONE)
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

        // 4. Strict In-Front Directional Detection
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Strict In-Front Detection Only"),
                        config.strictInFrontOnly
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.strictInFrontOnly = val)
                .setTooltip(Component.literal("Only counts items moving directly in the 1-block stream facing the sign board."))
                .build());

        // 5. Global Enable Sidebar HUD (DISABLED BY DEFAULT)
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Enable Live Watch Sidebar HUD"),
                        config.showLiveWatchHud
                )
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.showLiveWatchHud = val)
                .setTooltip(Component.literal("Master switch for displaying the movable live watch sidebar box on screen (Default: OFF)."))
                .build());

        // 6. Currency Symbol
        general.addEntry(entryBuilder.startStrField(
                        Component.literal("Currency Symbol"),
                        config.currencySymbol
                )
                .setDefaultValue("$")
                .setSaveConsumer(val -> config.currencySymbol = (val != null && !val.isBlank()) ? val.trim() : "$")
                .setTooltip(Component.literal("Currency prefix to display (e.g. $, €, £, Coins)."))
                .build());

        // 7. Interactive Drag-and-Drop Explanation
        general.addEntry(entryBuilder.startTextDescription(
                Component.literal("§e💡 Move Sidebar: §fType §b/itemcounter hud §fin chat to click and drag the sidebar anywhere with your mouse!")
        ).build());

        // Saving logic with 2-step confirmation when enabling coordinates
        builder.setSavingRunnable(() -> {
            boolean wasEnabled = config.showCoordinatesInChat;
            boolean wantsEnabled = requestedShowCoords[0];

            if (!wasEnabled && wantsEnabled) {
                // Must ask TWO TIMES before enabling coordinates!
                Minecraft client = Minecraft.getInstance();
                ItemCounterConfirmation.requestEnableCoordinates(client, parent, () -> {
                    config.showCoordinatesInChat = true;
                    config.save();
                });
            } else {
                config.showCoordinatesInChat = wantsEnabled;
                config.save();
            }
        });

        return builder.build();
    }
}
