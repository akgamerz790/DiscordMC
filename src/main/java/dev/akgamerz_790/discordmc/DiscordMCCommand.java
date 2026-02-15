package dev.akgamerz_790.discordmc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DiscordMCCommand {
    private DiscordMCCommand() {
    }

    public static void register(DiscordPresenceService service) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("discordmc")
                .then(literal("enabled")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            DiscordMCConfig.get().enabled = value;
                            DiscordMCConfig.save();
                            if (value) {
                                service.start();
                            } else {
                                service.stop();
                            }
                            return feedback(ctx.getSource(), "Discord RPC enabled: " + value);
                        })))
                .then(literal("private")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            DiscordMCConfig.get().privateServerMode = BoolArgumentType.getBool(ctx, "value");
                            DiscordMCConfig.save();
                            return feedback(ctx.getSource(), "Private server mode: " + DiscordMCConfig.get().privateServerMode);
                        })))
                .then(literal("privateState")
                    .then(argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            DiscordMCConfig.get().privateServerState = StringArgumentType.getString(ctx, "text");
                            DiscordMCConfig.save();
                            return feedback(ctx.getSource(), "Private state updated.");
                        })))
                .then(literal("showDimension")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showDimension", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("showServerName")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showServerName", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("showServerAddress")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showServerAddress", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("showPlayerCount")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showPlayerCount", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("showMOTD")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showMOTD", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("showServerIcon")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> toggle(ctx.getSource(), "showServerIcon", BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("interval")
                    .then(argument("seconds", IntegerArgumentType.integer(1, 60))
                        .executes(ctx -> {
                            DiscordMCConfig.get().updateIntervalSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            DiscordMCConfig.save();
                            return feedback(ctx.getSource(), "Update interval set to " + DiscordMCConfig.get().updateIntervalSeconds + "s");
                        })))
                .then(literal("appId")
                    .then(argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            DiscordMCConfig.get().applicationId = StringArgumentType.getString(ctx, "id");
                            DiscordMCConfig.save();
                            service.restart();
                            return feedback(ctx.getSource(), "Discord application id updated.");
                        })))
                .then(literal("images")
                    .then(literal("overworld")
                        .then(argument("key", StringArgumentType.word())
                            .executes(ctx -> {
                                DiscordMCConfig.get().largeImageOverworld = StringArgumentType.getString(ctx, "key");
                                DiscordMCConfig.save();
                                return feedback(ctx.getSource(), "Overworld image key updated.");
                            })))
                    .then(literal("nether")
                        .then(argument("key", StringArgumentType.word())
                            .executes(ctx -> {
                                DiscordMCConfig.get().largeImageNether = StringArgumentType.getString(ctx, "key");
                                DiscordMCConfig.save();
                                return feedback(ctx.getSource(), "Nether image key updated.");
                            })))
                    .then(literal("end")
                        .then(argument("key", StringArgumentType.word())
                            .executes(ctx -> {
                                DiscordMCConfig.get().largeImageEnd = StringArgumentType.getString(ctx, "key");
                                DiscordMCConfig.save();
                                return feedback(ctx.getSource(), "End image key updated.");
                            })))
                    .then(literal("small")
                        .then(argument("key", StringArgumentType.word())
                            .executes(ctx -> {
                                DiscordMCConfig.get().smallImageFallback = StringArgumentType.getString(ctx, "key");
                                DiscordMCConfig.save();
                                return feedback(ctx.getSource(), "Small image key updated.");
                            }))))
                .then(literal("reload")
                    .executes(ctx -> {
                        DiscordMCConfig.load();
                        service.restart();
                        return feedback(ctx.getSource(), "Config reloaded.");
                    }))
                .then(literal("status")
                    .executes(ctx -> {
                        DiscordMCConfig.Data c = DiscordMCConfig.get();
                        return feedback(ctx.getSource(),
                            "enabled=" + c.enabled +
                                ", private=" + c.privateServerMode +
                                ", showDimension=" + c.showDimension +
                                ", showServerName=" + c.showServerName +
                                ", showPlayerCount=" + c.showPlayerCount +
                                ", showMOTD=" + c.showMOTD +
                                ", interval=" + c.updateIntervalSeconds + "s");
                    }))
            ));
    }

    private static int toggle(FabricClientCommandSource source, String key, boolean value) {
        DiscordMCConfig.Data c = DiscordMCConfig.get();
        switch (key) {
            case "showDimension" -> c.showDimension = value;
            case "showServerName" -> c.showServerName = value;
            case "showServerAddress" -> c.showServerAddress = value;
            case "showPlayerCount" -> c.showPlayerCount = value;
            case "showMOTD" -> c.showMOTD = value;
            case "showServerIcon" -> c.showServerIcon = value;
            default -> {
                return feedback(source, "Unknown toggle.");
            }
        }
        DiscordMCConfig.save();
        return feedback(source, key + "=" + value);
    }

    private static int feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("[DiscordMC] " + message));
        return 1;
    }
}
