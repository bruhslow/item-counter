package me.giridharan.itemcounter.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
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

            Vec3 camPos = camera.pos;

            for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
                BlockPos pos = entry.getKey();
                CounterSign sign = entry.getValue();

                double distSq = camPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distSq > 32.0 * 32.0) {
                    continue; // Skip rendering if further than 32 blocks away
                }

                renderHologram(collector, poseStack, camera, pos, sign);
            }
        });
    }

    private static void renderHologram(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera, BlockPos pos, CounterSign sign) {
        double baseX = pos.getX() + 0.5;
        double baseY = pos.getY() + 1.25;
        double baseZ = pos.getZ() + 0.5;

        // Line 1: Header & Mode status
        String modeName = sign.getMode().displayName;
        String headerStatus = sign.isFinished() ? "§a[FINISHED]" : (sign.isRunning() ? "§e[COUNTING]" : "§7[PAUSED]");
        Component line1 = Component.literal("§6§l⚡ ITEM COUNTER ⚡ §b" + modeName + " " + headerStatus);

        // Line 2: Items Counted & Instant Speed
        double rateSec = sign.getRatePerSecond();
        Component line2 = Component.literal("§fPassed: §e§l" + sign.getTotalItemsCounted() + " items §7(" + String.format("%.1f", rateSec) + "/s)");

        // Line 3: Rate per Minute
        double rateMin = sign.getRatePerMinute();
        Component line3 = Component.literal("§bSpeed: §a§l~" + String.format("%.0f", rateMin) + " §7items/min");

        // Line 4: Timer Remaining or Restart prompt
        Component line4;
        if (sign.getMode() == CounterSign.Mode.LIVE) {
            line4 = Component.literal("§a● Live Monitoring §7(Right-click to reset)");
        } else if (sign.isFinished()) {
            line4 = Component.literal("§6★ Test Complete! §7(Right-click to restart)");
        } else {
            line4 = Component.literal("§a⏳ " + sign.getRemainingSeconds() + "s remaining §7(Shift+R-Click to change mode)");
        }

        // Submitting billboard nametags spaced along Y
        collector.submitNameTag(poseStack, new Vec3(baseX, baseY + 0.84, baseZ), 0, line1, false, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(baseX, baseY + 0.56, baseZ), 0, line2, false, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(baseX, baseY + 0.28, baseZ), 0, line3, false, 0xF000F0, camera);
        collector.submitNameTag(poseStack, new Vec3(baseX, baseY + 0.00, baseZ), 0, line4, false, 0xF000F0, camera);
    }
}
