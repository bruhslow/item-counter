package me.giridharan.itemcounter.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.ItemCounterConfigScreen;
import me.giridharan.itemcounter.config.ItemCounterConfirmation;
import me.giridharan.itemcounter.config.ItemCounterHudEditScreen;
import me.giridharan.itemcounter.config.SignConfigData;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class ItemCounterCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("counter")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(ClientCommands.literal("gui").executes(ctx -> openGui()))
                .then(ClientCommands.literal("menu").executes(ctx -> openGui()))
                .then(ClientCommands.literal("hud").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("move").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("coords").executes(ctx -> toggleCoordinates(ctx.getSource())))
                .then(ClientCommands.literal("showcoords").executes(ctx -> toggleCoordinates(ctx.getSource())))
                .then(ClientCommands.literal("money").executes(ctx -> toggleMoney(ctx.getSource())))
                .then(ClientCommands.literal("height")
                        .then(ClientCommands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 10.0))
                                .executes(ctx -> setHologramHeight(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "blocks")))))
                .then(ClientCommands.literal("scale")
                        .then(ClientCommands.argument("size", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> setHologramScale(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "size")))))
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
                .then(ClientCommands.literal("rename")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> renameClosest(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("price")
                        .then(ClientCommands.argument("item", StringArgumentType.word())
                                .then(ClientCommands.argument("cost", DoubleArgumentType.doubleArg(0.0))
                                        .executes(ctx -> setItemPrice(ctx.getSource(), StringArgumentType.getString(ctx, "item"), DoubleArgumentType.getDouble(ctx, "cost"))))))
        );

        // Alias /itemcounter
        dispatcher.register(ClientCommands.literal("itemcounter")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(ClientCommands.literal("gui").executes(ctx -> openGui()))
                .then(ClientCommands.literal("menu").executes(ctx -> openGui()))
                .then(ClientCommands.literal("hud").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("move").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("coords").executes(ctx -> toggleCoordinates(ctx.getSource())))
                .then(ClientCommands.literal("showcoords").executes(ctx -> toggleCoordinates(ctx.getSource())))
                .then(ClientCommands.literal("money").executes(ctx -> toggleMoney(ctx.getSource())))
                .then(ClientCommands.literal("height")
                        .then(ClientCommands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 10.0))
                                .executes(ctx -> setHologramHeight(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "blocks")))))
                .then(ClientCommands.literal("scale")
                        .then(ClientCommands.argument("size", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> setHologramScale(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "size")))))
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
                .then(ClientCommands.literal("rename")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> renameClosest(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("price")
                        .then(ClientCommands.argument("item", StringArgumentType.word())
                                .then(ClientCommands.argument("cost", DoubleArgumentType.doubleArg(0.0))
                                        .executes(ctx -> setItemPrice(ctx.getSource(), StringArgumentType.getString(ctx, "item"), DoubleArgumentType.getDouble(ctx, "cost"))))))
        );
    }

    private static int openGui() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            client.gui.setScreen(ItemCounterConfigScreen.create(null));
        });
        return 1;
    }

    private static int openHudEditor() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            client.gui.setScreen(new ItemCounterHudEditScreen(null));
        });
        return 1;
    }

    private static int setHologramHeight(FabricClientCommandSource source, double height) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        config.hologramHeight = height;
        config.save();
        source.sendFeedback(Component.literal("§6[Item Counter] §aSet 3D hologram vertical height to: §b§l" + height + " blocks"));
        return 1;
    }

    private static int setHologramScale(FabricClientCommandSource source, double scale) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        config.hologramScale = scale;
        config.save();
        source.sendFeedback(Component.literal("§6[Item Counter] §aSet 3D hologram text size/scale to: §b§l" + scale + "x"));
        return 1;
    }

    private static int toggleMoney(FabricClientCommandSource source) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        config.showMoneyInHologram = !config.showMoneyInHologram;
        config.showMoneyInHud = config.showMoneyInHologram;
        config.save();
        String status = config.showMoneyInHologram ? "§a§lENABLED (Entities + Money)" : "§e§lDISABLED (Only Entities)";
        source.sendFeedback(Component.literal("§6[Item Counter] §aMoney display in hologram & sidebar: " + status));
        return 1;
    }

    private static int toggleCoordinates(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        ItemCounterConfig config = ItemCounterConfig.getInstance();

        if (config.showCoordinatesInChat) {
            config.showCoordinatesInChat = false;
            config.save();
            source.sendFeedback(Component.literal("§6[Item Counter] §aCoordinates in chat have been §c§lHIDDEN§a (safe mode)."));
            return 1;
        } else {
            client.execute(() -> {
                ItemCounterConfirmation.requestEnableCoordinates(client, null, () -> {
                    config.showCoordinatesInChat = true;
                    config.save();
                });
            });
            return 1;
        }
    }

    private static int showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6§l=== Item Counter Guide (Fabric 26.2) ==="));
        source.sendFeedback(Component.literal("§e1. §fPlace a sign and write §bcounter §fon it."));
        source.sendFeedback(Component.literal("§e2. §fOpen Settings: §b/counter gui §7(or press your bound key)"));
        source.sendFeedback(Component.literal("   §7- Adjust 3D Hologram vertical height & text scale"));
        source.sendFeedback(Component.literal("   §7- Toggle between 'Only Entities' or 'Entities + Money'"));
        source.sendFeedback(Component.literal("   §7- Rename each sign board & filter specific items"));
        source.sendFeedback(Component.literal("§e3. §fCommands:"));
        source.sendFeedback(Component.literal("   §7- §b/counter height <blocks> §f: Set hologram vertical height (e.g. 1.5, 2.5)"));
        source.sendFeedback(Component.literal("   §7- §b/counter scale <size> §f: Set hologram size (e.g. 0.8, 1.2)"));
        source.sendFeedback(Component.literal("   §7- §b/counter money §f: Toggle money generated on/off"));
        source.sendFeedback(Component.literal("   §7- §b/counter hud §f: Drag & move sidebar HUD"));
        source.sendFeedback(Component.literal("   §7- §b/counter coords §f: Toggle coordinate privacy"));
        source.sendFeedback(Component.literal("   §7- §b/counter reset §f: Restart nearest counter"));
        source.sendFeedback(Component.literal("   §7- §b/counter list §f: Show all active counters & values"));
        return 1;
    }

    private static int listCounters(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§6[Item Counter] §eNo active counter signs nearby. Place a sign with §bcounter §etyped on it!"));
            return 1;
        }

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        source.sendFeedback(Component.literal("§6§l[Item Counter] Active Counters (" + counters.size() + "):"));
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            CounterSign c = entry.getValue();
            String name = c.getSignCustomName();
            String posStr = config.showCoordinatesInChat ? (" §7(" + pos.toShortString() + ")") : "";
            double money = c.getTotalMoneyValue();
            String moneyStr = config.showMoneyInHud ? (" §7| Value: §a" + config.currencySymbol + String.format("%,.0f", money)) : "";

            source.sendFeedback(Component.literal("§e- §f" + name + posStr + " §7Mode: §b" + c.getDisplayName()
                    + " §7| Items: §a" + c.getTotalItemsCounted() + " §7(~" + (int) c.getRatePerMinute() + "/m)" + moneyStr));
        }
        return 1;
    }

    private static int renameClosest(FabricClientCommandSource source, String newName) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§c[Item Counter] No active counters found nearby to rename!"));
            return 0;
        }

        BlockPos playerPos = source.getPlayer().blockPosition();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : counters.keySet()) {
            double d = pos.distSqr(playerPos);
            if (d < closestDist) {
                closestDist = d;
                closest = pos;
            }
        }

        if (closest != null) {
            SignConfigData data = ItemCounterConfig.getInstance().getOrCreateSignConfig(closest.getX(), closest.getY(), closest.getZ());
            data.customName = newName;
            ItemCounterConfig.getInstance().save();

            String posStr = ItemCounterConfig.getInstance().showCoordinatesInChat ? (" at §e" + closest.toShortString()) : "";
            source.sendFeedback(Component.literal("§6[Item Counter] §aRenamed sign" + posStr + " §ato: §b§l" + newName));
            return 1;
        }
        return 0;
    }

    private static int setItemPrice(FabricClientCommandSource source, String item, double cost) {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        config.setItemPrice(item, cost);
        config.save();
        source.sendFeedback(Component.literal("§6[Item Counter] §aSet price for §e" + item + " §ato: §b§l" + config.currencySymbol + cost));
        return 1;
    }

    public static int resetClosest(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§c[Item Counter] No active counters found nearby!"));
            return 0;
        }

        BlockPos playerPos = source.getPlayer().blockPosition();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : counters.keySet()) {
            double d = pos.distSqr(playerPos);
            if (d < closestDist) {
                closestDist = d;
                closest = pos;
            }
        }

        if (closest != null) {
            CounterSign c = counters.get(closest);
            c.restart();
            String posStr = ItemCounterConfig.getInstance().showCoordinatesInChat ? (" (" + closest.toShortString() + ")") : "";
            source.sendFeedback(Component.literal("§6[Item Counter] §aRestarted " + c.getSignCustomName() + posStr + "!"));
        }
        return 1;
    }

    private static int clearCounters(FabricClientCommandSource source) {
        int count = CounterManager.getActiveCounters().size();
        CounterManager.clearAll();
        source.sendFeedback(Component.literal("§6[Item Counter] §cCleared " + count + " active counter(s)!"));
        return 1;
    }
}
