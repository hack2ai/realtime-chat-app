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

/**
 * Reads and writes {@link Envelope} messages over a raw socket stream
 * using length-prefixed framing.
 *
 * <h2>Why length-prefixing is necessary</h2>
 * TCP is a byte stream, not a message stream: it makes no guarantee
 * that one {@code write()} on the sending side corresponds to one
 * {@code read()} on the receiving side. Two messages sent back-to-back
 * can arrive concatenated in a single read, and a single large message
 * can arrive split across several reads. If we just wrote raw JSON text
 * and tried to read "until newline" or "until socket reports no more
 * data", we would intermittently glue two JSON objects together (broken
 * parse) or read a half-arrived object (broken parse), and that failure
 * would only show up under load/timing conditions that are hard to
 * reproduce — exactly the kind of bug a portfolio/demo should not have.
 *
 * <p>The fix: every message is preceded by a 4-byte big-endian integer
 * giving the exact byte length of the JSON payload that follows. The
 * reader always knows precisely how many bytes to consume for "one
 * message", regardless of how the underlying TCP packets happened to
 * be chunked.
 *
 * <pre>
 *   [ 4 bytes: payload length N ] [ N bytes: UTF-8 JSON payload ]
 * </pre>
 *
 * <p>This class is stateless and thread-safe — a single shared instance
 * can be used by multiple {@code ClientHandler} threads concurrently,
 * since each call operates only on the streams passed into it.
 */
public final class MessageCodec {

    /**
     * Defensive upper bound on a single message's byte size. Without
     * this, a corrupted or malicious length prefix (e.g. someone
     * connecting directly with a raw TCP client and sending garbage)
     * could claim a multi-gigabyte payload and cause the server to
     * attempt a huge allocation, denial-of-servicing itself.
     */
    private static final int MAX_MESSAGE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final Gson gson;

    public MessageCodec() {
        this.gson = new GsonBuilder()
                // LocalDateTime/LocalDate need an adapter; registered in Phase 2
                // once DTOs that carry them are introduced. Core Envelope/MessageType
                // serialize fine with Gson defaults (enum -> name(), JsonElement passthrough).
                .create();
    }

    /**
     * Writes one envelope to the stream as a length-prefixed JSON frame.
     * Flushes immediately so the message is not left buffered if the
     * caller doesn't flush themselves.
     */
    public void write(DataOutputStream out, Envelope envelope) throws IOException {
        String json = gson.toJson(envelope);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        if (bytes.length > MAX_MESSAGE_BYTES) {
            throw new IOException(
                    "Refusing to send oversized message: " + bytes.length +
                    " bytes exceeds limit of " + MAX_MESSAGE_BYTES
            );
        }

        synchronized (out) {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    /**
     * Blocks until one complete envelope has been read from the stream,
     * or the stream is closed.
     *
     * @return the parsed envelope
     * @throws EOFException if the stream closes cleanly before a full
     *                       message arrives (this is the normal signal
     *                       that the peer disconnected — callers should
     *                       treat it as "client left", not as an error
     *                       to log at ERROR level)
     * @throws IOException   for any other I/O failure, or if a length
     *                       prefix exceeds {@link #MAX_MESSAGE_BYTES}
     */
    public Envelope read(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            // Clean disconnect: peer closed the socket before sending
            // another message. Re-throw as-is so callers can distinguish
            // this from a genuine error.
            throw e;
        }

        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException(
                    "Received invalid frame length: " + length +
                    " (limit is " + MAX_MESSAGE_BYTES + " bytes). " +
                    "Connection is likely corrupted or speaking a different protocol; closing."
            );
        }

        byte[] bytes = new byte[length];
        in.readFully(bytes); // throws EOFException if stream closes mid-message

        String json = new String(bytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, Envelope.class);
    }

    /**
     * Exposes the underlying Gson instance so callers can serialize or
     * deserialize the {@code payload} field of an {@link Envelope} into
     * a concrete payload type, e.g.:
     * <pre>
     *   LoginRequest req = codec.getGson().fromJson(envelope.getPayload(), LoginRequest.class);
     * </pre>
     */
    public Gson getGson() {
        return gson;
    }

    /**
     * Convenience: wraps an arbitrary payload object into an Envelope
     * of the given type, converting it to a {@link JsonElement} via
     * this codec's Gson instance.
     */
    public Envelope wrap(MessageType type, Object payload) {
        JsonElement element = gson.toJsonTree(payload);
        return new Envelope(type, element);
    }

    /**
     * Convenience: deserializes an envelope's payload into the given
     * concrete type.
     */
    public <T> T unwrap(Envelope envelope, Class<T> payloadType) {
        if (envelope.getPayload() == null) {
            return null;
        }
        return gson.fromJson(envelope.getPayload(), payloadType);
    }

    /**
     * Parses a raw JSON string into a JsonElement. Exposed for callers
     * building an Envelope's payload manually without a strongly-typed
     * DTO (rare; prefer {@link #wrap}).
     */
    public JsonElement parsePayload(String rawJson) {
        return JsonParser.parseString(rawJson);
    }
}
