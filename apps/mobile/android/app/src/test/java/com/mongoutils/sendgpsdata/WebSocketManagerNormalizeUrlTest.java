package com.mongoutils.sendgpsdata;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketManagerNormalizeUrlTest {

    @Property
    boolean anyNonNullInputProducesWebSocketScheme(@ForAll String input) {
        String result = WebSocketManager.normalizeUrl(input);
        return result.startsWith("wss://") || result.startsWith("ws://");
    }

    @Property
    boolean noSchemeGetsWssPrefix(
            @ForAll @StringLength(min = 1, max = 200) String input
    ) {
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) {
            return true;
        }
        String result = WebSocketManager.normalizeUrl(input);
        return result.startsWith("wss://");
    }

    @Property
    boolean wsSchemeIsPreserved(
            @ForAll @StringLength(min = 1, max = 200) String host
    ) {
        String trimmed = host.trim();
        if (trimmed.isEmpty()) return true;
        String input = "ws://" + trimmed;
        String result = WebSocketManager.normalizeUrl(input);
        return result.startsWith("ws://");
    }

    @Property
    boolean wssSchemeIsPreserved(
            @ForAll @StringLength(min = 1, max = 200) String host
    ) {
        String trimmed = host.trim();
        if (trimmed.isEmpty()) return true;
        String input = "wss://" + trimmed;
        String result = WebSocketManager.normalizeUrl(input);
        return result.startsWith("wss://");
    }

    @Property
    boolean trailingSlashesAreStripped(
            @ForAll @StringLength(min = 1, max = 200) String input
    ) {
        if (input.trim().isEmpty()) return true;
        String result = WebSocketManager.normalizeUrl(input);
        return !result.endsWith("/");
    }

    @Property
    boolean leadingAndTrailingWhitespaceIsTrimmed(
            @ForAll @StringLength(min = 1, max = 100) String core,
            @ForAll @IntRange(min = 1, max = 5) int leadingSpaces,
            @ForAll @IntRange(min = 1, max = 5) int trailingSpaces
    ) {
        if (core.trim().isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leadingSpaces; i++) sb.append(' ');
        sb.append(core);
        for (int i = 0; i < trailingSpaces; i++) sb.append(' ');
        String withSpaces = sb.toString();
        String resultWithSpaces = WebSocketManager.normalizeUrl(withSpaces);
        String resultWithoutSpaces = WebSocketManager.normalizeUrl(core);
        return resultWithSpaces.equals(resultWithoutSpaces);
    }

    @Example
    void nullReturnsWssScheme() {
        assertEquals("wss://", WebSocketManager.normalizeUrl(null));
    }

    @Example
    void emptyReturnsWssScheme() {
        assertEquals("wss://", WebSocketManager.normalizeUrl(""));
    }

    @Example
    void blankReturnsWssScheme() {
        assertEquals("wss://", WebSocketManager.normalizeUrl("   "));
    }

    @Example
    void hostnameWithPort() {
        assertEquals("wss://192.168.1.1:8080", WebSocketManager.normalizeUrl("192.168.1.1:8080"));
    }

    @Example
    void wsSchemePreserved() {
        assertEquals("ws://localhost:3000/ws", WebSocketManager.normalizeUrl("ws://localhost:3000/ws"));
    }

    @Example
    void wssSchemePreserved() {
        assertEquals("wss://example.com/ws", WebSocketManager.normalizeUrl("wss://example.com/ws"));
    }

    @Example
    void multipleTrailingSlashesStripped() {
        assertEquals("wss://host.com", WebSocketManager.normalizeUrl("wss://host.com///"));
    }
}
