package me.giridharan.itemcounter.counter;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CounterManager {

    private static final Map<BlockPos, CounterSign> activeCounters = new ConcurrentHashMap<>();
    private static int scanCooldown = 0;

    public static Map<BlockPos, CounterSign> getActiveCounters() {
        return activeCounters;
    }

    public static boolean onRightClickSign(Level level, BlockPos pos, Player player, boolean isCrouching) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity)) {
            return false;
        }

        CounterSign existing = activeCounters.get(pos);
        if (existing != null) {
            if (isCrouching) {
                // Cycle through 10s -> 30s -> 60s -> Live
                existing.cycleMode(player);
            } else {
                // Restart current test
                existing.restart();
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
                player.sendSystemMessage(Component.literal("§6§l[Item Counter] §aRestarted §e" + existing.getMode().displayName + " §atest! Tracking items passing through..."));
            }
            return true;
        } else {
            // Register this sign as a counter
            CounterSign newCounter = new CounterSign(pos);
            activeCounters.put(pos, newCounter);
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
            player.sendSystemMessage(Component.literal("§6§l[Item Counter] §aSign registered as §e30s Item Counter§a!"));
            player.sendSystemMessage(Component.literal("§7Right-click to restart test, or Shift + Right-click to change duration (10s, 30s, 60s, Live)."));
            return true;
        }
    }

    public static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        Level level = client.level;
        Player player = client.player;

        // 1. Tick all active counters
        Iterator<Map.Entry<BlockPos, CounterSign>> it = activeCounters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, CounterSign> entry = it.next();
            BlockPos pos = entry.getKey();
            CounterSign counter = entry.getValue();

            // Validate sign still exists
            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof SignBlockEntity)) {
                    it.remove();
                    continue;
                }
            }

            counter.tick(level, player);
        }

        // 2. Periodic automatic detection of signs with [counter] text (every 20 ticks / 1 second)
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = 20;
            scanNearbySigns(level, player.blockPosition());
        }
    }

    private static void scanNearbySigns(Level level, BlockPos playerPos) {
        int radius = 16;
        BlockPos min = playerPos.offset(-radius, -6, -radius);
        BlockPos max = playerPos.offset(radius, 6, radius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (activeCounters.containsKey(pos)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SignBlockEntity sign) {
                checkAndRegisterSignText(pos.immutable(), sign);
            }
        }
    }

    private static void checkAndRegisterSignText(BlockPos pos, SignBlockEntity sign) {
        String frontLine1 = getSignLine(sign.getFrontText(), 0);
        String frontLine2 = getSignLine(sign.getFrontText(), 1);
        String backLine1 = getSignLine(sign.getBackText(), 0);
        String backLine2 = getSignLine(sign.getBackText(), 1);

        String trigger = null;
        String modeText = null;

        if (isTrigger(frontLine1)) {
            trigger = frontLine1;
            modeText = frontLine2;
        } else if (isTrigger(backLine1)) {
            trigger = backLine1;
            modeText = backLine2;
        }

        if (trigger != null) {
            CounterSign counter = new CounterSign(pos);
            if (modeText != null && !modeText.isBlank()) {
                counter.setMode(CounterSign.Mode.fromString(modeText));
            } else if (trigger.contains(":")) {
                String sub = trigger.substring(trigger.indexOf(":") + 1).replace("]", "").trim();
                counter.setMode(CounterSign.Mode.fromString(sub));
            }
            activeCounters.put(pos, counter);
        }
    }

    private static boolean isTrigger(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase();
        return lower.contains("[counter") || lower.contains("[count]") || lower.contains("[itemcounter");
    }

    private static String getSignLine(SignText signText, int lineIndex) {
        if (signText == null) return "";
        Component msg = signText.getMessage(lineIndex, false);
        return msg != null ? msg.getString().trim() : "";
    }

    public static void clearAll() {
        activeCounters.clear();
    }
}
