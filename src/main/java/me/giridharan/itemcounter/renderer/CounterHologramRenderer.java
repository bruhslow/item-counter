package me.giridharan.itemcounter.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.SignConfigData;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class CounterHologramRenderer {

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            ItemCounterConfig config = ItemCounterConfig.getInstance();
            if (!config.showHologram) {
                return;
            }

            LevelRenderState levelState = context.levelState();
            if (levelState == null || levelState.cameraRenderState == null) {
                return;
            }

            CameraRenderState camera = levelState.cameraRenderState;
            if (camera.pos == null) {
                return;
            }

            SubmitNodeCollector collector = context.submitNodeCollector();
            PoseStack poseStack = context.poseStack();
            Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
            if (counters.isEmpty()) return;

            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.level == null) return;

            Vec3 camPos = camera.pos;

            for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
                BlockPos pos = entry.getKey();
                CounterSign sign = entry.getValue();

                // If sign was physically broken or removed, immediately stop rendering hologram
                net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                if (state.isAir() || !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) {
                    counters.remove(pos);
                    config.removeSignConfig(pos.getX(), pos.getY(), pos.getZ());
                    continue;
                }

                SignConfigData sc = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
                if (!sc.showHologram) {
                    continue; // Hologram disabled for this particular sign board
                }

                double heightOffset = sc.getEffectiveHeight(config.hologramHeight);

                double baseX = pos.getX() + 0.5;
                double baseY = pos.getY() + heightOffset;
                double baseZ = pos.getZ() + 0.5;

                double distSq = camPos.distanceToSqr(baseX, baseY, baseZ);
                if (distSq > 36.0 * 36.0) {
                    continue; // Skip rendering if further than 36 blocks away
                }

                renderHologram(collector, poseStack, camera, camPos, baseX, baseY, baseZ, sign, config, sc);
            }
        });
    }

    private static void renderHologram(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera, Vec3 camPos, double baseX, double baseY, double baseZ, CounterSign sign, ItemCounterConfig config, SignConfigData sc) {
        poseStack.pushPose();

        // Translate poseStack to sign position + custom vertical height relative to camera origin
        poseStack.translate(baseX - camPos.x, baseY - camPos.y, baseZ - camPos.z);

        // Apply custom hologram text scale for this sign board
        float scale = (float) Math.max(0.4, Math.min(3.0, sc.getEffectiveScale(config.hologramScale)));
        poseStack.scale(scale, scale, scale);

        // Line 1: Header with custom sign name and single clean mode badge
        String signName = sign.getSignCustomName();
        String headerTitle;
        if (sign.getTargetDurationSeconds() <= 0) {
            headerTitle = "§6§l⚡ " + signName + " §a● [LIVE RATE]";
        } else if (sign.isFinished()) {
            headerTitle = "§6§l⚡ " + signName + " §6★ [DONE - " + sign.getDisplayName() + "]";
        } else {
            headerTitle = "§6§l⚡ " + signName + " §e⏳ [" + sign.getDisplayName() + "]";
        }
        Component line1 = Component.literal(headerTitle);

        // Line 2: Items / Entities Passed (starts at 0, climbs as items pass)
        int total = sign.getTotalItemsCounted();
        double rateMin = sign.getRatePerMinute();
        String rateText = (total == 0) ? "§7(Waiting...)" : ("§7(§b~" + String.format("%.0f", rateMin) + "/m§7)");
        Component line2 = Component.literal("§fEntity Passed: §e§l" + total + " " + rateText);

        // Line 3: Money Made if enabled for this sign board (starts at $0, climbs as items pass)
        Component line3;
        if (sc.showMoneyInHologram) {
            double totalMoney = sign.getTotalMoneyValue();
            double moneyRateMin = sign.getMoneyRatePerMinute();
            String moneyRateText = (total == 0) ? "" : (" §7(§a~" + config.currencySymbol + String.format("%,.0f", moneyRateMin) + "/m§7)");
            line3 = Component.literal("§aMoney Made: §e§l" + config.currencySymbol + String.format("%,.0f", totalMoney) + moneyRateText);
        } else {
            double rateSec = sign.getRatePerSecond();
            line3 = Component.literal("§bSpeed: §a§l~" + String.format("%.0f", rateMin) + " §7items/min §8(" + String.format("%.1f", rateSec) + "/s)");
        }

        // Line 4: Quick interaction guide / countdown remaining
        Component line4;
        if (sign.getTargetDurationSeconds() <= 0) {
            line4 = Component.literal("§7Right-click: reset | Shift+Right-click: mode");
        } else if (sign.isFinished()) {
            line4 = Component.literal("§6★ Test Finished! §8| §7Right-click: restart");
        } else {
            line4 = Component.literal("§a⏳ " + sign.getRemainingSeconds() + "s remaining §8| §7Right-click: reset");
        }

        // Submitting billboard nametags spaced along Y with seeThrough=true
        collector.submitNameTag(poseStack, new Vec3(0, 0.75, 0), 0, line1, true, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(0, 0.50, 0), 0, line2, true, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(0, 0.25, 0), 0, line3, true, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(0, 0.00, 0), 0, line4, true, 0xF000F0, camera);

        poseStack.popPose();
    }
}
