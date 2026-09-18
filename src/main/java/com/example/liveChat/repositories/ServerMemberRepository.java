package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    List<ServerMember> findByUserIdOrderByJoinedAtDesc(String userId);

    Optional<ServerMember> findByServerIdAndUserId(String serverId, String userId);

    List<ServerMember> findByServerId(String serverId);
}
