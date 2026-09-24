package com.example.liveChat.repositories;

import com.example.liveChat.models.ChannelMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    Page<ChannelMessage> findByChannelIdOrderByCreatedAtDescIdDesc(String channelId, Pageable pageable);

    void deleteByChannelServerId(String serverId);
}
