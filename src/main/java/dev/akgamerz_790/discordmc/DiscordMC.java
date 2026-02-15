package dev.akgamerz_790.discordmc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordMC implements ClientModInitializer {
    public static final String MOD_ID = "[DiscordMC]";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final DiscordPresenceService presenceService = new DiscordPresenceService();

    @Override
    public void onInitializeClient() {
        DiscordMCConfig.load();
        DiscordMCCommand.register(presenceService);

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> presenceService.start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> presenceService.stop());
        ClientTickEvents.END_CLIENT_TICK.register(presenceService::onTick);

        LOGGER.info("DiscordMC initialized.");
    }
}
