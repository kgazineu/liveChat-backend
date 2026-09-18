package com.example.liveChat.repositories;

import com.example.liveChat.models.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    List<DirectMessage> findByDirectChannelIdOrderByCreatedAtAscIdAsc(String directChannelId);

    boolean existsByLegacyMessageId(Long legacyMessageId);
}
