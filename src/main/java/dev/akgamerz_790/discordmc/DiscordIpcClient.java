package dev.akgamerz_790.discordmc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DiscordIpcClient implements Closeable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    private RandomAccessFile pipe;
    private volatile boolean connected;

    public synchronized boolean connect(String clientId) {
        closeQuietly();

        for (int i = 0; i < 10; i++) {
            String path = "\\\\?\\pipe\\discord-ipc-" + i;
            try {
                pipe = new RandomAccessFile(path, "rw");
                JsonObject handshake = new JsonObject();
                handshake.addProperty("v", 1);
                handshake.addProperty("client_id", clientId);
                writeFrame(OP_HANDSHAKE, handshake.toString());

                Frame frame = readFrame();
                if (frame.opcode == OP_FRAME || frame.opcode == OP_PING) {
                    connected = true;
                    return true;
                }
            } catch (IOException ignored) {
                closeQuietly();
            }
        }
        return false;
    }

    public synchronized boolean isConnected() {
        return connected && pipe != null;
    }

    public synchronized void setActivity(Activity activity) throws IOException {
        ensureConnected();

        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());

        JsonObject payload = new JsonObject();
        addIfPresent(payload, "state", activity.state());
        addIfPresent(payload, "details", activity.details());

        JsonObject timestamps = new JsonObject();
        if (activity.startTimestamp() > 0) {
            timestamps.addProperty("start", activity.startTimestamp());
        }
        if (!timestamps.entrySet().isEmpty()) {
            payload.add("timestamps", timestamps);
        }

        JsonObject assets = new JsonObject();
        addIfPresent(assets, "large_image", activity.largeImageKey());
        addIfPresent(assets, "large_text", activity.largeImageText());
        addIfPresent(assets, "small_image", activity.smallImageKey());
        addIfPresent(assets, "small_text", activity.smallImageText());
        if (!assets.entrySet().isEmpty()) {
            payload.add("assets", assets);
        }

        JsonObject party = new JsonObject();
        addIfPresent(party, "id", activity.partyId());
        if (activity.partySize() > 0 && activity.partyMax() >= activity.partySize()) {
            party.addProperty("size", activity.partySize());
            party.addProperty("max", activity.partyMax());
        }
        if (!party.entrySet().isEmpty()) {
            payload.add("party", party);
        }

        JsonObject secrets = new JsonObject();
        addIfPresent(secrets, "join", activity.joinSecret());
        if (!secrets.entrySet().isEmpty()) {
            payload.add("secrets", secrets);
            payload.addProperty("instance", true);
        }

        args.add("activity", payload);
        root.add("args", args);
        root.addProperty("nonce", UUID.randomUUID().toString());

        writeFrame(OP_FRAME, root.toString());
    }

    public synchronized void clearActivity() {
        if (!isConnected()) {
            return;
        }
        try {
            setActivity(new Activity(null, null, null, null, null, null, null, 0, 0, null, 0));
        } catch (IOException ignored) {
        }
    }

    public synchronized void poll() throws IOException {
        if (!isConnected()) {
            return;
        }

        while (pipe.length() - pipe.getFilePointer() >= 8) {
            Frame frame = readFrame();
            if (frame.opcode == OP_PING) {
                writeFrame(OP_PONG, frame.payload);
            } else if (frame.opcode == OP_CLOSE) {
                throw new IOException("Discord IPC closed by peer");
            } else if (frame.opcode == OP_FRAME && frame.payload != null && !frame.payload.isBlank()) {
                JsonObject obj = JsonParser.parseString(frame.payload).getAsJsonObject();
                if (obj.has("evt") && "ERROR".equals(obj.get("evt").getAsString())) {
                    throw new IOException("Discord IPC error: " + frame.payload);
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        connected = false;
        closeQuietly();
    }

    private void closeQuietly() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {
            } finally {
                pipe = null;
            }
        }
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Discord IPC is not connected");
        }
    }

    private void addIfPresent(JsonObject obj, String key, String value) {
        if (value != null && !value.isBlank()) {
            obj.addProperty(key, value);
        }
    }

    private void writeFrame(int opcode, String payload) throws IOException {
        byte[] payloadBytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode);
        header.putInt(payloadBytes.length);
        pipe.write(header.array());
        pipe.write(payloadBytes);
    }

    private Frame readFrame() throws IOException {
        byte[] headerBytes = new byte[8];
        int read = pipe.read(headerBytes);
        if (read < 0) {
            throw new EOFException("Discord IPC stream ended");
        }
        if (read < 8) {
            throw new EOFException("Discord IPC short header");
        }

        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = header.getInt();
        int length = header.getInt();
        if (length < 0 || length > 1_048_576) {
            throw new IOException("Invalid Discord IPC frame length: " + length);
        }

        byte[] payload = new byte[length];
        if (length > 0) {
            pipe.readFully(payload);
        }
        return new Frame(opcode, new String(payload, StandardCharsets.UTF_8));
    }

    private record Frame(int opcode, String payload) {
    }

    public record Activity(
        String details,
        String state,
        String largeImageKey,
        String largeImageText,
        String smallImageKey,
        String smallImageText,
        String partyId,
        int partySize,
        int partyMax,
        String joinSecret,
        long startTimestamp
    ) {
    }
}
