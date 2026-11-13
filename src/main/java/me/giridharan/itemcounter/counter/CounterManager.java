package me.giridharan.itemcounter.counter;

import me.giridharan.itemcounter.ItemCounterMod;
import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.SignConfigData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CounterManager {

    private static final Map<BlockPos, CounterSign> activeCounters = new ConcurrentHashMap<>();
    private static int scanCooldown = 0;

    public static Map<BlockPos, CounterSign> getActiveCounters() {
        return activeCounters;
    }

    public static boolean onRightClickSign(Level level, BlockPos pos, Player player, boolean isCrouching) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) {
            return false;
        }

        // A sign ONLY becomes an item counter if "counter" is typed on it!
        boolean frontHas = hasCounterText(sign.getFrontText());
        boolean backHas = hasCounterText(sign.getBackText());

        if (!frontHas && !backHas) {
            // Normal sign without "counter" - do NOT convert!
            return false;
        }

        CounterSign existing = activeCounters.get(pos);
        if (existing != null) {
            if (isCrouching) {
                // Cycle mode: 10s -> 30s -> 60s -> Live
                existing.cycleMode(player);
            } else {
                // Restart current test
                existing.restart();
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
                player.sendSystemMessage(Component.literal("§6§l[Item Counter] §aRestarted §e" + existing.getSignCustomName() + " (" + existing.getDisplayName() + ") §atest!"));
            }
            return true;
        } else {
            // Register this sign as a counter
            CounterSign counter = createCounterFromSign(level, pos, sign, frontHas, backHas);
            activeCounters.put(pos, counter);

            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
            player.sendSystemMessage(Component.literal("§6§l[Item Counter] §aSign converted to §e" + counter.getSignCustomName() + " (" + counter.getDisplayName() + ")§a!"));
            player.sendSystemMessage(Component.literal("§7Right-click: Restart test | Shift + Right-click: Change duration. Open GUI to rename or set item price!"));
            return true;
        }
    }

    public static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        Level level = client.level;
        Player player = client.player;

        // 1. Tick all active counters and validate they still exist and still have "counter" written on them
        Iterator<Map.Entry<BlockPos, CounterSign>> it = activeCounters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, CounterSign> entry = it.next();
            BlockPos pos = entry.getKey();
            CounterSign counter = entry.getValue();

            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !(state.getBlock() instanceof SignBlock)) {
                    // Sign was physically broken/removed
                    it.remove();
                    continue;
                }

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SignBlockEntity sign) {
                    // If text was erased and no longer contains "counter", remove counter
                    boolean frontHas = hasCounterText(sign.getFrontText());
                    boolean backHas = hasCounterText(sign.getBackText());
                    if (!frontHas && !backHas) {
                        it.remove();
                        continue;
                    }
                }
            }

            try {
                counter.tick(level, player);
            } catch (Throwable t) {
                ItemCounterMod.LOGGER.error("[Item Counter] Error during counter tick at " + pos, t);
            }
        }

        // 2. Periodic automatic detection of signs with "counter" text (every 10 ticks / 0.5s)
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = 10;
            try {
                scanNearbySigns(level, player.blockPosition());
            } catch (Throwable t) {
                ItemCounterMod.LOGGER.error("[Item Counter] Error scanning nearby signs", t);
            }
        }
    }

    private static void scanNearbySigns(Level level, BlockPos playerPos) {
        int radius = 24;
        BlockPos min = playerPos.offset(-radius, -12, -radius);
        BlockPos max = playerPos.offset(radius, 12, radius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (activeCounters.containsKey(pos)) continue;

            try {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof SignBlock)) continue;

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SignBlockEntity sign) {
                    boolean frontHas = hasCounterText(sign.getFrontText());
                    boolean backHas = hasCounterText(sign.getBackText());

                    if (frontHas || backHas) {
                        CounterSign counter = createCounterFromSign(level, pos.immutable(), sign, frontHas, backHas);
                        if (counter != null) {
                            activeCounters.put(pos.immutable(), counter);
                        }
                    }
                }
            } catch (Throwable t) {
                ItemCounterMod.LOGGER.error("[Item Counter] Error processing sign at " + pos, t);
            }
        }
    }

    private static CounterSign createCounterFromSign(Level level, BlockPos pos, SignBlockEntity sign, boolean frontHas, boolean backHas) {
        try {
            CounterSign counter = new CounterSign(pos);

            // Auto register / ensure config entry for this sign board
            SignConfigData signConfig = ItemCounterConfig.getInstance().getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());

            // Determine sign front facing
            Direction frontDir = Direction.NORTH;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SignBlock signBlock) {
                float yRot = signBlock.getYRotationDegrees(state);
                frontDir = Direction.fromYRot(yRot);
            }

            boolean isBackSide = (!frontHas && backHas);
            counter.setFacing(frontDir, isBackSide);

            // Parse optional duration (e.g. 10s, 30s, 60s, live, or custom seconds)
            SignText targetText = isBackSide ? sign.getBackText() : sign.getFrontText();
            String fullSignContent = getCombinedText(targetText);

            Integer parsedSeconds = parseDurationSeconds(fullSignContent);
            if (parsedSeconds != null) {
                if (parsedSeconds == 0) {
                    counter.setMode(CounterSign.Mode.LIVE);
                } else if (parsedSeconds == 10) {
                    counter.setMode(CounterSign.Mode.SEC_10);
                } else if (parsedSeconds == 30) {
                    counter.setMode(CounterSign.Mode.SEC_30);
                } else if (parsedSeconds == 60) {
                    counter.setMode(CounterSign.Mode.SEC_60);
                } else {
                    counter.setCustomDuration(parsedSeconds);
                }
                signConfig.testDurationSeconds = parsedSeconds;
            } else {
                int defSec = signConfig.testDurationSeconds;
                if (defSec == 10) counter.setMode(CounterSign.Mode.SEC_10);
                else if (defSec == 30) counter.setMode(CounterSign.Mode.SEC_30);
                else if (defSec == 60) counter.setMode(CounterSign.Mode.SEC_60);
                else if (defSec <= 0) counter.setMode(CounterSign.Mode.LIVE);
                else counter.setCustomDuration(defSec);
            }

            return counter;
        } catch (Throwable t) {
            ItemCounterMod.LOGGER.error("[Item Counter] Failed to create counter sign at " + pos, t);
            return null;
        }
    }

    public static boolean hasCounterText(SignText text) {
        if (text == null) return false;
        for (int i = 0; i < 4; i++) {
            Component msg = text.getMessage(i, false);
            if (msg != null) {
                String line = msg.getString().toLowerCase().trim();
                if (line.contains("counter")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getCombinedText(SignText text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            Component msg = text.getMessage(i, false);
            if (msg != null) {
                sb.append(" ").append(msg.getString());
            }
        }
        return sb.toString().toLowerCase();
    }

    private static Integer parseDurationSeconds(String text) {
        if (text.contains("live") || text.contains("inf") || text.contains("rate")) {
            return 0;
        }
        String[] tokens = text.split("[\\s,:\\[\\]]+");
        for (String token : tokens) {
            token = token.trim();
            if (token.endsWith("s") && token.length() > 1) {
                try {
                    return Integer.parseInt(token.substring(0, token.length() - 1));
                } catch (NumberFormatException ignored) {}
            }
            if (token.endsWith("m") && token.length() > 1) {
                try {
                    return Integer.parseInt(token.substring(0, token.length() - 1)) * 60;
                } catch (NumberFormatException ignored) {}
            }
            if (token.equals("10") || token.equals("30") || token.equals("60")) {
                try {
                    return Integer.parseInt(token);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    public static void clearAll() {
        activeCounters.clear();
    }
}
