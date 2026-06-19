package com.chatapp.socket.protocol;

import com.google.gson.JsonElement;

/**
 * The single envelope structure for every message sent over the socket
 * connection, in either direction.
 *
 * <p>Wire shape (as JSON): {@code {"type": "...", "payload": {...},
 * "timestamp": 1234567890}}. The {@code payload} field is a raw
 * {@link JsonElement} rather than a concrete typed field, because
 * different {@link MessageType} values carry structurally different
 * payloads (a login request payload looks nothing like a private
 * message payload) — see {@link com.chatapp.socket.protocol.payload}
 * (introduced in Phase 2) for the concrete payload record types.
 *
 * <p>This class itself is what gets serialized/deserialized by Gson;
 * see {@code MessageCodec} for the length-prefixed read/write logic
 * that frames these envelopes over the raw socket stream.
 */
public class Envelope {

    private MessageType type;
    private JsonElement payload;
    private long timestamp;

    /** No-arg constructor required for Gson deserialization. */
    public Envelope() {
    }

    public Envelope(MessageType type, JsonElement payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public JsonElement getPayload() { return payload; }
    public void setPayload(JsonElement payload) { this.payload = payload; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "Envelope{type=" + type + ", timestamp=" + timestamp + "}";
    }
}
