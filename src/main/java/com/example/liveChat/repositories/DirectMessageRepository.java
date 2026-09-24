package com.example.liveChat.repositories;

import com.example.liveChat.models.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    Page<DirectMessage> findByDirectChannelIdOrderByCreatedAtDescIdDesc(String directChannelId, Pageable pageable);

    boolean existsByLegacyMessageId(Long legacyMessageId);
}
