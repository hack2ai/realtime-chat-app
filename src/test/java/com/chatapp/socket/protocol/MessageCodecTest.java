package com.chatapp.socket.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    private static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;
    private final MessageCodec codec = new MessageCodec();

    @Test
    void roundTripPreservesMessageTypeAndPayload() throws Exception {
        Envelope original = codec.wrap(MessageType.C2S_LOGIN,
                new Payload("alice", "correct-horse"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        codec.write(new DataOutputStream(bytes), original);

        Envelope decoded = codec.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(MessageType.C2S_LOGIN, decoded.getType());
        Payload payload = codec.unwrap(decoded, Payload.class);
        assertNotNull(payload);
        assertEquals("alice", payload.username());
        assertEquals("correct-horse", payload.password());
    }

    @Test
    void roundTripPreservesLocalDateTimePayloads() throws Exception {
        TimestampPayload original = new TimestampPayload(LocalDateTime.of(2026, 9, 3, 9, 30, 15));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        codec.write(new DataOutputStream(bytes), codec.wrap(MessageType.S2C_PRIVATE_MESSAGE, original));

        Envelope decoded = codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        TimestampPayload payload = codec.unwrap(decoded, TimestampPayload.class);

        assertEquals(original.sentAt(), payload.sentAt());
    }

    @Test
    void readRejectsNegativeFrameLength() {
        byte[] invalidFrame = {0, 0, 0, -1};

        assertThrows(IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(invalidFrame))));
    }

    @Test
    void readRejectsOversizedFrameLength() {
        byte[] invalidFrame = {0x01, 0x00, 0x00, 0x01};

        assertThrows(IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(invalidFrame))));
    }

    @Test
    void readRejectsTruncatedFrame() throws Exception {
        byte[] payload = "{\"type\":\"C2S_LOGIN\"}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(payload.length + 10);
        out.write(payload);

        assertThrows(IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void readRejectsMalformedJson() throws Exception {
        byte[] payload = "{not-json".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(payload.length);
        out.write(payload);

        assertThrows(IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void readRejectsEnvelopeWithoutMessageType() throws Exception {
        byte[] payload = "{\"payload\":{\"username\":\"alice\"}}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(payload.length);
        out.write(payload);

        assertThrows(IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void readRejectsNullInputStream() {
        assertThrows(IllegalArgumentException.class, () -> codec.read(null));
    }

    @Test
    void writeRejectsNullOutputStream() {
        Envelope envelope = codec.wrap(MessageType.C2S_LOGIN, new Payload("alice", "password"));
        assertThrows(IllegalArgumentException.class, () -> codec.write(null, envelope));
    }

    @Test
    void writeAcceptsExactMaximumFrameSize() throws Exception {
        Envelope template = codec.wrap(MessageType.C2S_PRIVATE_MESSAGE, new Payload("alice", ""));
        int fixedJsonBytes = codec.getGson().toJson(template).getBytes(StandardCharsets.UTF_8).length;
        int payloadChars = MAX_FRAME_BYTES - fixedJsonBytes;
        assertTrue(payloadChars > 0);

        Envelope envelope = codec.wrap(MessageType.C2S_PRIVATE_MESSAGE,
                new Payload("alice", "x".repeat(payloadChars)));
        byte[] jsonBytes = codec.getGson().toJson(envelope).getBytes(StandardCharsets.UTF_8);
        assertEquals(MAX_FRAME_BYTES, jsonBytes.length);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAX_FRAME_BYTES + Integer.BYTES);
        codec.write(new DataOutputStream(bytes), envelope);
        assertEquals(MAX_FRAME_BYTES + Integer.BYTES, bytes.size());
    }

    @Test
    void writeUsesUtf8ByteLengthForFrameLimit() throws Exception {
        Envelope template = codec.wrap(MessageType.C2S_PRIVATE_MESSAGE, new Payload("alice", ""));
        int fixedJsonBytes = codec.getGson().toJson(template).getBytes(StandardCharsets.UTF_8).length;
        int repetitions = ((MAX_FRAME_BYTES - fixedJsonBytes) / 2) + 1;

        Envelope envelope = codec.wrap(MessageType.C2S_PRIVATE_MESSAGE,
                new Payload("alice", "é".repeat(repetitions)));
        byte[] jsonBytes = codec.getGson().toJson(envelope).getBytes(StandardCharsets.UTF_8);

        assertTrue(jsonBytes.length > MAX_FRAME_BYTES);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThrows(IOException.class, () ->
                codec.write(new DataOutputStream(bytes), envelope));
        assertEquals(0, bytes.size());
    }

    @Test
    void writeRejectsOversizedFrameWithoutWritingPartialData() {
        String oversizedPayload = "x".repeat(8 * 1024 * 1024);
        Envelope envelope = codec.wrap(MessageType.C2S_PRIVATE_MESSAGE,
                new Payload("alice", oversizedPayload));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertThrows(IOException.class, () ->
                codec.write(new DataOutputStream(bytes), envelope));
        assertEquals(0, bytes.size());
    }

    @Test
    void concurrentWritesProduceIntactFrames() throws Exception {
        final int writerCount = 20;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);

        try {
            List<Future<?>> futures = new ArrayList<>(writerCount);
            for (int i = 0; i < writerCount; i++) {
                final int writerId = i;
                futures.add(executor.submit(() -> {
                    codec.write(out, codec.wrap(MessageType.C2S_PRIVATE_MESSAGE,
                            new Payload("user-" + writerId, "message-" + writerId)));
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        Set<String> usernames = new HashSet<>();
        for (int i = 0; i < writerCount; i++) {
            Envelope decoded = codec.read(in);
            Payload payload = codec.unwrap(decoded, Payload.class);
            assertNotNull(payload);
            assertEquals(MessageType.C2S_PRIVATE_MESSAGE, decoded.getType());
            usernames.add(payload.username());
        }

        assertEquals(writerCount, usernames.size());
        assertEquals(-1, in.read());
    }

    @Test
    void wrapRejectsNullMessageType() {
        assertThrows(IllegalArgumentException.class, () ->
                codec.wrap(null, new Payload("alice", "password")));
    }

    @Test
    void unwrapRejectsNullPayloadType() {
        Envelope envelope = codec.wrap(MessageType.C2S_LOGIN,
                new Payload("alice", "password"));

        assertThrows(IllegalArgumentException.class, () -> codec.unwrap(envelope, null));
    }

    @Test
    void unwrapReturnsNullForMissingPayload() {
        Envelope envelope = new Envelope(MessageType.C2S_LOGIN, null);
        assertNull(codec.unwrap(envelope, Payload.class));
    }

    @Test
    void writeRejectsNullEnvelope() {
        assertThrows(IllegalArgumentException.class, () ->
                codec.write(new DataOutputStream(new ByteArrayOutputStream()), null));
    }

    @Test
    void parsePayloadRejectsNullJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.parsePayload(null));
    }

    @Test
    void parsePayloadRejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> codec.parsePayload("{not-json"));
    }

    private record Payload(String username, String password) {
    }

    private record TimestampPayload(LocalDateTime sentAt) {
    }
}
