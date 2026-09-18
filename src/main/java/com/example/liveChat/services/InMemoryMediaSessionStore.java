package com.example.liveChat.services;

import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.MediaSessionStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Armazenamento determinístico usado somente no perfil de testes, sem depender de um Redis externo. */
@Service
@Profile("test")
public class InMemoryMediaSessionStore implements MediaSessionStore {
    private final Map<String, MediaSession> sessionsByUserId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expiryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "media-presence-test-expiry");
        thread.setDaemon(true);
        return thread;
    });
    private final ApplicationEventPublisher eventPublisher;
    private final MediaPresenceProperties properties;

    public InMemoryMediaSessionStore(ApplicationEventPublisher eventPublisher, MediaPresenceProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    public Optional<MediaSession> findByUserId(String userId) {
        return Optional.ofNullable(sessionsByUserId.get(userId));
    }

    @Override
    public List<MediaSession> findByChannel(MediaChannelKind channelKind, String channelId) {
        return sessionsByUserId.values().stream()
                .filter(session -> session.belongsTo(channelKind, channelId))
                .sorted(Comparator.comparing(MediaSession::joinedAt).thenComparing(MediaSession::userId))
                .toList();
    }

    @Override
    public void saveActive(MediaSession session) {
        sessionsByUserId.put(session.userId(), session);
    }

    @Override
    public void markReconnecting(MediaSession session) {
        sessionsByUserId.put(session.userId(), session);
        expiryScheduler.schedule(() -> eventPublisher.publishEvent(
                        new MediaSessionExpiryEvent(session.userId(), session.reconnectionId())),
                properties.getReconnectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void delete(MediaSession session) {
        sessionsByUserId.remove(session.userId(), session);
    }

    @Override
    public Optional<MediaSession> removeExpired(String userId, String reconnectionId) {
        MediaSession session = sessionsByUserId.get(userId);
        if (session == null || session.status() != MediaSessionStatus.RECONNECTING
                || !reconnectionId.equals(session.reconnectionId())) {
            return Optional.empty();
        }
        return sessionsByUserId.remove(userId, session) ? Optional.of(session) : Optional.empty();
    }
}
