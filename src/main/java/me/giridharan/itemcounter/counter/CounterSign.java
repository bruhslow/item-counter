package me.giridharan.itemcounter.counter;

import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.SignConfigData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class CounterSign {

    public record CountRecord(long timeMs, int count, double money) {}

    public enum Mode {
        LIVE(0, "Live Rate"),
        SEC_10(10, "10s"),
        SEC_30(30, "30s"),
        SEC_60(60, "60s");

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
            if (str == null) return LIVE;
            String lower = str.toLowerCase().trim();
            if (lower.contains("10")) return SEC_10;
            if (lower.contains("60") || lower.contains("1m")) return SEC_60;
            if (lower.contains("30")) return SEC_30;
            return LIVE;
        }
    }

    private final BlockPos pos;
    private Direction frontDirection = Direction.NORTH;
    private boolean isBackSide = false;

    private Mode mode = Mode.LIVE;
    private int customDurationSeconds = 0; // If non-zero, overrides mode.durationSeconds
    private boolean isRunning = true;
    private boolean isFinished = false;
    private int elapsedTicks = 0;
    private int totalItemsCounted = 0;
    private double totalMoneyValue = 0.0;

    // Entity deduplication so passing items are counted only once
    private final Set<Integer> countedEntityIds = new HashSet<>();
    // Sliding window of records for rate calculation
    private final List<CountRecord> itemRecords = new ArrayList<>();
    // Item counts by ID
    private final Map<String, Integer> itemCounts = new LinkedHashMap<>();

    public CounterSign(BlockPos pos) {
        this.pos = pos;
        this.isRunning = true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFrontDirection() {
        return frontDirection;
    }

    public void setFacing(Direction facing, boolean isBackSide) {
        this.frontDirection = facing;
        this.isBackSide = isBackSide;
    }

    public Direction getActiveDetectionDirection() {
        return isBackSide ? frontDirection.getOpposite() : frontDirection;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        this.customDurationSeconds = 0;
        restart();
    }

    public void setCustomDuration(int seconds) {
        this.customDurationSeconds = seconds;
        restart();
    }

    public int getTargetDurationSeconds() {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        SignConfigData sc = config.getSignConfig(pos.getX(), pos.getY(), pos.getZ());
        if (sc != null) {
            return sc.testDurationSeconds;
        }
        if (customDurationSeconds > 0) return customDurationSeconds;
        return mode.durationSeconds;
    }

    public String getDisplayName() {
        int sec = getTargetDurationSeconds();
        if (sec <= 0) return "Live Rate";
        if (sec % 60 == 0) return (sec / 60) + "m";
        return sec + "s";
    }

    public String getSignCustomName() {
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        SignConfigData data = config.getSignConfig(pos.getX(), pos.getY(), pos.getZ());
        if (data != null && data.customName != null && !data.customName.isBlank()) {
            return data.customName.trim();
        }
        if (config.showCoordinatesInChat) {
            return "Sign (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        } else {
            return "Sign Counter";
        }
    }

    public void cycleMode(Player player) {
        this.customDurationSeconds = 0;
        this.mode = this.mode.next();
        ItemCounterConfig config = ItemCounterConfig.getInstance();
        SignConfigData sc = config.getSignConfig(pos.getX(), pos.getY(), pos.getZ());
        if (sc != null) {
            sc.testDurationSeconds = this.mode.durationSeconds;
            config.save();
        }
        restart();
        if (player != null) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            player.sendSystemMessage(Component.literal("§6§l[Item Counter] §eMode switched to: §b§l" + getDisplayName()));
        }
    }

    public void restart() {
        this.isRunning = true;
        this.isFinished = false;
        this.elapsedTicks = 0;
        this.totalItemsCounted = 0;
        this.totalMoneyValue = 0.0;
        this.countedEntityIds.clear();
        this.itemRecords.clear();
        this.itemCounts.clear();
    }

    public void tick(Level level, Player localPlayer) {
        // Update sign facing if needed
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SignBlock signBlock) {
            float yRot = signBlock.getYRotationDegrees(state);
            this.frontDirection = Direction.fromYRot(yRot);
        }

        if (!isRunning) return;

        elapsedTicks++;
        long now = System.currentTimeMillis();

        // 1. Purge records older than 30 seconds for live sliding rate calculation
        itemRecords.removeIf(record -> (now - record.timeMs()) > 30000L);

        // 2. Multi-Zone Stream Detection Box:
        // Captures items flowing directly in front of the sign face AND items passing through the sign block itself (water gate)
        Direction targetDir = getActiveDetectionDirection();
        BlockPos frontPos = pos.relative(targetDir);

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        boolean strict = config.strictInFrontOnly;
        AABB box;
        if (strict) {
            double minX = Math.min(pos.getX(), frontPos.getX()) - 0.2;
            double maxX = Math.max(pos.getX(), frontPos.getX()) + 1.2;
            double minY = Math.min(pos.getY(), frontPos.getY()) - 0.9;
            double maxY = Math.max(pos.getY(), frontPos.getY()) + 1.4;
            double minZ = Math.min(pos.getZ(), frontPos.getZ()) - 0.2;
            double maxZ = Math.max(pos.getZ(), frontPos.getZ()) + 1.2;
            box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            box = new AABB(pos).inflate(2.0, 1.5, 2.0);
        }

        SignConfigData signConfig = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
        String itemFilter = signConfig.itemFilter != null ? signConfig.itemFilter.trim().toLowerCase() : "";

        // Query entities within the stream box
        List<Entity> candidateEntities = new ArrayList<>(level.getEntities((Entity) null, box, entity -> (entity instanceof ItemEntity || entity.isAlive())));

        // Fallback for client section rendering desync (queries all rendered client entities)
        if (candidateEntities.isEmpty() && level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
            for (Entity e : clientLevel.entitiesForRendering()) {
                if (e != null && (e instanceof ItemEntity || e.isAlive())) {
                    if (box.intersects(e.getBoundingBox())) {
                        candidateEntities.add(e);
                    }
                }
            }
        }

        for (Entity entity : candidateEntities) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            if (!box.intersects(entity.getBoundingBox())) {
                continue;
            }

            int count = 1;
            String rawId = "entity";
            if (entity instanceof ItemEntity itemEntity) {
                count = Math.max(1, itemEntity.getItem().getCount());
                Identifier id = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                rawId = id.getPath();

                // Apply custom item filter if set by the user in GUI
                if (!itemFilter.isEmpty()) {
                    if (!matchesFilter(rawId, id.toString(), itemFilter)) {
                        continue;
                    }
                }
            }

            if (!countedEntityIds.contains(entity.getId())) {
                countedEntityIds.add(entity.getId());

                double unitPrice;
                if (signConfig.useSignCustomPrice) {
                    unitPrice = Math.max(0.0, signConfig.pricePerItem);
                } else {
                    unitPrice = config.getItemPrice(rawId);
                }
                double money = count * unitPrice;

                totalItemsCounted += count;
                totalMoneyValue += money;
                itemCounts.merge(rawId, count, Integer::sum);
                itemRecords.add(new CountRecord(now, count, money));
            }
        }

        // 3. Check timer expiration for timed modes
        int targetSeconds = getTargetDurationSeconds();
        if (targetSeconds > 0) {
            int maxTicks = targetSeconds * 20;
            if (elapsedTicks >= maxTicks) {
                finishTimer(localPlayer);
            }
        }
    }

    private static boolean matchesFilter(String path, String full, String filter) {
        if (filter == null || filter.trim().isEmpty()) return true;
        String[] parts = filter.split("[,;\\s]+");
        for (String part : parts) {
            part = part.trim().toLowerCase();
            if (!part.isEmpty() && (path.toLowerCase().contains(part) || full.toLowerCase().contains(part))) {
                return true;
            }
        }
        return false;
    }

    private void finishTimer(Player player) {
        this.isRunning = false;
        this.isFinished = true;

        if (player != null) {
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.4f);
            double rateMin = getRatePerMinute();
            double moneyRateMin = getMoneyRatePerMinute();
            ItemCounterConfig config = ItemCounterConfig.getInstance();

            String moneyStr = config.showMoneyInHud
                    ? " §7| §aValue: §e§l" + config.currencySymbol + String.format("%,.0f", totalMoneyValue) + " §7(§a~" + config.currencySymbol + String.format("%,.0f", moneyRateMin) + "/min§7)"
                    : "";

            // Respect coordinates privacy: hide unless explicitly confirmed & enabled
            String posStr = config.showCoordinatesInChat
                    ? " §7at §e" + pos.toShortString()
                    : "";

            player.sendSystemMessage(Component.literal(
                    "§6§l[Item Counter] §a§l✔ " + getSignCustomName() + " (" + getDisplayName() + ") Complete! §e§l"
                            + totalItemsCounted + " items §7(§b~" + String.format("%.0f", rateMin) + "/min§7)"
                            + moneyStr
                            + posStr
            ));
        }
    }

    public double getRatePerSecond() {
        if (itemRecords.isEmpty()) return 0.0;
        int targetSeconds = getTargetDurationSeconds();
        if (targetSeconds <= 0) {
            long windowMs = Math.min(30000L, Math.max(1000L, elapsedTicks * 50L));
            long sum = 0;
            for (CountRecord r : itemRecords) sum += r.count();
            return (sum * 1000.0) / windowMs;
        } else {
            int secondsPassed = Math.max(1, Math.min(targetSeconds, elapsedTicks / 20));
            return (double) totalItemsCounted / secondsPassed;
        }
    }

    public double getRatePerMinute() {
        return getRatePerSecond() * 60.0;
    }

    public double getMoneyRatePerMinute() {
        if (itemRecords.isEmpty()) return 0.0;
        int targetSeconds = getTargetDurationSeconds();
        if (targetSeconds <= 0) {
            long windowMs = Math.min(30000L, Math.max(1000L, elapsedTicks * 50L));
            double sum = 0.0;
            for (CountRecord r : itemRecords) sum += r.money();
            return (sum * 1000.0 * 60.0) / windowMs;
        } else {
            int secondsPassed = Math.max(1, Math.min(targetSeconds, elapsedTicks / 20));
            return (totalMoneyValue / secondsPassed) * 60.0;
        }
    }

    public double getTotalMoneyValue() {
        return totalMoneyValue;
    }

    public Map<String, Integer> getItemCounts() {
        return itemCounts;
    }

    public int getRemainingSeconds() {
        int targetSeconds = getTargetDurationSeconds();
        if (targetSeconds <= 0) return 0;
        int remainingTicks = Math.max(0, (targetSeconds * 20) - elapsedTicks);
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
