package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerChannelRepository extends JpaRepository<ServerChannel, String> {
    List<ServerChannel> findByServerIdOrderByPositionAscIdAsc(String serverId);

    long countByServerId(String serverId);

    void deleteByServerId(String serverId);
}
