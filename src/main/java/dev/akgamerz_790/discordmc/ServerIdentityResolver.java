package dev.akgamerz_790.discordmc;

import net.minecraft.client.network.ServerInfo;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ServerIdentityResolver {
    private static final Map<String, String> KNOWN_HOSTS = new LinkedHashMap<>();
    private static final Map<String, String> KNOWN_ICON_KEYS = new LinkedHashMap<>();

    static {
        KNOWN_HOSTS.put("hypixel.net", "Hypixel");
        KNOWN_HOSTS.put("minemen.club", "Minemen Club");
        KNOWN_HOSTS.put("cubecraft.net", "CubeCraft");
        KNOWN_HOSTS.put("mineplex.com", "Mineplex");
        KNOWN_HOSTS.put("play.hivemc.com", "The Hive");
        KNOWN_HOSTS.put("hivemc.com", "The Hive");

        // These are Discord application asset keys, not raw server favicon images.
        KNOWN_ICON_KEYS.put("hypixel.net", "hypixel_net");
        KNOWN_ICON_KEYS.put("minemen.club", "minemen");
        KNOWN_ICON_KEYS.put("cubecraft.net", "cubecraft");
        KNOWN_ICON_KEYS.put("mineplex.com", "mineplex");
        KNOWN_ICON_KEYS.put("play.hivemc.com", "hive");
        KNOWN_ICON_KEYS.put("hivemc.com", "hive");
    }

    private ServerIdentityResolver() {
    }

    static String resolve(ServerInfo info, String motd) {
        String fromMotd = fromMotd(motd);
        if (!fromMotd.isEmpty()) {
            return fromMotd;
        }

        String host = extractHost(info == null ? null : info.address);
        if (host.isEmpty()) {
            return "";
        }

        String known = fromKnownHost(host);
        return known.isEmpty() ? host : known;
    }

    static String extractHost(String address) {
        if (address == null) {
            return "";
        }
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // IPv6 with explicit port: [2001:db8::1]:25565
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close > 1) {
                return normalizeHost(trimmed.substring(1, close));
            }
        }

        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.indexOf(':') == colon) {
            return normalizeHost(trimmed.substring(0, colon));
        }
        return normalizeHost(trimmed);
    }

    static String resolveSmallImageKey(ServerInfo info, String fallbackKey) {
        String host = extractHost(info == null ? null : info.address);
        if (!host.isEmpty()) {
            for (Map.Entry<String, String> entry : KNOWN_ICON_KEYS.entrySet()) {
                String knownHost = entry.getKey();
                if (host.equals(knownHost) || host.endsWith("." + knownHost)) {
                    return entry.getValue();
                }
            }
        }
        return fallbackKey == null ? "" : fallbackKey.trim();
    }

    private static String fromKnownHost(String host) {
        for (Map.Entry<String, String> entry : KNOWN_HOSTS.entrySet()) {
            String knownHost = entry.getKey();
            if (host.equals(knownHost) || host.endsWith("." + knownHost)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static String fromMotd(String motd) {
        if (motd == null || motd.isBlank()) {
            return "";
        }
        String clean = motd
            .replaceAll("\\u00A7.", "")
            .replace('|', ' ')
            .replaceAll("\\[[^\\]]+\\]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (clean.isEmpty()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("hypixel")) {
            return "Hypixel";
        }
        if (lower.contains("mineplex")) {
            return "Mineplex";
        }
        if (lower.contains("cubecraft")) {
            return "CubeCraft";
        }
        if (lower.contains("hive")) {
            return "The Hive";
        }
        if (lower.contains("minemen")) {
            return "Minemen Club";
        }
        return "";
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }
}
