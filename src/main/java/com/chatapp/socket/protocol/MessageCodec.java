package com.chatapp.socket.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Reads and writes length-prefixed UTF-8 JSON protocol frames. */
public final class MessageCodec {
    private static final int MAX_MESSAGE_BYTES = 10 * 1024 * 1024;
    private final Gson gson;

    public MessageCodec() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonSerializer<LocalDateTime>) (value, type, context) ->
                                new com.google.gson.JsonPrimitive(value.toString()))
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonDeserializer<LocalDateTime>) (json, type, context) ->
                                LocalDateTime.parse(json.getAsString()))
                .create();
    }

    public void write(DataOutputStream out, Envelope envelope) throws IOException {
        String json = gson.toJson(envelope);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Refusing to send oversized message: " + bytes.length + " bytes.");
        }
        synchronized (out) {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    public Envelope read(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            throw e;
        }
        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("Received invalid frame length: " + length + " (limit " + MAX_MESSAGE_BYTES + ").");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        try {
            return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), Envelope.class);
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON message.", e);
        }
    }

    public Gson getGson() { return gson; }

    public Envelope wrap(MessageType type, Object payload) {
        JsonElement element = payload == null ? null : gson.toJsonTree(payload);
        return new Envelope(type, element);
    }

    public <T> T unwrap(Envelope envelope, Class<T> payloadType) {
        if (envelope == null || envelope.getPayload() == null || envelope.getPayload().isJsonNull()) return null;
        try {
            return gson.fromJson(envelope.getPayload(), payloadType);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid payload for " + payloadType.getSimpleName(), e);
        }
    }

    public JsonElement parsePayload(String rawJson) { return JsonParser.parseString(rawJson); }
}
