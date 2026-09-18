package com.example.liveChat.services;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Traduz a expiração do marcador Redis em uma saída definitiva de presença. */
@Component
@Profile("!test")
public class RedisMediaSessionExpiryListener implements MessageListener {
    private static final String TIMEOUT_PREFIX = "livechat:media:timeout:";

    private final RedisMessageListenerContainer listenerContainer;
    private final ApplicationEventPublisher eventPublisher;

    public RedisMediaSessionExpiryListener(RedisMessageListenerContainer listenerContainer,
                                           ApplicationEventPublisher eventPublisher) {
        this.listenerContainer = listenerContainer;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic("__keyevent@*__:expired"));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(TIMEOUT_PREFIX)) {
            return;
        }
        String[] keyParts = expiredKey.substring(TIMEOUT_PREFIX.length()).split(":", 2);
        if (keyParts.length == 2 && !keyParts[0].isBlank() && !keyParts[1].isBlank()) {
            eventPublisher.publishEvent(new MediaSessionExpiryEvent(keyParts[0], keyParts[1]));
        }
    }
}
