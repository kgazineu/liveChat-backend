package com.example.liveChat.repositories;

import com.example.liveChat.models.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    List<ChannelMessage> findByChannelIdOrderByCreatedAtAscIdAsc(String channelId);

    void deleteByChannelServerId(String serverId);
}
