package com.chatapp.socket.protocol;

/**
 * Every message type exchangeable between client and server.
 *
 * <p>This is the single canonical definition of the wire protocol's
 * message types. Both {@code ChatServer}/{@code ClientHandler} on the
 * server side and {@code ChatClient} on the client side reference this
 * same enum — there must never be a second, separately-defined copy of
 * this list, since client and server would then drift out of sync
 * silently (a packet sent as one enum's {@code PRIVATE_MESSAGE} would
 * not equal the other's, even with the same name, if they were
 * different enum classes).
 *
 * <p>Naming convention: {@code C2S_} prefix = client-to-server request,
 * {@code S2C_} prefix = server-to-client push/response. A few types
 * (like PING/PONG) are symmetric and carry no prefix.
 */
public enum MessageType {

    // ---------------- Connection lifecycle ----------------
    PING,
    PONG,

    // ---------------- Authentication ----------------
    C2S_REGISTER,
    S2C_REGISTER_SUCCESS,
    S2C_REGISTER_FAILED,

    C2S_LOGIN,
    S2C_LOGIN_SUCCESS,
    S2C_LOGIN_FAILED,

    C2S_LOGOUT,
    S2C_LOGOUT_ACK,

    // ---------------- Presence ----------------
    S2C_USER_ONLINE,
    S2C_USER_OFFLINE,
    C2S_REQUEST_USER_LIST,
    S2C_USER_LIST,

    C2S_TYPING_START,
    C2S_TYPING_STOP,
    S2C_TYPING_START,
    S2C_TYPING_STOP,

    // ---------------- Private messaging ----------------
    C2S_PRIVATE_MESSAGE,
    S2C_PRIVATE_MESSAGE,
    C2S_MESSAGE_READ,
    S2C_MESSAGE_DELIVERED,
    S2C_MESSAGE_READ,
    C2S_REQUEST_PRIVATE_HISTORY,
    S2C_PRIVATE_HISTORY,

    // ---------------- Group messaging (Phase 3) ----------------
    C2S_CREATE_GROUP,
    S2C_GROUP_CREATED,
    C2S_JOIN_GROUP,
    C2S_LEAVE_GROUP,
    C2S_GROUP_MESSAGE,
    S2C_GROUP_MESSAGE,
    C2S_REQUEST_GROUP_LIST,
    S2C_GROUP_LIST,
    C2S_REQUEST_GROUP_HISTORY,
    S2C_GROUP_HISTORY,

    // ---------------- System / errors ----------------
    S2C_ERROR,
    S2C_NOTIFICATION
}
