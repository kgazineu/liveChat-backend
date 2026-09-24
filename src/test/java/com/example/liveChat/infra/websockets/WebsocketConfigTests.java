package com.example.liveChat.infra.websockets;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsocketConfigTests {
    @Test
    void registersOnlyTheConfiguredWebsocketOrigins() {
        WebsocketSecurityInterceptor interceptor = mock(WebsocketSecurityInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        WebsocketConfig config = new WebsocketConfig(
                interceptor, "https://chat.example.test, https://admin.example.test");

        config.registerStompEndpoints(registry);

        verify(registration).setAllowedOrigins("https://chat.example.test", "https://admin.example.test");
    }
}
