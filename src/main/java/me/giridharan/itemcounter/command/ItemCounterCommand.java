package me.giridharan.itemcounter.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.ItemCounterConfigScreen;
import me.giridharan.itemcounter.config.ItemCounterHudEditScreen;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

public class ItemCounterCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Register primary /itemcounter command
        dispatcher.register(buildRootCommand("itemcounter"));

        // Register /counter shortcut alias
        dispatcher.register(buildRootCommand("counter"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRootCommand(String root) {
        return ClientCommands.literal(root)
                .executes(ctx -> openGui())
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("gui").executes(ctx -> openGui()))
                .then(ClientCommands.literal("menu").executes(ctx -> openGui()))
                .then(ClientCommands.literal("hud").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("move").executes(ctx -> openHudEditor()))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())));
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
            ItemCounterConfig config = ItemCounterConfig.getInstance();
            config.showLiveWatchHud = true;
            config.save();
            client.gui.setScreen(new ItemCounterHudEditScreen(null));
        });
        return 1;
    }

    private static int showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6§l================ Item Counter Commands & Guide ================"));
        source.sendFeedback(Component.literal("§fReal-time item and entity flow counter for Minecraft 26.x"));
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§e§lCommands: §7(Click any command below to execute)"));
        source.sendFeedback(createCommandRow("/itemcounter", "Open full Settings GUI (Holograms, HUD, Prices, Filters, Privacy)", "§aClick to open GUI"));
        source.sendFeedback(createCommandRow("/itemcounter help", "Display this help guide with all functions and commands", "§eClick to run /itemcounter help"));
        source.sendFeedback(createCommandRow("/itemcounter hud", "Drag and reposition the live watch sidebar box on your screen", "§aClick to move HUD"));
        source.sendFeedback(createCommandRow("/itemcounter list", "List all active counter signs, status, items counted & rates", "§aClick to list active counters"));
        source.sendFeedback(createCommandRow("/itemcounter reset", "Instantly restart the test on the nearest counter sign", "§aClick to reset nearest counter"));
        source.sendFeedback(createCommandRow("/itemcounter clear", "Clear all active counter signs from tracking memory", "§cClick to clear counters"));
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§e§lFunctions & How To Use:"));
        source.sendFeedback(Component.literal(" §b• Sign Activation: §fPlace a sign and write §bcounter§f on it to activate it."));
        source.sendFeedback(Component.literal(" §b• Flow Detection: §fOnly items moving directly in front of the sign face are counted."));
        source.sendFeedback(Component.literal(" §b• Sign Controls: §fRight-click sign to restart test; §fShift+Right-click to change timer (10s, 30s, 60s, Live)."));
        source.sendFeedback(Component.literal(" §b• 3D Hologram: §fDisplays floating text above sign with items passed, rates & value (height/size customizable in GUI)."));
        source.sendFeedback(Component.literal(" §b• Sidebar HUD: §fLive watch display showing active farm rates in real-time. Move it using §b/itemcounter hud§f."));
        source.sendFeedback(Component.literal(" §b• Pricing & Economy: §fSet item prices in §b/itemcounter gui §fto see hourly earnings & total value."));
        source.sendFeedback(Component.literal("§6§l=============================================================="));
        return 1;
    }

    private static MutableComponent createCommandRow(String cmd, String desc, String hover) {
        MutableComponent button = Component.literal("§b" + cmd)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));

        return Component.literal("  ").append(button).append(Component.literal(" §8» §7" + desc));
    }

    private static int listCounters(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§6[Item Counter] §eNo active counter signs nearby. Place a sign with §bcounter §etyped on it!"));
            return 1;
        }

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        source.sendFeedback(Component.literal("§6§l=== Active Counter Signs (" + counters.size() + ") ==="));
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            CounterSign c = entry.getValue();
            String name = c.getSignCustomName();
            double money = c.getTotalMoneyValue();
            String moneyStr = config.showMoneyInHud ? (" §7| Value: §a" + config.currencySymbol + String.format("%,.0f", money)) : "";
            String status = c.isFinished() ? "§a[DONE]" : (c.isRunning() ? "§e[RUNNING]" : "§7[PAUSED]");

            source.sendFeedback(Component.literal("§e- §f" + name + " " + status + " §7Mode: §b" + c.getDisplayName()
                    + " §7| Items: §a§l" + c.getTotalItemsCounted() + " §7(~" + (int) c.getRatePerMinute() + "/m)" + moneyStr));
        }
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
            source.sendFeedback(Component.literal("§6[Item Counter] §aRestarted " + c.getSignCustomName() + "!"));
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
