package com.example.liveChat.repositories;

import com.example.liveChat.models.LegacyMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegacyMessageRepository extends JpaRepository<LegacyMessage, Long> {
    List<LegacyMessage> findAllByOrderByTimestampAscIdAsc();
}
