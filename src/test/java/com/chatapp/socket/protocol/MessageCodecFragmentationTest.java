package com.chatapp.socket.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageCodecFragmentationTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void readHandlesTcpStyleFragmentationOfFrameHeaderAndPayload() throws Exception {
        Envelope original = codec.wrap(
                MessageType.C2S_LOGIN,
                new Payload("alice", "correct-horse"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        codec.write(new DataOutputStream(bytes), original);

        try (InputStream fragmented = new SingleByteInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Envelope decoded = codec.read(new java.io.DataInputStream(fragmented));
            assertNotNull(decoded);
            assertEquals(MessageType.C2S_LOGIN, decoded.getType());

            Payload payload = codec.unwrap(decoded, Payload.class);
            assertNotNull(payload);
            assertEquals("alice", payload.username());
            assertEquals("correct-horse", payload.password());
        }
    }

    private static final class SingleByteInputStream extends FilterInputStream {
        private SingleByteInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
            return super.read(bytes, offset, Math.min(length, 1));
        }
    }

    private record Payload(String username, String password) {
    }
}
