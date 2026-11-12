package me.giridharan.itemcounter.client;

import me.giridharan.itemcounter.ItemCounterMod;
import me.giridharan.itemcounter.command.ItemCounterCommand;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.renderer.CounterHologramRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public class ItemCounterClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Register in-world 3D hologram renderer above counter signs
        CounterHologramRenderer.register();

        // 2. Register tick loop to scan entities and update sign counters
        ClientTickEvents.END_CLIENT_TICK.register(CounterManager::onClientTick);

        // 3. Register Right-Click on Sign interaction
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
                boolean handled = CounterManager.onRightClickSign(level, hitResult.getBlockPos(), player, player.isShiftKeyDown());
                if (handled) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // 4. Register client commands (/counter and /itemcounter)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ItemCounterCommand.register(dispatcher);
        });

        ItemCounterMod.LOGGER.info("[Item Counter] Client initialized with 3D Hologram display for Minecraft 26.2!");
    }
}
