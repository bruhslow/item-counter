package me.giridharan.itemcounter.command;

import com.mojang.brigadier.CommandDispatcher;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class ItemCounterCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("counter")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
        );

        // Alias /itemcounter
        dispatcher.register(ClientCommands.literal("itemcounter")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(ClientCommands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(ClientCommands.literal("list").executes(ctx -> listCounters(ctx.getSource())))
                .then(ClientCommands.literal("clear").executes(ctx -> clearCounters(ctx.getSource())))
                .then(ClientCommands.literal("reset").executes(ctx -> resetClosest(ctx.getSource())))
        );
    }

    private static int showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6§l=== Item Counter Guide (Fabric 26.2) ==="));
        source.sendFeedback(Component.literal("§e1. §fPlace a sign near your farm's water/ice stream."));
        source.sendFeedback(Component.literal("§e2. §fWrite §b[counter] §fon line 1, and §b10s§f, §b30s§f, or §b60s §fon line 2."));
        source.sendFeedback(Component.literal("§e3. §aOR §fsimply §bRight-Click §fany sign to turn it into an Item Counter!"));
        source.sendFeedback(Component.literal("§e4. §fInteractions:"));
        source.sendFeedback(Component.literal("   §7- §bRight-Click Sign: §fRestarts the test"));
        source.sendFeedback(Component.literal("   §7- §bShift + Right-Click Sign: §fChanges timer mode (10s → 30s → 60s → Live)"));
        source.sendFeedback(Component.literal("§eCommands: §7/counter list, /counter reset, /counter clear"));
        return 1;
    }

    private static int listCounters(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§6[Item Counter] §eNo active counter signs found nearby. Place a sign with §b[counter] §eor right-click any sign!"));
            return 1;
        }

        source.sendFeedback(Component.literal("§6§l[Item Counter] Active Counters (" + counters.size() + "):"));
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            CounterSign c = entry.getValue();
            source.sendFeedback(Component.literal("§e- §f(" + pos.toShortString() + ") §7Mode: §b" + c.getMode().displayName
                    + " §7| Counted: §a" + c.getTotalItemsCounted() + " §7| Rate: §b~" + (int) c.getRatePerMinute() + "/min"));
        }
        return 1;
    }

    private static int resetClosest(FabricClientCommandSource source) {
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            source.sendFeedback(Component.literal("§c[Item Counter] No active counters found!"));
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
