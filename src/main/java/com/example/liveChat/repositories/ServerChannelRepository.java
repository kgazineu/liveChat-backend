package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerChannelRepository extends JpaRepository<ServerChannel, String> {
    List<ServerChannel> findByServerIdOrderByPositionAscIdAsc(String serverId);

    Page<ServerChannel> findByServerId(String serverId, Pageable pageable);

    long countByServerId(String serverId);

    void deleteByServerId(String serverId);
}
