package com.chatapp.server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

/** Bounded outbound frame queue with both message-count and byte-memory limits. */
final class OutboundMessageQueue {
    static final int DEFAULT_CAPACITY = 256;
    static final int DEFAULT_MAX_QUEUED_BYTES = 16 * 1024 * 1024;

    record Entry(byte[] frame) {
        Entry {
            if (frame == null || frame.length == 0) {
                throw new IllegalArgumentException("Outbound frame must not be empty.");
            }
        }
    }

    private final BlockingQueue<Entry> queue;
    private final Semaphore byteBudget;
    private final int maxQueuedBytes;
    private final Object monitor = new Object();
    private boolean closed;

    OutboundMessageQueue() {
        this(DEFAULT_CAPACITY, DEFAULT_MAX_QUEUED_BYTES);
    }

    OutboundMessageQueue(int capacity, int maxQueuedBytes) {
        if (capacity <= 0) throw new IllegalArgumentException("Queue capacity must be positive.");
        if (maxQueuedBytes <= 0) throw new IllegalArgumentException("Byte budget must be positive.");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.byteBudget = new Semaphore(maxQueuedBytes);
        this.maxQueuedBytes = maxQueuedBytes;
    }

    boolean offer(byte[] frame) {
        if (frame == null || frame.length == 0 || frame.length > maxQueuedBytes) return false;
        synchronized (monitor) {
            if (closed || !byteBudget.tryAcquire(frame.length)) return false;
            Entry entry = new Entry(frame);
            if (!queue.offer(entry)) {
                byteBudget.release(frame.length);
                return false;
            }
            return true;
        }
    }

    Entry take() throws InterruptedException {
        return queue.take();
    }

    void complete(Entry entry) {
        if (entry == null) return;
        byteBudget.release(entry.frame().length);
    }

    void close() {
        synchronized (monitor) {
            if (closed) return;
            closed = true;
            Entry entry;
            while ((entry = queue.poll()) != null) {
                byteBudget.release(entry.frame().length);
            }
        }
    }

    int queuedBytes() {
        return Math.max(0, maxQueuedBytes - byteBudget.availablePermits());
    }
}
