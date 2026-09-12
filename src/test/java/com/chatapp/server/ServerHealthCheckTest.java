package com.chatapp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerHealthCheckTest {

    @Test
    void parsePortDefaultsWhenUnset() {
        assertEquals(5050, ServerHealthCheck.parsePort(null));
        assertEquals(5050, ServerHealthCheck.parsePort("   "));
    }

    @Test
    void parsePortAcceptsValidPorts() {
        assertEquals(1, ServerHealthCheck.parsePort("1"));
        assertEquals(5050, ServerHealthCheck.parsePort(" 5050 "));
        assertEquals(65535, ServerHealthCheck.parsePort("65535"));
    }

    @Test
    void parsePortRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("0"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("not-a-port"));
    }

    @Test
    void resolvePortPrefersSystemProperty() {
        assertEquals(5443, ServerHealthCheck.resolvePort("5443", "6000"));
        assertEquals(6000, ServerHealthCheck.resolvePort(null, "6000"));
        assertEquals(5050, ServerHealthCheck.resolvePort(null, null));
    }
}
