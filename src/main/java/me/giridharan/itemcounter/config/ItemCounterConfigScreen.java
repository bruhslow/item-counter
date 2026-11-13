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
        Minecraft client = Minecraft.getInstance();

        // Clean up any sign configs if the sign block was physically destroyed or text erased
        if (client.level != null) {
            config.signConfigs.entrySet().removeIf(entry -> {
                SignConfigData data = entry.getValue();
                BlockPos pos = new BlockPos(data.x, data.y, data.z);
                if (client.level.isLoaded(pos)) {
                    net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                    if (state.isAir() || !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) {
                        CounterManager.getActiveCounters().remove(pos);
                        return true;
                    }
                    net.minecraft.world.level.block.entity.BlockEntity be = client.level.getBlockEntity(pos);
                    if (be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                        boolean frontHas = CounterManager.hasCounterText(sign.getFrontText());
                        boolean backHas = CounterManager.hasCounterText(sign.getBackText());
                        if (!frontHas && !backHas) {
                            CounterManager.getActiveCounters().remove(pos);
                            return true;
                        }
                    }
                }
                return false;
            });
            config.save();
        }

        // Sync any active in-world signs into config
        for (BlockPos pos : CounterManager.getActiveCounters().keySet()) {
            config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
        }

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("§6§lItem Counter Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Tracking variable for 2-step confirmation when enabling coordinates
        final boolean[] requestedShowCoords = new boolean[]{ config.showCoordinatesInChat };

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
                signSub.setExpanded(true);

                // --- 1. General & Farm Settings ---
                SubCategoryBuilder generalSub = entryBuilder.startSubCategory(Component.literal("⚙️ General & Farm Settings"));
                generalSub.setExpanded(true);

                generalSub.add(entryBuilder.startStrField(
                                Component.literal("Sign Board Name"),
                                data.customName
                        )
                        .setDefaultValue(data.getDisplayName(true))
                        .setSaveConsumer(val -> data.customName = (val != null ? val.trim() : ""))
                        .setTooltip(Component.literal("Rename this sign board (e.g. Twisting Vine, Iron Farm, Gold Pipeline)."))
                        .build());

                generalSub.add(entryBuilder.startIntField(
                                Component.literal("Test Duration Seconds (0 = Live Continuous)"),
                                data.testDurationSeconds
                        )
                        .setDefaultValue(0)
                        .setMin(0)
                        .setMax(3600)
                        .setSaveConsumer(val -> data.testDurationSeconds = Math.max(0, val))
                        .setTooltip(Component.literal("Test countdown duration for this sign (0 = Live continuous feed, or 10, 30, 60s)."))
                        .build());

                generalSub.add(entryBuilder.startDoubleField(
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

                generalSub.add(entryBuilder.startStrField(
                                Component.literal("Filter Items (e.g. twisting_vines)"),
                                data.itemFilter
                        )
                        .setDefaultValue("")
                        .setSaveConsumer(val -> data.itemFilter = (val != null ? val.trim() : ""))
                        .setTooltip(Component.literal("Comma-separated item IDs to count at this sign (e.g. twisting_vines, iron_ingot). Leave blank for ALL items."))
                        .build());

                signSub.add(generalSub.build());

                // --- 2. Outer GUI (Sidebar HUD) Settings ---
                SubCategoryBuilder hudSub = entryBuilder.startSubCategory(Component.literal("📺 Outer GUI (Sidebar HUD) Settings"));
                hudSub.setExpanded(true);

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Enable Sidebar HUD (Master Switch)"),
                                config.showLiveWatchHud
                        )
                        .setDefaultValue(false)
                        .setSaveConsumer(val -> config.showLiveWatchHud = val)
                        .setTooltip(Component.literal("Master on/off switch for displaying the movable live watch sidebar HUD on your screen."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show this Sign on Sidebar HUD"),
                                data.showInHud
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> {
                            data.showInHud = val;
                            if (val) config.showLiveWatchHud = true;
                        })
                        .setTooltip(Component.literal("YES = Display this sign board on the HUD sidebar. Automatically activates the HUD."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Sign Name & Header"),
                                data.showHudHeader
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHudHeader = val)
                        .setTooltip(Component.literal("YES = Show the sign board's custom name in the sidebar HUD card."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Mode / Status Badge"),
                                data.showHudBadge
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHudBadge = val)
                        .setTooltip(Component.literal("YES = Show [LIVE] or [10s] mode badge in the sidebar header."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Entity Passed (Item Count)"),
                                data.showHudCount
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHudCount = val)
                        .setTooltip(Component.literal("YES = Show total items/entities passed through the stream on the sidebar."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Item Flow Speed (~items/min)"),
                                data.showHudRate
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHudRate = val)
                        .setTooltip(Component.literal("YES = Show calculated items per minute speed indicator next to the item count."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Total Money Made ($)"),
                                data.showMoneyInHud
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showMoneyInHud = val)
                        .setTooltip(Component.literal("YES = Display calculated total earnings on the outer GUI sidebar."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Money Rate (~$/min)"),
                                data.showHudMoneyRate
                        )
                        .setDefaultValue(false)
                        .setSaveConsumer(val -> data.showHudMoneyRate = val)
                        .setTooltip(Component.literal("YES = Show money earnings speed per minute/second next to money total. (Default: OFF / Hidden)."))
                        .build());

                hudSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Status Monitor Footer"),
                                data.showHudFooter
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHudFooter = val)
                        .setTooltip(Component.literal("YES = Show 'Continuous Flow Monitor' or countdown timer status at the bottom of the card."))
                        .build());

                signSub.add(hudSub.build());

                // --- 3. 3D In-World Hologram Settings ---
                SubCategoryBuilder holoSub = entryBuilder.startSubCategory(Component.literal("✨ 3D In-World Hologram Settings"));
                holoSub.setExpanded(true);

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Enable 3D Hologram for this Sign"),
                                data.showHologram
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHologram = val)
                        .setTooltip(Component.literal("YES = Project floating 3D text in-world directly above this sign board. NO = Disable hologram."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Sign Name in Header"),
                                data.showHoloHeader
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHoloHeader = val)
                        .setTooltip(Component.literal("YES = Show the custom name of the sign board in the hologram header."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Mode / Status Badge"),
                                data.showHoloBadge
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHoloBadge = val)
                        .setTooltip(Component.literal("YES = Show [LIVE RATE] or [10s] status badge in the hologram header."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Entity Passed (Item Count)"),
                                data.showHoloCount
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHoloCount = val)
                        .setTooltip(Component.literal("YES = Show total items/entities passed on line 2 of the hologram."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Item Flow Speed (~items/min)"),
                                data.showHoloRate
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHoloRate = val)
                        .setTooltip(Component.literal("YES = Show live calculated flow rate (~items/min) next to entity passed."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Total Money Made ($)"),
                                data.showMoneyInHologram
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showMoneyInHologram = val)
                        .setTooltip(Component.literal("YES = Show calculated total dollar/money value earned on line 3."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Money Rate (~$/min)"),
                                data.showHoloMoneyRate
                        )
                        .setDefaultValue(false)
                        .setSaveConsumer(val -> data.showHoloMoneyRate = val)
                        .setTooltip(Component.literal("YES = Show money earnings speed per minute/second next to money total. (Default: OFF / Hidden)."))
                        .build());

                holoSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Interaction Guide Footer"),
                                data.showHoloGuide
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(val -> data.showHoloGuide = val)
                        .setTooltip(Component.literal("YES = Show 'Right-click: reset | Shift+Right-click: mode' footer in the hologram."))
                        .build());

                holoSub.add(entryBuilder.startDoubleField(
                                Component.literal("Hologram Vertical Height (Blocks)"),
                                data.hologramHeight
                        )
                        .setDefaultValue(1.25)
                        .setMin(0.0)
                        .setMax(10.0)
                        .setSaveConsumer(val -> data.hologramHeight = Math.max(0.0, val))
                        .setTooltip(Component.literal("Vertical distance of the floating hologram above this sign board (e.g. 1.25 just above, 2.5 higher up)."))
                        .build());

                holoSub.add(entryBuilder.startDoubleField(
                                Component.literal("Hologram Text Size / Scale"),
                                data.hologramScale
                        )
                        .setDefaultValue(1.0)
                        .setMin(0.4)
                        .setMax(3.0)
                        .setSaveConsumer(val -> data.hologramScale = Math.max(0.4, Math.min(3.0, val)))
                        .setTooltip(Component.literal("Text scaling factor for this sign's hologram (e.g. 0.8x smaller, 1.0x normal, 1.5x larger)."))
                        .build());

                signSub.add(holoSub.build());

                // --- 4. Coordinates & Anti-Leak Privacy ---
                SubCategoryBuilder coordSub = entryBuilder.startSubCategory(Component.literal("📍 Coordinates & Privacy"));
                coordSub.setExpanded(true);

                coordSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("Show Coordinates in Chat & UI"),
                                config.showCoordinatesInChat
                        )
                        .setDefaultValue(false)
                        .setSaveConsumer(val -> requestedShowCoords[0] = val)
                        .setTooltip(Component.literal("Toggle ON and click Save to reveal farm coordinates. Requires 2-step confirmation to protect your base."))
                        .build());

                coordSub.add(entryBuilder.startTextDescription(
                        Component.literal(config.showCoordinatesInChat
                                ? ("§a📍 Exact Farm Location: §eX=" + data.x + ", Y=" + data.y + ", Z=" + data.z)
                                : "§8[📍 Location Coordinates Hidden for Anti-Leak Privacy - Toggle ON above to reveal]")
                ).build());

                signSub.add(coordSub.build());

                // --- 5. Danger Zone / Delete Sign ---
                SubCategoryBuilder manageSub = entryBuilder.startSubCategory(Component.literal("🗑 Delete & Manage"));
                manageSub.setExpanded(false);

                manageSub.add(entryBuilder.startBooleanToggle(
                                Component.literal("§c🗑 Remove this Sign Board on Save"),
                                false
                        )
                        .setSaveConsumer(remove -> {
                            if (remove) {
                                config.removeSignConfig(data.x, data.y, data.z);
                                CounterManager.getActiveCounters().remove(new BlockPos(data.x, data.y, data.z));
                            }
                        })
                        .setTooltip(Component.literal("Toggle YES and click Save to permanently remove this sign board from your settings."))
                        .build());

                signSub.add(manageSub.build());

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
