package com.example.liveChat.services;

import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.DirectMessage;
import com.example.liveChat.models.LegacyMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.DirectMessageRepository;
import com.example.liveChat.repositories.LegacyMessageRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class LegacyDirectMessageMigration {
    private final LegacyMessageRepository legacyMessageRepository;
    private final DirectChannelRepository directChannelRepository;
    private final DirectMessageRepository directMessageRepository;

    public LegacyDirectMessageMigration(LegacyMessageRepository legacyMessageRepository,
                                        DirectChannelRepository directChannelRepository,
                                        DirectMessageRepository directMessageRepository) {
        this.legacyMessageRepository = legacyMessageRepository;
        this.directChannelRepository = directChannelRepository;
        this.directMessageRepository = directMessageRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyMessages() {
        legacyMessageRepository.findAllByOrderByTimestampAscIdAsc().forEach(this::migrate);
    }

    private void migrate(LegacyMessage legacyMessage) {
        if (directMessageRepository.existsByLegacyMessageId(legacyMessage.getId())
                || legacyMessage.getSender() == null || legacyMessage.getReceiver() == null
                || legacyMessage.getContent() == null || legacyMessage.getContent().isBlank()) {
            return;
        }

        User sender = legacyMessage.getSender();
        User receiver = legacyMessage.getReceiver();
        if (sender.getId().equals(receiver.getId())) {
            return;
        }
        boolean senderComesFirst = sender.getId().compareTo(receiver.getId()) < 0;
        User participantOne = senderComesFirst ? sender : receiver;
        User participantTwo = senderComesFirst ? receiver : sender;
        DirectChannel directChannel = directChannelRepository
                .findByParticipantOneIdAndParticipantTwoId(participantOne.getId(), participantTwo.getId())
                .orElseGet(() -> directChannelRepository.save(new DirectChannel(participantOne, participantTwo)));
        Instant createdAt = legacyMessage.getTimestamp() == null
                ? Instant.now()
                : legacyMessage.getTimestamp().toInstant(ZoneOffset.UTC);
        directMessageRepository.save(new DirectMessage(directChannel, sender, legacyMessage.getContent(), createdAt,
                legacyMessage.getId()));
    }
}
