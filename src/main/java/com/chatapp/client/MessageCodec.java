package com.chatapp.client;

import com.chatapp.socket.protocol.Envelope;

/** Small client-side facade over the shared wire codec. */
final class MessageCodec {
    private final com.chatapp.socket.protocol.MessageCodec delegate = new com.chatapp.socket.protocol.MessageCodec();
    <T> T unwrap(Envelope envelope, Class<T> type) { return delegate.unwrap(envelope, type); }
}
