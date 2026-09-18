package com.example.liveChat.services;

import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.MediaSessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Redis é a fonte de verdade das sessões efêmeras fora do perfil de testes. */
@Service
@Profile("!test")
public class RedisMediaSessionStore implements MediaSessionStore {
    private static final String SESSION_PREFIX = "livechat:media:session:";
    private static final String CHANNEL_PREFIX = "livechat:media:channel:";
    private static final String TIMEOUT_PREFIX = "livechat:media:timeout:";
    private static final Duration EXPIRY_GRACE_PERIOD = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MediaPresenceProperties properties;

    public RedisMediaSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper,
                                  MediaPresenceProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<MediaSession> findByUserId(String userId) {
        return deserialize(redis.opsForValue().get(sessionKey(userId)));
    }

    @Override
    public List<MediaSession> findByChannel(MediaChannelKind channelKind, String channelId) {
        Set<String> userIds = redis.opsForSet().members(channelKey(channelKind, channelId));
        if (userIds == null) {
            return List.of();
        }
        return userIds.stream()
                .map(userId -> findByUserId(userId).filter(session -> session.belongsTo(channelKind, channelId))
                        .orElseGet(() -> {
                            redis.opsForSet().remove(channelKey(channelKind, channelId), userId);
                            return null;
                        }))
                .filter(session -> session != null)
                .sorted(Comparator.comparing(MediaSession::joinedAt).thenComparing(MediaSession::userId))
                .toList();
    }

    @Override
    public void saveActive(MediaSession session) {
        redis.opsForValue().set(sessionKey(session.userId()), serialize(session));
        redis.opsForSet().add(channelKey(session.channelKind(), session.channelId()), session.userId());
    }

    @Override
    public void markReconnecting(MediaSession session) {
        Duration timeout = properties.getReconnectionTimeout();
        redis.opsForValue().set(sessionKey(session.userId()), serialize(session), timeout.plus(EXPIRY_GRACE_PERIOD));
        redis.opsForSet().add(channelKey(session.channelKind(), session.channelId()), session.userId());
        redis.opsForValue().set(timeoutKey(session.userId(), session.reconnectionId()), "1", timeout);
    }

    @Override
    public void delete(MediaSession session) {
        redis.delete(sessionKey(session.userId()));
        redis.opsForSet().remove(channelKey(session.channelKind(), session.channelId()), session.userId());
        if (session.reconnectionId() != null) {
            redis.delete(timeoutKey(session.userId(), session.reconnectionId()));
        }
    }

    @Override
    public Optional<MediaSession> removeExpired(String userId, String reconnectionId) {
        MediaSession session = findByUserId(userId).orElse(null);
        if (session == null || session.status() != MediaSessionStatus.RECONNECTING
                || !reconnectionId.equals(session.reconnectionId())) {
            return Optional.empty();
        }
        delete(session);
        return Optional.of(session);
    }

    private String sessionKey(String userId) {
        return SESSION_PREFIX + userId;
    }

    private String channelKey(MediaChannelKind channelKind, String channelId) {
        return CHANNEL_PREFIX + channelKind.name().toLowerCase() + ":" + channelId;
    }

    private String timeoutKey(String userId, String reconnectionId) {
        return TIMEOUT_PREFIX + userId + ":" + reconnectionId;
    }

    private String serialize(MediaSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize media session", exception);
        }
    }

    private Optional<MediaSession> deserialize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, MediaSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize media session", exception);
        }
    }
}
