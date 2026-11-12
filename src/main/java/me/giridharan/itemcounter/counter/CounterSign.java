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
            if (lower.contains("30")) return SEC_30;
            return SEC_30;
        }
    }

    private final BlockPos pos;
    private Direction frontDirection = Direction.NORTH;
    private boolean isBackSide = false;

    private Mode mode = Mode.SEC_30;
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
        if (customDurationSeconds > 0) return customDurationSeconds;
        return mode.durationSeconds;
    }

    public String getDisplayName() {
        if (customDurationSeconds > 0) {
            return customDurationSeconds + "s";
        }
        return mode.displayName;
    }

    public String getSignCustomName() {
        SignConfigData data = ItemCounterConfig.getInstance().getSignConfig(pos.getX(), pos.getY(), pos.getZ());
        if (data != null) {
            return data.getDisplayName();
        }
        return "Sign (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    public void cycleMode(Player player) {
        this.customDurationSeconds = 0;
        this.mode = this.mode.next();
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

        // 2. Strict In-Front Directional Detection Box
        Direction targetDir = getActiveDetectionDirection();
        BlockPos frontPos = pos.relative(targetDir);

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        boolean strict = config.strictInFrontOnly;
        AABB box;
        if (strict) {
            box = new AABB(
                    frontPos.getX() + 0.02, frontPos.getY() - 0.75, frontPos.getZ() + 0.02,
                    frontPos.getX() + 0.98, frontPos.getY() + 1.25, frontPos.getZ() + 0.98
            );
        } else {
            box = new AABB(frontPos).inflate(0.5, 0.8, 0.5);
        }

        SignConfigData signConfig = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
        String itemFilter = signConfig.itemFilter != null ? signConfig.itemFilter.trim().toLowerCase() : "";

        List<Entity> entities = level.getEntities((Entity) null, box, entity -> entity instanceof ItemEntity || entity.isAlive());

        for (Entity entity : entities) {
            if (!box.intersects(entity.getBoundingBox())) {
                continue;
            }

            if (!isEntityMoving(entity)) {
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

                double unitPrice = config.getItemPrice(rawId);
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

    private boolean isEntityMoving(Entity entity) {
        Vec3 vel = entity.getDeltaMovement();
        double speedSqr = vel.x * vel.x + vel.y * vel.y + vel.z * vel.z;
        if (speedSqr > 0.00005) {
            return true;
        }
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        return (dx * dx + dy * dy + dz * dz) > 0.00005;
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

            player.sendSystemMessage(Component.literal(
                    "§6§l[Item Counter] §a§l✔ " + getSignCustomName() + " (" + getDisplayName() + ") Complete! §e§l"
                            + totalItemsCounted + " items §7(§b~" + String.format("%.0f", rateMin) + "/min§7)"
                            + moneyStr
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
