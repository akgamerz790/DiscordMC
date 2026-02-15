package dev.akgamerz_790.discordmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordPresenceService {
    private static final Pattern PLAYER_COUNT_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    private final DiscordIpcClient ipcClient = new DiscordIpcClient();
    private Thread callbacksThread;
    private long startEpochSeconds;
    private long nextUpdateMillis;
    private boolean started;
    private PresenceSnapshot lastSnapshot;

    public void start() {
        if (started) {
            return;
        }

        DiscordMCConfig.Data config = DiscordMCConfig.get();
        if (!config.enabled) {
            return;
        }

        String appId = config.applicationId == null ? "" : config.applicationId.trim();
        if (appId.isEmpty() || appId.equals("000000000000000000")) {
            DiscordMC.LOGGER.warn("Discord app id is not set. Update config/discordmc.json -> applicationId.");
            return;
        }

        if (!ipcClient.connect(appId)) {
            DiscordMC.LOGGER.warn("Could not connect to Discord IPC. Make sure Discord desktop app is running.");
            return;
        }

        startEpochSeconds = System.currentTimeMillis() / 1000L;
        callbacksThread = new Thread(this::runCallbacks, "DiscordMC-RPC");
        callbacksThread.setDaemon(true);
        callbacksThread.start();
        started = true;
        lastSnapshot = null;
        nextUpdateMillis = 0L;
    }

    public void stop() {
        if (!started) {
            return;
        }

        started = false;
        if (callbacksThread != null) {
            callbacksThread.interrupt();
            callbacksThread = null;
        }

        ipcClient.clearActivity();
        ipcClient.close();
    }

    public void restart() {
        stop();
        start();
    }

    public void onTick(MinecraftClient client) {
        if (!started) {
            return;
        }

        DiscordMCConfig.Data config = DiscordMCConfig.get();
        if (!config.enabled) {
            stop();
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextUpdateMillis) {
            return;
        }
        int interval = Math.max(1, config.updateIntervalSeconds);
        nextUpdateMillis = now + (interval * 1000L);

        PresenceSnapshot snapshot = PresenceSnapshot.capture(client, config);
        if (Objects.equals(snapshot, lastSnapshot)) {
            return;
        }
        lastSnapshot = snapshot;

        try {
            ipcClient.setActivity(new DiscordIpcClient.Activity(
                snapshot.details,
                snapshot.state,
                snapshot.largeImageKey,
                snapshot.largeImageText,
                snapshot.smallImageKey,
                snapshot.smallImageText,
                startEpochSeconds
            ));
        } catch (IOException e) {
            DiscordMC.LOGGER.warn("Failed to update Discord presence: {}", e.getMessage());
            stop();
        }
    }

    private void runCallbacks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ipcClient.poll();
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                DiscordMC.LOGGER.warn("Discord IPC disconnected: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private record PresenceSnapshot(
        String details,
        String state,
        String largeImageKey,
        String largeImageText,
        String smallImageKey,
        String smallImageText
    ) {
        static PresenceSnapshot capture(MinecraftClient client, DiscordMCConfig.Data config) {
            if (client.world == null || client.player == null) {
                return menu(config);
            }

            if (client.isInSingleplayer()) {
                return singleplayer(client, config);
            }

            ServerInfo info = client.getCurrentServerEntry();
            return multiplayer(client, info, config);
        }

        private static PresenceSnapshot menu(DiscordMCConfig.Data config) {
            return new PresenceSnapshot(
                normalizeDetails(config.menuDetails),
                "In the menus",
                emptyToNull(config.largeImageMenu),
                emptyToNull(config.largeImageText),
                null,
                null
            );
        }

        private static PresenceSnapshot singleplayer(MinecraftClient client, DiscordMCConfig.Data config) {
            String details = "Singleplayer";
            String state = config.singleplayerState;
            String dimension = getDimensionName(client.world.getRegistryKey());

            if (config.showDimension) {
                state = state + " | " + dimension;
            }

            return new PresenceSnapshot(
                details,
                state,
                pickLargeImageForDimension(client.world.getRegistryKey(), config),
                emptyToNull(config.largeImageText),
                null,
                null
            );
        }

        private static PresenceSnapshot multiplayer(MinecraftClient client, ServerInfo info, DiscordMCConfig.Data config) {
            String details = normalizeDetails(config.menuDetails);
            String state = "Playing multiplayer";

            if (config.privateServerMode) {
                state = config.privateServerState;
            } else {
                if (config.showServerName && info != null && info.name != null && !info.name.isBlank()) {
                    state = "Playing on " + info.name;
                }
                if (config.showServerAddress && info != null && info.address != null && !info.address.isBlank()) {
                    state = state + " (" + info.address + ")";
                }
            }

            String dimension = getDimensionName(client.world.getRegistryKey());
            if (config.showDimension) {
                details = "In the " + dimension.toLowerCase();
            }

            if (!config.privateServerMode && config.showPlayerCount) {
                String counts = getPlayerCounts(info);
                if (!counts.isEmpty()) {
                    state = state + " players(" + counts + ")";
                }
            }

            if (!config.privateServerMode && config.showMOTD) {
                String motd = getMotd(info);
                if (!motd.isEmpty()) {
                    details = motd;
                }
            }

            String smallKey = null;
            String smallText = null;
            if (!config.privateServerMode && config.showServerIcon) {
                smallKey = emptyToNull(config.smallImageFallback);
                smallText = info != null ? info.name : null;
            }

            return new PresenceSnapshot(
                details,
                state,
                pickLargeImageForDimension(client.world.getRegistryKey(), config),
                emptyToNull(config.largeImageText),
                smallKey,
                smallText
            );
        }

        private static String pickLargeImageForDimension(RegistryKey<World> worldKey, DiscordMCConfig.Data config) {
            if (World.NETHER.equals(worldKey)) {
                return emptyToNull(config.largeImageNether);
            }
            if (World.END.equals(worldKey)) {
                return emptyToNull(config.largeImageEnd);
            }
            return emptyToNull(config.largeImageOverworld);
        }

        private static String getPlayerCounts(ServerInfo info) {
            if (info == null) {
                return "";
            }
            String raw = getFieldString(info, "playerCountLabel");
            Matcher matcher = PLAYER_COUNT_PATTERN.matcher(raw);
            if (matcher.find()) {
                return matcher.group(1) + "/" + matcher.group(2);
            }
            return "";
        }

        private static String getMotd(ServerInfo info) {
            if (info == null) {
                return "";
            }
            String raw = getFieldString(info, "label");
            return clean(raw);
        }

        private static String getFieldString(ServerInfo info, String fieldName) {
            try {
                Field field = ServerInfo.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(info);
                if (value instanceof Text text) {
                    return text.getString();
                }
                if (value != null) {
                    return value.toString();
                }
            } catch (Throwable ignored) {
            }
            return "";
        }

        private static String getDimensionName(RegistryKey<World> worldKey) {
            if (World.NETHER.equals(worldKey)) {
                return "Nether";
            }
            if (World.END.equals(worldKey)) {
                return "End";
            }
            return "Overworld";
        }

        private static String clean(String value) {
            if (value == null) {
                return "";
            }
            return value.replaceAll("\\s+", " ").trim();
        }

        private static String normalizeDetails(String value) {
            String cleaned = clean(value);
            if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("Playing Minecraft")) {
                return null;
            }
            return cleaned;
        }

        private static String emptyToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
