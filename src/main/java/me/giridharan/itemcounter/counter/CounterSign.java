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

    // Entity item counts map to properly handle item stacking & merging in streams
    private final Map<Integer, Integer> countedEntityItemCounts = new HashMap<>();
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
        this.countedEntityItemCounts.clear();
        this.itemRecords.clear();
        this.itemCounts.clear();
    }

    public void tick(Level level, Player localPlayer) {
        // Update sign facing if needed
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SignBlock signBlock) {
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                this.frontDirection = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            } else {
                float yRot = signBlock.getYRotationDegrees(state);
                this.frontDirection = Direction.fromYRot(yRot);
            }
        }

        if (!isRunning) return;

        elapsedTicks++;
        long now = System.currentTimeMillis();

        // 1. Purge records older than 30 seconds for live sliding rate calculation
        itemRecords.removeIf(record -> (now - record.timeMs()) > 30000L);

        // 2. Stream Detection Box:
        // Designed to seamlessly detect both:
        // - Standing sign placed on top of border block facing the stream (Image 1):
        //   detects items in the ice/water stream 1 block in front and 1-2 blocks below (Y - 1, Y - 2 on blue ice).
        // - Wall sign placed sideways on the glass wall (Image 2):
        //   detects items in the ice stream through/beside the glass block.
        // - High-speed ice movement: spans 4 blocks along the stream channel and uses continuous swept trajectory testing.
        Direction targetDir = getActiveDetectionDirection();
        BlockPos frontPos = pos.relative(targetDir);
        BlockPos backPos = pos.relative(targetDir.getOpposite());

        Direction.Axis facingAxis = targetDir.getAxis();
        Direction.Axis perpAxis = (facingAxis == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        boolean strict = config.strictInFrontOnly;
        AABB box;
        if (strict) {
            // Across stream: covers backPos (through glass/wall for Image 2) up to frontPos (stream in front for Image 1)
            double minFacing = Math.min(backPos.get(facingAxis), frontPos.get(facingAxis)) - 0.15;
            double maxFacing = Math.max(backPos.get(facingAxis), frontPos.get(facingAxis)) + 1.15;

            // Along the ice stream channel (perpendicular to sign face): spans 4 blocks so fast items on ice are caught
            double minPerp = pos.get(perpAxis) - 1.5;
            double maxPerp = pos.get(perpAxis) + 2.5;

            // Vertically (Y): covers pos (sign itself) down to Y - 2.5 (detecting water at Y - 1 and blue ice floor at Y - 2)
            double minY = pos.getY() - 2.5;
            double maxY = pos.getY() + 1.15;

            double minX = (facingAxis == Direction.Axis.X) ? minFacing : minPerp;
            double maxX = (facingAxis == Direction.Axis.X) ? maxFacing : maxPerp;
            double minZ = (facingAxis == Direction.Axis.Z) ? minFacing : minPerp;
            double maxZ = (facingAxis == Direction.Axis.Z) ? maxFacing : maxPerp;

            box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            box = new AABB(pos).inflate(3.5, 3.0, 3.5);
        }

        SignConfigData signConfig = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
        String itemFilter = signConfig.itemFilter != null ? signConfig.itemFilter.trim().toLowerCase() : "";

        // Query entities within the stream box using dual lookup (spatial index + client rendering iterator)
        Set<Entity> candidateEntities = new LinkedHashSet<>();
        try {
            AABB queryBox = box.inflate(1.0, 0.5, 1.0);
            List<Entity> list = level.getEntities((Entity) null, queryBox, entity -> entity != null && !entity.isRemoved() && !(entity instanceof Player));
            if (list != null) {
                candidateEntities.addAll(list);
            }
        } catch (Throwable ignored) {}

        if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
            try {
                for (Entity e : clientLevel.entitiesForRendering()) {
                    if (e != null && !e.isRemoved() && !(e instanceof Player)) {
                        if (e instanceof ItemEntity || e.isAlive()) {
                            candidateEntities.add(e);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        for (Entity entity : candidateEntities) {
            if (entity == null || entity.isRemoved() || entity instanceof Player) {
                continue;
            }

            // Check direct bounding box intersection OR swept trajectory intersection (for items moving at high speed on ice)
            AABB entityBox = entity.getBoundingBox();
            boolean intersects = box.intersects(entityBox);
            if (!intersects) {
                AABB sweptBox = entityBox.minmax(new AABB(
                        entity.xOld - 0.2, entity.yOld - 0.2, entity.zOld - 0.2,
                        entity.xOld + 0.2, entity.yOld + 0.2, entity.zOld + 0.2
                ));
                if (box.intersects(sweptBox)) {
                    intersects = true;
                }
            }

            if (!intersects) {
                continue;
            }

            int currentCount = 1;
            String rawId = "entity";
            if (entity instanceof ItemEntity itemEntity) {
                currentCount = Math.max(1, itemEntity.getItem().getCount());
                Identifier id = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                rawId = id.getPath();

                // Apply custom item filter if set by the user in GUI
                if (!itemFilter.isEmpty()) {
                    if (!matchesFilter(rawId, id.toString(), itemFilter)) {
                        continue;
                    }
                }
            }

            int prevCount = countedEntityItemCounts.getOrDefault(entity.getId(), 0);
            int newItems = currentCount - prevCount;

            if (newItems > 0) {
                countedEntityItemCounts.put(entity.getId(), currentCount);

                double unitPrice;
                if (signConfig.useSignCustomPrice) {
                    unitPrice = Math.max(0.0, signConfig.pricePerItem);
                } else {
                    unitPrice = config.getItemPrice(rawId);
                }
                double money = newItems * unitPrice;

                totalItemsCounted += newItems;
                totalMoneyValue += money;
                itemCounts.merge(rawId, newItems, Integer::sum);
                itemRecords.add(new CountRecord(now, newItems, money));
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
