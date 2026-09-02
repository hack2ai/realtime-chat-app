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
    // 5 MiB attachments are Base64 encoded inside JSON, so 8 MiB leaves protocol overhead
    // while reducing the maximum per-connection memory exposure from a 10 MiB frame.
    private static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;
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
        if (out == null) throw new IllegalArgumentException("Output stream must not be null.");
        if (envelope == null || envelope.getType() == null) {
            throw new IllegalArgumentException("Envelope and message type must not be null.");
        }
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
        if (in == null) throw new IllegalArgumentException("Input stream must not be null.");
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
            Envelope envelope = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), Envelope.class);
            if (envelope == null || envelope.getType() == null) {
                throw new IOException("Message envelope must contain a message type.");
            }
            return envelope;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON message.", e);
        }
    }

    public Gson getGson() { return gson; }

    public Envelope wrap(MessageType type, Object payload) {
        if (type == null) throw new IllegalArgumentException("Message type must not be null.");
        JsonElement element = payload == null ? null : gson.toJsonTree(payload);
        return new Envelope(type, element);
    }

    public <T> T unwrap(Envelope envelope, Class<T> payloadType) {
        if (envelope == null || envelope.getPayload() == null || envelope.getPayload().isJsonNull()) return null;
        if (payloadType == null) throw new IllegalArgumentException("Payload type must not be null.");
        try {
            return gson.fromJson(envelope.getPayload(), payloadType);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid payload for " + payloadType.getSimpleName(), e);
        }
    }

    public JsonElement parsePayload(String rawJson) {
        if (rawJson == null) throw new IllegalArgumentException("JSON payload must not be null.");
        return JsonParser.parseString(rawJson);
    }
}
