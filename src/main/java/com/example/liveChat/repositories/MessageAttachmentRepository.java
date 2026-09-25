package com.example.liveChat.repositories;

import com.example.liveChat.models.MessageAttachment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attachment from MessageAttachment attachment where attachment.id in :ids")
    List<MessageAttachment> findAllByIdForUpdate(@Param("ids") Collection<String> ids);

    List<MessageAttachment> findByDirectMessageIdIn(Collection<Long> directMessageIds);

    List<MessageAttachment> findByChannelMessageIdIn(Collection<Long> channelMessageIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Page<MessageAttachment> findByDirectMessageIsNullAndChannelMessageIsNullAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            Instant expiresAt, Pageable pageable);
}
