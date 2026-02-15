package dev.akgamerz_790.discordmc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DiscordMCCommand {
    private DiscordMCCommand() {
    }

    public static void register(DiscordPresenceService service) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            String[] roots = {"discordmc", "dcrpc", "drpc", "discordrpc", "rpc", "discordpresence"};
            for (String root : roots) {
                dispatcher.register(buildRoot(root, service));
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRoot(String name, DiscordPresenceService service) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal(name)
            .executes(ctx -> toggleEnabled(ctx.getSource(), service))
            .then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> setEnabled(ctx.getSource(), service, BoolArgumentType.getBool(ctx, "value"))));

        root.then(enabledNode("enabled", service));
        root.then(enabledNode("enable", service));
        root.then(enabledNode("en", service));
        root.then(enabledNode("toggle", service));

        root.then(privateNode("private"));
        root.then(privateNode("pvt"));
        root.then(privateNode("priv"));

        root.then(privateStateNode("privateState"));
        root.then(privateStateNode("pvtState"));
        root.then(privateStateNode("pstate"));

        root.then(booleanSettingNode("showDimension", "showDimension"));
        root.then(booleanSettingNode("showDimension", "dimension"));
        root.then(booleanSettingNode("showDimension", "dim"));

        root.then(booleanSettingNode("showServerName", "showServerName"));
        root.then(booleanSettingNode("showServerName", "serverName"));
        root.then(booleanSettingNode("showServerName", "sname"));

        root.then(booleanSettingNode("showServerAddress", "showServerAddress"));
        root.then(booleanSettingNode("showServerAddress", "serverAddress"));
        root.then(booleanSettingNode("showServerAddress", "saddr"));

        root.then(booleanSettingNode("showPlayerCount", "showPlayerCount"));
        root.then(booleanSettingNode("showPlayerCount", "playerCount"));
        root.then(booleanSettingNode("showPlayerCount", "pcount"));

        root.then(booleanSettingNode("showMOTD", "showMOTD"));
        root.then(booleanSettingNode("showMOTD", "motd"));

        root.then(booleanSettingNode("showServerIcon", "showServerIcon"));
        root.then(booleanSettingNode("showServerIcon", "icon"));

        root.then(intervalNode("interval"));
        root.then(intervalNode("int"));
        root.then(intervalNode("rate"));

        root.then(appIdNode("appId", service));
        root.then(appIdNode("appid", service));
        root.then(appIdNode("clientid", service));

        root.then(imagesNode("images"));
        root.then(imagesNode("image"));
        root.then(imagesNode("img"));

        root.then(reloadNode("reload", service));
        root.then(reloadNode("r", service));
        root.then(reloadNode("reloadcfg", service));

        root.then(statusNode("status"));
        root.then(statusNode("stats"));
        root.then(statusNode("s"));

        return root;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> enabledNode(String name, DiscordPresenceService service) {
        return literal(name)
            .executes(ctx -> toggleEnabled(ctx.getSource(), service))
            .then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> setEnabled(ctx.getSource(), service, BoolArgumentType.getBool(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> privateNode(String name) {
        return literal(name)
            .executes(ctx -> togglePrivate(ctx.getSource()))
            .then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> setPrivate(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> privateStateNode(String name) {
        return literal(name)
            .then(argument("text", StringArgumentType.greedyString())
                .executes(ctx -> {
                    DiscordMCConfig.get().privateServerState = StringArgumentType.getString(ctx, "text");
                    DiscordMCConfig.save();
                    return feedback(ctx.getSource(), "Private state updated.");
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> booleanSettingNode(String key, String name) {
        return literal(name)
            .executes(ctx -> toggleKey(ctx.getSource(), key))
            .then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> setKey(ctx.getSource(), key, BoolArgumentType.getBool(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> intervalNode(String name) {
        return literal(name)
            .then(argument("seconds", IntegerArgumentType.integer(1, 60))
                .executes(ctx -> {
                    DiscordMCConfig.get().updateIntervalSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                    DiscordMCConfig.save();
                    return feedback(ctx.getSource(), "Update interval set to " + DiscordMCConfig.get().updateIntervalSeconds + "s");
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> appIdNode(String name, DiscordPresenceService service) {
        return literal(name)
            .then(argument("id", StringArgumentType.word())
                .executes(ctx -> {
                    DiscordMCConfig.get().applicationId = StringArgumentType.getString(ctx, "id");
                    DiscordMCConfig.save();
                    service.restart();
                    return feedback(ctx.getSource(), "Discord application id updated.");
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> imagesNode(String name) {
        return literal(name)
            .then(imageKeyNode("overworld", "largeImageOverworld", "Overworld image key updated."))
            .then(imageKeyNode("ow", "largeImageOverworld", "Overworld image key updated."))
            .then(imageKeyNode("nether", "largeImageNether", "Nether image key updated."))
            .then(imageKeyNode("n", "largeImageNether", "Nether image key updated."))
            .then(imageKeyNode("end", "largeImageEnd", "End image key updated."))
            .then(imageKeyNode("e", "largeImageEnd", "End image key updated."))
            .then(imageKeyNode("small", "smallImageFallback", "Small image key updated."))
            .then(imageKeyNode("s", "smallImageFallback", "Small image key updated."));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> imageKeyNode(String name, String key, String success) {
        return literal(name)
            .then(argument("value", StringArgumentType.word())
                .executes(ctx -> {
                    String value = StringArgumentType.getString(ctx, "value");
                    DiscordMCConfig.Data c = DiscordMCConfig.get();
                    switch (key) {
                        case "largeImageOverworld" -> c.largeImageOverworld = value;
                        case "largeImageNether" -> c.largeImageNether = value;
                        case "largeImageEnd" -> c.largeImageEnd = value;
                        case "smallImageFallback" -> c.smallImageFallback = value;
                        default -> {
                            return feedback(ctx.getSource(), "Unknown image key.");
                        }
                    }
                    DiscordMCConfig.save();
                    return feedback(ctx.getSource(), success);
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> reloadNode(String name, DiscordPresenceService service) {
        return literal(name)
            .executes(ctx -> {
                DiscordMCConfig.load();
                service.restart();
                return feedback(ctx.getSource(), "Config reloaded.");
            });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> statusNode(String name) {
        return literal(name)
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
            });
    }

    private static int toggleEnabled(FabricClientCommandSource source, DiscordPresenceService service) {
        boolean next = !DiscordMCConfig.get().enabled;
        return setEnabled(source, service, next);
    }

    private static int setEnabled(FabricClientCommandSource source, DiscordPresenceService service, boolean value) {
        DiscordMCConfig.get().enabled = value;
        DiscordMCConfig.save();
        if (value) {
            service.start();
        } else {
            service.stop();
        }
        return feedback(source, "Discord RPC enabled: " + value);
    }

    private static int togglePrivate(FabricClientCommandSource source) {
        boolean next = !DiscordMCConfig.get().privateServerMode;
        return setPrivate(source, next);
    }

    private static int setPrivate(FabricClientCommandSource source, boolean value) {
        DiscordMCConfig.get().privateServerMode = value;
        DiscordMCConfig.save();
        return feedback(source, "Private server mode: " + value);
    }

    private static int toggleKey(FabricClientCommandSource source, String key) {
        DiscordMCConfig.Data c = DiscordMCConfig.get();
        return switch (key) {
            case "showDimension" -> setKey(source, key, !c.showDimension);
            case "showServerName" -> setKey(source, key, !c.showServerName);
            case "showServerAddress" -> setKey(source, key, !c.showServerAddress);
            case "showPlayerCount" -> setKey(source, key, !c.showPlayerCount);
            case "showMOTD" -> setKey(source, key, !c.showMOTD);
            case "showServerIcon" -> setKey(source, key, !c.showServerIcon);
            default -> feedback(source, "Unknown toggle.");
        };
    }

    private static int setKey(FabricClientCommandSource source, String key, boolean value) {
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
