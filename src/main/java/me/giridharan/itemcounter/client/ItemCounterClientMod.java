package me.giridharan.itemcounter.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.giridharan.itemcounter.ItemCounterMod;
import me.giridharan.itemcounter.command.ItemCounterCommand;
import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.ItemCounterConfigScreen;
import me.giridharan.itemcounter.config.SignConfigData;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import me.giridharan.itemcounter.renderer.CounterHologramRenderer;
import me.giridharan.itemcounter.renderer.ItemCounterHudRenderer;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;

public class ItemCounterClientMod implements ClientModInitializer {

    private static boolean wasOpenKeyPressed = false;
    private static boolean wasResetKeyPressed = false;

    @Override
    public void onInitializeClient() {
        // 1. Initialize configuration & preload classes
        ItemCounterConfig.getInstance();
        try {
            Class.forName(CounterSign.class.getName());
            Class.forName(CounterSign.Mode.class.getName());
            Class.forName(CounterSign.CountRecord.class.getName());
            Class.forName(SignConfigData.class.getName());
            Class.forName(ItemCounterConfigScreen.class.getName());
        } catch (Throwable ignored) {}

        // 2. Register in-world 3D hologram renderer directly above counter signs
        CounterHologramRenderer.register();

        // 3. Register movable Live Watch on-screen HUD box
        ItemCounterHudRenderer.register();

        // 4. Register client tick loop for sign tracking, entity detection, and multi-key shortcuts
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CounterManager.onClientTick(client);

            if (client.player == null) {
                wasOpenKeyPressed = false;
                wasResetKeyPressed = false;
                return;
            }

            ItemCounterConfig config = ItemCounterConfig.getInstance();

            // Check Open GUI Keybind (supports multi-key e.g. Ctrl + O, starts UNBOUND)
            ModifierKeyCode openKey = config.getOpenGuiKey();
            if (!openKey.isUnknown()) {
                boolean pressed = isKeyPressed(openKey);
                if (client.gui.screen() == null) {
                    if (pressed && !wasOpenKeyPressed) {
                        client.gui.setScreen(ItemCounterConfigScreen.create(null));
                    }
                }
                wasOpenKeyPressed = pressed;
            } else {
                wasOpenKeyPressed = false;
            }

            // Check Quick Reset Keybind (optional, starts UNBOUND)
            ModifierKeyCode resetKey = config.getResetCounterKey();
            if (!resetKey.isUnknown()) {
                boolean pressed = isKeyPressed(resetKey);
                if (client.gui.screen() == null) {
                    if (pressed && !wasResetKeyPressed) {
                        handleQuickReset(client);
                    }
                }
                wasResetKeyPressed = pressed;
            } else {
                wasResetKeyPressed = false;
            }
        });

        // 5. Register Right-Click on Sign interaction
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
                boolean handled = CounterManager.onRightClickSign(level, hitResult.getBlockPos(), player, player.isShiftKeyDown());
                if (handled) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // 6. Register client commands (/counter and /itemcounter)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ItemCounterCommand.register(dispatcher);
        });

        // 7. Clear active counters when disconnecting or switching servers
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CounterManager.clearAll();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CounterManager.clearAll();
        });

        ItemCounterMod.LOGGER.info("[Item Counter] Client initialized! Live Watch HUD, custom keybinds, strict flow detection, and 3D hologram active for Fabric 26.2.");
    }

    private static boolean isKeyPressed(ModifierKeyCode key) {
        if (key == null || key.isUnknown()) return false;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return key.matchesCurrentMouse();
        } else {
            return key.matchesCurrentKey();
        }
    }

    private static void handleQuickReset(Minecraft client) {
        if (client.player == null) return;
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) return;

        BlockPos playerPos = client.player.blockPosition();
        BlockPos closestPos = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : counters.keySet()) {
            double d = pos.distSqr(playerPos);
            if (d < closestDist) {
                closestDist = d;
                closestPos = pos;
            }
        }

        if (closestPos != null && closestDist <= 32 * 32) {
            CounterSign c = counters.get(closestPos);
            if (c != null) {
                c.restart();
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f);
                String posStr = ItemCounterConfig.getInstance().showCoordinatesInChat ? (" (" + closestPos.toShortString() + ")") : "";
                client.player.sendSystemMessage(Component.literal("§6§l[Item Counter] §aRestarted " + c.getSignCustomName() + posStr + "!"));
            }
        }
    }
}
