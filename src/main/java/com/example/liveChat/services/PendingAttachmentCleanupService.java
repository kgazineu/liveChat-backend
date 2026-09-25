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

import java.time.Duration;
import java.time.Instant;

@Service
@Profile("!test")
public class PendingAttachmentCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PendingAttachmentCleanupService.class);
    private static final int BATCH_SIZE = 100;
    private static final Duration UPLOAD_SIGNATURE_REUSE_WINDOW = Duration.ofHours(1);

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
        // Retain the reservation until its Cloudinary upload signature can no longer recreate the asset.
        var expired = attachmentRepository
                .findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        Instant.now().minus(UPLOAD_SIGNATURE_REUSE_WINDOW), PageRequest.of(0, BATCH_SIZE));
        for (MessageAttachment attachment : expired) {
            try {
                objectStorage.delete(attachment.getObjectKey(), attachment.getContentType());
                attachmentRepository.delete(attachment);
            } catch (AttachmentStorageException exception) {
                LOGGER.warn("Could not remove expired pending attachment {} from object storage", attachment.getId());
            }
        }
    }
}
