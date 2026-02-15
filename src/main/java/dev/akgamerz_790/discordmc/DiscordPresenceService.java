package dev.akgamerz_790.discordmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordPresenceService {
    private static final Pattern PLAYER_COUNT_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern MODE_PREFIX_PATTERN = Pattern.compile("(?i)^(mode|game|map|server|lobby)\\s*:\\s*");

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
                snapshot.partyId,
                snapshot.partySize,
                snapshot.partyMax,
                snapshot.joinSecret,
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
        String smallImageText,
        String partyId,
        int partySize,
        int partyMax,
        String joinSecret
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
                null,
                null,
                0,
                0,
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
                null,
                null,
                0,
                0,
                null
            );
        }

        private static PresenceSnapshot multiplayer(MinecraftClient client, ServerInfo info, DiscordMCConfig.Data config) {
            String details = null;
            String state = "Playing multiplayer";
            String motd = getMotd(info);
            String resolvedServer = ServerIdentityResolver.resolve(info, motd);
            String mode = getModeFromSidebar(client);
            String modeWithVariant = addTeamVariantIfPresent(client, mode);

            if (config.privateServerMode) {
                state = config.privateServerState;
            } else {
                if (config.showServerName && !resolvedServer.isEmpty()) {
                    state = "Playing on " + resolvedServer;
                }
                if (config.showServerAddress && info != null && info.address != null && !info.address.isBlank()) {
                    state = state + " (" + info.address + ")";
                }
            }

            if (!config.privateServerMode && config.showPlayerCount) {
                String counts = getPlayerCounts(client, info);
                if (!counts.isEmpty()) {
                    state = state + " (" + counts + ")";
                }
            }

            if (!config.privateServerMode && "Hypixel".equalsIgnoreCase(resolvedServer) && !modeWithVariant.isEmpty()) {
                state = state + " | Playing " + modeWithVariant;
            }

            if (!config.privateServerMode) {
                String color = getOwnTeamColor(client);
                if (!mode.isEmpty()) {
                    if ("Hypixel".equalsIgnoreCase(resolvedServer)) {
                        if (!color.isEmpty()) {
                            details = "Team " + color;
                        }
                    } else {
                        details = "Playing " + mode;
                        if (!color.isEmpty()) {
                            details = details + " [" + color + "]";
                        }
                    }
                }
            }

            if (details == null && !config.privateServerMode && config.showMOTD) {
                if (!motd.isEmpty()) {
                    details = motd;
                }
            }

            if (details == null) {
                String fallback = normalizeDetails(config.menuDetails);
                if (fallback != null) {
                    details = fallback;
                } else if (config.showDimension) {
                    String dimension = getDimensionName(client.world.getRegistryKey());
                    details = "In the " + dimension.toLowerCase();
                }
            }

            String smallKey = null;
            String smallText = null;
            if (!config.privateServerMode && config.showServerIcon) {
                smallKey = emptyToNull(ServerIdentityResolver.resolveSmallImageKey(info, config.smallImageFallback));
                smallText = resolvedServer.isEmpty() ? null : resolvedServer;
            }

            String partyId = null;
            int partySize = 0;
            int partyMax = 0;
            String joinSecret = null;
            if (!config.privateServerMode && config.enableJoinInvites && info != null && info.address != null && !info.address.isBlank()) {
                String address = info.address.trim();
                String host = ServerIdentityResolver.extractHost(address);
                partyId = "server:" + (host.isEmpty() ? address.toLowerCase(Locale.ROOT) : host);
                int[] counts = getPlayerCountParts(client, info);
                partySize = counts[0];
                partyMax = counts[1];
                joinSecret = address;
            }

            return new PresenceSnapshot(
                details,
                state,
                pickLargeImageForDimension(client.world.getRegistryKey(), config),
                emptyToNull(config.largeImageText),
                smallKey,
                smallText,
                partyId,
                partySize,
                partyMax,
                joinSecret
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

        private static String getPlayerCounts(MinecraftClient client, ServerInfo info) {
            int[] countParts = getPlayerCountParts(client, info);
            if (countParts[0] > 0 && countParts[1] >= countParts[0]) {
                return countParts[0] + "/" + countParts[1];
            }
            if (countParts[0] > 0) {
                return Integer.toString(countParts[0]);
            }
            return "";
        }

        private static int[] getPlayerCountParts(MinecraftClient client, ServerInfo info) {
            int[] pingCounts = parseCountParts(getFieldString(info, "playerCountLabel"));
            if (pingCounts[0] > 0 && pingCounts[1] > 0) {
                return pingCounts;
            }

            int[] tabCounts = parseCountParts(getTabHeaderFooter(client));
            if (tabCounts[0] > 0 && tabCounts[1] > 0) {
                return tabCounts;
            }

            int online = getOnlineTabCount(client);
            if (online > 0) {
                return new int[]{online, 0};
            }
            return new int[]{0, 0};
        }

        private static int[] parseCountParts(String raw) {
            if (raw == null || raw.isBlank()) {
                return new int[]{0, 0};
            }
            Matcher matcher = PLAYER_COUNT_PATTERN.matcher(raw);
            if (!matcher.find()) {
                return new int[]{0, 0};
            }
            try {
                int current = Integer.parseInt(matcher.group(1));
                int max = Integer.parseInt(matcher.group(2));
                return new int[]{current, max};
            } catch (NumberFormatException ignored) {
                return new int[]{0, 0};
            }
        }

        private static int getOnlineTabCount(MinecraftClient client) {
            Object handler = invokeNoArg(client, "getNetworkHandler");
            if (handler == null) {
                return -1;
            }
            Object playerList = invokeNoArg(handler, "getPlayerList");
            if (playerList instanceof Collection<?> entries) {
                return entries.size();
            }
            return -1;
        }

        private static String getTabHeaderFooter(MinecraftClient client) {
            Object handler = invokeNoArg(client, "getNetworkHandler");
            if (handler == null) {
                return "";
            }

            StringBuilder combined = new StringBuilder();
            String header = textToString(invokeNoArg(handler, "getPlayerListHeader"));
            String footer = textToString(invokeNoArg(handler, "getPlayerListFooter"));
            if (!header.isEmpty()) {
                combined.append(header).append(' ');
            }
            if (!footer.isEmpty()) {
                combined.append(footer);
            }
            return clean(combined.toString());
        }

        private static String getModeFromSidebar(MinecraftClient client) {
            if (client.world == null) {
                return "";
            }

            Object scoreboard = invokeNoArg(client.world, "getScoreboard");
            if (scoreboard == null) {
                return "";
            }

            Object sidebarSlot = getSidebarSlotConstant();
            Object objective = null;
            if (sidebarSlot != null) {
                objective = invoke(scoreboard, "getObjectiveForSlot", new Class<?>[]{sidebarSlot.getClass()}, new Object[]{sidebarSlot});
            }
            if (objective == null) {
                objective = invoke(scoreboard, "getObjectiveForSlot", new Class<?>[]{int.class}, new Object[]{1});
            }
            if (objective == null) {
                return "";
            }

            String title = clean(textToString(invokeNoArg(objective, "getDisplayName")));
            if (title.isEmpty()) {
                return "";
            }
            title = MODE_PREFIX_PATTERN.matcher(title).replaceFirst("");
            title = title.replace('_', ' ');
            return title;
        }

        private static String addTeamVariantIfPresent(MinecraftClient client, String mode) {
            String cleanedMode = clean(mode);
            if (cleanedMode.isEmpty()) {
                return "";
            }

            String lower = cleanedMode.toLowerCase(Locale.ROOT);
            if (!lower.contains("bedwars") && !lower.contains("bed wars")) {
                return cleanedMode;
            }

            String variant = getTeamVariant(client);
            if (variant.isEmpty()) {
                return cleanedMode;
            }
            return cleanedMode + " " + variant;
        }

        private static String getTeamVariant(MinecraftClient client) {
            if (client.world == null) {
                return "";
            }
            Object scoreboard = invokeNoArg(client.world, "getScoreboard");
            if (scoreboard == null) {
                return "";
            }

            Object teamsObj = invokeNoArg(scoreboard, "getTeams");
            if (!(teamsObj instanceof Iterable<?> iterable)) {
                return "";
            }

            List<Integer> teamSizes = new ArrayList<>();
            for (Object team : iterable) {
                if (team == null || !isColoredTeam(team)) {
                    continue;
                }
                int size = getTeamPlayerCount(team);
                if (size <= 0) {
                    continue;
                }
                teamSizes.add(size);
            }

            if (teamSizes.size() < 2) {
                return "";
            }

            StringJoiner joiner = new StringJoiner("v");
            for (Integer size : teamSizes) {
                joiner.add(Integer.toString(size));
            }
            return joiner.toString();
        }

        private static boolean isColoredTeam(Object team) {
            Object color = invokeNoArg(team, "getColor");
            if (color == null) {
                return false;
            }
            String name = color.toString().trim().toUpperCase(Locale.ROOT);
            return !name.isEmpty() && !"RESET".equals(name);
        }

        private static int getTeamPlayerCount(Object team) {
            Object players = invokeNoArg(team, "getPlayerList");
            if (players instanceof Collection<?> entries) {
                return entries.size();
            }
            if (players instanceof Iterable<?> iterable) {
                int count = 0;
                for (Object ignored : iterable) {
                    count++;
                }
                return count;
            }
            return -1;
        }

        private static Object getSidebarSlotConstant() {
            try {
                Class<?> slotClass = Class.forName("net.minecraft.scoreboard.ScoreboardDisplaySlot");
                Field sidebarField = slotClass.getField("SIDEBAR");
                return sidebarField.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static String getOwnTeamColor(MinecraftClient client) {
            if (client.player == null) {
                return "";
            }

            Object team = invokeNoArg(client.player, "getScoreboardTeam");
            if (team == null) {
                team = invokeNoArg(client.player, "getTeam");
            }
            if (team == null) {
                return "";
            }

            Object color = invokeNoArg(team, "getColor");
            if (color == null) {
                return "";
            }

            String colorName = color.toString().trim();
            if (colorName.isEmpty()) {
                return "";
            }
            return colorName.toUpperCase(Locale.ROOT);
        }

        private static String getMotd(ServerInfo info) {
            if (info == null) {
                return "";
            }
            String raw = getFieldString(info, "label");
            return clean(raw);
        }

        private static String getFieldString(ServerInfo info, String fieldName) {
            if (info == null) {
                return "";
            }
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

        private static Object invokeNoArg(Object target, String methodName) {
            if (target == null) {
                return null;
            }
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
            if (target == null) {
                return null;
            }
            try {
                Method method = target.getClass().getMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static String textToString(Object textObj) {
            if (textObj instanceof Text text) {
                return text.getString();
            }
            return textObj == null ? "" : textObj.toString();
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
