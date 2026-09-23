package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    List<ServerMember> findByUserIdOrderByJoinedAtDesc(String userId);

    Optional<ServerMember> findByServerIdAndUserId(String serverId, String userId);

    List<ServerMember> findByServerId(String serverId);

    List<ServerMember> findByServerIdOrderByJoinedAtAscIdAsc(String serverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServerMember> findFirstByServerIdAndUserIdNotAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
            String serverId, String excludedUserId);

    void deleteByUserId(String userId);

    void deleteByServerId(String serverId);
}
