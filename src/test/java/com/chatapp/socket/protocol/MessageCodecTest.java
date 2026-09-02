package com.chatapp.socket.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

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
    void readRejectsNegativeFrameLength() {
        byte[] invalidFrame = {0, 0, 0, -1};

        assertThrows(java.io.IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(invalidFrame))));
    }

    @Test
    void readRejectsOversizedFrameLength() {
        byte[] invalidFrame = {0x01, 0x00, 0x00, 0x01}; // 16,777,217 bytes

        assertThrows(java.io.IOException.class, () ->
                codec.read(new DataInputStream(new ByteArrayInputStream(invalidFrame))));
    }

    private record Payload(String username, String password) {
    }
}
