package com.chatapp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboundMessageQueueTest {

    @Test
    void rejectsFramesThatWouldExceedByteBudget() {
        OutboundMessageQueue queue = new OutboundMessageQueue(4, 10);

        assertTrue(queue.offer(new byte[8]));
        assertFalse(queue.offer(new byte[3]));
        assertEquals(8, queue.queuedBytes());
    }

    @Test
    void releasesBytesAfterWriterCompletesFrame() throws Exception {
        OutboundMessageQueue queue = new OutboundMessageQueue(4, 10);

        assertTrue(queue.offer(new byte[8]));
        OutboundMessageQueue.Entry entry = queue.take();

        assertEquals(8, queue.queuedBytes());
        queue.complete(entry);
        assertEquals(0, queue.queuedBytes());
        assertTrue(queue.offer(new byte[10]));
    }

    @Test
    void rejectsFrameLargerThanByteBudget() {
        OutboundMessageQueue queue = new OutboundMessageQueue(4, 10);

        assertFalse(queue.offer(new byte[11]));
        assertEquals(0, queue.queuedBytes());
    }

    @Test
    void closeReleasesQueuedFramesAndRejectsFutureOffers() {
        OutboundMessageQueue queue = new OutboundMessageQueue(4, 10);

        assertTrue(queue.offer(new byte[4]));
        assertTrue(queue.offer(new byte[3]));
        assertEquals(7, queue.queuedBytes());

        queue.close();

        assertEquals(0, queue.queuedBytes());
        assertFalse(queue.offer(new byte[1]));
    }

    @Test
    void validatesConstructorLimits() {
        assertThrows(IllegalArgumentException.class, () -> new OutboundMessageQueue(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new OutboundMessageQueue(4, 0));
    }
}
