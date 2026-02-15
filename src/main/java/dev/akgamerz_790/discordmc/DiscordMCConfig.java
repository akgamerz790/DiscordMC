package dev.akgamerz_790.discordmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiscordMCConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("discordmc.json");

    private static Data data = new Data();

    private DiscordMCConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            data = loaded == null ? new Data() : loaded;
        } catch (IOException e) {
            DiscordMC.LOGGER.error("Failed to load config, using defaults.", e);
            data = new Data();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            DiscordMC.LOGGER.error("Failed to save config.", e);
        }
    }

    public static Data get() {
        return data;
    }

    public static final class Data {
        public boolean enabled = true;
        public String applicationId = "1472653254188859422";
        public boolean showDimension = true;
        public boolean showServerName = true;
        public boolean showServerAddress = false;
        public boolean showPlayerCount = true;
        public boolean showMOTD = true;
        public boolean showServerIcon = true;
        public boolean privateServerMode = false;
        public String privateServerState = "Playing on a Private server";
        public String menuDetails = "In Minecraft";
        public String singleplayerState = "Playing singleplayer";
        public String largeImageOverworld = "overworld";
        public String largeImageNether = "nether";
        public String largeImageEnd = "end";
        public String largeImageMenu = "minecraft";
        public String largeImageText = "Minecraft";
        public String smallImageFallback = "server";
        public int updateIntervalSeconds = 5;
    }
}
