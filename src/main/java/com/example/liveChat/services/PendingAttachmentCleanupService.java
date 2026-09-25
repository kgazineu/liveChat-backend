package com.example.liveChat.services;

import com.example.liveChat.infra.storage.AttachmentObjectStorage;
import com.example.liveChat.infra.storage.AttachmentStorageException;
import com.example.liveChat.models.MessageAttachment;
import com.example.liveChat.repositories.MessageAttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Profile("!test")
public class PendingAttachmentCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PendingAttachmentCleanupService.class);
    private static final int BATCH_SIZE = 100;

    private final MessageAttachmentRepository attachmentRepository;
    private final AttachmentObjectStorage objectStorage;

    public PendingAttachmentCleanupService(MessageAttachmentRepository attachmentRepository,
                                           AttachmentObjectStorage objectStorage) {
        this.attachmentRepository = attachmentRepository;
        this.objectStorage = objectStorage;
    }

    @Scheduled(fixedDelayString = "${livechat.attachments.cleanup-interval:60s}")
    @Transactional
    public void removeExpiredPendingAttachments() {
        var expired = attachmentRepository
                .findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (MessageAttachment attachment : expired) {
            try {
                objectStorage.delete(attachment.getObjectKey());
                attachmentRepository.delete(attachment);
            } catch (AttachmentStorageException exception) {
                LOGGER.warn("Could not remove expired pending attachment {} from object storage", attachment.getId());
            }
        }
    }
}
