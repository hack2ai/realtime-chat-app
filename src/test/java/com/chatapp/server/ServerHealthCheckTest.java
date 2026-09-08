package com.chatapp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerHealthCheckTest {
    @Test
    void blankPortUsesDefault() {
        assertEquals(5050, ServerHealthCheck.parsePort(null));
        assertEquals(5050, ServerHealthCheck.parsePort(""));
        assertEquals(5050, ServerHealthCheck.parsePort("   "));
    }

    @Test
    void acceptsTcpPortBoundaries() {
        assertEquals(1, ServerHealthCheck.parsePort("1"));
        assertEquals(65_535, ServerHealthCheck.parsePort("65535"));
    }

    @Test
    void rejectsNonNumericPort() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("not-a-port"));
    }

    @Test
    void rejectsPortsOutsideTcpRange() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("0"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("-1"));
    }

    @Test
    void systemPropertyTakesPrecedenceOverEnvironment() {
        assertEquals(6000, ServerHealthCheck.resolvePort(" 6000 ", "7000"));
    }

    @Test
    void invalidSystemPropertyCannotBeBypassedByEnvironment() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.resolvePort("not-a-port", "7000"));
    }

    @Test
    void environmentValueIsUsedWhenPropertyIsBlank() {
        assertEquals(7000, ServerHealthCheck.resolvePort("   ", "7000"));
    }

    @Test
    void defaultIsUsedWhenBothOverridesAreBlank() {
        assertEquals(5050, ServerHealthCheck.resolvePort(null, ""));
    }
}
