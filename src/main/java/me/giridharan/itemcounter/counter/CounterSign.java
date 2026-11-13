package me.giridharan.itemcounter.counter;

import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.SignConfigData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CounterSign {

    public record CountRecord(long timeMs, int count, double money) {}

    public enum Mode {
        LIVE(0, "Live Rate"),
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
            if (lower.contains("60") || lower.contains("1m")) return SEC_60;
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
    private boolean justRestarted = true;

    // Remote / Chunk-loaded simulation support
    private double lastKnownRatePerSec = 0.0;
    private double simulatedItemBuffer = 0.0;
    private boolean isSimulatingRemote = false;

    // Entity item counts map to properly handle item stacking & merging in streams
    private final Map<Integer, Integer> countedEntityItemCounts = new HashMap<>();
    // Entity previous known position map for high-speed trajectory sweeping
    private final Map<Integer, Vec3> lastKnownEntityPositions = new java.util.concurrent.ConcurrentHashMap<>();
    // Sliding window of records for rate calculation
    private final List<CountRecord> itemRecords = new ArrayList<>();
    // Item counts by ID
    private final Map<String, Integer> itemCounts = new LinkedHashMap<>();

    public CounterSign(BlockPos pos) {
        this.pos = pos;
        this.isRunning = true;
        this.justRestarted = true;
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
        return "Counter 1";
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
        this.lastKnownEntityPositions.clear();
        this.itemRecords.clear();
        this.itemCounts.clear();
        this.justRestarted = true;
        this.simulatedItemBuffer = 0.0;
        this.isSimulatingRemote = false;
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

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        SignConfigData signConfig = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());

        boolean isChunkLoaded = level.isLoaded(pos);
        double distSq = (localPlayer != null)
                ? localPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : Double.MAX_VALUE;
        // On multiplayer servers, item entities are only tracked/sent to clients within ~32-48 blocks
        boolean isPlayerNearby = isChunkLoaded && (distSq <= 40.0 * 40.0);

        if (!isPlayerNearby) {
            // Player is away from the farm (remote chunk-loaded farm simulation)
            if (signConfig.remoteSimulation && !isFinished) {
                if (lastKnownRatePerSec <= 0 && signConfig.savedRatePerSec > 0) {
                    lastKnownRatePerSec = signConfig.savedRatePerSec;
                }
                if (lastKnownRatePerSec > 0) {
                    this.isSimulatingRemote = true;
                    simulatedItemBuffer += (lastKnownRatePerSec / 20.0);
                    if (simulatedItemBuffer >= 1.0) {
                        int itemsToAdd = (int) simulatedItemBuffer;
                        simulatedItemBuffer -= itemsToAdd;

                        double unitPrice = signConfig.useSignCustomPrice
                                ? Math.max(0.0, signConfig.pricePerItem)
                                : config.defaultItemPrice;
                        double money = itemsToAdd * unitPrice;

                        totalItemsCounted += itemsToAdd;
                        totalMoneyValue += money;

                        // Maintain sliding window records so rate display remains active
                        itemRecords.add(new CountRecord(now, itemsToAdd, money));
                    }
                } else {
                    this.isSimulatingRemote = false;
                }
            } else {
                this.isSimulatingRemote = false;
            }

            // Check timer expiration for timed modes while away
            int targetSeconds = getTargetDurationSeconds();
            if (targetSeconds > 0) {
                int maxTicks = targetSeconds * 20;
                if (elapsedTicks >= maxTicks) {
                    finishTimer(localPlayer);
                }
            }
            return;
        }

        // When player is nearby, real in-world 3D entity tracking takes over
        this.isSimulatingRemote = false;
        this.simulatedItemBuffer = 0.0;

        // 2. Stream Detection Box:
        // Designed to detect strictly in front of the sign board:
        // - Standing sign placed on top of border block facing the stream (Image 1):
        //   detects items in the ice/water stream 1 block in front and 1-2 blocks below (Y - 1, Y - 2 on blue ice).
        // - Wall sign placed sideways on the glass wall (Image 2):
        //   detects items in the ice stream at the sign block or in front of it.
        // - Perpendicular / Side axis: STRICTLY restricted to that particular 1-block slice aligned with the sign.
        //   Items passing 1 block or 2 blocks to the side are completely ignored!
        // - High-speed ice movement: uses exact swept trajectory testing so items moving fast on ice are not skipped.
        Direction targetDir = getActiveDetectionDirection();
        BlockPos frontPos = pos.relative(targetDir);

        Direction.Axis facingAxis = targetDir.getAxis();
        Direction.Axis perpAxis = (facingAxis == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;

        boolean strict = config.strictInFrontOnly;
        AABB box;
        if (strict) {
            // Across stream (facing axis): covers sign block and out into the stream in front of the sign face (up to 2.8 blocks out)
            double signFacing = pos.get(facingAxis);
            double step = (facingAxis == Direction.Axis.X) ? targetDir.getStepX() : targetDir.getStepZ();
            double minFacing, maxFacing;
            if (step > 0) {
                minFacing = signFacing;
                maxFacing = signFacing + 2.8;
            } else if (step < 0) {
                minFacing = signFacing - 1.8;
                maxFacing = signFacing + 1.0;
            } else {
                minFacing = signFacing - 1.0;
                maxFacing = signFacing + 2.0;
            }

            // Along the stream flow (perpAxis): clean 2.4 blocks centered right in front of the sign (-0.7 to +1.7)
            // Perfectly timed as items pass the sign face, with trajectory raycasting ensuring 100% catch rate on ice!
            double minPerp = pos.get(perpAxis) - 0.7;
            double maxPerp = pos.get(perpAxis) + 1.7;

            // Vertically (Y): covers from Y - 2.5 (detecting water stream and blue ice floor) up to Y + 1.5
            double minY = pos.getY() - 2.5;
            double maxY = pos.getY() + 1.5;

            double minX = (facingAxis == Direction.Axis.X) ? minFacing : minPerp;
            double maxX = (facingAxis == Direction.Axis.X) ? maxFacing : maxPerp;
            double minZ = (facingAxis == Direction.Axis.Z) ? minFacing : minPerp;
            double maxZ = (facingAxis == Direction.Axis.Z) ? maxFacing : maxPerp;

            box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            box = new AABB(pos).inflate(4.5, 3.0, 4.5);
        }

        String itemFilter = signConfig.itemFilter != null ? signConfig.itemFilter.trim().toLowerCase() : "";

        // Query entities within the stream box using dual lookup (spatial index + client rendering iterator)
        Set<Entity> candidateEntities = new LinkedHashSet<>();
        try {
            AABB queryBox = box.inflate(3.0, 1.0, 3.0);
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

            // Only count actual ItemEntities (ignore ambient mobs, spiders, slimes, item frames, etc.)
            if (!(entity instanceof ItemEntity itemEntity)) {
                continue;
            }

            // Continuous high-speed trajectory tracking between ticks
            Vec3 currentPos = entity.position();
            Vec3 prevPos = lastKnownEntityPositions.put(entity.getId(), currentPos);

            AABB entityBox = entity.getBoundingBox();
            boolean intersects = box.intersects(entityBox);

            if (!intersects) {
                // 1. Exact swept bounding box with our recorded previous position
                if (prevPos != null) {
                    AABB prevBox = entityBox.move(prevPos.x - currentPos.x, prevPos.y - currentPos.y, prevPos.z - currentPos.z);
                    if (box.intersects(entityBox.minmax(prevBox))) {
                        intersects = true;
                    }
                }

                // 2. Line segment raycast from prevPos to currentPos through the detection box
                if (!intersects && prevPos != null) {
                    if (box.clip(prevPos, currentPos).isPresent()) {
                        intersects = true;
                    }
                }

                // 3. Swept trajectory with entity velocity vector (deltaMovement)
                if (!intersects) {
                    Vec3 vel = entity.getDeltaMovement();
                    if (vel != null && vel.lengthSqr() > 0.001) {
                        AABB velBox = entityBox.move(-vel.x * 2.0, -vel.y * 2.0, -vel.z * 2.0);
                        if (box.intersects(entityBox.minmax(velBox))) {
                            intersects = true;
                        }
                    }
                }

                // 4. Swept trajectory with vanilla entity xOld / yOld / zOld
                if (!intersects) {
                    double dx = entity.xOld - entity.getX();
                    double dy = entity.yOld - entity.getY();
                    double dz = entity.zOld - entity.getZ();
                    if (dx * dx + dy * dy + dz * dz > 0.001) {
                        AABB oldBox = entityBox.move(dx, dy, dz);
                        if (box.intersects(entityBox.minmax(oldBox))) {
                            intersects = true;
                        }
                    }
                }
            }

            if (!intersects) {
                continue;
            }

            int currentCount = getEffectiveItemCount(itemEntity);
            Identifier id = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
            String rawId = id.getPath();

            // Apply custom item filter if set by the user in GUI
            if (!itemFilter.isEmpty()) {
                if (!matchesFilter(rawId, id.toString(), itemFilter)) {
                    continue;
                }
            }

            int prevCount = countedEntityItemCounts.getOrDefault(entity.getId(), 0);
            int newItems = currentCount - prevCount;

            if (newItems > 0) {
                countedEntityItemCounts.put(entity.getId(), currentCount);

                // If counter was just reset, baseline-track any items already sitting in the stream so counter starts at 0
                if (justRestarted) {
                    continue;
                }

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

        justRestarted = false;

        // Calibrate live flow rate for remote chunk-loaded simulation
        double liveRate = getRatePerSecond();
        if (liveRate > 0) {
            this.lastKnownRatePerSec = liveRate;
            signConfig.savedRatePerSec = liveRate;
        }

        // Periodically purge despawned or collected entities from memory
        if (countedEntityItemCounts.size() > 500) {
            countedEntityItemCounts.keySet().removeIf(id -> level.getEntity(id) == null);
        }
        if (lastKnownEntityPositions.size() > 500) {
            lastKnownEntityPositions.keySet().removeIf(id -> level.getEntity(id) == null);
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

    public void setSavedRatePerSec(double rate) {
        if (rate > 0) {
            this.lastKnownRatePerSec = rate;
        }
    }

    public double getLastKnownRatePerSec() {
        return lastKnownRatePerSec;
    }

    public boolean isRemoteSimulating() {
        return isSimulatingRemote;
    }

    // ==========================================
    // Stacked / Grouped Item Support (up to 6400+)
    // ==========================================

    private static final Pattern X_PATTERN = Pattern.compile("(?i)(?:[xX\u00D7]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*[kKmM]?)\\b|\\b([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*[kKmM]?)\\s*[xX\u00D7])");
    private static final Pattern ENCLOSED_PATTERN = Pattern.compile("[\\(\\[\\{]\\s*(?:x\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*[kKmM]?)(?:\\s*x)?\\s*[\\)\\]\\}]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[-:×*]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*[kKmM]?)\\b");
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("\\b([0-9]+(?:\\.[0-9]+)?)\\s*([kKmM])\\b");
    private static final Pattern LEADING_NUM_PATTERN = Pattern.compile("^\\s*([0-9]+(?:,[0-9]{3})*)\\s+");
    private static final Pattern LARGE_NUM_PATTERN = Pattern.compile("\\b([0-9]+(?:,[0-9]{3})*)\\b");

    public static int getEffectiveItemCount(ItemEntity itemEntity) {
        if (itemEntity == null) return 1;

        int vanillaCount = Math.max(1, itemEntity.getItem().getCount());
        int detectedCount = 0;

        // 1. Check entity custom name (RoseStacker, WildStacker, StackMob, etc.)
        Component customNameComp = itemEntity.getCustomName();
        if (customNameComp != null) {
            String text = customNameComp.getString();
            int parsed = parseCountFromText(text);
            if (parsed > detectedCount) {
                detectedCount = parsed;
            }
        }

        // 2. Check entity display name
        Component displayNameComp = itemEntity.getDisplayName();
        if (displayNameComp != null) {
            String text = displayNameComp.getString();
            int parsed = parseCountFromText(text);
            if (parsed > detectedCount) {
                detectedCount = parsed;
            }
        }

        // 3. Check item hover name / custom name on the ItemStack
        ItemStack stack = itemEntity.getItem();
        if (stack != null && !stack.isEmpty()) {
            Component hoverName = stack.getHoverName();
            if (hoverName != null) {
                String text = hoverName.getString();
                int parsed = parseCountFromText(text);
                if (parsed > detectedCount) {
                    detectedCount = parsed;
                }
            }

            // 4. Check ItemStack CustomData (NBT tags)
            try {
                CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    int nbtCount = parseCountFromTag(tag);
                    if (nbtCount > detectedCount) {
                        detectedCount = nbtCount;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 5. Check Entity scoreboard tags (e.g. "stack:6400", "amount:3400")
        try {
            Set<String> tags = itemEntity.entityTags();
            if (tags != null) {
                for (String tagStr : tags) {
                    int parsed = parseCountFromTagString(tagStr);
                    if (parsed > detectedCount) {
                        detectedCount = parsed;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Math.max(vanillaCount, detectedCount);
    }

    private static int parseNumberToken(String token) {
        if (token == null) return 0;
        String s = token.replace(",", "").trim().toLowerCase();
        try {
            if (s.endsWith("k")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return (int) Math.round(val * 1000.0);
            }
            if (s.endsWith("m")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return (int) Math.round(val * 1000000.0);
            }
            double val = Double.parseDouble(s);
            return (int) Math.round(val);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int parseCountFromText(String text) {
        if (text == null || text.isBlank()) return 0;
        String cleaned = text.replaceAll("[§\u00A7].", " ").replaceAll("&[0-9a-fk-orxA-FK-ORX]", " ").trim();
        if (cleaned.isEmpty()) return 0;

        Matcher m = X_PATTERN.matcher(cleaned);
        if (m.find()) {
            String token = m.group(1) != null ? m.group(1) : m.group(2);
            int val = parseNumberToken(token);
            if (val > 0) return val;
        }

        m = ENCLOSED_PATTERN.matcher(cleaned);
        if (m.find()) {
            int val = parseNumberToken(m.group(1));
            if (val > 0) return val;
        }

        m = SEPARATOR_PATTERN.matcher(cleaned);
        if (m.find()) {
            int val = parseNumberToken(m.group(1));
            if (val > 0) return val;
        }

        m = SUFFIX_PATTERN.matcher(cleaned);
        if (m.find()) {
            int val = parseNumberToken(m.group(1) + m.group(2));
            if (val > 0) return val;
        }

        m = LEADING_NUM_PATTERN.matcher(cleaned);
        if (m.find()) {
            int val = parseNumberToken(m.group(1));
            if (val > 1) return val;
        }

        m = LARGE_NUM_PATTERN.matcher(cleaned);
        while (m.find()) {
            int val = parseNumberToken(m.group(1));
            if (val > 64) return val;
        }

        return 0;
    }

    private static int parseCountFromTag(CompoundTag tag) {
        if (tag == null) return 0;
        String[] commonKeys = {
            "amount", "stack_amount", "stack_size", "count", "stacked",
            "quantity", "total", "size", "item_count", "merged_count"
        };
        for (String key : commonKeys) {
            if (tag.contains(key)) {
                try {
                    int val = tag.getIntOr(key, 0);
                    if (val > 1) return val;
                } catch (Throwable ignored) {}
            }
        }
        for (String key : tag.keySet()) {
            String lower = key.toLowerCase();
            if (lower.contains("stack") || lower.contains("merge")) {
                try {
                    CompoundTag sub = tag.getCompoundOrEmpty(key);
                    if (!sub.isEmpty()) {
                        int subCount = parseCountFromTag(sub);
                        if (subCount > 1) return subCount;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return 0;
    }

    private static int parseCountFromTagString(String tagStr) {
        if (tagStr == null || tagStr.isBlank()) return 0;
        String lower = tagStr.toLowerCase().trim();
        if (lower.contains(":") || lower.contains("=")) {
            String[] parts = lower.split("[:=]", 2);
            if (parts.length == 2) {
                int parsed = parseCountFromText(parts[1].trim());
                if (parsed > 0) return parsed;
            }
        }
        return parseCountFromText(lower);
    }
}
