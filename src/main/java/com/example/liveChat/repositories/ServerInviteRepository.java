package com.example.liveChat.repositories;

import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServerInviteRepository extends JpaRepository<ServerInvite, Long> {
    Optional<ServerInvite> findByIdAndInviteeId(Long id, String inviteeId);

    List<ServerInvite> findByInviteeIdAndStatusOrderByCreatedAtDesc(String inviteeId, ServerInviteStatus status);

    boolean existsByServerIdAndInviteeId(String serverId, String inviteeId);

    @Modifying
    @Query("delete from ServerInvite invite where invite.status = 'PENDING' and " +
            "(invite.inviter.id = :userId or invite.invitee.id = :userId)")
    void deleteActiveInvolvingUser(@Param("userId") String userId);

    void deleteByServerId(String serverId);
}
