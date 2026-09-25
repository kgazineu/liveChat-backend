package com.example.liveChat;

import com.example.liveChat.infra.storage.AttachmentObjectStorage;
import com.example.liveChat.infra.storage.AttachmentStorageException;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.MessageAttachment;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.MessageAttachmentRepository;
import com.example.liveChat.services.PendingAttachmentCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingAttachmentCleanupServiceTests {
    @Mock private MessageAttachmentRepository attachments;
    @Mock private AttachmentObjectStorage objectStorage;

    @Test
    void removesExpiredReservationOnlyAfterDeletingItsObject() {
        MessageAttachment expired = expiredAttachment();
        when(attachments.findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                any(Instant.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(expired)));
        PendingAttachmentCleanupService cleaner = new PendingAttachmentCleanupService(attachments, objectStorage);

        Instant beforeCleanup = Instant.now();
        cleaner.removeExpiredPendingAttachments();

        var cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(attachments).findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(beforeCleanup.minusSeconds(3600))
                .isBeforeOrEqualTo(Instant.now().minusSeconds(3600));
        var ordered = org.mockito.Mockito.inOrder(objectStorage, attachments);
        ordered.verify(objectStorage).delete(expired.getObjectKey(), expired.getContentType());
        ordered.verify(attachments).delete(expired);
    }

    @Test
    void keepsReservationForRetryWhenObjectStorageFails() {
        MessageAttachment expired = expiredAttachment();
        when(attachments.findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                any(Instant.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(expired)));
        doThrow(new AttachmentStorageException("DELETE")).when(objectStorage)
                .delete(expired.getObjectKey(), expired.getContentType());
        PendingAttachmentCleanupService cleaner = new PendingAttachmentCleanupService(attachments, objectStorage);

        cleaner.removeExpiredPendingAttachments();

        verify(attachments, never()).delete(expired);
    }

    private MessageAttachment expiredAttachment() {
        User author = new User("User", "user@example.test", "hash");
        author.setId("author-id");
        User participant = new User("Participant", "participant@example.test", "hash");
        participant.setId("participant-id");
        return MessageAttachment.forDirectChannel(new DirectChannel(author, participant), author,
                "message-attachments/author-id/object-id", "photo.png", "image/png", 100,
                10, 10, Instant.now().minusSeconds(1));
    }
}
