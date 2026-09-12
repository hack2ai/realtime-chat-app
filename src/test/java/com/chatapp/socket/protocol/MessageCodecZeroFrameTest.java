package com.chatapp.socket.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageCodecZeroFrameTest {

    @Test
    void readRejectsZeroLengthFrame() throws Exception {
        byte[] frame = {0, 0, 0, 0};

        assertThrows(IOException.class, () ->
                new MessageCodec().read(new DataInputStream(new ByteArrayInputStream(frame))));
    }
}
