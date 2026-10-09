package com.example.liveChat.infra.websockets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {
    /**
     * Heartbeats STOMP (enviar e esperar a cada 10 s). Sem eles, um proxy pode fechar o WebSocket ocioso ou deixá-lo
     * meio-aberto, e o navegador para de receber eventos de presença sem perceber que a conexão morreu.
     */
    static final long[] HEARTBEAT_MILLIS = {10_000, 10_000};

    private final WebsocketSecurityInterceptor websocketSecurityInterceptor;
    private final TaskScheduler messageBrokerTaskScheduler;
    private final String[] allowedOrigins;

    public WebsocketConfig(WebsocketSecurityInterceptor websocketSecurityInterceptor,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler,
                           @Value("${livechat.security.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.websocketSecurityInterceptor = websocketSecurityInterceptor;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue")
                .setHeartbeatValue(HEARTBEAT_MILLIS)
                .setTaskScheduler(messageBrokerTaskScheduler);
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(websocketSecurityInterceptor);
    }

    private static String[] parseAllowedOrigins(String configuredOrigins) {
        String[] origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.asList(origins).contains("*")) {
            throw new IllegalArgumentException("livechat.security.allowed-origins must contain explicit origins");
        }
        return origins;
    }
}
