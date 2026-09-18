package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerInviteRepository extends JpaRepository<ServerInvite, Long> {
    Optional<ServerInvite> findByIdAndInviteeId(Long id, String inviteeId);

    List<ServerInvite> findByInviteeIdAndStatusOrderByCreatedAtDesc(String inviteeId, ServerInviteStatus status);

    boolean existsByServerIdAndInviteeId(String serverId, String inviteeId);
}
