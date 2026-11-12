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

        // ==================== Category 1: General & Keys ====================
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

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

        // 4. Default Timer Duration
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

        // 5. Show In-World 3D Hologram
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Show 3D In-World Hologram"),
                        config.showHologram
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showHologram = val)
                .setTooltip(Component.literal("Displays live floating 3D text directly above the sign board in the world."))
                .build());

        // 6. Strict In-Front Directional Detection
        general.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Strict In-Front Detection Only"),
                        config.strictInFrontOnly
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.strictInFrontOnly = val)
                .setTooltip(Component.literal("Only counts items moving directly in the 1-block stream facing the sign board."))
                .build());

        // ==================== Category 2: Live Watch Sidebar HUD ====================
        ConfigCategory hudCat = builder.getOrCreateCategory(Component.literal("Sidebar HUD"));

        // 7. Live Watch On-Screen HUD Toggle
        hudCat.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Enable Sidebar HUD"),
                        config.showLiveWatchHud
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showLiveWatchHud = val)
                .setTooltip(Component.literal("Displays a movable live on-screen sidebar box showing count, speed, and time."))
                .build());

        // 8. Show Money in Sidebar Toggle
        hudCat.addEntry(entryBuilder.startBooleanToggle(
                        Component.literal("Show Money / Earnings in Sidebar"),
                        config.showMoneyInHud
                )
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showMoneyInHud = val)
                .setTooltip(Component.literal("Calculates and displays total value and hourly rate alongside item counts."))
                .build());

        // 9. Currency Symbol
        hudCat.addEntry(entryBuilder.startStrField(
                        Component.literal("Currency Symbol"),
                        config.currencySymbol
                )
                .setDefaultValue("$")
                .setSaveConsumer(val -> config.currencySymbol = (val != null && !val.isBlank()) ? val.trim() : "$")
                .setTooltip(Component.literal("Currency prefix to display (e.g. $, €, £, Coins)."))
                .build());

        // 10. Interactive Drag-and-Drop Explanation
        hudCat.addEntry(entryBuilder.startTextDescription(
                Component.literal("§e💡 Move Anywhere: §fType §b/counter hud §for §b/counter move §fin chat to click and drag the sidebar anywhere with your mouse!")
        ).build());

        // 11. Manual Coordinates
        hudCat.addEntry(entryBuilder.startIntField(
                        Component.literal("Sidebar X Position (Pixels)"),
                        config.liveWatchX
                )
                .setDefaultValue(10)
                .setMin(0)
                .setMax(4000)
                .setSaveConsumer(val -> config.liveWatchX = val)
                .build());

        hudCat.addEntry(entryBuilder.startIntField(
                        Component.literal("Sidebar Y Position (Pixels)"),
                        config.liveWatchY
                )
                .setDefaultValue(10)
                .setMin(0)
                .setMax(4000)
                .setSaveConsumer(val -> config.liveWatchY = val)
                .build());

        // ==================== Category 3: Item & Entity Prices ====================
        ConfigCategory priceCat = builder.getOrCreateCategory(Component.literal("Item Prices"));

        priceCat.addEntry(entryBuilder.startTextDescription(
                Component.literal("§eSet price per item/entity. The mod calculates total value and per-minute earnings in the sidebar!")
        ).build());

        priceCat.addEntry(entryBuilder.startDoubleField(
                        Component.literal("Default Price (Fallback)"),
                        config.defaultItemPrice
                )
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setMax(100000.0)
                .setSaveConsumer(val -> config.defaultItemPrice = Math.max(0.0, val))
                .setTooltip(Component.literal("Price applied to any item without a specific price configured."))
                .build());

        addPriceField(priceCat, entryBuilder, config, "Iron Ingot", "iron_ingot", 10.0);
        addPriceField(priceCat, entryBuilder, config, "Gold Ingot", "gold_ingot", 25.0);
        addPriceField(priceCat, entryBuilder, config, "Diamond", "diamond", 100.0);
        addPriceField(priceCat, entryBuilder, config, "Emerald", "emerald", 50.0);
        addPriceField(priceCat, entryBuilder, config, "Netherite Ingot", "netherite_ingot", 500.0);
        addPriceField(priceCat, entryBuilder, config, "Copper Ingot", "copper_ingot", 2.0);
        addPriceField(priceCat, entryBuilder, config, "Redstone", "redstone", 3.0);
        addPriceField(priceCat, entryBuilder, config, "Lapis Lazuli", "lapis_lazuli", 4.0);
        addPriceField(priceCat, entryBuilder, config, "Bamboo", "bamboo", 0.5);
        addPriceField(priceCat, entryBuilder, config, "Sugar Cane", "sugar_cane", 1.0);
        addPriceField(priceCat, entryBuilder, config, "Slime Ball", "slime_ball", 5.0);
        addPriceField(priceCat, entryBuilder, config, "Gunpowder", "gunpowder", 4.0);
        addPriceField(priceCat, entryBuilder, config, "Rotten Flesh", "rotten_flesh", 1.0);
        addPriceField(priceCat, entryBuilder, config, "Bone", "bone", 2.0);
        addPriceField(priceCat, entryBuilder, config, "String", "string", 2.0);
        addPriceField(priceCat, entryBuilder, config, "Ender Pearl", "ender_pearl", 15.0);

        // ==================== Category 4: Sign Boards & Farms ====================
        ConfigCategory signsCat = builder.getOrCreateCategory(Component.literal("Sign Boards & Farms"));

        signsCat.addEntry(entryBuilder.startTextDescription(
                Component.literal("§e✦ All signs placed in your world with 'counter' are listed here. You can rename them, choose if they show in the sidebar, and filter specific items!")
        ).build());

        if (config.signConfigs.isEmpty()) {
            signsCat.addEntry(entryBuilder.startTextDescription(
                    Component.literal("§7No sign boards registered yet. Place a sign with 'counter' in-world or add one below!")
            ).build());
        } else {
            for (SignConfigData data : config.signConfigs.values()) {
                String locTag = config.showCoordinatesInChat ? (" §7(" + data.x + ", " + data.y + ", " + data.z + ")") : "";
                SubCategoryBuilder signSub = entryBuilder.startSubCategory(
                        Component.literal("§6" + data.getDisplayName() + locTag)
                );

                signSub.add(entryBuilder.startStrField(
                                Component.literal("Custom Sign Name"),
                                data.customName
                        )
                        .setDefaultValue(data.getDisplayName(true))
                        .setSaveConsumer(val -> data.customName = val)
                        .setTooltip(Component.literal("Rename this sign board (e.g. Iron Farm, Gold Line, Pipeline A)"))
                        .build());

                signSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show in Sidebar HUD"),
                                data.showInHud
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showInHud = val)
                        .setTooltip(Component.literal("Whether this sign board is visible in your on-screen sidebar HUD."))
                        .build());

                signSub.add(entryBuilder.startStrField(
                                Component.literal("Filter Items (e.g. iron_ingot)"),
                                data.itemFilter
                        )
                        .setDefaultValue("")
                        .setSaveConsumer(val -> data.itemFilter = val)
                        .setTooltip(Component.literal("Comma-separated item names to count/show (e.g. iron_ingot, poppy). Leave blank for ALL items."))
                        .build());

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

    private static void addPriceField(ConfigCategory cat, ConfigEntryBuilder builder, ItemCounterConfig config, String title, String itemId, double defPrice) {
        cat.addEntry(builder.startDoubleField(
                        Component.literal(title + " (" + config.currencySymbol + ")"),
                        config.getItemPrice(itemId)
                )
                .setDefaultValue(defPrice)
                .setMin(0.0)
                .setMax(1000000.0)
                .setSaveConsumer(val -> config.setItemPrice(itemId, Math.max(0.0, val)))
                .build());
    }
}
