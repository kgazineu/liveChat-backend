package com.example.liveChat.repositories;

import com.example.liveChat.models.PendingProfileUpdate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PendingProfileUpdateRepository extends JpaRepository<PendingProfileUpdate, Long> {
    @Query("select update.user.id from PendingProfileUpdate update where update.tokenHash = :tokenHash")
    Optional<String> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingProfileUpdate> findByTokenHash(String tokenHash);

    void deleteByUserId(String userId);
}
