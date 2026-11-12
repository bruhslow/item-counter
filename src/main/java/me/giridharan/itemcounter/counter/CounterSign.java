package me.giridharan.itemcounter.counter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CounterSign {

    public enum Mode {
        SEC_10(10, "10s"),
        SEC_30(30, "30s"),
        SEC_60(60, "60s"),
        LIVE(0, "Live Rate");

        public final int durationSeconds;
        public final String displayName;

        Mode(int durationSeconds, String displayName) {
            this.durationSeconds = durationSeconds;
            this.displayName = displayName;
        }

        public Mode next() {
            Mode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        public static Mode fromString(String str) {
            if (str == null) return SEC_30;
            String lower = str.toLowerCase().trim();
            if (lower.contains("10")) return SEC_10;
            if (lower.contains("60") || lower.contains("1m")) return SEC_60;
            if (lower.contains("live") || lower.contains("inf") || lower.contains("rate")) return LIVE;
            return SEC_30;
        }
    }

    private final BlockPos pos;
    private Mode mode = Mode.SEC_30;
    private boolean isRunning = false;
    private boolean isFinished = false;
    private int elapsedTicks = 0;
    private int totalItemsCounted = 0;

    // Entity deduplication so floating items are counted only once
    private final Set<Integer> countedEntityIds = new HashSet<>();
    // Sliding window of timestamps for rate calculation
    private final List<Long> itemTimestamps = new ArrayList<>();

    public CounterSign(BlockPos pos) {
        this.pos = pos;
        this.isRunning = true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        restart();
    }

    public void cycleMode(Player player) {
        this.mode = this.mode.next();
        restart();
        if (player != null) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            player.sendSystemMessage(Component.literal("§6§l[Item Counter] §eMode switched to: §b§l" + mode.displayName));
        }
    }

    public void restart() {
        this.isRunning = true;
        this.isFinished = false;
        this.elapsedTicks = 0;
        this.totalItemsCounted = 0;
        this.countedEntityIds.clear();
        this.itemTimestamps.clear();
    }

    public void tick(Level level, Player localPlayer) {
        if (!isRunning) return;

        elapsedTicks++;
        long now = System.currentTimeMillis();

        // 1. Purge timestamps older than 30 seconds for live rate calculations
        itemTimestamps.removeIf(time -> (now - time) > 30000L);

        // 2. Scan entities inside the detection bounding box (extends 1.8 blocks horizontally and 1.2 blocks vertically)
        AABB box = new AABB(pos).inflate(1.8, 1.2, 1.8);
        List<Entity> entities = level.getEntities((Entity) null, box, entity -> entity instanceof ItemEntity || entity.isAlive());

        for (Entity entity : entities) {
            if (!countedEntityIds.contains(entity.getId())) {
                countedEntityIds.add(entity.getId());

                int count = 1;
                if (entity instanceof ItemEntity itemEntity) {
                    count = Math.max(1, itemEntity.getItem().getCount());
                }

                totalItemsCounted += count;
                for (int c = 0; c < count; c++) {
                    itemTimestamps.add(now);
                }
            }
        }

        // 3. Check timer expiration for timed modes (10s, 30s, 60s)
        if (mode.durationSeconds > 0) {
            int maxTicks = mode.durationSeconds * 20;
            if (elapsedTicks >= maxTicks) {
                finishTimer(localPlayer);
            }
        }
    }

    private void finishTimer(Player player) {
        this.isRunning = false;
        this.isFinished = true;

        if (player != null) {
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.4f);
            double rateMin = getRatePerMinute();
            player.sendSystemMessage(Component.literal(
                    "§6§l[Item Counter] §a§l✔ " + mode.displayName + " Test Complete! §e§l"
                            + totalItemsCounted + " items §7counted (§b~" + String.format("%.0f", rateMin) + " items/min§7) at " + pos.toShortString()
            ));
        }
    }

    public double getRatePerSecond() {
        if (mode == Mode.LIVE) {
            if (itemTimestamps.isEmpty()) return 0.0;
            long windowMs = Math.min(30000L, Math.max(1000L, elapsedTicks * 50L));
            return (itemTimestamps.size() * 1000.0) / windowMs;
        } else {
            int secondsPassed = Math.max(1, elapsedTicks / 20);
            return (double) totalItemsCounted / secondsPassed;
        }
    }

    public double getRatePerMinute() {
        return getRatePerSecond() * 60.0;
    }

    public int getRemainingSeconds() {
        if (mode.durationSeconds <= 0) return 0;
        int remainingTicks = Math.max(0, (mode.durationSeconds * 20) - elapsedTicks);
        return (int) Math.ceil(remainingTicks / 20.0);
    }

    public int getTotalItemsCounted() {
        return totalItemsCounted;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isFinished() {
        return isFinished;
    }
}
