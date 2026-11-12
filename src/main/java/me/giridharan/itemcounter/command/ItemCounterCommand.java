package me.giridharan.itemcounter.command;

import com.mojang.brigadier.CommandDispatcher;
import me.giridharan.itemcounter.config.ItemCounterConfigScreen;
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
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
        );

        // Alias /itemcounter
        dispatcher.register(ClientCommands.literal("itemcounter")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(ClientCommands.literal("gui").executes(ctx -> openGui()))
                .then(ClientCommands.literal("menu").executes(ctx -> openGui()))
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
        );
    }

    private static int openGui() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            client.gui.setScreen(ItemCounterConfigScreen.create(null));
        });
        return 1;
    }

    private static int showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6§l=== Item Counter Guide (Fabric 26.2) ==="));
        source.sendFeedback(Component.literal("§e1. §fPlace a sign in front of or on your farm's water/ice stream."));
        source.sendFeedback(Component.literal("§e2. §fWrite §bcounter §fon the sign (e.g. §b[counter] §for §bcounter 10s§f)."));
        source.sendFeedback(Component.literal("   §7(Signs ONLY convert into item counters if 'counter' is typed on them!)"));
        source.sendFeedback(Component.literal("§e3. §fItems are counted §eONLY when moving directly in front §fof the sign."));
        source.sendFeedback(Component.literal("§e4. §fInteractions:"));
        source.sendFeedback(Component.literal("   §7- §bRight-Click Sign: §fRestarts / zeroes current test"));
        source.sendFeedback(Component.literal("   §7- §bShift + Right-Click: §fCycles mode (10s → 30s → 60s → Live)"));
        source.sendFeedback(Component.literal("§eCommands:"));
        source.sendFeedback(Component.literal("   §7- §b/counter gui §f: Open Settings to set custom keybind (e.g. Ctrl+O) & default time"));
        source.sendFeedback(Component.literal("   §7- §b/counter list §f: Show active counters nearby"));
        source.sendFeedback(Component.literal("   §7- §b/counter reset §f: Restart closest counter"));
        source.sendFeedback(Component.literal("   §7- §b/counter clear §f: Unregister active counters"));
        return 1;
    }

    private static int listCounters(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§6[Item Counter] §eNo active counter signs nearby. Place a sign with §bcounter §etyped on it!"));
            return 1;
        }

        source.sendFeedback(Component.literal("§6§l[Item Counter] Active Counters (" + counters.size() + "):"));
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            CounterSign c = entry.getValue();
            source.sendFeedback(Component.literal("§e- §f(" + pos.toShortString() + ") §7Mode: §b" + c.getDisplayName()
                    + " §7| Counted: §a" + c.getTotalItemsCounted() + " §7| Rate: §b~" + (int) c.getRatePerMinute() + "/min"));
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
            source.sendFeedback(Component.literal("§6[Item Counter] §aRestarted closest counter at §e(" + closest.toShortString() + ")!"));
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
