package com.example.liveChat.repositories;

import com.example.liveChat.models.DirectChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DirectChannelRepository extends JpaRepository<DirectChannel, String> {
    Optional<DirectChannel> findByParticipantOneIdAndParticipantTwoId(String participantOneId, String participantTwoId);

    List<DirectChannel> findByParticipantOneIdOrParticipantTwoIdOrderByCreatedAtDesc(String participantOneId,
                                                                                        String participantTwoId);
}
